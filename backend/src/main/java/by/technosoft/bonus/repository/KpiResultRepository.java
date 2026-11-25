package by.technosoft.bonus.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import by.technosoft.bonus.domain.KpiResult;

/** Доступ к подтверждённым результатам KPI (BR-5). */
public interface KpiResultRepository extends JpaRepository<KpiResult, Long> {

    /** Результат сотрудника по показателю за период, содержащий дату {@code date}. */
    @Query("""
            select r from KpiResult r
            where r.employee.id = :employeeId and r.kpi.id = :kpiId
              and r.periodStart <= :date and r.periodEnd >= :date
            """)
    Optional<KpiResult> findForDate(@Param("employeeId") Long employeeId, @Param("kpiId") Long kpiId,
                                    @Param("date") LocalDate date);

    /** Все результаты за периоды, содержащие дату (выполнение KPI «за текущий период»). */
    @Query("select r from KpiResult r where r.periodStart <= :date and r.periodEnd >= :date")
    List<KpiResult> findCovering(@Param("date") LocalDate date);
}
