package by.technosoft.bonus.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import by.technosoft.bonus.domain.Program;

/** Доступ к каталогу программ мотивации (UC3). */
public interface ProgramRepository extends JpaRepository<Program, Long> {
}
