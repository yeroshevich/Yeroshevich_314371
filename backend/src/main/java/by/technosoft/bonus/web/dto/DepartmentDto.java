package by.technosoft.bonus.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Подразделение (TS: {@code Department}). */
@Schema(name = "Department")
public record DepartmentDto(String id, String name, @Schema(description = "Сотрудник-руководитель") String headId) {
}
