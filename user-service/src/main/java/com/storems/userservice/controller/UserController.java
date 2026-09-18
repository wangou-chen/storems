package com.storems.userservice.controller;

import com.storems.userservice.dto.LoginRequest;
import com.storems.userservice.dto.RegisterRequest;
import com.storems.userservice.service.LoginService;
import com.storems.userservice.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;
    private final LoginService loginService;

    public UserController(UserService userService,
                          LoginService loginService) {
        this.userService = userService;
        this.loginService = loginService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(
            @RequestBody RegisterRequest request) {

        Long userId = userService.register(request);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "register success");
        result.put("userId", userId);
        return result;
    }

    @PostMapping("/login")
    public Map<String, Object> login(
            @RequestBody LoginRequest request) {
        return loginService.login(request);
    }

    @GetMapping("/me")
    public Map<String, Object> currentUser(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return loginService.currentUser(authorization);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        loginService.logout(authorization);
    }
}
