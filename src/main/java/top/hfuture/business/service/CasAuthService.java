package top.hfuture.business.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import top.hfuture.business.model.SessionInfo;
import top.hfuture.config.HfutProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class CasAuthService {

    private final OkHttpClient okHttpClient;
    private final HfutProperties hfutProperties;
    private final top.hfuture.config.MockProperties mockProperties;

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String AES_KEY = "hfut1234567890ab";
    private static final Pattern EXECUTION_PATTERN = Pattern.compile("name=\"execution\"\\s+value=\"([^\"]+)\"");

    public SessionInfo initCasSession() throws IOException {
        if (mockProperties.isEnabled()) {
            return mockInitCasSession();
        }
        
        log.info("开始初始化 CAS 会话...");
        
        SessionInfo sessionInfo = SessionInfo.builder()
                .cookies(new java.util.HashMap<>())
                .build();

        Request request = new Request.Builder()
                .url(hfutProperties.getCas().getLoginUrl())
                .header("User-Agent", USER_AGENT)
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("初始化 CAS 会话失败: " + response.code());
            }

            extractCookies(response, sessionInfo);

            String html = response.body().string();
            String execution = extractExecution(html);
            if (StrUtil.isEmpty(execution)) {
                throw new RuntimeException("无法提取 execution 参数");
            }
            
            sessionInfo.addCookie("execution", execution);
            log.info("CAS 会话初始化成功，execution: {}", execution);
            
            return sessionInfo;
        }
    }

    private SessionInfo mockInitCasSession() {
        log.info("[Mock] 初始化 CAS 会话...");
        SessionInfo sessionInfo = SessionInfo.builder()
                .cookies(new java.util.HashMap<>())
                .build();
        sessionInfo.addCookie("SESSIONID", "MOCK_SESSION_" + System.currentTimeMillis());
        sessionInfo.addCookie("execution", "e1s1");
        log.info("[Mock] CAS 会话初始化成功");
        return sessionInfo;
    }

    public void getVercodeAndFlavoring(SessionInfo sessionInfo) throws IOException {
        log.info("开始获取验证码和风控 Cookie...");

        Request vercodeRequest = new Request.Builder()
                .url(hfutProperties.getCas().getVercodeUrl())
                .header("User-Agent", USER_AGENT)
                .header("Cookie", sessionInfo.getCookieHeader())
                .build();

        try (Response vercodeResponse = okHttpClient.newCall(vercodeRequest).execute()) {
            extractCookies(vercodeResponse, sessionInfo);
            log.info("验证码 Cookie 获取成功");
        }

        Request flavoringRequest = new Request.Builder()
                .url(hfutProperties.getCas().getCheckInitUrl())
                .header("User-Agent", USER_AGENT)
                .header("Cookie", sessionInfo.getCookieHeader())
                .build();

        try (Response flavoringResponse = okHttpClient.newCall(flavoringRequest).execute()) {
            extractCookies(flavoringResponse, sessionInfo);
            log.info("风控 Cookie 获取成功");
        }
    }

    public boolean casLogin(SessionInfo sessionInfo, String username, String password, String captcha) throws IOException {
        if (mockProperties.isEnabled()) {
            return mockCasLogin(sessionInfo, username, password, captcha);
        }
        
        log.info("开始 CAS 登录，用户名: {}", username);

        String secretKey = sessionInfo.getCookie("LOGIN_FLAVORING");
        if (StrUtil.isEmpty(secretKey)) {
            log.error("无法获取 LOGIN_FLAVORING Cookie");
            return false;
        }

        String encryptedPassword = encryptPassword(password, secretKey);
        String execution = sessionInfo.getCookie("execution");

        RequestBody formBody = new FormBody.Builder()
                .add("username", username)
                .add("password", encryptedPassword)
                .add("execution", execution)
                .add("_eventId", "submit")
                .add("captcha", captcha)
                .build();

        Request request = new Request.Builder()
                .url(hfutProperties.getCas().getLoginUrl())
                .header("User-Agent", USER_AGENT)
                .header("Cookie", sessionInfo.getCookieHeader())
                .post(formBody)
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            extractCookies(response, sessionInfo);

            String tgc = sessionInfo.getCookie("TGC");
            if (StrUtil.isNotEmpty(tgc)) {
                sessionInfo.setTgc(tgc);
                sessionInfo.setStudentNo(username);
                log.info("CAS 登录成功，TGC: {}", tgc);
                return true;
            }

            log.error("CAS 登录失败，未获取到 TGC");
            return false;
        }
    }

    private boolean mockCasLogin(SessionInfo sessionInfo, String username, String password, String captcha) {
        log.info("[Mock] CAS 登录，用户名: {}", username);
        
        String expectedCaptcha = mockProperties.getCaptcha();
        if (expectedCaptcha.equals(captcha)) {
            String mockTgc = "MOCK_TGC_" + System.currentTimeMillis();
            sessionInfo.setTgc(mockTgc);
            sessionInfo.setStudentNo(username);
            sessionInfo.addCookie("TGC", mockTgc);
            log.info("[Mock] CAS 登录成功，TGC: {}", mockTgc);
            return true;
        }
        
        log.error("[Mock] CAS 登录失败，验证码错误（期望: {}, 实际: {}）", expectedCaptcha, captcha);
        return false;
    }

    private String encryptPassword(String password, String secretKey) {
        cn.hutool.crypto.symmetric.AES aes = cn.hutool.crypto.SecureUtil.aes(secretKey.getBytes(StandardCharsets.UTF_8));
        return aes.encryptBase64(password);
    }

    private void extractCookies(Response response, SessionInfo sessionInfo) {
        Headers headers = response.headers();
        for (String name : headers.names()) {
            if ("Set-Cookie".equalsIgnoreCase(name)) {
                for (String cookie : headers.values(name)) {
                    String[] parts = cookie.split(";")[0].split("=", 2);
                    if (parts.length == 2) {
                        sessionInfo.addCookie(parts[0].trim(), parts[1].trim());
                    }
                }
            }
        }
    }

    private String extractExecution(String html) {
        Matcher matcher = EXECUTION_PATTERN.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        Document doc = Jsoup.parse(html);
        Element input = doc.selectFirst("input[name=execution]");
        return input != null ? input.val() : null;
    }

    public byte[] getCaptchaImage(SessionInfo sessionInfo) throws IOException {
        if (mockProperties.isEnabled()) {
            return mockGetCaptchaImage(sessionInfo);
        }
        
        log.info("开始获取验证码图片和风控 Cookie...");

        Request flavoringRequest = new Request.Builder()
                .url(hfutProperties.getCas().getCheckInitUrl())
                .header("User-Agent", USER_AGENT)
                .header("Cookie", sessionInfo.getCookieHeader())
                .build();

        try (Response flavoringResponse = okHttpClient.newCall(flavoringRequest).execute()) {
            extractCookies(flavoringResponse, sessionInfo);
            log.info("风控 Cookie (LOGIN_FLAVORING) 获取成功");
        }

        Request vercodeRequest = new Request.Builder()
                .url(hfutProperties.getCas().getVercodeUrl())
                .header("User-Agent", USER_AGENT)
                .header("Cookie", sessionInfo.getCookieHeader())
                .build();

        try (Response vercodeResponse = okHttpClient.newCall(vercodeRequest).execute()) {
            if (!vercodeResponse.isSuccessful()) {
                throw new RuntimeException("获取验证码失败: " + vercodeResponse.code());
            }

            extractCookies(vercodeResponse, sessionInfo);
            log.info("验证码 Cookie (JSESSIONID) 获取成功");

            try (ResponseBody body = vercodeResponse.body()) {
                if (body == null) {
                    throw new RuntimeException("验证码响应体为空");
                }
                return body.bytes();
            }
        }
    }

    private byte[] mockGetCaptchaImage(SessionInfo sessionInfo) {
        log.info("[Mock] 获取验证码图片...");
        sessionInfo.addCookie("JSESSIONID", "MOCK_JSESSION_" + System.currentTimeMillis());
        sessionInfo.addCookie("LOGIN_FLAVORING", "mock_flavoring_key_16");
        log.info("[Mock] 验证码和风控 Cookie 获取成功");
        
        String captchaText = mockProperties.getCaptcha();
        String mockImage = "Mock Captcha: " + captchaText;
        return mockImage.getBytes(StandardCharsets.UTF_8);
    }
}
