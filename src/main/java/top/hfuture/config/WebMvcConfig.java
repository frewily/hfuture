package top.hfuture.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
    
    private final JwtAuthenticationInterceptor jwtAuthenticationInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthenticationInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        "/api/v1/auth/captcha",
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/auth/forgot-password",
                        "/error"
                );
    }
}
