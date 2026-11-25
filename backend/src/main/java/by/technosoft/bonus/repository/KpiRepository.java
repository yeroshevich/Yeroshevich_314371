package by.technosoft.bonus.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import by.technosoft.bonus.domain.Kpi;

/** Справочник показателей KPI (UC8). */
public interface KpiRepository extends JpaRepository<Kpi, Long> {

    Optional<Kpi> findByNameIgnoreCase(String name);

    @Query(value = "select nextval(pg_get_serial_sequence('kpi', 'id'))", nativeQuery = true)
    long nextId();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "insert into kpi (id, code, name, unit) values (:id, :code, :name, '%')", nativeQuery = true)
    int insert(@Param("id") long id, @Param("code") String code, @Param("name") String name);
}
