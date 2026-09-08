package com.meridian.event.infrastructure.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetails;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetails handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetails problem = ProblemDetails.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Bad Request");
        problem.setDetail(ex.getMessage());
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetails handleIllegalState(IllegalStateException ex) {
        ProblemDetails problem = ProblemDetails.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Conflict");
        problem.setDetail(ex.getMessage());
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }

    @ExceptionHandler(RuntimeException.class)
    public ProblemDetails handleRuntime(RuntimeException ex) {
        ProblemDetails problem = ProblemDetails.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Internal Server Error");
        problem.setDetail("An unexpected error occurred");
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }
}
