package by.technosoft.bonus.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import by.technosoft.bonus.domain.Employee;

/** Доступ к сотрудникам. */
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    /** Команда руководителя: сотрудники, у которых он непосредственный руководитель. */
    List<Employee> findByManagerIdOrderByFullName(Long managerEmployeeId);

    /** Команда руководителя вместе с подразделениями (экран «Моя команда»). */
    @EntityGraph(attributePaths = "department")
    List<Employee> findWithDepartmentByManagerIdOrderByFullName(Long managerEmployeeId);

    /** Все сотрудники вместе с подразделениями. */
    @EntityGraph(attributePaths = "department")
    @Query("select e from Employee e order by e.fullName")
    List<Employee> findAllWithDepartment();

    /** Сотрудники подразделения (аналитика руководителя, UC11). */
    List<Employee> findByDepartmentId(Long departmentId);

    /** Проверка дубля email (UC12). */
    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    // ── Изменения (UC12): явные SQL-команды, доменная сущность не меняется. ──

    @Query(value = "select nextval(pg_get_serial_sequence('employee', 'id'))", nativeQuery = true)
    long nextId();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into employee (id, full_name, email, position, department_id, manager_id, hired_at)
            values (:id, :fullName, :email, :position, :departmentId, cast(:managerId as bigint), :hiredAt)
            """, nativeQuery = true)
    int insert(@Param("id") long id, @Param("fullName") String fullName, @Param("email") String email,
               @Param("position") String position, @Param("departmentId") long departmentId,
               @Param("managerId") Long managerId, @Param("hiredAt") LocalDate hiredAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update employee set full_name = :fullName, email = :email, position = :position,
                                department_id = :departmentId, manager_id = cast(:managerId as bigint)
            where id = :id
            """, nativeQuery = true)
    int update(@Param("id") long id, @Param("fullName") String fullName, @Param("email") String email,
               @Param("position") String position, @Param("departmentId") long departmentId,
               @Param("managerId") Long managerId);
}
