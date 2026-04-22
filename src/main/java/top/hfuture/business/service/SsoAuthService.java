package top.hfuture.business.service;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import top.hfuture.business.model.SessionInfo;
import top.hfuture.config.HfutProperties;

import java.io.IOException;
import java.net.HttpURLConnection;

@Slf4j
@Service
public class SsoAuthService {

    private final OkHttpClient noRedirectClient;
    private final HfutProperties hfutProperties;

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int MAX_REDIRECTS = 10;

    @Autowired
    public SsoAuthService(
            @Qualifier("noRedirectClient") OkHttpClient noRedirectClient,
            HfutProperties hfutProperties) {
        this.noRedirectClient = noRedirectClient;
        this.hfutProperties = hfutProperties;
    }

    public String getTicket(SessionInfo sessionInfo) throws IOException {
        log.info("开始申请教务系统 Ticket...");

        String serviceUrl = hfutProperties.getEams().getSsoLoginUrl();
        String url = hfutProperties.getCas().getLoginUrl() + "?service=" + serviceUrl;

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Cookie", sessionInfo.getCookieHeader())
                .build();

        try (Response response = noRedirectClient.newCall(request).execute()) {
            if (response.code() != HttpURLConnection.HTTP_MOVED_TEMP && 
                response.code() != HttpURLConnection.HTTP_MOVED_PERM) {
                log.error("获取 Ticket 失败，状态码: {}", response.code());
                return null;
            }

            String location = response.header("Location");
            if (StrUtil.isEmpty(location)) {
                log.error("响应头中没有 Location");
                return null;
            }

            String ticket = extractTicket(location);
            if (StrUtil.isEmpty(ticket)) {
                log.error("无法从 Location 中提取 Ticket: {}", location);
                return null;
            }

            log.info("Ticket 获取成功: {}", ticket);
            return ticket;
        }
    }

    public boolean activateSession(SessionInfo sessionInfo, String ticket) throws IOException {
        log.info("开始激活教务 SESSION...");

        String url = hfutProperties.getEams().getSsoLoginUrl() + "?ticket=" + ticket;
        
        int redirectCount = 0;
        
        while (redirectCount < MAX_REDIRECTS) {
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Cookie", sessionInfo.getCookieHeader())
                    .build();

            try (Response response = noRedirectClient.newCall(request).execute()) {
                extractCookies(response, sessionInfo);
                
                if (response.code() == HttpURLConnection.HTTP_OK) {
                    String session = sessionInfo.getCookie("SESSION");
                    if (StrUtil.isNotEmpty(session)) {
                        sessionInfo.setSession(session);
                        log.info("教务 SESSION 激活成功: {}", session);
                        return true;
                    }
                    log.error("教务 SESSION 激活失败，未获取到 SESSION Cookie");
                    return false;
                }
                
                if (response.code() == HttpURLConnection.HTTP_MOVED_TEMP || 
                    response.code() == HttpURLConnection.HTTP_MOVED_PERM ||
                    response.code() == HttpURLConnection.HTTP_SEE_OTHER) {
                    String location = response.header("Location");
                    if (StrUtil.isEmpty(location)) {
                        log.error("重定向响应中没有 Location");
                        return false;
                    }
                    
                    log.info("跟随重定向 #{}: {} -> {}", redirectCount + 1, url, location);
                    url = location;
                    redirectCount++;
                    continue;
                }
                
                log.error("激活 SESSION 失败，意外的状态码: {}", response.code());
                return false;
            }
        }
        
        log.error("激活 SESSION 失败，超过最大重定向次数: {}", MAX_REDIRECTS);
        return false;
    }

    private void extractCookies(Response response, SessionInfo sessionInfo) {
        Headers headers = response.headers();
        for (String name : headers.names()) {
            if ("Set-Cookie".equalsIgnoreCase(name)) {
                for (String cookie : headers.values(name)) {
                    String[] parts = cookie.split(";")[0].split("=", 2);
                    if (parts.length == 2) {
                        sessionInfo.addCookie(parts[0].trim(), parts[1].trim());
                        log.debug("提取到 Cookie: {} = {}", parts[0].trim(), parts[1].trim());
                    }
                }
            }
        }
    }

    private String extractTicket(String location) {
        int ticketIndex = location.indexOf("ticket=");
        if (ticketIndex == -1) {
            return null;
        }
        String ticket = location.substring(ticketIndex + 7);
        int ampIndex = ticket.indexOf("&");
        if (ampIndex != -1) {
            ticket = ticket.substring(0, ampIndex);
        }
        return ticket;
    }
}
