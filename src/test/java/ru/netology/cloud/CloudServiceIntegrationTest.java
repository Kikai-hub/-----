package ru.netology.cloud;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Интеграционные тесты на реальном PostgreSQL в контейнере (Testcontainers). Нужен запущенный Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
class CloudServiceIntegrationTest extends AbstractCloudApiTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
}
