package top.hfuture.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hfut")
public class HfutProperties {

    private CasConfig cas = new CasConfig();
    private EamsConfig eams = new EamsConfig();

    @Data
    public static class CasConfig {
        private String baseUrl;
        private String loginUrl;
        private String vercodeUrl;
        private String checkInitUrl;
    }

    @Data
    public static class EamsConfig {
        private String baseUrl;
        private String ssoLoginUrl;
        private String courseTableUrl;
        private String getDataUrl;
        private String scheduleDatumUrl;
        private String studentInfoUrl;
    }
}
