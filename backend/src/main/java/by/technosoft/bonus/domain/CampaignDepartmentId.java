package by.technosoft.bonus.domain;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Составной ключ таблицы campaign_department. */
@Embeddable
public class CampaignDepartmentId implements Serializable {

    @Column(name = "campaign_id")
    private Long campaignId;

    @Column(name = "department_id")
    private Long departmentId;

    protected CampaignDepartmentId() {
    }

    public CampaignDepartmentId(Long campaignId, Long departmentId) {
        this.campaignId = campaignId;
        this.departmentId = departmentId;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CampaignDepartmentId other)) {
            return false;
        }
        return Objects.equals(campaignId, other.campaignId) && Objects.equals(departmentId, other.departmentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(campaignId, departmentId);
    }
}
