package ru.netology.cloud.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import ru.netology.cloud.dto.ErrorResponse;
import ru.netology.cloud.dto.LoginErrorResponse;
import ru.netology.cloud.exception.BadRequestException;
import ru.netology.cloud.exception.LoginFailedException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(LoginFailedException.class)
    public ResponseEntity<LoginErrorResponse> loginFailed(LoginFailedException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(LoginErrorResponse.of(e.getMessage()));
    }

    @ExceptionHandler({BadRequestException.class, MaxUploadSizeExceededException.class,
            MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> badRequest(Exception e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> internal(Exception e) {
        log.error("Unhandled error", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(message, status.value()));
    }
}
