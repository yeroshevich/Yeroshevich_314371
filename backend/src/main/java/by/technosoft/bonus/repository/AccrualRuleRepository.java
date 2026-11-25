package by.technosoft.bonus.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import by.technosoft.bonus.domain.AccrualRule;

/**
 * Доступ к правилам начисления (UC8).
 * Изменение правила, уже применённого в текущем периоде, сохраняется новой версией строки
 * с более поздней датой effective_from (альт. поток UC8); версии одного правила связаны показателем KPI.
 */
public interface AccrualRuleRepository extends JpaRepository<AccrualRule, Long> {

    /**
     * Действующее на дату правило по коду KPI: последняя вступившая в силу версия, если она активна.
     * Список содержит не более одного элемента; первым идёт самое свежее.
     */
    @Query("""
            select r from AccrualRule r join fetch r.kpi k
            where k.code = :kpiCode and r.active = true and r.effectiveFrom <= :date
              and not exists (
                  select n.id from AccrualRule n
                  where n.kpi = r.kpi and n.effectiveFrom <= :date
                    and (n.effectiveFrom > r.effectiveFrom or (n.effectiveFrom = r.effectiveFrom and n.id > r.id)))
            order by r.effectiveFrom desc, r.id desc
            """)
    List<AccrualRule> findApplicableByKpiCode(@Param("kpiCode") String kpiCode, @Param("date") LocalDate date);

    /** Все версии всех правил вместе с показателями. */
    @EntityGraph(attributePaths = "kpi")
    @Query("select r from AccrualRule r order by r.id")
    List<AccrualRule> findAllWithKpi();

    @EntityGraph(attributePaths = "kpi")
    @Query("select r from AccrualRule r where r.id = :id")
    Optional<AccrualRule> findWithKpiById(@Param("id") Long id);

    /** Версии правила по показателю: последняя (самая поздняя effective_from) — первой. */
    @EntityGraph(attributePaths = "kpi")
    @Query("select r from AccrualRule r where r.kpi.id = :kpiId order by r.effectiveFrom desc, r.id desc")
    List<AccrualRule> findVersionsByKpiId(@Param("kpiId") Long kpiId);

    // ── Изменения (UC8): явные SQL-команды, доменная сущность не меняется. ──

    @Query(value = "select nextval(pg_get_serial_sequence('accrual_rule', 'id'))", nativeQuery = true)
    long nextId();

    /** Новое правило; params по умолчанию '{}' (округление до 50 баллов). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into accrual_rule (id, name, kpi_id, weight, base_points, periodicity, threshold,
                                      active, effective_from, updated_at)
            values (:id, :name, :kpiId, :weight, :basePoints, :periodicity, :threshold,
                    :active, :effectiveFrom, :now)
            """, nativeQuery = true)
    int insert(@Param("id") long id, @Param("name") String name, @Param("kpiId") long kpiId,
               @Param("weight") BigDecimal weight, @Param("basePoints") int basePoints,
               @Param("periodicity") String periodicity, @Param("threshold") BigDecimal threshold,
               @Param("active") boolean active, @Param("effectiveFrom") LocalDate effectiveFrom,
               @Param("now") OffsetDateTime now);

    /** Новая версия правила: параметры JSONB копируются из версии {@code sourceId}. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into accrual_rule (id, name, kpi_id, weight, base_points, periodicity, threshold, params,
                                      active, effective_from, updated_at)
            select :id, :name, s.kpi_id, :weight, :basePoints, :periodicity, :threshold, s.params,
                   :active, :effectiveFrom, :now
            from accrual_rule s where s.id = :sourceId
            """, nativeQuery = true)
    int insertVersion(@Param("id") long id, @Param("sourceId") long sourceId, @Param("name") String name,
                      @Param("weight") BigDecimal weight, @Param("basePoints") int basePoints,
                      @Param("periodicity") String periodicity, @Param("threshold") BigDecimal threshold,
                      @Param("active") boolean active, @Param("effectiveFrom") LocalDate effectiveFrom,
                      @Param("now") OffsetDateTime now);

    /** Правка версии на месте (правило ещё не применялось в текущем периоде или вступит в силу позже). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update accrual_rule set name = :name, kpi_id = :kpiId, weight = :weight, base_points = :basePoints,
                                    periodicity = :periodicity, threshold = :threshold, active = :active,
                                    updated_at = :now
            where id = :id
            """, nativeQuery = true)
    int update(@Param("id") long id, @Param("name") String name, @Param("kpiId") long kpiId,
               @Param("weight") BigDecimal weight, @Param("basePoints") int basePoints,
               @Param("periodicity") String periodicity, @Param("threshold") BigDecimal threshold,
               @Param("active") boolean active, @Param("now") OffsetDateTime now);
}
