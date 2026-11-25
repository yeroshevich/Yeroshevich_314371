package by.technosoft.bonus.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import by.technosoft.bonus.domain.AppUser;

/** Доступ к учётным записям. */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** Учётная запись вместе с ролью и сотрудником — для проверки прав (SEC-3). */
    @EntityGraph(attributePaths = {"role", "employee"})
    Optional<AppUser> findWithRoleById(Long id);

    Optional<AppUser> findByEmployeeId(Long employeeId);

    /** Вход по email сотрудника (SEC-1); email сравнивается без учёта регистра. */
    @EntityGraph(attributePaths = {"role", "employee"})
    Optional<AppUser> findByEmployeeEmailIgnoreCase(String email);

    /** Все учётные записи с ролью и сотрудником (UC12). */
    @EntityGraph(attributePaths = {"role", "employee"})
    @Query("select u from AppUser u order by u.employee.fullName")
    List<AppUser> findAllWithDetails();

    // ── Изменения (UC12). Сущность AppUser неизменяема в доменной модели, поэтому
    //    создание и правка выполняются явными SQL-командами репозитория. ──────────

    @Query(value = "select nextval(pg_get_serial_sequence('app_user', 'id'))", nativeQuery = true)
    long nextId();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into app_user (id, employee_id, role_code, password_hash, status, created_at)
            values (:id, :employeeId, :roleCode, :passwordHash, 'active', :createdAt)
            """, nativeQuery = true)
    int insert(@Param("id") long id, @Param("employeeId") long employeeId, @Param("roleCode") String roleCode,
               @Param("passwordHash") String passwordHash, @Param("createdAt") OffsetDateTime createdAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update app_user set role_code = :roleCode where id = :id", nativeQuery = true)
    int updateRole(@Param("id") long id, @Param("roleCode") String roleCode);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update app_user set status = :status where id = :id", nativeQuery = true)
    int updateStatus(@Param("id") long id, @Param("status") String status);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update app_user set last_login_at = :at where id = :id", nativeQuery = true)
    int touchLastLogin(@Param("id") long id, @Param("at") OffsetDateTime at);
}
