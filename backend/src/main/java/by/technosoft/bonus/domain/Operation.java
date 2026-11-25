package by.technosoft.bonus.domain;

import java.time.OffsetDateTime;
import java.util.Objects;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * Операция по бонусному счёту (таблица operation, DATA-1: полная история движений).
 * Идентификатор назначается заранее из последовательности вместе с номером
 * (см. {@link OperationNumber}), поэтому сущность реализует {@link Persistable}:
 * Spring Data выполняет INSERT без предварительного SELECT.
 */
@Entity
@Table(name = "operation")
public class Operation implements Persistable<Long> {

    /** Максимальная длина причины в схеме. */
    public static final int REASON_LENGTH = 300;

    @Id
    private Long id;

    @Column(name = "number", nullable = false, length = 12)
    private String number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private BonusAccount account;

    @Column(name = "type", nullable = false, length = 10)
    private OperationType type;

    @Column(name = "status", nullable = false, length = 12)
    private OperationStatus status;

    @Column(name = "basis", nullable = false, length = 12)
    private OperationBasis basis;

    /** Сумма со знаком: «+» начисление, «−» списание. */
    @Column(name = "amount", nullable = false)
    private int amount;

    /** Сумма корректируемой операции до корректировки (UC7). */
    @Column(name = "original_amount")
    private Integer originalAmount;

    @Column(name = "reason", nullable = false, length = REASON_LENGTH)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    private AccrualRule rule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id")
    private Program program;

    /** Событие-источник автоначисления; UNIQUE — одно событие, одно начисление (NFR-4). */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private BusinessEvent event;

    /** Корректируемая операция (UC7). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corrects_id")
    private Operation corrects;

    /** Автор; {@code null} — автоматическое начисление. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Transient
    private boolean isNew;

    protected Operation() {
    }

    /**
     * Создаёт новую операцию. Необязательные связи задаются методами {@code with…}.
     */
    public static Operation create(OperationNumber number, BonusAccount account, OperationType type,
                                   OperationStatus status, OperationBasis basis, int amount, String reason,
                                   AppUser createdBy, OffsetDateTime createdAt) {
        if (amount == 0) {
            throw new IllegalArgumentException("Сумма операции не может быть нулевой");
        }
        Objects.requireNonNull(reason, "reason");
        if (reason.length() > REASON_LENGTH) {
            throw new IllegalArgumentException("Причина длиннее " + REASON_LENGTH + " символов");
        }
        Operation op = new Operation();
        op.id = number.id();
        op.number = number.number();
        op.account = Objects.requireNonNull(account, "account");
        op.type = Objects.requireNonNull(type, "type");
        op.status = Objects.requireNonNull(status, "status");
        op.basis = Objects.requireNonNull(basis, "basis");
        op.amount = amount;
        op.reason = reason;
        op.createdBy = createdBy;
        op.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        op.isNew = true;
        return op;
    }

    public Operation withRule(AccrualRule rule) {
        this.rule = rule;
        return this;
    }

    public Operation withCampaign(Campaign campaign) {
        this.campaign = campaign;
        return this;
    }

    public Operation withProgram(Program program) {
        this.program = program;
        return this;
    }

    public Operation withEvent(BusinessEvent event) {
        this.event = event;
        return this;
    }

    /** Помечает операцию как корректировку операции {@code original} (UC7). */
    public Operation correcting(Operation original) {
        this.corrects = original;
        this.originalAmount = original.getAmount();
        return this;
    }

    /**
     * Переводит операцию в статус {@code target} по диаграмме состояний
     * ({@link OperationStatus#canTransitionTo(OperationStatus)}).
     */
    public void transitionTo(OperationStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Недопустимый переход операции " + number + ": " + status + " → " + target);
        }
        this.status = target;
    }

    /** Корректировка-отмена: отрицательная сумма у операции типа correction. */
    public boolean isCancellationRequest() {
        return type == OperationType.CORRECTION && amount < 0;
    }

    /** Изменение баланса, которое даст проведение этой корректировки: новая сумма минус исходная. */
    public int correctionDelta() {
        if (type != OperationType.CORRECTION || originalAmount == null) {
            throw new IllegalStateException("Операция " + number + " не является корректировкой");
        }
        return isCancellationRequest() ? -originalAmount : amount - originalAmount;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @Override
    public Long getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    public BonusAccount getAccount() {
        return account;
    }

    public OperationType getType() {
        return type;
    }

    public OperationStatus getStatus() {
        return status;
    }

    public OperationBasis getBasis() {
        return basis;
    }

    public int getAmount() {
        return amount;
    }

    public Integer getOriginalAmount() {
        return originalAmount;
    }

    public String getReason() {
        return reason;
    }

    public AccrualRule getRule() {
        return rule;
    }

    public Campaign getCampaign() {
        return campaign;
    }

    public Program getProgram() {
        return program;
    }

    public BusinessEvent getEvent() {
        return event;
    }

    public Operation getCorrects() {
        return corrects;
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
