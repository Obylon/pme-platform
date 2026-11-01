package com.pme.common.web;

/** Ressource inexistante ou non visible pour l'appelant (HTTP 404). */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
