package by.technosoft.bonus.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/** Целевое подразделение кампании (таблица campaign_department, связь M:N). */
@Entity
@Table(name = "campaign_department")
public class CampaignDepartment {

    @EmbeddedId
    private CampaignDepartmentId id;

    @MapsId("campaignId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    @MapsId("departmentId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id")
    private Department department;

    protected CampaignDepartment() {
    }

    public CampaignDepartmentId getId() {
        return id;
    }

    public Campaign getCampaign() {
        return campaign;
    }

    public Department getDepartment() {
        return department;
    }
}
