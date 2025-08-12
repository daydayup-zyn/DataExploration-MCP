package cn.daydayup.dev.mcpserverplus.controller;

import cn.daydayup.dev.mcpserverplus.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import jakarta.annotation.PostConstruct;

/**
 * @ClassName AuthController
 * @Description 鉴权服务
 * @Author ZhaoYanNing
 * @Date 2025/8/12 9:09
 * @Version 1.0
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtUtils jwtUtils;

    private final PasswordEncoder passwordEncoder;

    // 模拟用户存储，实际应用中应使用数据库
    private Map<String, String> userStorage = new HashMap<>();
    private Map<String, String> userRoles = new HashMap<>();

    @PostConstruct
    public void init() {
        // 添加一个默认用户用于测试
        String encodedPassword = passwordEncoder.encode("123456");
        userStorage.put("admin", encodedPassword);
        userRoles.put("admin", "ADMIN");

        // 添加普通用户
        userStorage.put("user", encodedPassword);
        userRoles.put("user", "USER");
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody Map<String, String> loginRequest) {
        String username = loginRequest.get("username");
        String password = loginRequest.get("password");

        // 验证用户凭据
        String storedPassword = userStorage.get(username);
        if (storedPassword == null || !passwordEncoder.matches(password, storedPassword)) {
            return ResponseEntity.badRequest().body("Invalid username or password");
        }

        // 生成JWT token
        String role = userRoles.get(username);
        String token = jwtUtils.generateToken(username, role);

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("username", username);
        response.put("role", role);
        response.put("expiresIn", jwtUtils.getExpirationMs());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody Map<String, String> registerRequest) {
        String username = registerRequest.get("username");
        String password = registerRequest.get("password");

        if (userStorage.containsKey(username)) {
            return ResponseEntity.badRequest().body("Username already exists");
        }

        userStorage.put(username, passwordEncoder.encode(password));
        userRoles.put(username, "USER"); // 默认角色为USER

        Map<String, String> response = new HashMap<>();
        response.put("message", "User registered successfully");

        return ResponseEntity.ok(response);
    }
}


