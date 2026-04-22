package top.hfuture.business.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    
    private String loginSessionId;
    
    private String studentId;
    
    private String password;
    
    private String captcha;
}
