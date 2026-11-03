package ru.netology.cloud.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ru.netology.cloud.model.UserAccount;
import ru.netology.cloud.repository.UserRepository;

/**
 * Создаёт пользователей из app.users, если их ещё нет в БД.
 */
@Component
public class UserSeeder implements CommandLineRunner {

    private final AppProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserSeeder(AppProperties properties, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        for (AppProperties.SeedUser seed : properties.users()) {
            if (userRepository.findByLogin(seed.login()).isEmpty()) {
                userRepository.save(new UserAccount(seed.login(), passwordEncoder.encode(seed.password())));
            }
        }
    }
}
