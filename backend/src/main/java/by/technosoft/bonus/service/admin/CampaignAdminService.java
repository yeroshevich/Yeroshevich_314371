package by.technosoft.bonus.service.admin;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.technosoft.bonus.domain.AppUser;
import by.technosoft.bonus.domain.CampaignStatus;
import by.technosoft.bonus.domain.RoleCode;
import by.technosoft.bonus.repository.CampaignDepartmentRepository;
import by.technosoft.bonus.repository.CampaignRepository;
import by.technosoft.bonus.repository.DepartmentRepository;
import by.technosoft.bonus.service.AccessPolicy;
import by.technosoft.bonus.service.audit.AuditTrail;
import by.technosoft.bonus.service.exception.DomainValidationException;
import by.technosoft.bonus.service.query.CampaignQueryService;
import by.technosoft.bonus.service.query.CampaignQueryService.CampaignEntry;

/**
 * Создание бонусных кампаний HR (UC9): название, сроки, целевые подразделения, коэффициент, бюджет.
 * Кампания без бюджета получает статус «Требуется утверждение» (альт. поток UC9) и не влияет
 * на расчёт, пока бюджет не утверждён; остальные — «Запланирована», «Активна» или «Завершена» по датам.
 */
@Service
public class CampaignAdminService {

    static final BigDecimal MIN_COEFFICIENT = BigDecimal.ONE;
    static final BigDecimal MAX_COEFFICIENT = BigDecimal.valueOf(5);

    /**
     * Параметры кампании.
     *
     * @param budget бюджет в баллах; {@code null} — не выделен
     */
    public record CampaignCommand(String name, String description, LocalDate startDate, LocalDate endDate,
                                  List<Long> departmentIds, BigDecimal coefficient, Integer budget) {
    }

    private final AccessPolicy accessPolicy;
    private final CampaignRepository campaignRepository;
    private final CampaignDepartmentRepository campaignDepartmentRepository;
    private final DepartmentRepository departmentRepository;
    private final CampaignQueryService campaignQueryService;
    private final AuditTrail auditTrail;
    private final Clock clock;

    public CampaignAdminService(AccessPolicy accessPolicy,
                                CampaignRepository campaignRepository,
                                CampaignDepartmentRepository campaignDepartmentRepository,
                                DepartmentRepository departmentRepository,
                                CampaignQueryService campaignQueryService,
                                AuditTrail auditTrail,
                                Clock clock) {
        this.accessPolicy = accessPolicy;
        this.campaignRepository = campaignRepository;
        this.campaignDepartmentRepository = campaignDepartmentRepository;
        this.departmentRepository = departmentRepository;
        this.campaignQueryService = campaignQueryService;
        this.auditTrail = auditTrail;
        this.clock = clock;
    }

    @Transactional
    public CampaignEntry create(Long actorUserId, CampaignCommand command) {
        AppUser actor = accessPolicy.requireActiveUser(actorUserId);
        accessPolicy.requireRole(actor, RoleCode.HR);
        validate(command);
        TreeSet<Long> departments = new TreeSet<>(command.departmentIds());
        if (departmentRepository.findAllById(departments).size() != departments.size()) {
            throw new DomainValidationException("В списке есть неизвестное подразделение. Обновите страницу.");
        }

        LocalDate today = LocalDate.now(clock);
        CampaignStatus status = statusOf(command, today);
        long id = campaignRepository.nextId();
        campaignRepository.insert(id, command.name().trim(), description(command), command.startDate(),
                command.endDate(), command.coefficient(), command.budget(), status.dbValue(), OffsetDateTime.now(clock));
        departments.forEach(departmentId -> campaignDepartmentRepository.insert(id, departmentId));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("label", command.name().trim());
        details.put("status", status.dbValue());
        details.put("coefficient", command.coefficient());
        details.put("budget", command.budget());
        auditTrail.record(actorUserId, "Создание кампании", "campaign", id, details);
        return campaignQueryService.get(id);
    }

    /** Статус новой кампании: без бюджета — на утверждение, иначе по датам относительно сегодня. */
    static CampaignStatus statusOf(CampaignCommand command, LocalDate today) {
        if (command.budget() == null) {
            return CampaignStatus.NEEDS_APPROVAL;
        }
        if (today.isBefore(command.startDate())) {
            return CampaignStatus.PLANNED;
        }
        return today.isAfter(command.endDate()) ? CampaignStatus.FINISHED : CampaignStatus.ACTIVE;
    }

    private static void validate(CampaignCommand c) {
        if (c.name() == null || c.name().isBlank()) {
            throw new DomainValidationException("Укажите название кампании.");
        }
        if (c.startDate() == null || c.endDate() == null) {
            throw new DomainValidationException("Укажите сроки кампании.");
        }
        if (c.endDate().isBefore(c.startDate())) {
            throw new DomainValidationException("Дата окончания раньше даты начала. Проверьте сроки кампании.");
        }
        if (c.departmentIds() == null || c.departmentIds().isEmpty()) {
            throw new DomainValidationException("Выберите хотя бы одно целевое подразделение.");
        }
        if (c.coefficient() == null || c.coefficient().compareTo(MIN_COEFFICIENT) < 0
                || c.coefficient().compareTo(MAX_COEFFICIENT) > 0) {
            throw new DomainValidationException("Коэффициент кампании — от 1 до 5.");
        }
        if (c.budget() != null && c.budget() <= 0) {
            throw new DomainValidationException("Бюджет должен быть больше нуля или не задан.");
        }
    }

    private static String description(CampaignCommand c) {
        return c.description() == null ? "" : c.description().trim();
    }
}
