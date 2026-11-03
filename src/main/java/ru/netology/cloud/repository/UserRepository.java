package ru.netology.cloud.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.netology.cloud.model.UserAccount;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByLogin(String login);
}
