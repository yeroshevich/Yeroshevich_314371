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

/** Решение финансового отдела по операции (таблица operation_review). */
@Entity
@Table(name = "operation_review")
public class OperationReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operation_id", nullable = false)
    private Operation operation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private AppUser reviewer;

    @Column(name = "decision", nullable = false, length = 10)
    private ReviewDecision decision;

    @Column(name = "comment", length = 300)
    private String comment;

    @Column(name = "decided_at", nullable = false)
    private OffsetDateTime decidedAt;

    protected OperationReview() {
    }

    public OperationReview(Operation operation, AppUser reviewer, ReviewDecision decision, String comment,
                           OffsetDateTime decidedAt) {
        this.operation = operation;
        this.reviewer = reviewer;
        this.decision = decision;
        this.comment = comment;
        this.decidedAt = decidedAt;
    }

    public Long getId() {
        return id;
    }

    public Operation getOperation() {
        return operation;
    }

    public AppUser getReviewer() {
        return reviewer;
    }

    public ReviewDecision getDecision() {
        return decision;
    }

    public String getComment() {
        return comment;
    }

    public OffsetDateTime getDecidedAt() {
        return decidedAt;
    }
}
