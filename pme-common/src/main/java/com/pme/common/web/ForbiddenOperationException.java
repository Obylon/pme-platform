package com.pme.common.web;

/** Action refusée par une règle métier d'autorisation, par exemple agir sur la ressource d'un autre membre (HTTP 403). */
public class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
