package by.technosoft.bonus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import by.technosoft.bonus.domain.BonusAccount;
import by.technosoft.bonus.domain.DemoData;
import by.technosoft.bonus.domain.Operation;
import by.technosoft.bonus.domain.OperationBasis;
import by.technosoft.bonus.domain.OperationStatus;
import by.technosoft.bonus.domain.OperationType;
import by.technosoft.bonus.domain.Program;
import by.technosoft.bonus.domain.TestEntities;
import by.technosoft.bonus.repository.AppUserRepository;
import by.technosoft.bonus.repository.BonusAccountRepository;
import by.technosoft.bonus.repository.OperationRepository;
import by.technosoft.bonus.repository.ProgramRepository;
import by.technosoft.bonus.service.dto.RedemptionResult;
import by.technosoft.bonus.service.event.OperationAction;
import by.technosoft.bonus.service.event.OperationRegisteredEvent;
import by.technosoft.bonus.service.exception.AccountBlockedException;
import by.technosoft.bonus.service.exception.InsufficientBalanceException;
import by.technosoft.bonus.service.exception.ObjectNotFoundException;
import by.technosoft.bonus.service.exception.ProgramUnavailableException;

/** UC3 «Использование бонусов» (алгоритм А4): проверка баланса, списание, обновление баланса. */
@ExtendWith(MockitoExtension.class)
@DisplayName("RedemptionService — списание баллов на программы (UC3)")
class RedemptionServiceTest {

    @Mock private ProgramRepository programRepository;
    @Mock private BonusAccountRepository accountRepository;
    @Mock private AppUserRepository userRepository;
    @Mock private OperationRepository operationRepository;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private ApplicationEventPublisher events;

    private final DemoData d = new DemoData();
    private final Program english = TestEntities.program(5, "Курс английского языка", 6000, true, null);
    private final Program conference = TestEntities.program(6, "Участие в профильной конференции", 12000, true, null);
    private RedemptionService service;

    @BeforeEach
    void setUp() {
        service = new RedemptionService(programRepository, accountRepository, userRepository, operationRepository,
                new OperationNumberGenerator(jdbcTemplate), events, TestEntities.clock());
        lenient().when(programRepository.findById(5L)).thenReturn(Optional.of(english));
        lenient().when(programRepository.findById(6L)).thenReturn(Optional.of(conference));
        lenient().when(accountRepository.lockByEmployeeId(1L)).thenReturn(Optional.of(d.annaAccount));
        lenient().when(userRepository.findByEmployeeId(1L)).thenReturn(Optional.of(d.employeeUser));
        lenient().when(jdbcTemplate.queryForObject(OperationNumberGenerator.NEXT_VALUE_SQL, Long.class)).thenReturn(310L);
    }

    @Test
    @DisplayName("FR3-1: при достаточном балансе списание разрешено — создаётся операция «Списано» на −стоимость")
    void redeemsWhenBalanceIsEnough() {
        RedemptionResult result = service.redeem(1L, 5L);

        ArgumentCaptor<Operation> saved = ArgumentCaptor.forClass(Operation.class);
        verify(operationRepository).save(saved.capture());
        Operation op = saved.getValue();
        assertThat(op.getType()).isEqualTo(OperationType.WRITEOFF);
        assertThat(op.getStatus()).isEqualTo(OperationStatus.WRITTEN_OFF);
        assertThat(op.getBasis()).isEqualTo(OperationBasis.PROGRAM);
        assertThat(op.getAmount()).isEqualTo(-6000);
        assertThat(op.getProgram()).isSameAs(english);
        assertThat(op.getReason()).isEqualTo("Программа «Курс английского языка»");
        assertThat(op.getCreatedBy()).isSameAs(d.employeeUser);
        assertThat(result.operation().number()).isEqualTo("ОП-000310");
    }

    @Test
    @DisplayName("FR3-1: баланс, равный стоимости программы, достаточен для списания")
    void balanceEqualToCostIsEnough() {
        BonusAccount exact = TestEntities.account(1, d.anna, 6000);
        when(accountRepository.lockByEmployeeId(1L)).thenReturn(Optional.of(exact));

        assertThat(service.redeem(1L, 5L).balance()).isZero();
    }

    @Test
    @DisplayName("FR3-2: при недостаточном балансе списание отклоняется с указанием недостающей суммы")
    void rejectsWhenBalanceIsInsufficient() {
        assertThatThrownBy(() -> service.redeem(1L, 6L))
                .isInstanceOfSatisfying(InsufficientBalanceException.class, e -> {
                    assertThat(e.getBalance()).isEqualTo(8900);
                    assertThat(e.getRequired()).isEqualTo(12000);
                    assertThat(e.getShortage()).isEqualTo(3100);
                    assertThat(e.getCode()).isEqualTo("INSUFFICIENT_FUNDS");
                })
                .hasMessage("Не хватает 3\u00A0100\u00A0Б: программа стоит 12\u00A0000\u00A0Б, на счёте 8\u00A0900\u00A0Б.");
        assertThat(d.annaAccount.getBalance()).isEqualTo(8900);
        verify(operationRepository, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    @DisplayName("FR3-2: списание с заблокированного счёта отклоняется")
    void rejectsBlockedAccount() {
        when(accountRepository.lockByEmployeeId(9L)).thenReturn(Optional.of(d.pavelAccount));
        assertThatThrownBy(() -> service.redeem(9L, 5L))
                .isInstanceOf(AccountBlockedException.class)
                .hasMessage("Баланс недоступен, обратитесь к HR.");
        verify(operationRepository, never()).save(any());
    }

    @Test
    @DisplayName("FR3-2: недоступная или неизвестная программа — списание отклоняется")
    void rejectsInactiveOrUnknownProgram() {
        Program rafting = TestEntities.program(8, "Корпоративный сплав по Неману", 3500, false, "Сезон закрыт.");
        when(programRepository.findById(8L)).thenReturn(Optional.of(rafting));
        when(programRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redeem(1L, 8L))
                .isInstanceOf(ProgramUnavailableException.class)
                .hasMessage("Программа «Корпоративный сплав по Неману» сейчас недоступна. Сезон закрыт.");
        assertThatThrownBy(() -> service.redeem(1L, 99L)).isInstanceOf(ObjectNotFoundException.class);
        verify(accountRepository, never()).lockByEmployeeId(any());
    }

    @Test
    @DisplayName("FR3-3: после подтверждения списания баланс уменьшается на стоимость программы")
    void balanceIsUpdatedAfterRedemption() {
        RedemptionResult result = service.redeem(1L, 5L);

        assertThat(result.balance()).isEqualTo(2900);
        assertThat(d.annaAccount.getBalance()).isEqualTo(2900);
        assertThat(d.annaAccount.getUpdatedAt()).isEqualTo(TestEntities.now());
    }

    @Test
    @DisplayName("FR3-3: о списании публикуется событие для журнала аудита и уведомления сотрудника")
    void redemptionIsPublished() {
        service.redeem(1L, 5L);

        ArgumentCaptor<OperationRegisteredEvent> event = ArgumentCaptor.forClass(OperationRegisteredEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().action()).isEqualTo(OperationAction.REDEMPTION);
        assertThat(event.getValue().amount()).isEqualTo(-6000);
        assertThat(event.getValue().employeeId()).isEqualTo(1L);
        assertThat(event.getValue().actorUserId()).isEqualTo(1L);
    }
}
