package top.hfuture;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.hfuture.business.model.CourseTableResponse;
import top.hfuture.business.model.SessionInfo;
import top.hfuture.business.service.CasAuthService;
import top.hfuture.business.service.CourseTableService;
import top.hfuture.business.service.SsoAuthService;

import java.io.IOException;
import java.util.List;

@Slf4j
@SpringBootTest
class HFutureApplicationTests {

    @Autowired
    private CasAuthService casAuthService;

    @Autowired
    private SsoAuthService ssoAuthService;

    @Autowired
    private CourseTableService courseTableService;

    @Test
    void testCasAuthFlow() throws IOException {
        log.info("=== 测试 CAS 认证流程 ===");

        SessionInfo sessionInfo = casAuthService.initCasSession();
        log.info("初始化会话成功，execution: {}", sessionInfo.getCookie("execution"));

        casAuthService.getVercodeAndFlavoring(sessionInfo);
        log.info("获取验证码和风控 Cookie 成功");

        log.info("请在实际测试时输入真实的用户名、密码和验证码");
    }

    @Test
    void testFullFlow() throws IOException {
        log.info("=== 测试完整流程 ===");

        String username = "YOUR_USERNAME";
        String password = "YOUR_PASSWORD";
        String captcha = "YOUR_CAPTCHA";

        log.warn("请替换为真实的测试账号信息后再运行此测试");
    }
}
