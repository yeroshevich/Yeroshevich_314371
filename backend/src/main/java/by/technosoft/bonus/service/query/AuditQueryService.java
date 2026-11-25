package by.technosoft.bonus.service.query;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import by.technosoft.bonus.domain.AuditLog;
import by.technosoft.bonus.repository.AuditLogRepository;
import by.technosoft.bonus.service.PointsFormat;
import by.technosoft.bonus.service.exception.DomainValidationException;

/** Просмотр журнала аудита администратором (NFR-3, SEC-4). */
@Service
@Transactional(readOnly = true)
public class AuditQueryService {

    /** Не больше записей за один запрос (PERF: журнал растёт без ограничений). */
    static final int MAX_ENTRIES = 1000;

    private static final OffsetDateTime EPOCH = OffsetDateTime.parse("2000-01-01T00:00:00Z");

    /**
     * @param log         запись журнала (автор, его сотрудник и роль загружены)
     * @param objectLabel подпись объекта для людей: номер операции, название правила, email и т. п.
     */
    public record AuditRecord(AuditLog log, String objectLabel) {
    }

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditQueryService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper, Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** Записи за период [from; to] (даты включительно; без дат — вся история), с поиском подстроки. */
    public List<AuditRecord> search(LocalDate from, LocalDate to, String query) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new DomainValidationException("Дата начала периода позже даты окончания.");
        }
        ZoneId zone = clock.getZone();
        OffsetDateTime start = from == null ? EPOCH : BonusPeriods.startOfDay(from, zone);
        OffsetDateTime end = to == null ? OffsetDateTime.now(clock).plusDays(1) : BonusPeriods.startOfDay(to.plusDays(1), zone);
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return auditLogRepository.findPeriod(start, end, PageRequest.of(0, MAX_ENTRIES)).stream()
                .map(log -> new AuditRecord(log, label(log)))
                .filter(r -> q.isEmpty() || haystack(r).contains(q))
                .toList();
    }

    private static String haystack(AuditRecord r) {
        String user = r.log().getUser() == null ? "система" : r.log().getUser().getEmployee().getFullName();
        return (user + " " + r.log().getAction() + " " + r.objectLabel()).toLowerCase(Locale.ROOT);
    }

    /** Подпись объекта из details: label, номер операции с суммой, сотрудник, причина или id. */
    private String label(AuditLog log) {
        JsonNode details = parse(log.getDetails());
        if (details.hasNonNull("label")) {
            return details.get("label").asText();
        }
        if (details.hasNonNull("number")) {
            String amount = details.hasNonNull("amount") ? ", " + PointsFormat.format(details.get("amount").asLong()) : "";
            return details.get("number").asText() + amount;
        }
        if (details.hasNonNull("employee")) {
            String amount = details.hasNonNull("amount") ? ", " + PointsFormat.format(details.get("amount").asLong()) : "";
            return details.get("employee").asText() + amount;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = details.fields();
        if (fields.hasNext()) {
            Map.Entry<String, JsonNode> first = fields.next();
            return first.getKey() + ": " + (first.getValue().isValueNode() ? first.getValue().asText() : first.getValue());
        }
        return log.getObjectId() == null ? "—" : "#" + log.getObjectId();
    }

    private JsonNode parse(String json) {
        try {
            return json == null ? objectMapper.createObjectNode() : objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            return objectMapper.createObjectNode();
        }
    }
}
