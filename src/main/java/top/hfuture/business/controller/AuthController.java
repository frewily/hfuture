package top.hfuture.business.controller;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import top.hfuture.business.dto.CaptchaResponse;
import top.hfuture.business.dto.LoginRequest;
import top.hfuture.business.dto.LoginResponse;
import top.hfuture.business.model.SessionInfo;
import top.hfuture.business.service.CasAuthService;
import top.hfuture.business.service.CourseTableFacadeService;
import top.hfuture.business.service.CourseTableService;
import top.hfuture.business.service.SsoAuthService;
import top.hfuture.common.dto.Result;
import top.hfuture.common.util.JwtUtil;

import java.util.Base64;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final CasAuthService casAuthService;
    private final SsoAuthService ssoAuthService;
    private final CourseTableService courseTableService;
    private final CourseTableFacadeService courseTableFacadeService;
    private final StringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil;
    
    private static final String LOGIN_SESSION_PREFIX = "hfut:login-session:";
    private static final long LOGIN_SESSION_TIMEOUT_MINUTES = 5;
    
    @GetMapping("/captcha")
    public Result<CaptchaResponse> getCaptcha() {
        try {
            log.info("开始获取验证码...");
            
            SessionInfo sessionInfo = casAuthService.initCasSession();
            byte[] imageBytes = casAuthService.getCaptchaImage(sessionInfo);
            
            String loginSessionId = UUID.randomUUID().toString().replace("-", "");
            String key = LOGIN_SESSION_PREFIX + loginSessionId;
            String value = JSON.toJSONString(sessionInfo);
            redisTemplate.opsForValue().set(key, value, LOGIN_SESSION_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            
            String base64Image = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(imageBytes);
            
            CaptchaResponse response = CaptchaResponse.builder()
                    .loginSessionId(loginSessionId)
                    .captchaImage(base64Image)
                    .build();
            
            log.info("验证码获取成功，loginSessionId: {}", loginSessionId);
            return Result.success("获取验证码成功", response);
        } catch (Exception e) {
            log.error("获取验证码失败", e);
            return Result.error(500, "获取验证码失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        try {
            log.info("开始登录，studentId: {}", request.getStudentId());
            
            if (StrUtil.isEmpty(request.getLoginSessionId())) {
                return Result.error(400, "loginSessionId 不能为空");
            }
            
            if (StrUtil.isEmpty(request.getStudentId())) {
                return Result.error(400, "学号不能为空");
            }
            
            if (StrUtil.isEmpty(request.getPassword())) {
                return Result.error(400, "密码不能为空");
            }
            
            String key = LOGIN_SESSION_PREFIX + request.getLoginSessionId();
            String json = redisTemplate.opsForValue().get(key);
            
            if (json == null) {
                return Result.error(403, "验证码会话已过期，请重新获取验证码");
            }
            
            SessionInfo sessionInfo = JSON.parseObject(json, SessionInfo.class);
            redisTemplate.delete(key);
            
            boolean loginSuccess = casAuthService.casLogin(
                    sessionInfo,
                    request.getStudentId(),
                    request.getPassword(),
                    request.getCaptcha() != null ? request.getCaptcha() : ""
            );
            
            if (!loginSuccess) {
                return Result.error(403, "登录失败，请检查用户名、密码或验证码");
            }
            
            // 步骤4：获取教务系统 Ticket
            String ticket = ssoAuthService.getTicket(sessionInfo);
            if (ticket == null) {
                return Result.error(500, "获取 Ticket 失败");
            }
            
            // 步骤5：激活教务 SESSION
            boolean activated = ssoAuthService.activateSession(sessionInfo, ticket);
            if (!activated) {
                return Result.error(500, "激活教务 SESSION 失败");
            }
            
            // 步骤6：获取 dataId
            Long dataId = courseTableService.getDataId(sessionInfo);
            if (dataId == null) {
                return Result.error(500, "获取 dataId 失败");
            }
            
            boolean envSuccess = courseTableService.getEnvironmentVariables(sessionInfo);
            if (!envSuccess) {
                return Result.error(500, "获取环境变量失败");
            }
            
            String token = jwtUtil.generateToken(request.getStudentId());
            
            String sessionKey = "hfut:session:" + request.getStudentId();
            redisTemplate.opsForValue().set(sessionKey, JSON.toJSONString(sessionInfo), 3, TimeUnit.HOURS);
            
            LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder()
                    .studentId(request.getStudentId())
                    .name(sessionInfo.getStudentName() != null ? sessionInfo.getStudentName() : "用户" + request.getStudentId())
                    .major(sessionInfo.getMajor() != null ? sessionInfo.getMajor() : "未知专业")
                    .college(sessionInfo.getCollege() != null ? sessionInfo.getCollege() : "未知学院")
                    .avatarFrame("beta_tester")
                    .build();
            
            LoginResponse response = LoginResponse.builder()
                    .token(token)
                    .userInfo(userInfo)
                    .build();
            
            log.info("登录成功，studentId: {}", request.getStudentId());
            return Result.success("登录成功", response);
        } catch (Exception e) {
            log.error("登录失败", e);
            return Result.error(500, "登录失败: " + e.getMessage());
        }
    }
}
