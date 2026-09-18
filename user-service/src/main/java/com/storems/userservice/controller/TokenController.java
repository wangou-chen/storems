package com.storems.userservice.controller;

import com.storems.userservice.service.LoginService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TokenController {
    private final LoginService loginService;

    public TokenController(LoginService loginService) {
        this.loginService = loginService;
    }

    @GetMapping("/internal/auth/validate")
    public Long validate(@RequestHeader(value = HttpHeaders.AUTHORIZATION,
            required = false) String authorization) {
        return loginService.validateToken(authorization);
    }
}
