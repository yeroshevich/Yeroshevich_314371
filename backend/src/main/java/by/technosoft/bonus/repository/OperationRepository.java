package by.technosoft.bonus.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import by.technosoft.bonus.domain.Operation;
import by.technosoft.bonus.domain.OperationStatus;
import jakarta.persistence.LockModeType;

/** Журнал операций по счетам (DATA-1). */
public interface OperationRepository extends JpaRepository<Operation, Long>, JpaSpecificationExecutor<Operation> {

    /**
     * Поиск по набору условий ({@link OperationSpecifications}) с загрузкой владельца счёта и автора
     * одним запросом — без N+1 при построении списков (UC2, журнал операций, отчёты).
     */
    @Override
    @EntityGraph(attributePaths = {"account", "account.employee", "createdBy", "createdBy.employee"})
    List<Operation> findAll(Specification<Operation> spec, Sort sort);

    /** Сумма операций по кампаниям в указанных статусах — израсходованный бюджет всех кампаний (UC9). */
    @Query("""
            select new by.technosoft.bonus.repository.AmountByKey(o.campaign.id, sum(o.amount))
            from Operation o
            where o.campaign is not null and o.status in :statuses
            group by o.campaign.id
            """)
    List<AmountByKey> sumAmountGroupedByCampaign(@Param("statuses") Collection<OperationStatus> statuses);

    /** Есть ли операции, ссылающиеся на кампанию (удалять такую кампанию нельзя — DATA-1). */
    boolean existsByCampaignId(Long campaignId);

    /** Применялось ли правило (любая его версия по показателю KPI) в расчётах начиная с даты (UC8). */
    @Query("""
            select count(o) > 0 from Operation o
            where o.rule.kpi.id = :kpiId and o.createdAt >= :since
            """)
    boolean existsByRuleKpiSince(@Param("kpiId") Long kpiId, @Param("since") OffsetDateTime since);

    /** История операций по счёту, новые сверху (UC2, индекс ix_operation_account_dt). */
    Page<Operation> findByAccountIdOrderByCreatedAtDesc(Long accountId, Pageable page);

    /** Очередь финотдела: операции в статусе {@code status}, старые сверху. */
    List<Operation> findByStatusOrderByCreatedAtAsc(OperationStatus status);

    /** Есть ли уже операция по событию (идемпотентность UC4, NFR-4). */
    boolean existsByEventId(Long eventId);

    List<Operation> findByEventId(Long eventId);

    /** Есть ли у операции корректировка в статусе {@code status} (например, на согласовании). */
    boolean existsByCorrectsIdAndStatus(Long correctsId, OperationStatus status);

    /** Операция с блокировкой строки — для смены статуса (UC7, контроль финотдела). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Operation o where o.id = :id")
    Optional<Operation> lockById(@Param("id") Long id);

    /** Сумма операций кампании в указанных статусах — израсходованный бюджет (UC9). */
    @Query("""
            select coalesce(sum(o.amount), 0) from Operation o
            where o.campaign.id = :campaignId and o.status in :statuses
            """)
    long sumAmountByCampaign(@Param("campaignId") Long campaignId,
                             @Param("statuses") Collection<OperationStatus> statuses);

    /** Сумма операций счёта в указанных статусах — сверка баланса с журналом (BR-6). */
    @Query("""
            select coalesce(sum(o.amount), 0) from Operation o
            where o.account.id = :accountId and o.status in :statuses
            """)
    long sumAmountByAccount(@Param("accountId") Long accountId,
                            @Param("statuses") Collection<OperationStatus> statuses);
}
