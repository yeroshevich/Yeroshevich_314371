package by.technosoft.bonus.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import by.technosoft.bonus.domain.Department;

/** Доступ к подразделениям. */
public interface DepartmentRepository extends JpaRepository<Department, Long> {
}
