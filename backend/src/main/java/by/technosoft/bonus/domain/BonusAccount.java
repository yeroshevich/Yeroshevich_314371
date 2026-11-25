package by.technosoft.bonus.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Единый бонусный счёт сотрудника (таблица bonus_account, BR-6).
 * Баланс всегда равен сумме операций в статусах {@link OperationStatus#SETTLED};
 * изменяется только через {@link #applyDelta(int, OffsetDateTime)}.
 * Поле {@code version} — оптимистичная блокировка (JPA {@code @Version}).
 */
@Entity
@Table(name = "bonus_account")
public class BonusAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "balance", nullable = false)
    private int balance;

    @Column(name = "status", nullable = false, length = 10)
    private AccountStatus status;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    protected BonusAccount() {
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public int getBalance() {
        return balance;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public int getVersion() {
        return version;
    }

    /**
     * Изменяет баланс на {@code delta} баллов (начисление «+», списание «−»).
     * Защищает инвариант схемы {@code CHECK (balance >= 0)}: бизнес-проверки
     * (достаточно ли баллов) выполняет сервис до вызова.
     */
    public void applyDelta(int delta, OffsetDateTime now) {
        int result = Math.addExact(balance, delta);
        if (result < 0) {
            throw new IllegalStateException("Баланс счёта " + id + " не может стать отрицательным: " + result);
        }
        this.balance = result;
        this.updatedAt = now;
    }
}
