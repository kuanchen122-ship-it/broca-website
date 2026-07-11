package com.example.brocawebsite.registration;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = RegistrationAdminController.class)
class RegistrationApiExceptionHandler {

    @ExceptionHandler(RegistrationDuplicateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    RegistrationErrorResponse duplicate(RegistrationDuplicateException exception) {
        return new RegistrationErrorResponse(exception.getMessage(), exception.duplicates());
    }
}
