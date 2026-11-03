package ru.netology.cloud.dto;

import java.util.List;

/**
 * Ошибка входа. Поля message и id — по спецификации; email и password нужны FRONT, который по ним
 * показывает ошибки под полями формы (без них он падает на res.data.email.length).
 */
public record LoginErrorResponse(String message, int id, List<String> email, List<String> password) {

    public static LoginErrorResponse of(String message) {
        return new LoginErrorResponse(message, 400, List.of(), List.of(message));
    }
}
