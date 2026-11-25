package by.technosoft.bonus.domain;

import java.net.InetAddress;
import java.time.OffsetDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Запись журнала аудита (таблица audit_log, NFR-3, SEC-4, DATA-3). */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    /** Пользователь; {@code null} — действие системы. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "object_type", nullable = false, length = 50)
    private String objectType;

    @Column(name = "object_id")
    private Long objectId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", nullable = false, columnDefinition = "jsonb")
    private String details;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip", columnDefinition = "inet")
    private InetAddress ip;

    protected AuditLog() {
    }

    public AuditLog(OffsetDateTime occurredAt, AppUser user, String action, String objectType, Long objectId,
                    String details, InetAddress ip) {
        this.occurredAt = occurredAt;
        this.user = user;
        this.action = action;
        this.objectType = objectType;
        this.objectId = objectId;
        this.details = details == null ? "{}" : details;
        this.ip = ip;
    }

    public Long getId() {
        return id;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public AppUser getUser() {
        return user;
    }

    public String getAction() {
        return action;
    }

    public String getObjectType() {
        return objectType;
    }

    public Long getObjectId() {
        return objectId;
    }

    public String getDetails() {
        return details;
    }

    public InetAddress getIp() {
        return ip;
    }
}
