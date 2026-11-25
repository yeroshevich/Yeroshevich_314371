package by.technosoft.bonus.web.config;

import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.stereotype.Component;

import by.technosoft.bonus.web.error.ApiErrorResponse;
import by.technosoft.bonus.web.error.ErrorCode;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;

/**
 * Добавляет в документацию типовые ответы об ошибках в формате {@link ApiErrorResponse} — один раз
 * для всех операций, а не аннотациями на каждом методе (DRY):
 * 401/403 — защищённым операциям, 400/422 — операциям с параметрами или телом,
 * 404 — операциям с идентификатором в пути, 409 — командам (POST/PUT/PATCH).
 */
@Component
public class ApiErrorResponsesCustomizer implements GlobalOpenApiCustomizer {

    private static final String SCHEMA = "ApiError";

    @Override
    public void customise(OpenAPI openApi) {
        ModelConverters.getInstance().read(ApiErrorResponse.class)
                .forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));
        openApi.getPaths().forEach((path, item) -> item.readOperationsMap()
                .forEach((method, operation) -> addErrors(path, method, operation)));
    }

    private static void addErrors(String path, PathItem.HttpMethod method, Operation op) {
        boolean open = op.getSecurity() != null && op.getSecurity().isEmpty();
        boolean hasInput = op.getRequestBody() != null || (op.getParameters() != null && !op.getParameters().isEmpty());
        boolean command = method == PathItem.HttpMethod.POST || method == PathItem.HttpMethod.PUT
                || method == PathItem.HttpMethod.PATCH;
        if (hasInput) {
            add(op, ErrorCode.MALFORMED_REQUEST, "Тело или параметр не разобраны");
            add(op, ErrorCode.VALIDATION_FAILED, "Данные не прошли проверку");
        }
        if (!open) {
            add(op, ErrorCode.UNAUTHORIZED, "Нет действительного токена");
            add(op, ErrorCode.ACCESS_DENIED, "Роль не позволяет выполнить действие");
        }
        if (path.contains("{")) {
            add(op, ErrorCode.NOT_FOUND, "Объект не найден");
        }
        if (command && !open) {
            add(op, ErrorCode.CONFLICT, "Конфликт с состоянием данных");
        }
    }

    private static void add(Operation op, ErrorCode code, String description) {
        String status = String.valueOf(code.status().value());
        if (op.getResponses().containsKey(status)) {
            return;
        }
        Schema<?> ref = new Schema<>().$ref("#/components/schemas/" + SCHEMA);
        op.getResponses().addApiResponse(status, new ApiResponse()
                .description(description + " (" + code.name() + " и др.)")
                .content(new Content().addMediaType("application/json", new MediaType().schema(ref))));
    }
}
