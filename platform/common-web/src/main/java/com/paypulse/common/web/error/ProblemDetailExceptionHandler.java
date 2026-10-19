package com.paypulse.common.web.error;

import com.paypulse.common.web.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The one place that turns exceptions into HTTP error responses (RFC 9457).
 * Controllers and services never catch exceptions just to build a response.
 *
 * <p>Extends Spring's ResponseEntityExceptionHandler: it already answers the standard
 * "the client made a mistake" errors (bad JSON 400, wrong method 405, bad media type 415,
 * bad path variable 400...) with the right status. Without it, our catch-all below would
 * turn all of those into a misleading 500.
 */
@RestControllerAdvice
public class ProblemDetailExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailExceptionHandler.class);

    /** Our own business errors: the category decides the status. */
    @ExceptionHandler(PayPulseException.class)
    public ResponseEntity<ProblemDetail> handlePayPulse(PayPulseException ex) {
        HttpStatus status = switch (ex.getCategory()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;                 // 404
            case CONFLICT -> HttpStatus.CONFLICT;                   // 409
            case BUSINESS_RULE -> HttpStatus.UNPROCESSABLE_CONTENT; // 422
        };
        // Safe to show: we wrote this message ourselves.
        return respond(status, ex.getCode(), ex.getMessage());
    }

    /**
     * Bean Validation failed (@Valid on a request body). The parent class already handles
     * this exception, so we override its method instead of adding a second handler
     * (two handlers for one exception type would be ambiguous).
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "message", String.valueOf(fe.getDefaultMessage())))
                .toList();

        ProblemDetail problem = build(HttpStatus.BAD_REQUEST, "validation-failed",
                "Request validation failed");
        problem.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    /**
     * Every standard Spring MVC error passes through here on its way out. We let Spring
     * build the answer, then add our two things: a stable "type" and the correlation id,
     * so these errors look exactly like the ones above.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode statusCode,
                                                             WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
                if (problem.getType() == null || problem.getType().toString().isBlank()
                    || URI.create("about:blank").equals(problem.getType())) {
                problem.setType(URI.create("urn:paypulse:problem:" + slug(statusCode)));
            }
            problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        }
        return response;
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

    /** 400 "Bad Request" becomes "bad-request", 405 becomes "method-not-allowed". */
    private static String slug(HttpStatusCode code) {
        HttpStatus status = HttpStatus.resolve(code.value());
        return status == null ? "error"
                : status.getReasonPhrase().toLowerCase(Locale.ROOT).replace(' ', '-');
    }
}
