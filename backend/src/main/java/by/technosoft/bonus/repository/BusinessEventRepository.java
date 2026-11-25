package by.technosoft.bonus.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import by.technosoft.bonus.domain.BusinessEvent;
import by.technosoft.bonus.domain.EventStatus;
import jakarta.persistence.LockModeType;

/** Очередь бизнес-событий (UC4, SI-4). */
public interface BusinessEventRepository extends JpaRepository<BusinessEvent, Long> {

    /** Идентификаторы событий в статусе {@code status} в порядке поступления (пакет задаётся {@code page}). */
    @Query("select e.id from BusinessEvent e where e.status = :status order by e.receivedAt, e.id")
    List<Long> findIdsByStatus(@Param("status") EventStatus status, Pageable page);

    /** Событие с блокировкой строки: два обработчика не обработают его одновременно (NFR-4). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from BusinessEvent e where e.id = :id")
    Optional<BusinessEvent> lockById(@Param("id") Long id);
}
