package by.technosoft.bonus.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import by.technosoft.bonus.domain.BonusAccount;
import jakarta.persistence.LockModeType;

/** Доступ к бонусным счетам (BR-6). */
public interface BonusAccountRepository extends JpaRepository<BonusAccount, Long> {

    Optional<BonusAccount> findByEmployeeId(Long employeeId);

    /** Счета группы сотрудников (команда, отчёты). */
    List<BonusAccount> findByEmployeeIdIn(Collection<Long> employeeIds);

    /**
     * Счёт сотрудника с блокировкой строки (SELECT … FOR UPDATE) до конца транзакции:
     * параллельные списания и начисления по одному счёту выполняются последовательно.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from BonusAccount a where a.employee.id = :employeeId")
    Optional<BonusAccount> lockByEmployeeId(@Param("employeeId") Long employeeId);

    /** Счёт по id с блокировкой строки. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from BonusAccount a where a.id = :id")
    Optional<BonusAccount> lockById(@Param("id") Long id);

    /** Открывает пустой активный счёт новому сотруднику (UC12, BR-6: один счёт на сотрудника). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into bonus_account (employee_id, balance, status, updated_at)
            values (:employeeId, 0, 'active', :now)
            """, nativeQuery = true)
    int open(@Param("employeeId") long employeeId, @Param("now") OffsetDateTime now);
}
