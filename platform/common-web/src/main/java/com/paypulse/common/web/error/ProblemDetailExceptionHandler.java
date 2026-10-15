package com.paypulse.common.web.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.paypulse.common.web.CorrelationIdFilter;

/**
 * The one place that turns exceptions into HTTP error responses (RFC 9457).
 * Controllers and services never catch exceptions just to build a response.
 */
@RestControllerAdvice
public class ProblemDetailExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailExceptionHandler.class);

    /** Our own business errors: the category decides the status. */
    @ExceptionHandler(PayPulseException.class)
    public ResponseEntity<ProblemDetail> handlePayPulse(PayPulseException ex) {
        HttpStatus status = switch (ex.getCategory()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;            // 404
            case CONFLICT -> HttpStatus.CONFLICT;              // 409
            case BUSINESS_RULE -> HttpStatus.UNPROCESSABLE_CONTENT; // 422
        };
        // Safe to show: we wrote this message ourselves.
        return respond(status, ex.getCode(), ex.getMessage());
    }

    /** Bean Validation failed (@Valid on a request body). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "message", String.valueOf(fe.getDefaultMessage())))
                .toList();

        ProblemDetail problem = build(HttpStatus.BAD_REQUEST, "validation-failed",
                "Request validation failed");
        problem.setProperty("errors", errors);
        return toResponse(problem);
    }

    /** Anything we did not expect. Log the details, show nothing. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        // Logged once, with the stack, for us. The correlation id is in the log line via MDC.
        log.error("Unexpected error", ex);
        // Generic text only: never ex.getMessage(), class name or stack in the response.
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
                "An unexpected error occurred");
    }

    private ResponseEntity<ProblemDetail> respond(HttpStatus status, String code, String detail) {
        return toResponse(build(status, code, detail));
    }

    private ProblemDetail build(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:paypulse:problem:" + code));
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        return problem;
    }

    private ResponseEntity<ProblemDetail> toResponse(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
