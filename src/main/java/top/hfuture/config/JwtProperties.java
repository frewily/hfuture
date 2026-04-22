package top.hfuture.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    
    private String secret = "hfuture_jwt_secret_key_2025_very_long_secret_for_security";
    
    private long expiration = 86400000;
    
    private String header = "Authorization";
    
    private String prefix = "Bearer ";
}
