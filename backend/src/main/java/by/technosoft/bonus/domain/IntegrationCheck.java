package by.technosoft.bonus.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Результат проверки соединения с внешней системой (таблица integration_check). */
@Entity
@Table(name = "integration_check")
public class IntegrationCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "integration_id", nullable = false)
    private Integration integration;

    @Column(name = "ok", nullable = false)
    private boolean ok;

    @Column(name = "message", nullable = false, length = 255)
    private String message;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "checked_at", nullable = false)
    private OffsetDateTime checkedAt;

    protected IntegrationCheck() {
    }

    public Long getId() {
        return id;
    }

    public Integration getIntegration() {
        return integration;
    }

    public boolean isOk() {
        return ok;
    }

    public String getMessage() {
        return message;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public OffsetDateTime getCheckedAt() {
        return checkedAt;
    }
}
