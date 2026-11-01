package com.pme.common.web;

/** Conflit avec l'état courant, par exemple stock insuffisant (HTTP 409). */
public class BusinessConflictException extends RuntimeException {
    public BusinessConflictException(String message) {
        super(message);
    }
}
