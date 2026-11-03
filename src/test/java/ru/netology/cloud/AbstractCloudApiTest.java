package ru.netology.cloud;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Сквозные сценарии API. Наследники выбирают базу данных: H2 (всегда) или PostgreSQL в Testcontainers (нужен Docker).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractCloudApiTest {

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("app.storage-dir", () -> {
            try {
                return Files.createTempDirectory("cloud-it").toString();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Autowired
    private TestRestTemplate rest;

    private String login() {
        ResponseEntity<Map> response = rest.postForEntity("/login",
                Map.of("login", "user", "password", "password"), Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String token = (String) response.getBody().get("auth-token");
        assertNotNull(token);
        return token;
    }

    private HttpHeaders auth(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("auth-token", token);
        return headers;
    }

    private HttpStatus upload(String token, String name, byte[] content) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return name;
            }
        });
        HttpHeaders headers = auth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return HttpStatus.valueOf(rest.exchange("/file?filename=" + name, HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class).getStatusCode().value());
    }

    @Test
    void wrongPasswordReturns400() {
        ResponseEntity<Map> response = rest.postForEntity("/login",
                Map.of("login", "user", "password", "wrong"), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        // поля из спецификации (message, id) и поля, которых ждёт FRONT (email, password)
        assertEquals(400, response.getBody().get("id"));
        assertNotNull(response.getBody().get("message"));
        assertEquals(List.of(), response.getBody().get("email"));
        assertEquals(1, ((List<?>) response.getBody().get("password")).size());
    }

    @Test
    void requestsWithoutTokenReturn401() {
        assertEquals(HttpStatus.UNAUTHORIZED, rest.getForEntity("/list", String.class).getStatusCode());
    }

    /** Без CORS-заголовков на 401 браузер блокирует ответ, и FRONT не может разлогинить пользователя. */
    @Test
    void unauthorizedResponseCarriesCorsHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Origin", "http://localhost:8081");
        ResponseEntity<String> response = rest.exchange("/list", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("http://localhost:8081", response.getHeaders().getFirst("Access-Control-Allow-Origin"));
    }

    @Test
    void invalidTokenReturns401() {
        assertEquals(HttpStatus.UNAUTHORIZED, rest.exchange("/list", HttpMethod.GET,
                new HttpEntity<>(auth("not-a-real-token")), String.class).getStatusCode());
    }

    @Test
    void fullFileLifecycle() {
        String token = login();
        byte[] content = "hello cloud".getBytes();

        assertEquals(HttpStatus.OK, upload(token, "hello.txt", content));
        assertEquals(HttpStatus.BAD_REQUEST, upload(token, "hello.txt", content));

        ResponseEntity<List> list = rest.exchange("/list?limit=10", HttpMethod.GET,
                new HttpEntity<>(auth(token)), List.class);
        assertEquals(1, list.getBody().size());

        ResponseEntity<byte[]> download = rest.exchange("/file?filename=hello.txt", HttpMethod.GET,
                new HttpEntity<>(auth(token)), byte[].class);
        assertArrayEquals(content, download.getBody());
        assertEquals("nosniff", download.getHeaders().getFirst("X-Content-Type-Options"));

        HttpHeaders json = auth(token);
        json.setContentType(MediaType.APPLICATION_JSON);
        assertEquals(HttpStatus.OK, rest.exchange("/file?filename=hello.txt", HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "renamed.txt"), json), String.class).getStatusCode());

        assertEquals(HttpStatus.OK, rest.exchange("/file?filename=renamed.txt", HttpMethod.DELETE,
                new HttpEntity<>(auth(token)), String.class).getStatusCode());

        List<?> afterDelete = rest.exchange("/list", HttpMethod.GET,
                new HttpEntity<>(auth(token)), List.class).getBody();
        assertEquals(0, afterDelete.size());
    }

    /** Ровно так, как работает FRONT: токен с префиксом Bearer, переименование через {"filename": ...}. */
    @Test
    void behavesTheWayFrontExpects() {
        String token = login();
        String bearer = "Bearer " + token;
        assertEquals(HttpStatus.OK, upload(bearer, "front.txt", "x".getBytes()));

        ResponseEntity<List> list = rest.exchange("/list?limit=100", HttpMethod.GET,
                new HttpEntity<>(auth(bearer)), List.class);
        Map<?, ?> file = (Map<?, ?>) list.getBody().get(0);
        assertEquals("front.txt", file.get("filename"));
        assertNotNull(file.get("editedAt"));

        HttpHeaders json = auth(bearer);
        json.setContentType(MediaType.APPLICATION_JSON);
        assertEquals(HttpStatus.OK, rest.exchange("/file?filename=front.txt", HttpMethod.PUT,
                new HttpEntity<>(Map.of("filename", "front2.txt"), json), String.class).getStatusCode());
        assertEquals(HttpStatus.OK, rest.exchange("/file?filename=front2.txt", HttpMethod.DELETE,
                new HttpEntity<>(auth(bearer)), String.class).getStatusCode());
    }

    @Test
    void missingFilenameReturns400() {
        String token = login();
        assertEquals(HttpStatus.BAD_REQUEST, rest.exchange("/file", HttpMethod.GET,
                new HttpEntity<>(auth(token)), String.class).getStatusCode());
    }

    @Test
    void logoutInvalidatesToken() {
        String token = login();
        assertEquals(HttpStatus.OK, rest.exchange("/logout", HttpMethod.POST,
                new HttpEntity<>(auth(token)), String.class).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, rest.exchange("/list", HttpMethod.GET,
                new HttpEntity<>(auth(token)), String.class).getStatusCode());
    }
}
