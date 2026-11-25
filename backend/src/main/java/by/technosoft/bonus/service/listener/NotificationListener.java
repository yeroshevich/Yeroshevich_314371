package by.technosoft.bonus.service.listener;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import by.technosoft.bonus.domain.Notification;
import by.technosoft.bonus.domain.OperationStatus;
import by.technosoft.bonus.repository.AppUserRepository;
import by.technosoft.bonus.repository.NotificationRepository;
import by.technosoft.bonus.service.PointsFormat;
import by.technosoft.bonus.service.event.OperationAction;
import by.technosoft.bonus.service.event.OperationRegisteredEvent;

/**
 * Наблюдатель (Observer): уведомляет сотрудника об операции по его счёту.
 * Срабатывает только после успешного коммита (не уведомляем об откатившихся операциях)
 * и пишет в отдельной транзакции: сбой уведомления не отменяет операцию.
 */
@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);
    private static final int TITLE_LENGTH = 150;
    private static final int TEXT_LENGTH = 500;

    private final NotificationRepository notificationRepository;
    private final AppUserRepository userRepository;
    private final Clock clock;

    public NotificationListener(NotificationRepository notificationRepository, AppUserRepository userRepository,
                                Clock clock) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOperationRegistered(OperationRegisteredEvent event) {
        try {
            userRepository.findByEmployeeId(event.employeeId()).ifPresent(user -> notificationRepository.save(
                    new Notification(user, cut(title(event), TITLE_LENGTH), cut(text(event), TEXT_LENGTH),
                            OffsetDateTime.now(clock))));
        } catch (RuntimeException e) {
            log.warn("Не удалось создать уведомление по операции {}: {}", event.operationNumber(), e.getMessage());
        }
    }

    static String title(OperationRegisteredEvent e) {
        boolean pending = e.status() == OperationStatus.PENDING;
        return switch (e.action()) {
            case MANUAL_ACCRUAL, AUTO_ACCRUAL ->
                    pending ? "Начисление на согласовании" : "Начислено " + PointsFormat.format(e.amount());
            case REDEMPTION -> "Списано " + PointsFormat.format(-e.amount());
            case CORRECTION, CANCELLATION -> pending ? "Корректировка на согласовании" : "Начисление скорректировано";
            case APPROVAL -> "Начисление подтверждено";
            case BLOCKING -> "Начисление заблокировано";
        };
    }

    static String text(OperationRegisteredEvent e) {
        return switch (e.action()) {
            case MANUAL_ACCRUAL, AUTO_ACCRUAL -> e.status() == OperationStatus.PENDING
                    ? e.reason() + ": " + PointsFormat.format(e.amount()) + " ждут подтверждения финотдела."
                    : e.reason();
            case REDEMPTION -> "Вы участвуете: " + e.reason() + ". Детали придут на почту.";
            case CORRECTION, CANCELLATION -> {
                int from = e.originalAmount() == null ? 0 : e.originalAmount();
                int to = e.action() == OperationAction.CANCELLATION ? 0 : e.amount();
                yield e.operationNumber() + ": " + PointsFormat.format(from) + " → " + PointsFormat.format(to)
                        + ". " + e.reason();
            }
            case APPROVAL -> e.operationNumber() + ": " + e.reason();
            case BLOCKING -> e.operationNumber() + ": " + (e.comment() == null ? e.reason() : e.comment());
        };
    }

    private static String cut(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length - 1) + "…";
    }
}
