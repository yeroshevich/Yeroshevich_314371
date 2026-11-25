package by.technosoft.bonus.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Подключение к внешней системе (таблица integration, UC13). */
@Entity
@Table(name = "integration")
public class Integration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kind", nullable = false, length = 15)
    private IntegrationKind kind;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "url", nullable = false, length = 255)
    private String url;

    /** API-ключ в зашифрованном виде (DATA-4). */
    @Column(name = "api_key_encrypted", nullable = false, length = 512)
    private String apiKeyEncrypted;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "last_sync_at")
    private OffsetDateTime lastSyncAt;

    protected Integration() {
    }

    public Long getId() {
        return id;
    }

    public IntegrationKind getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public String getApiKeyEncrypted() {
        return apiKeyEncrypted;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public OffsetDateTime getLastSyncAt() {
        return lastSyncAt;
    }
}
