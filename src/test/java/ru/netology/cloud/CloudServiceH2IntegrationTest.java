package ru.netology.cloud;

import org.springframework.test.context.ActiveProfiles;

/**
 * Те же сквозные сценарии на встроенной H2 — работают без Docker.
 */
@ActiveProfiles("local")
class CloudServiceH2IntegrationTest extends AbstractCloudApiTest {
}
