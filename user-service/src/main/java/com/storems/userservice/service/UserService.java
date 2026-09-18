package com.storems.userservice.service;

import com.storems.userservice.dto.RegisterRequest;
import com.storems.userservice.mapper.UserMapper;
import com.storems.userservice.po.User;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public Long register(RegisterRequest request) {
        if (request == null || request.getUsername() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "username is required");
        }

        String username = request.getUsername().trim();
        String password = request.getPassword();

        if (!username.matches("[a-zA-Z0-9_]{3,50}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "username must contain 3-50 letters, digits or underscores");
        }

        if (password == null || password.trim().isEmpty()
                || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "password must have at least 8 characters and at most 72 UTF-8 bytes");
        }

        if (userMapper.findByUsername(username) != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "username already exists");
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));

        try {
            if (userMapper.insert(user) != 1) {
                throw new IllegalStateException("Failed to insert user");
            }
        } catch (DuplicateKeyException ex) {
            // 两个请求同时注册同一用户名时，由数据库唯一索引兜底。
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "username already exists");
        }

        return user.getId();
    }
}
