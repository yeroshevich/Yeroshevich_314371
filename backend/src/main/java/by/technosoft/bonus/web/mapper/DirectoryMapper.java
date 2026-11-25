package by.technosoft.bonus.web.mapper;

import static by.technosoft.bonus.web.mapper.ApiValues.code;
import static by.technosoft.bonus.web.mapper.ApiValues.id;

import org.springframework.stereotype.Component;

import by.technosoft.bonus.domain.AccountStatus;
import by.technosoft.bonus.domain.Department;
import by.technosoft.bonus.domain.Employee;
import by.technosoft.bonus.domain.Program;
import by.technosoft.bonus.service.query.EmployeeQueryService.EmployeeRow;
import by.technosoft.bonus.service.query.EmployeeQueryService.TeamMemberRow;
import by.technosoft.bonus.web.dto.DepartmentDto;
import by.technosoft.bonus.web.dto.EmployeeDto;
import by.technosoft.bonus.web.dto.ProgramDto;
import by.technosoft.bonus.web.dto.TeamMemberDto;

/** Справочники → JSON: подразделения, сотрудники, команда, программы мотивации. */
@Component
public class DirectoryMapper {

    public DepartmentDto toDto(Department department) {
        return new DepartmentDto(id(department.getId()), department.getName(),
                department.getHead() == null ? null : id(department.getHead().getId()));
    }

    public ProgramDto toDto(Program program) {
        return new ProgramDto(id(program.getId()), program.getName(), code(program.getCategory()),
                program.getDescription(), program.getCost(), program.isActive(), program.getInactiveReason());
    }

    public EmployeeDto toDto(EmployeeRow row) {
        Employee e = row.employee();
        return new EmployeeDto(id(e.getId()), e.getFullName(), e.getEmail(), e.getPosition(),
                id(e.getDepartment().getId()), managerId(e), e.getHiredAt(), row.kpiCompletion());
    }

    public TeamMemberDto toDto(TeamMemberRow row) {
        Employee e = row.employee();
        boolean hasAccount = row.account() != null;
        return new TeamMemberDto(id(e.getId()), e.getFullName(), e.getEmail(), e.getPosition(),
                id(e.getDepartment().getId()), managerId(e), e.getHiredAt(), row.kpiCompletion(),
                e.getDepartment().getName(),
                hasAccount ? row.account().getBalance() : 0,
                code(hasAccount ? row.account().getStatus() : AccountStatus.BLOCKED),
                row.accruedThisMonth());
    }

    private static String managerId(Employee e) {
        return e.getManager() == null ? null : id(e.getManager().getId());
    }
}
