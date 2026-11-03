package ru.netology.cloud.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.netology.cloud.dto.LoginRequest;
import ru.netology.cloud.dto.LoginResponse;
import ru.netology.cloud.security.AuthTokenFilter;
import ru.netology.cloud.service.AuthService;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return new LoginResponse(authService.login(request.login(), request.password()));
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        authService.logout(AuthTokenFilter.extractToken(request));
    }
}
