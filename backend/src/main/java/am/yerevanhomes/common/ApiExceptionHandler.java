package am.yerevanhomes.common;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Turns exceptions into RFC 9457 problem details. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final String STALE = "The listing was changed by someone else. Reload it and try again.";

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    ProblemDetail badRequest(BadRequestException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** Unknown enum value or a non-number in a query parameter, e.g. {@code ?district=Paris}. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail typeMismatch(MethodArgumentTypeMismatchException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Invalid value '" + e.getValue() + "' for parameter '" + e.getName() + "'");
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail invalidParameters(HandlerMethodValidationException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request has invalid parameters");
        Map<String, String> errors = e.getParameterValidationResults().stream()
                .collect(Collectors.toMap(r -> r.getMethodParameter().getParameterName(),
                        r -> r.getResolvableErrors().getFirst().getDefaultMessage(), (a, b) -> a));
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(PreconditionRequiredException.class)
    ProblemDetail preconditionRequired(PreconditionRequiredException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PRECONDITION_REQUIRED, e.getMessage());
    }

    @ExceptionHandler(PreconditionFailedException.class)
    ProblemDetail preconditionFailed(PreconditionFailedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PRECONDITION_FAILED, e.getMessage());
    }

    /** Another request saved between our read and our write. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail lostRace(OptimisticLockingFailureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PRECONDITION_FAILED, STALE);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalid(MethodArgumentNotValidException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request has invalid fields");
        Map<String, String> errors = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(f -> f.getField(), f -> String.valueOf(f.getDefaultMessage()), (a, b) -> a));
        problem.setProperty("errors", errors);
        return problem;
    }
}
