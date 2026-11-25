package by.technosoft.bonus.web.mapper;

import static by.technosoft.bonus.web.mapper.ApiValues.code;
import static by.technosoft.bonus.web.mapper.ApiValues.id;

import org.springframework.stereotype.Component;

import by.technosoft.bonus.domain.AppUser;
import by.technosoft.bonus.domain.Employee;
import by.technosoft.bonus.domain.RoleCode;
import by.technosoft.bonus.service.admin.UserAdminService.UserCommand;
import by.technosoft.bonus.web.dto.UserDto;
import by.technosoft.bonus.web.dto.UserRequest;

/** Учётная запись ↔ JSON (вход, UC12). */
@Component
public class UserMapper {

    public UserDto toDto(AppUser user) {
        Employee e = user.getEmployee();
        return new UserDto(id(user.getId()), e.getFullName(), e.getEmail(), id(e.getDepartment().getId()),
                e.getPosition(), code(user.getRoleCode()), code(user.getStatus()), id(e.getId()),
                user.getLastLoginAt());
    }

    public UserCommand toCommand(UserRequest dto) {
        return new UserCommand(dto.fullName(), dto.email(), dto.departmentId(), dto.position(),
                ApiValues.parse(RoleCode.class, dto.role()));
    }
}
