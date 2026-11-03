package ru.netology.cloud.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.netology.cloud.dto.ErrorResponse;
import ru.netology.cloud.service.AuthService;

import java.io.IOException;
import java.util.Optional;

/**
 * Проверяет заголовок auth-token на всех запросах, кроме /login и CORS preflight.
 */
@Component
public class AuthTokenFilter extends OncePerRequestFilter {

    public static final String TOKEN_HEADER = "auth-token";
    public static final String LOGIN_ATTRIBUTE = "login";

    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public AuthTokenFilter(AuthService authService, ObjectMapper objectMapper) {
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || "/login".equals(request.getRequestURI())
                || "/error".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<String> login = authService.findLoginByToken(extractToken(request));
        if (login.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), new ErrorResponse("Unauthorized", 401));
            return;
        }
        request.setAttribute(LOGIN_ATTRIBUTE, login.get());
        chain.doFilter(request, response);
    }

    public static String extractToken(HttpServletRequest request) {
        String header = request.getHeader(TOKEN_HEADER);
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return header.substring(7).trim();
        }
        return header;
    }
}
