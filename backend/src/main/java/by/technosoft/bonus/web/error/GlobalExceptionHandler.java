package by.technosoft.bonus.web.error;

import java.util.Comparator;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import by.technosoft.bonus.service.exception.BonusDomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

/**
 * Единый обработчик ошибок REST API (DRY): любое исключение превращается в {@link ApiErrorResponse}
 * {@code {status, code, message, path, timestamp}}. Контроллеры не содержат try/catch.
 * Доменные исключения обрабатываются одним методом по базовому классу {@link BonusDomainException},
 * статус выбирается по иерархии ({@link DomainErrorStatus}) — новые исключения не требуют правок (OCP).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ErrorResponseFactory errors;
    private final DomainErrorStatus domainStatus;

    public GlobalExceptionHandler(ErrorResponseFactory errors, DomainErrorStatus domainStatus) {
        this.errors = errors;
        this.domainStatus = domainStatus;
    }

    /** Нарушение бизнес-правила: код и текст задаёт сервис, статус — иерархия исключений. */
    @ExceptionHandler(BonusDomainException.class)
    public ResponseEntity<ApiErrorResponse> domain(BonusDomainException e, HttpServletRequest request) {
        return errors.entity(domainStatus.of(e), e.getCode(), e.getMessage(), request);
    }

    /** Bean Validation тела запроса (@Valid @RequestBody). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> invalidBody(MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(FieldError::getDefaultMessage)
                .distinct()
                .collect(Collectors.joining(" "));
        return errors.entity(ErrorCode.VALIDATION_FAILED, message.isBlank() ? "Проверьте заполнение полей." : message,
                request);
    }

    /** Bean Validation параметров метода контроллера (@RequestParam, @PathVariable). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> invalidParameters(HandlerMethodValidationException e,
                                                              HttpServletRequest request) {
        String message = e.getAllValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream())
                .map(err -> err.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining(" "));
        return errors.entity(ErrorCode.VALIDATION_FAILED, message.isBlank() ? "Проверьте параметры запроса." : message,
                request);
    }

    /** Bean Validation на уровне сервисов (@Validated). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> constraintViolation(ConstraintViolationException e,
                                                                HttpServletRequest request) {
        String message = e.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .distinct()
                .collect(Collectors.joining(" "));
        return errors.entity(ErrorCode.VALIDATION_FAILED, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> unreadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        return errors.entity(ErrorCode.MALFORMED_REQUEST,
                "Не удалось разобрать тело запроса: проверьте формат JSON и типы полей.", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> typeMismatch(MethodArgumentTypeMismatchException e,
                                                         HttpServletRequest request) {
        return errors.entity(ErrorCode.MALFORMED_REQUEST,
                "Параметр «" + e.getName() + "» имеет недопустимое значение «" + e.getValue() + "».", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> missingParameter(MissingServletRequestParameterException e,
                                                             HttpServletRequest request) {
        return errors.entity(ErrorCode.MALFORMED_REQUEST,
                "Не указан обязательный параметр «" + e.getParameterName() + "».", request);
    }

    /** Отказ ролевой проверки {@code @PreAuthorize} (SEC-3). */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> accessDenied(AccessDeniedException e, HttpServletRequest request) {
        return errors.entity(ErrorCode.ACCESS_DENIED, SecurityMessages.FORBIDDEN, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> unauthenticated(AuthenticationException e, HttpServletRequest request) {
        return errors.entity(ErrorCode.UNAUTHORIZED, SecurityMessages.UNAUTHORIZED, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> noResource(NoResourceFoundException e, HttpServletRequest request) {
        return errors.entity(ErrorCode.NOT_FOUND, "Адрес " + request.getRequestURI() + " не найден.", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> methodNotAllowed(HttpRequestMethodNotSupportedException e,
                                                             HttpServletRequest request) {
        return errors.entity(ErrorCode.METHOD_NOT_ALLOWED,
                "Метод " + e.getMethod() + " не поддерживается для " + request.getRequestURI() + ".", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> mediaType(HttpMediaTypeNotSupportedException e,
                                                      HttpServletRequest request) {
        return errors.entity(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Передайте тело запроса в формате application/json.",
                request);
    }

    /** Гонка изменений: оптимистичная (@Version) или пессимистичная блокировка. */
    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ApiErrorResponse> concurrent(RuntimeException e, HttpServletRequest request) {
        return errors.entity(ErrorCode.CONCURRENT_MODIFICATION,
                "Данные только что изменил другой пользователь. Обновите страницу и повторите действие.", request);
    }

    /** Нарушено ограничение БД (уникальность, внешний ключ) — например, одновременное создание дубля. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> integrity(DataIntegrityViolationException e, HttpServletRequest request) {
        log.warn("Нарушено ограничение БД: {}", e.getMostSpecificCause().getMessage());
        return errors.entity(ErrorCode.CONFLICT,
                "Данные противоречат уже сохранённым (например, такой объект уже есть). Обновите страницу.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> unexpected(Exception e, HttpServletRequest request) {
        log.error("Непредвиденная ошибка {} {}", request.getMethod(), request.getRequestURI(), e);
        return errors.entity(ErrorCode.INTERNAL_ERROR,
                "Что-то пошло не так на сервере. Повторите попытку позже.", request);
    }
}
