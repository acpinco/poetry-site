package com.thinkordrinkpoetry.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Gives every JSON API error the same RFC 9457 "problem details" shape: a status and a detail
 * message safe to show visitors. The base class covers Spring's own exceptions, including the
 * ResponseStatusExceptions our code throws, whose reason becomes the detail.
 *
 * <p>Scoped to {@code @RestController}s so server-rendered pages keep their HTML error pages.
 */
@RestControllerAdvice(annotations = RestController.class)
class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** A unique constraint lost a race with another request, such as two people claiming one pen name. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(DataIntegrityViolationException exception) {
        log.warn(
                "Rejected a change that conflicts with existing data: {}",
                exception.getMostSpecificCause().getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "That change conflicts with existing data.");
    }

    /** Anything unexpected: log the details for us, show the visitor a generic message. */
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception) {
        log.error("Unexpected error handling an API request", exception);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.");
    }
}
