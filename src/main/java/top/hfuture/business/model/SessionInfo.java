package top.hfuture.business.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionInfo implements Serializable {

    private String studentNo;
    
    private String tgc;
    
    private String session;
    
    private Long dataId;
    
    private Integer bizTypeId;
    
    private Integer semesterId;
    
    private String studentName;
    
    private String major;
    
    private String college;
    
    @Builder.Default
    private Map<String, String> cookies = new HashMap<>();
    
    public void addCookie(String name, String value) {
        cookies.put(name, value);
    }
    
    public String getCookie(String name) {
        return cookies.get(name);
    }
    
    public String getCookieHeader() {
        StringBuilder sb = new StringBuilder();
        cookies.forEach((key, value) -> {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(key).append("=").append(value);
        });
        return sb.toString();
    }
}
