package com.storems.userservice.service;

import com.storems.userservice.dto.RegisterRequest;
import com.storems.userservice.mapper.UserMapper;
import com.storems.userservice.mapper.UserTokenMapper;
import com.storems.userservice.po.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class AdminUserService {
    private final LoginService login;
    private final UserService registration;
    private final UserMapper users;
    private final UserTokenMapper tokens;
    public AdminUserService(LoginService login, UserService registration, UserMapper users, UserTokenMapper tokens) {
        this.login = login; this.registration = registration; this.users = users; this.tokens = tokens;
    }
    private Long requireAdmin(String authorization) {
        Map<String, Object> me = login.currentUser(authorization);
        if (!"ADMIN".equals(me.get("role"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "administrator required");
        return (Long) me.get("userId");
    }
    public List<Map<String, Object>> list(String authorization) {
        requireAdmin(authorization);
        List<Map<String, Object>> result = new ArrayList<>();
        for (User user : users.listUsers()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", user.getId()); row.put("username", user.getUsername());
            row.put("role", user.getRole()); row.put("enabled", user.getEnabled()); row.put("createdAt", user.getCreatedAt());
            result.add(row);
        }
        return result;
    }
    public Long create(String authorization, RegisterRequest request) {
        requireAdmin(authorization);
        return registration.register(request);
    }
    @Transactional
    public void setEnabled(String authorization, Long id, Boolean enabled) {
        Long caller = requireAdmin(authorization);
        if (enabled == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "enabled required");
        User target = lockedUser(id);
        if (caller.equals(id) || "ADMIN".equals(target.getRole()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot disable administrator");
        users.setEnabled(id, enabled);
        if (!enabled) tokens.deleteForUser(id);
    }
    @Transactional
    public void resetPassword(String authorization, Long id, String password) {
        Long caller = requireAdmin(authorization);
        User target = lockedUser(id);
        if ("ADMIN".equals(target.getRole()) && !caller.equals(id))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "cannot reset another administrator");
        if (password == null || password.trim().isEmpty() || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid password");
        users.resetPassword(id, new BCryptPasswordEncoder().encode(password));
        tokens.deleteForUser(id);
    }
    @Transactional
    public void delete(String authorization, Long id) {
        Long caller = requireAdmin(authorization);
        User target = lockedUser(id);
        if (caller.equals(id) || "ADMIN".equals(target.getRole()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot delete administrator or yourself");
        if (users.softDelete(id) != 1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found");
        tokens.deleteForUser(id);
    }

    private User lockedUser(Long id) {
        if (users.lockUser(id) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found");
        return users.findById(id);
    }
}
