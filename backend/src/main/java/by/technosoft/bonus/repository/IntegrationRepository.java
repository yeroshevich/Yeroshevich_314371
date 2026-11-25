package by.technosoft.bonus.repository;

import java.time.OffsetDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import by.technosoft.bonus.domain.Integration;

/** Подключения к внешним системам (UC13). */
public interface IntegrationRepository extends JpaRepository<Integration, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update integration set url = :url, api_key_encrypted = :apiKey where id = :id", nativeQuery = true)
    int updateConnection(@Param("id") long id, @Param("url") String url, @Param("apiKey") String apiKeyEncrypted);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update integration set enabled = :enabled,
                                   last_sync_at = coalesce(cast(:syncAt as timestamptz), last_sync_at)
            where id = :id
            """, nativeQuery = true)
    int updateEnabled(@Param("id") long id, @Param("enabled") boolean enabled,
                      @Param("syncAt") OffsetDateTime syncAt);
}
