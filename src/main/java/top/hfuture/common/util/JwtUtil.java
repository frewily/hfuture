package top.hfuture.common.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.hfuture.config.JwtProperties;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {
    
    private final JwtProperties jwtProperties;
    
    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
    
    public String generateToken(String studentId, Map<String, Object> claims) {
        if (claims == null) {
            claims = new HashMap<>();
        }
        claims.put("studentId", studentId);
        
        return Jwts.builder()
                .claims(claims)
                .subject(studentId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getExpiration()))
                .signWith(getSecretKey())
                .compact();
    }
    
    public String generateToken(String studentId) {
        return generateToken(studentId, null);
    }
    
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.error("JWT Token 已过期: {}", e.getMessage());
            throw new RuntimeException("Token 已过期，请重新登录");
        } catch (UnsupportedJwtException e) {
            log.error("不支持的 JWT Token: {}", e.getMessage());
            throw new RuntimeException("不支持的 Token 格式");
        } catch (MalformedJwtException e) {
            log.error("JWT Token 格式错误: {}", e.getMessage());
            throw new RuntimeException("Token 格式错误");
        } catch (IllegalArgumentException e) {
            log.error("JWT Token 为空: {}", e.getMessage());
            throw new RuntimeException("Token 不能为空");
        } catch (Exception e) {
            log.error("JWT Token 解析失败: {}", e.getMessage());
            throw new RuntimeException("Token 解析失败");
        }
    }
    
    public String getStudentIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("studentId", String.class);
    }
    
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
    
    public String refreshToken(String token) {
        Claims claims = parseToken(token);
        String studentId = claims.get("studentId", String.class);
        return generateToken(studentId);
    }
}
