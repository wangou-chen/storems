package com.storems.userservice.service;

import com.storems.userservice.dto.LoginRequest;
import com.storems.userservice.mapper.UserMapper;
import com.storems.userservice.mapper.UserTokenMapper;
import com.storems.userservice.po.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class LoginService {

    private final UserMapper userMapper;
    private final UserTokenMapper tokenMapper;
    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    // 用户不存在时也执行一次密码校验，减小两种失败情况的耗时差异。
    private final String dummyPasswordHash =
            passwordEncoder.encode("unused-dummy-password");

    public LoginService(UserMapper userMapper,
                        UserTokenMapper tokenMapper) {
        this.userMapper = userMapper;
        this.tokenMapper = tokenMapper;
    }

    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> login(LoginRequest request) {
        if (request == null
                || request.getUsername() == null
                || request.getPassword() == null) {
            throw unauthorized();
        }

        String username = request.getUsername().trim();
        String password = request.getPassword();

        if (!username.matches("[a-zA-Z0-9_]{3,50}")
                || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw unauthorized();
        }

        User user = userMapper.findByUsername(username);
        if (user != null) {
            userMapper.lockUser(user.getId());
            user = userMapper.findByUsername(username);
        }

        String passwordHash = user == null
                ? dummyPasswordHash
                : user.getPasswordHash();

        boolean passwordMatches =
                passwordEncoder.matches(password, passwordHash);

        if (user == null || !Boolean.TRUE.equals(user.getEnabled()) || !passwordMatches) {
            throw unauthorized();
        }

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);

        String token = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        int rows = tokenMapper.insert(sha256(token), user.getId());
        if (rows != 1) {
            throw new IllegalStateException("Failed to save login token");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "login success");
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("role", user.getRole());
        result.put("token", token);
        result.put("tokenType", "Bearer");
        result.put("expiresIn", 7200);
        return result;
    }

    public Long validateToken(String authorization) {
        if (authorization == null
                || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid token");
        }
        String token = authorization.substring(7);
        if (!token.matches("[A-Za-z0-9_-]{43}")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid token");
        }
        Long userId = tokenMapper.findValidUserId(sha256(token));
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid or expired token");
        }
        return userId;
    }

    public Map<String, Object> currentUser(String authorization) {
        User user = userMapper.findById(validateToken(authorization));
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid user");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("role", user.getRole());
        return result;
    }

    public void logout(String authorization) {
        validateToken(authorization);
        tokenMapper.delete(sha256(authorization.substring(7)));
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "invalid username or password");
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8));

            StringBuilder result = new StringBuilder(64);
            for (byte b : bytes) {
                result.append(Character.forDigit((b & 0xff) >>> 4, 16));
                result.append(Character.forDigit(b & 0x0f, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
