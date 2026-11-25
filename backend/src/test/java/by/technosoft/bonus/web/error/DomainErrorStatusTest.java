package by.technosoft.bonus.web.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import by.technosoft.bonus.service.admin.RuleAdminService;
import by.technosoft.bonus.service.exception.AccessDeniedDomainException;
import by.technosoft.bonus.service.exception.AccountBlockedException;
import by.technosoft.bonus.service.exception.BonusDomainException;
import by.technosoft.bonus.service.exception.ConflictException;
import by.technosoft.bonus.service.exception.DomainValidationException;
import by.technosoft.bonus.service.exception.DuplicateEmailException;
import by.technosoft.bonus.service.exception.InsufficientBalanceException;
import by.technosoft.bonus.service.exception.InvalidCalculationInputException;
import by.technosoft.bonus.service.exception.InvalidCredentialsException;
import by.technosoft.bonus.service.exception.ObjectNotFoundException;
import by.technosoft.bonus.service.exception.OperationStateException;
import by.technosoft.bonus.service.exception.ProgramUnavailableException;
import by.technosoft.bonus.service.exception.UserBlockedException;

/**
 * Статус доменной ошибки, выбранный по иерархии исключений, совпадает со статусом кода
 * в перечне {@link ErrorCode} (документация API и фактический ответ не расходятся).
 */
class DomainErrorStatusTest {

    private final DomainErrorStatus resolver = new DomainErrorStatus();

    private static final List<BonusDomainException> ALL = List.of(
            new AccessDeniedDomainException("x"),
            new AccountBlockedException("x"),
            new ConflictException("x"),
            new ConflictException(RuleAdminService.RULE_EXISTS, "x"),
            new DomainValidationException("x"),
            new DuplicateEmailException("a@b.by"),
            new InsufficientBalanceException(1, 2, "x"),
            new InvalidCalculationInputException("x"),
            new InvalidCredentialsException(),
            new ObjectNotFoundException("x"),
            new OperationStateException("x"),
            new ProgramUnavailableException("x"),
            new UserBlockedException());

    @Test
    void everyDomainCodeIsListedWithTheSameStatus() {
        for (BonusDomainException e : ALL) {
            ErrorCode code = ErrorCode.valueOf(e.getCode());
            assertThat(resolver.of(e)).as(e.getClass().getSimpleName()).isEqualTo(code.status());
        }
    }

    @Test
    void subclassInheritsStatusOfNearestRegisteredAncestor() {
        BonusDomainException custom = new ConflictException("NEW_CONFLICT", "x") { };
        assertThat(resolver.of(custom)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resolver.of(new DuplicateEmailException("a@b.by"))).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void unknownBranchFallsBackTo422() {
        BonusDomainException unknown = new BonusDomainException("SOMETHING", "x") { };
        assertThat(resolver.of(unknown)).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
