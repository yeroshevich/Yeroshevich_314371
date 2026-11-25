package by.technosoft.bonus.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Подтверждённый результат KPI сотрудника за период (таблица kpi_result, BR-5). */
@Entity
@Table(name = "kpi_result")
public class KpiResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kpi_id", nullable = false)
    private Kpi kpi;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    /** Уровень достижения, % (0–150). */
    @Column(name = "value_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal valuePct;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    protected KpiResult() {
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public Kpi getKpi() {
        return kpi;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public BigDecimal getValuePct() {
        return valuePct;
    }

    public OffsetDateTime getRecordedAt() {
        return recordedAt;
    }
}
