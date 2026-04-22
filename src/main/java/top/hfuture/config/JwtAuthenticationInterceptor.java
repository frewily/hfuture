package top.hfuture.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import top.hfuture.common.util.JwtUtil;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationInterceptor implements HandlerInterceptor {
    
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestURI = request.getRequestURI();
        log.debug("JWT 拦截器处理请求: {}", requestURI);
        
        String token = extractToken(request);
        
        if (!StringUtils.hasText(token)) {
            log.warn("请求未携带 Token: {}", requestURI);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"msg\":\"未登录或 Token 缺失\",\"data\":null}");
            return false;
        }
        
        try {
            if (!jwtUtil.validateToken(token)) {
                log.warn("Token 验证失败: {}", requestURI);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"msg\":\"Token 无效或已过期\",\"data\":null}");
                return false;
            }
            
            String studentId = jwtUtil.getStudentIdFromToken(token);
            request.setAttribute("studentId", studentId);
            log.debug("Token 验证成功, studentId: {}", studentId);
            
            return true;
        } catch (Exception e) {
            log.error("Token 解析异常: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"msg\":\"Token 解析失败: " + e.getMessage() + "\",\"data\":null}");
            return false;
        }
    }
    
    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader(jwtProperties.getHeader());
        
        if (StringUtils.hasText(authHeader) && authHeader.startsWith(jwtProperties.getPrefix())) {
            return authHeader.substring(jwtProperties.getPrefix().length());
        }
        
        return request.getParameter("token");
    }
}
