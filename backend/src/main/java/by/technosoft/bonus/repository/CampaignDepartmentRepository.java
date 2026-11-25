package by.technosoft.bonus.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import by.technosoft.bonus.domain.CampaignDepartment;
import by.technosoft.bonus.domain.CampaignDepartmentId;

/** Целевые подразделения кампаний. */
public interface CampaignDepartmentRepository extends JpaRepository<CampaignDepartment, CampaignDepartmentId> {

    /** Входит ли подразделение в цели кампании. */
    boolean existsByCampaignIdAndDepartmentId(Long campaignId, Long departmentId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "insert into campaign_department (campaign_id, department_id) values (:campaignId, :departmentId)",
            nativeQuery = true)
    int insert(@Param("campaignId") long campaignId, @Param("departmentId") long departmentId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from campaign_department where campaign_id = :campaignId", nativeQuery = true)
    int deleteByCampaign(@Param("campaignId") long campaignId);
}
