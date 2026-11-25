package by.technosoft.bonus.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Программа мотивации (UC3, TS: {@code Program}). */
@Schema(name = "Program")
public record ProgramDto(
        String id,
        String name,
        @Schema(allowableValues = {"gift", "education", "merch", "dayoff", "health", "event"}) String category,
        String description,
        @Schema(example = "1500") int cost,
        boolean active,
        String inactiveReason) {
}
