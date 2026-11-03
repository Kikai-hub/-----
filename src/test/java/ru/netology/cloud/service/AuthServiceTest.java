package ru.netology.cloud.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.netology.cloud.config.AppProperties;
import ru.netology.cloud.exception.BadRequestException;
import ru.netology.cloud.model.UserAccount;
import ru.netology.cloud.repository.UserRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    /** Часы, которыми управляет тест. */
    static class TestClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private final TestClock clock = new TestClock();
    private AuthService authService;

    @BeforeEach
    void setUp() {
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("dummy-hash");
        AppProperties properties = new AppProperties("storage", List.of(), List.of(),
                Duration.ofHours(1), 3, Duration.ofMinutes(1));
        authService = new AuthService(userRepository, passwordEncoder, properties, clock);
    }

    private void userExists() {
        when(userRepository.findByLogin("user")).thenReturn(Optional.of(new UserAccount("user", "hash")));
    }

    @Test
    void loginWithValidCredentialsReturnsWorkingToken() {
        userExists();
        when(passwordEncoder.matches("pass", "hash")).thenReturn(true);

        String token = authService.login("user", "pass");

        assertEquals(Optional.of("user"), authService.findLoginByToken(token));
    }

    @Test
    void loginWithWrongPasswordFails() {
        userExists();
        when(passwordEncoder.matches("bad", "hash")).thenReturn(false);

        assertThrows(BadRequestException.class, () -> authService.login("user", "bad"));
    }

    @Test
    void loginWithUnknownUserFailsButStillChecksHash() {
        when(userRepository.findByLogin("ghost")).thenReturn(Optional.empty());
        // даже если хэш-пустышка "совпал", неизвестный пользователь войти не должен
        when(passwordEncoder.matches("x", "dummy-hash")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> authService.login("ghost", "x"));
        verify(passwordEncoder).matches("x", "dummy-hash");
    }

    @Test
    void logoutInvalidatesToken() {
        userExists();
        when(passwordEncoder.matches("pass", "hash")).thenReturn(true);
        String token = authService.login("user", "pass");

        authService.logout(token);

        assertTrue(authService.findLoginByToken(token).isEmpty());
    }

    @Test
    void tokenExpiresAfterTtl() {
        userExists();
        when(passwordEncoder.matches("pass", "hash")).thenReturn(true);
        String token = authService.login("user", "pass");

        clock.now = clock.now.plus(Duration.ofHours(1)).plusSeconds(1);

        assertTrue(authService.findLoginByToken(token).isEmpty());
    }

    @Test
    void accountIsLockedAfterTooManyFailures() {
        userExists();
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        for (int i = 0; i < 3; i++) {
            assertThrows(BadRequestException.class, () -> authService.login("user", "bad"));
        }

        BadRequestException locked = assertThrows(BadRequestException.class,
                () -> authService.login("user", "pass"));
        assertEquals("Слишком много попыток, попробуйте позже", locked.getMessage());
    }

    @Test
    void lockExpiresAfterLockDuration() {
        userExists();
        when(passwordEncoder.matches("bad", "hash")).thenReturn(false);
        when(passwordEncoder.matches("pass", "hash")).thenReturn(true);
        for (int i = 0; i < 3; i++) {
            assertThrows(BadRequestException.class, () -> authService.login("user", "bad"));
        }

        clock.now = clock.now.plus(Duration.ofMinutes(1)).plusSeconds(1);

        assertTrue(authService.findLoginByToken(authService.login("user", "pass")).isPresent());
    }

    @Test
    void nullTokenIsNotAuthorized() {
        assertTrue(authService.findLoginByToken(null).isEmpty());
    }
}
