package by.technosoft.bonus.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import by.technosoft.bonus.domain.Campaign;
import by.technosoft.bonus.domain.CampaignStatus;

/** Доступ к бонусным кампаниям (UC9). */
public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    /**
     * Кампании в статусе {@code status}, которые идут на дату {@code date} и охватывают подразделение;
     * упорядочены по убыванию коэффициента.
     */
    @Query("""
            select c from CampaignDepartment cd join cd.campaign c
            where cd.department.id = :departmentId and c.status = :status
              and c.startDate <= :date and c.endDate >= :date
            order by c.coefficient desc, c.id
            """)
    List<Campaign> findRunningForDepartment(@Param("departmentId") Long departmentId,
                                            @Param("status") CampaignStatus status,
                                            @Param("date") LocalDate date);

    /** Все кампании с целевыми подразделениями; новые сверху. */
    @EntityGraph(attributePaths = "targets")
    @Query("select distinct c from Campaign c order by c.startDate desc, c.id desc")
    List<Campaign> findAllWithTargets();

    @EntityGraph(attributePaths = "targets")
    @Query("select c from Campaign c where c.id = :id")
    Optional<Campaign> findWithTargetsById(@Param("id") Long id);

    /**
     * Кандидаты на смену статуса по датам (дефект D-1): кампании в статусах {@code statuses},
     * начавшиеся не позже {@code date}. Кампании, которые ещё не начались, статус не меняют.
     */
    @Query("""
            select c from Campaign c
            where c.status in :statuses and c.startDate <= :date
            order by c.id
            """)
    List<Campaign> findStartedInStatuses(@Param("statuses") Collection<CampaignStatus> statuses,
                                         @Param("date") LocalDate date);

    // ── Изменения (UC9): явные SQL-команды, доменная сущность не меняется. ──

    @Query(value = "select nextval(pg_get_serial_sequence('campaign', 'id'))", nativeQuery = true)
    long nextId();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into campaign (id, name, description, start_date, end_date, coefficient, budget, status, created_at)
            values (:id, :name, :description, :startDate, :endDate, :coefficient, cast(:budget as integer),
                    :status, :now)
            """, nativeQuery = true)
    int insert(@Param("id") long id, @Param("name") String name, @Param("description") String description,
               @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
               @Param("coefficient") BigDecimal coefficient, @Param("budget") Integer budget,
               @Param("status") String status, @Param("now") OffsetDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update campaign set name = :name, description = :description, start_date = :startDate,
                                end_date = :endDate, coefficient = :coefficient,
                                budget = cast(:budget as integer), status = :status
            where id = :id
            """, nativeQuery = true)
    int update(@Param("id") long id, @Param("name") String name, @Param("description") String description,
               @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
               @Param("coefficient") BigDecimal coefficient, @Param("budget") Integer budget,
               @Param("status") String status);

    /**
     * Смена статуса кампании, только если он всё ещё {@code fromStatus} (условное обновление):
     * при одновременной синхронизации переход выполнит одна транзакция, вторая получит 0 строк.
     *
     * @return число изменённых строк (0 или 1)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update campaign set status = :toStatus where id = :id and status = :fromStatus",
            nativeQuery = true)
    int updateStatus(@Param("id") long id, @Param("fromStatus") String fromStatus,
                     @Param("toStatus") String toStatus);

    /** Удаление кампании; целевые подразделения удаляются каскадно (ON DELETE CASCADE). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from campaign where id = :id", nativeQuery = true)
    int deleteCampaign(@Param("id") long id);
}
