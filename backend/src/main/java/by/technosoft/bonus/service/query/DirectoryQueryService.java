package by.technosoft.bonus.service.query;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.technosoft.bonus.domain.Department;
import by.technosoft.bonus.domain.Program;
import by.technosoft.bonus.repository.DepartmentRepository;
import by.technosoft.bonus.repository.ProgramRepository;

/** Справочники без области видимости: подразделения и каталог программ мотивации (UC3). */
@Service
@Transactional(readOnly = true)
public class DirectoryQueryService {

    private static final Sort BY_ID = Sort.by("id");

    private final DepartmentRepository departmentRepository;
    private final ProgramRepository programRepository;

    public DirectoryQueryService(DepartmentRepository departmentRepository, ProgramRepository programRepository) {
        this.departmentRepository = departmentRepository;
        this.programRepository = programRepository;
    }

    public List<Department> departments() {
        return departmentRepository.findAll(BY_ID);
    }

    /** Каталог программ: активные и недоступные (с пояснением). */
    public List<Program> programs() {
        return programRepository.findAll(BY_ID);
    }
}
