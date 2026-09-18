package com.storems.userservice.service;

import com.storems.userservice.mapper.*;
import com.storems.userservice.po.User;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.Assert.*;

public class AdminUserTest {
    @Test
    public void permissionsDisableAndPasswordReset() {
        User admin = new User(); admin.setId(1L); admin.setUsername("admin"); admin.setRole("ADMIN");
        User operator = new User(); operator.setId(2L); operator.setUsername("operator");
        Map<Long, User> users = new HashMap<>(); users.put(1L, admin); users.put(2L, operator);
        List<Long> revoked = new ArrayList<>();
        UserMapper mapper = (UserMapper) Proxy.newProxyInstance(UserMapper.class.getClassLoader(), new Class[]{UserMapper.class}, (p,m,a) -> {
            switch (m.getName()) {
                case "findById": return users.get(a[0]);
                case "lockUser": return users.containsKey(a[0]) ? a[0] : null;
                case "listUsers": return new ArrayList<>(users.values());
                case "setEnabled": users.get(a[0]).setEnabled((Boolean)a[1]); return 1;
                case "resetPassword": users.get(a[0]).setPasswordHash((String)a[1]); return 1;
                case "softDelete": users.remove(a[0]); return 1;
                default: throw new UnsupportedOperationException(m.getName());
            }
        });
        UserTokenMapper tokens = (UserTokenMapper) Proxy.newProxyInstance(UserTokenMapper.class.getClassLoader(), new Class[]{UserTokenMapper.class}, (p,m,a) -> {
            if (m.getName().equals("deleteForUser")) { revoked.add((Long)a[0]); return 1; }
            throw new UnsupportedOperationException(m.getName());
        });
        LoginService login = new LoginService(mapper, tokens) {
            public Map<String,Object> currentUser(String auth) {
                Map<String,Object> me = new HashMap<>(); me.put("role", auth.equals("admin") ? "ADMIN" : "OPERATOR");
                me.put("userId", auth.equals("admin") ? 1L : 2L); return me;
            }
        };
        AdminUserService service = new AdminUserService(login, new UserService(mapper), mapper, tokens);
        try { service.list("operator"); fail(); }
        catch (ResponseStatusException e) { assertEquals(HttpStatus.FORBIDDEN, e.getStatus()); }
        assertFalse(service.list("admin").get(0).containsKey("passwordHash"));
        service.setEnabled("admin", 2L, false);
        assertFalse(operator.getEnabled()); assertEquals(Collections.singletonList(2L), revoked);
        service.setEnabled("admin", 2L, true); assertTrue(operator.getEnabled());
        service.resetPassword("admin", 2L, "NewPassword123");
        assertTrue(new BCryptPasswordEncoder().matches("NewPassword123", operator.getPasswordHash()));
        assertEquals(2, revoked.size());
        try { service.setEnabled("admin", 1L, false); fail(); }
        catch (ResponseStatusException e) { assertEquals(HttpStatus.BAD_REQUEST, e.getStatus()); }
        assertTrue(admin.getEnabled());
        try { service.delete("operator", 2L); fail(); }
        catch (ResponseStatusException e) { assertEquals(HttpStatus.FORBIDDEN, e.getStatus()); }
        try { service.delete("admin", 1L); fail(); }
        catch (ResponseStatusException e) { assertEquals(HttpStatus.BAD_REQUEST, e.getStatus()); }
        service.delete("admin", 2L);
        assertEquals(3, revoked.size());
        assertEquals(1, service.list("admin").size());
        try { service.resetPassword("admin", 2L, "NewPassword123"); fail(); }
        catch (ResponseStatusException e) { assertEquals(HttpStatus.NOT_FOUND, e.getStatus()); }
    }
}
