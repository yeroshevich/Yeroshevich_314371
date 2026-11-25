package by.technosoft.bonus.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Правило начисления (таблица accrual_rule, UC8, BR-4). */
@Entity
@Table(name = "accrual_rule")
public class AccrualRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kpi_id", nullable = false)
    private Kpi kpi;

    /** Вес показателя, (0; 1]. */
    @Column(name = "weight", nullable = false, precision = 3, scale = 2)
    private BigDecimal weight;

    /** Базовая сумма, к которой применяется формула, баллы. */
    @Column(name = "base_points", nullable = false)
    private int basePoints;

    @Column(name = "periodicity", nullable = false, length = 10)
    private RulePeriodicity periodicity;

    /** Порог срабатывания: минимальное выполнение KPI, % (BR-5). */
    @Column(name = "threshold", nullable = false, precision = 5, scale = 2)
    private BigDecimal threshold;

    /** Дополнительные условия правила в JSONB, например {@code {"roundTo": 50}}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params", nullable = false, columnDefinition = "jsonb")
    private String params;

    @Column(name = "active", nullable = false)
    private boolean active;

    /** Дата, с которой действует текущая редакция правила (альт. поток UC8). */
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected AccrualRule() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Kpi getKpi() {
        return kpi;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public int getBasePoints() {
        return basePoints;
    }

    public RulePeriodicity getPeriodicity() {
        return periodicity;
    }

    public BigDecimal getThreshold() {
        return threshold;
    }

    public String getParams() {
        return params;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    /** Действует ли правило на дату {@code date}: активно и вступило в силу. */
    public boolean isApplicableOn(LocalDate date) {
        return active && !effectiveFrom.isAfter(date);
    }
}
