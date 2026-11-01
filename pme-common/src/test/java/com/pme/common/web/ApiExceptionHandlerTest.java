package com.pme.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApiExceptionHandlerTest {

    /** Même nom simple que l'exception levée par Tomcat 11 sur un paramètre illisible. */
    static class InvalidParameterException extends IllegalStateException {
    }

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void malformedParametersAreABadRequestEvenWhenWrapped() {
        var wrapped = new RuntimeException("wrapper", new InvalidParameterException());

        assertThat(handler.unexpected(wrapped).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void otherErrorsStayInternalWithoutDetails() {
        var pd = handler.unexpected(new IllegalStateException("secret technique"));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(pd.getDetail()).isEqualTo("Erreur interne");
    }
}
