package com.pme.common.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduction des erreurs en réponses RFC 9457 (application/problem+json).
 * Aucune trace technique (stack trace, requête SQL) n'est renvoyée au client.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail notFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({ForbiddenOperationException.class, AccessDeniedException.class})
    ProblemDetail forbidden(RuntimeException ex) {
        return problem(HttpStatus.FORBIDDEN, "Action non autorisée");
    }

    @ExceptionHandler(BusinessConflictException.class)
    ProblemDetail conflict(BusinessConflictException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrentUpdate(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Ressource modifiée simultanément, veuillez réessayer");
    }

    /** Contrainte d'unicité violée par deux requêtes simultanées (ex. double ajout au panier). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrityViolation(DataIntegrityViolationException ex) {
        LOG.warn("Violation de contrainte d'intégrité : {}", ex.getMostSpecificCause().getClass().getSimpleName());
        return problem(HttpStatus.CONFLICT, "Requête concurrente détectée, veuillez réessayer");
    }

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail invalid(InvalidRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Tri demandé sur une propriété inexistante (paramètre sort fourni par le client). */
    @ExceptionHandler(PropertyReferenceException.class)
    ProblemDetail invalidSort(PropertyReferenceException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Critère de tri invalide");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex) {
        if (isMalformedRequest(ex)) {
            // Paramètres de requête illisibles (ex. « ?=&page=0 ») rejetés par Tomcat 11 : faute du client, pas du serveur
            return problem(HttpStatus.BAD_REQUEST, "Requête mal formée");
        }
        LOG.error("Erreur non gérée", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne");
    }

    /** Détecte, dans la chaîne des causes, une erreur d'analyse de requête levée par le conteneur (relevé OWASP ZAP). */
    static boolean isMalformedRequest(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause() == t ? null : t.getCause()) {
            String name = t.getClass().getSimpleName();
            if ("InvalidParameterException".equals(name) || "BadRequestException".equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
