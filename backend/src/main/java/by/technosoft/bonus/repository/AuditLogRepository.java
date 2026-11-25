package by.technosoft.bonus.repository;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import by.technosoft.bonus.domain.AuditLog;

/** Журнал аудита (NFR-3, SEC-4). */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByObjectTypeAndObjectIdOrderByOccurredAtDesc(String objectType, Long objectId);

    /** Записи за период [from; to) вместе с автором и его ролью; новые сверху. */
    @EntityGraph(attributePaths = {"user", "user.employee", "user.role"})
    @Query("""
            select a from AuditLog a
            where a.occurredAt >= :from and a.occurredAt < :to
            order by a.occurredAt desc, a.id desc
            """)
    List<AuditLog> findPeriod(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to, Pageable page);
}
