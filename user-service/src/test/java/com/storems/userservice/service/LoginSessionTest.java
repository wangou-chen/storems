package com.storems.userservice.service;

import com.storems.userservice.dto.LoginRequest;
import com.storems.userservice.mapper.UserMapper;
import com.storems.userservice.mapper.UserTokenMapper;
import com.storems.userservice.po.User;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class LoginSessionTest {
    @Test
    public void meDoesNotExposePasswordAndLogoutRevokesOnlyCurrentSession() {
        User user = new User();
        user.setId(7L); user.setUsername("warehouse_user");
        user.setPasswordHash(new BCryptPasswordEncoder().encode("Test123456"));
        UserMapper users = new UserMapper() {
            public User findById(Long id) { return user; }
            public int softDelete(Long id) { return 1; }
            public Long lockUser(Long id) { return id; }
            public java.util.List<User> listUsers() { return java.util.Collections.singletonList(user); }
            public int setEnabled(Long id, boolean enabled) { user.setEnabled(enabled); return 1; }
            public int resetPassword(Long id, String hash) { user.setPasswordHash(hash); return 1; }
            public User findByUsername(String username) { return user; }
            public int insert(User value) { throw new UnsupportedOperationException(); }
        };
        Map<String, Long> saved = new HashMap<>();
        UserTokenMapper tokens = new UserTokenMapper() {
            public int deleteForUser(Long id) { saved.clear(); return 1; }
            public Long findValidUserId(String hash) { return saved.get(hash); }
            public int insert(String hash, Long id) { saved.put(hash, id); return 1; }
            public int delete(String hash) { return saved.remove(hash) == null ? 0 : 1; }
        };
        LoginService service = new LoginService(users, tokens);
        LoginRequest request = new LoginRequest();
        request.setUsername("warehouse_user"); request.setPassword("Test123456");
        String first = "Bearer " + service.login(request).get("token");
        String second = "Bearer " + service.login(request).get("token");
        Map<String, Object> me = service.currentUser(first);
        assertEquals(7L, me.get("userId"));
        assertEquals("warehouse_user", me.get("username"));
        assertEquals(3, me.size());
        assertFalse(saved.containsKey(first.substring(7)));
        service.logout(first);
        try { service.validateToken(first); fail("Revoked token must fail"); }
        catch (ResponseStatusException error) { assertEquals(HttpStatus.UNAUTHORIZED, error.getStatus()); }
        assertEquals(Long.valueOf(7), service.validateToken(second));
        assertEquals(1, saved.size());
    }
}
