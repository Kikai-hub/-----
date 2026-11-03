package ru.netology.cloud.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ru.netology.cloud.config.AppProperties;
import ru.netology.cloud.exception.LoginFailedException;
import ru.netology.cloud.model.UserAccount;
import ru.netology.cloud.repository.UserRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private record Session(String login, Instant expiresAt) {
    }

    private static class Attempts {
        int failures;
        Instant lockedUntil = Instant.MIN;
    }

    private static final int MAX_TRACKED_LOGINS = 10_000;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;
    private final Clock clock;
    /** Хэш-пустышка: сверяем с ним пароль для несуществующего логина, чтобы время ответа не выдавало, есть ли такой пользователь. */
    private final String dummyHash;
    /** token -> сессия. Токены живут до logout, истечения TTL или перезапуска сервиса. */
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       AppProperties properties, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public String login(String login, String password) {
        if (login == null || password == null) {
            throw new LoginFailedException("Неверный логин или пароль");
        }
        Instant now = clock.instant();
        if (attempts.size() > MAX_TRACKED_LOGINS) {
            attempts.clear(); // защита от разрастания карты при переборе случайных логинов
        }
        Attempts state = attempts.computeIfAbsent(login, k -> new Attempts());
        synchronized (state) {
            if (now.isBefore(state.lockedUntil)) {
                throw new LoginFailedException("Слишком много попыток, попробуйте позже");
            }
            Optional<UserAccount> user = userRepository.findByLogin(login);
            boolean ok = passwordEncoder.matches(password, user.map(UserAccount::getPasswordHash).orElse(dummyHash))
                    && user.isPresent();
            if (!ok) {
                if (++state.failures >= properties.maxLoginAttempts()) {
                    state.failures = 0;
                    state.lockedUntil = now.plus(properties.lockDuration());
                }
                throw new LoginFailedException("Неверный логин или пароль");
            }
            state.failures = 0;
        }
        purgeExpired(now);
        String token = UUID.randomUUID().toString();
        sessions.put(token, new Session(login, now.plus(properties.tokenTtl())));
        return token;
    }

    public void logout(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    public Optional<String> findLoginByToken(String token) {
        if (token == null) {
            return Optional.empty();
        }
        Session session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(session.expiresAt())) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(session.login());
    }

    private void purgeExpired(Instant now) {
        sessions.values().removeIf(s -> !now.isBefore(s.expiresAt()));
    }
}
