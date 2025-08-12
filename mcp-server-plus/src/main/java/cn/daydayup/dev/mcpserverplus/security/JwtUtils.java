package cn.daydayup.dev.mcpserverplus.security;

import io.jsonwebtoken.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Date;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * @ClassName JwtUtils
 * @Description JWT工具类
 * @Author ZhaoYanNing
 * @Date 2025/8/12 9:08
 * @Version 1.0
 */
@Component
public class JwtUtils {

    // 使用Keys.secretKeyFor()生成符合要求的安全密钥
    // 或者确保提供的密钥足够长(至少32字节)
    private SecretKey secretKey;

    @Value("${mcp.security.jwt-expiration-ms:86400000}")
    private int jwtExpirationMs;

    // 在构造函数中生成安全密钥
    public JwtUtils() {
        // 生成一个符合HS512要求的安全密钥
        this.secretKey = Keys.secretKeyFor(SignatureAlgorithm.HS512);
    }

    // 如果需要使用配置文件中的密钥，确保它足够长
    @Value("${mcp.security.jwt-secret:}")
    public void setJwtSecret(String jwtSecret) {
        if (jwtSecret != null && !jwtSecret.isEmpty()) {
            // 确保密钥足够长，如果不够长则使用默认安全密钥
            byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length >= 32) { // 至少32字节(256位)
                this.secretKey = Keys.hmacShaKeyFor(keyBytes);
            }
            // 如果密钥不够长，保持使用构造函数中生成的安全密钥
        }
    }

    public String generateToken(String userId, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .setSubject(userId)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    public String getUserIdFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.getSubject();
    }

    public String getRoleFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.get("role", String.class);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // Token无效
        }
        return false;
    }

    public boolean isTokenExpired(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.getExpiration().before(new Date());
    }

    public int getExpirationMs() {
        return jwtExpirationMs;
    }
}



