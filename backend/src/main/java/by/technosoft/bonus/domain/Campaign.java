package by.technosoft.bonus.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/** Бонусная кампания (таблица campaign, UC9). */
@Entity
@Table(name = "campaign")
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** Повышающий коэффициент к начислениям, [1; 5]. */
    @Column(name = "coefficient", nullable = false, precision = 3, scale = 2)
    private BigDecimal coefficient;

    /** Бюджет в баллах; {@code null} — бюджет не выделен. */
    @Column(name = "budget")
    private Integer budget;

    @Column(name = "status", nullable = false, length = 15)
    private CampaignStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /** Целевые подразделения кампании. */
    @OneToMany(mappedBy = "campaign")
    private List<CampaignDepartment> targets = new ArrayList<>();

    protected Campaign() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public BigDecimal getCoefficient() {
        return coefficient;
    }

    public Integer getBudget() {
        return budget;
    }

    public CampaignStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public List<CampaignDepartment> getTargets() {
        return targets;
    }

    /** Кампания активна и дата {@code date} попадает в её сроки. */
    public boolean isRunningOn(LocalDate date) {
        return status == CampaignStatus.ACTIVE && !date.isBefore(startDate) && !date.isAfter(endDate);
    }
}
