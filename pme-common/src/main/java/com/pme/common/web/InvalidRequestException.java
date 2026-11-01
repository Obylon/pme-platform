package com.pme.common.web;

/** Requête valide syntaxiquement mais refusée par une règle métier, par exemple panier vide (HTTP 400). */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
