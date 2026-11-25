package by.technosoft.bonus.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import by.technosoft.bonus.domain.OperationReview;
import by.technosoft.bonus.domain.ReviewDecision;

/** Журнал решений финансового отдела. */
public interface OperationReviewRepository extends JpaRepository<OperationReview, Long> {

    /** Решения по операциям, старые сверху: последнее решение по операции — последнее в списке. */
    @Query("""
            select r from OperationReview r
            where r.operation.id in :operationIds
            order by r.decidedAt, r.id
            """)
    List<OperationReview> findByOperationIds(@Param("operationIds") Collection<Long> operationIds);

    /** Операции, по которым когда-либо принималось решение {@code decision} (например, «спорное»). */
    @Query("select distinct r.operation.id from OperationReview r where r.decision = :decision")
    List<Long> findOperationIdsByDecision(@Param("decision") ReviewDecision decision);
}
