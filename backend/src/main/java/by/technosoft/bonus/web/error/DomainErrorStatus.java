package by.technosoft.bonus.web.error;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import by.technosoft.bonus.service.exception.AccessDeniedDomainException;
import by.technosoft.bonus.service.exception.AccountBlockedException;
import by.technosoft.bonus.service.exception.BonusDomainException;
import by.technosoft.bonus.service.exception.ConflictException;
import by.technosoft.bonus.service.exception.DomainValidationException;
import by.technosoft.bonus.service.exception.InsufficientBalanceException;
import by.technosoft.bonus.service.exception.InvalidCalculationInputException;
import by.technosoft.bonus.service.exception.InvalidCredentialsException;
import by.technosoft.bonus.service.exception.ObjectNotFoundException;
import by.technosoft.bonus.service.exception.OperationStateException;
import by.technosoft.bonus.service.exception.ProgramUnavailableException;
import by.technosoft.bonus.service.exception.UserBlockedException;

/**
 * HTTP-статус доменного исключения по иерархии классов (OCP): ищется ближайший предок,
 * для которого задан статус. Новое исключение-наследник (например, {@code DuplicateEmailException}
 * от {@link ConflictException}) получает статус предка без правки обработчика ошибок.
 */
@Component
public class DomainErrorStatus {

    private final Map<Class<? extends BonusDomainException>, HttpStatus> statuses = new LinkedHashMap<>();

    public DomainErrorStatus() {
        statuses.put(InvalidCredentialsException.class, HttpStatus.UNAUTHORIZED);
        statuses.put(AccessDeniedDomainException.class, HttpStatus.FORBIDDEN);
        statuses.put(UserBlockedException.class, HttpStatus.FORBIDDEN);
        statuses.put(ObjectNotFoundException.class, HttpStatus.NOT_FOUND);
        statuses.put(ConflictException.class, HttpStatus.CONFLICT);
        statuses.put(OperationStateException.class, HttpStatus.CONFLICT);
        statuses.put(InsufficientBalanceException.class, HttpStatus.CONFLICT);
        statuses.put(ProgramUnavailableException.class, HttpStatus.CONFLICT);
        statuses.put(AccountBlockedException.class, HttpStatus.LOCKED);
        statuses.put(DomainValidationException.class, HttpStatus.UNPROCESSABLE_ENTITY);
        statuses.put(InvalidCalculationInputException.class, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    /** Статус для исключения; неизвестная ветка иерархии — 422 (нарушено бизнес-правило). */
    public HttpStatus of(BonusDomainException exception) {
        for (Class<?> type = exception.getClass(); BonusDomainException.class.isAssignableFrom(type);
             type = type.getSuperclass()) {
            HttpStatus status = statuses.get(type);
            if (status != null) {
                return status;
            }
        }
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }
}
