package by.technosoft.bonus.repository;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import by.technosoft.bonus.domain.IntegrationCheck;

/** Журнал проверок соединения с внешними системами (UC13). */
public interface IntegrationCheckRepository extends JpaRepository<IntegrationCheck, Long> {

    /** Последняя проверка интеграции. */
    Optional<IntegrationCheck> findFirstByIntegrationIdOrderByCheckedAtDescIdDesc(Long integrationId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into integration_check (integration_id, ok, message, latency_ms, checked_at)
            values (:integrationId, :ok, :message, cast(:latencyMs as integer), :checkedAt)
            """, nativeQuery = true)
    int insert(@Param("integrationId") long integrationId, @Param("ok") boolean ok, @Param("message") String message,
               @Param("latencyMs") Integer latencyMs, @Param("checkedAt") OffsetDateTime checkedAt);
}
