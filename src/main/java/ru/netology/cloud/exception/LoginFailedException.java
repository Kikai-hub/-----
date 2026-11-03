package ru.netology.cloud.exception;

/**
 * Неудачный вход: неверные данные или временная блокировка. Отвечает 400, как в спецификации.
 */
public class LoginFailedException extends BadRequestException {

    public LoginFailedException(String message) {
        super(message);
    }
}
