package top.hfuture.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hfut.mock")
public class MockProperties {
    
    private boolean enabled = false;
    
    private String captcha = "1234";
    
    private String username = "test_user";
    
    private String password = "test_password";
}
