package by.technosoft.bonus.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import by.technosoft.bonus.domain.AppUser;
import by.technosoft.bonus.domain.CampaignStatus;
import by.technosoft.bonus.domain.DemoData;
import by.technosoft.bonus.domain.TestEntities;
import by.technosoft.bonus.repository.AppUserRepository;
import by.technosoft.bonus.repository.CampaignDepartmentRepository;
import by.technosoft.bonus.repository.CampaignRepository;
import by.technosoft.bonus.repository.DepartmentRepository;
import by.technosoft.bonus.service.AccessPolicy;
import by.technosoft.bonus.service.admin.CampaignAdminService.CampaignCommand;
import by.technosoft.bonus.service.audit.AuditTrail;
import by.technosoft.bonus.service.exception.AccessDeniedDomainException;
import by.technosoft.bonus.service.exception.DomainValidationException;
import by.technosoft.bonus.service.query.CampaignQueryService;
import by.technosoft.bonus.service.query.CampaignQueryService.CampaignEntry;

/** UC9 «Бонусные кампании»: сохранение параметров, статус новой кампании, проверка ввода. */
@ExtendWith(MockitoExtension.class)
@DisplayName("CampaignAdminService — бонусные кампании (UC9)")
class CampaignAdminServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);
    private static final long HR = 20L;

    @Mock private AppUserRepository userRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private CampaignDepartmentRepository campaignDepartmentRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private CampaignQueryService campaignQueryService;
    @Mock private AuditTrail auditTrail;

    private final DemoData d = new DemoData();
    private CampaignAdminService service;

    @BeforeEach
    void setUp() {
        service = new CampaignAdminService(new AccessPolicy(userRepository), campaignRepository,
                campaignDepartmentRepository, departmentRepository, campaignQueryService, auditTrail,
                TestEntities.clock());
        for (AppUser u : new AppUser[] {d.managerUser, d.hrUser}) {
            lenient().when(userRepository.findWithRoleById(u.getId())).thenReturn(Optional.of(u));
        }
    }

    private static CampaignCommand campaign(String start, String end, Integer budget) {
        return new CampaignCommand("К", "", LocalDate.parse(start), LocalDate.parse(end), List.of(1L),
                BigDecimal.valueOf(1.5), budget);
    }

    private static CampaignCommand winter(Integer budget, List<Long> departments) {
        return new CampaignCommand("  Зимний спринт качества ", " Двойные баллы QA ", LocalDate.of(2026, 12, 1),
                LocalDate.of(2027, 1, 31), departments, new BigDecimal("2.00"), budget);
    }

    private void departmentsExist() {
        when(departmentRepository.findAllById(any())).thenAnswer(inv -> {
            Iterable<Long> ids = inv.getArgument(0);
            return Stream.of(d.development, d.qa).filter(dep -> {
                for (Long id : ids) {
                    if (id.equals(dep.getId())) {
                        return true;
                    }
                }
                return false;
            }).toList();
        });
    }

    // ── FR9-1: сохранение параметров кампании ─────────────────────────────────────────

    @Test
    @DisplayName("FR9-1: созданная кампания сохраняется с названием, описанием, сроками, коэффициентом и бюджетом")
    void createdCampaignParametersAreSaved() {
        departmentsExist();
        when(campaignRepository.nextId()).thenReturn(5L);
        CampaignEntry entry = new CampaignEntry(TestEntities.campaign(5, "Зимний спринт качества", "2026-12-01",
                "2027-01-31", "2.00", 15000, CampaignStatus.PLANNED), 0);
        when(campaignQueryService.get(5L)).thenReturn(entry);

        CampaignEntry result = service.create(HR, winter(15000, List.of(2L, 1L, 2L)));

        verify(campaignRepository).insert(5L, "Зимний спринт качества", "Двойные баллы QA", LocalDate.of(2026, 12, 1),
                LocalDate.of(2027, 1, 31), new BigDecimal("2.00"), 15000, "planned", TestEntities.now());
        InOrder departments = inOrder(campaignDepartmentRepository);
        departments.verify(campaignDepartmentRepository).insert(5L, 1L);
        departments.verify(campaignDepartmentRepository).insert(5L, 2L);
        departments.verifyNoMoreInteractions();
        assertThat(result).isSameAs(entry);
    }

    @Test
    @DisplayName("FR9-1: создание кампании фиксируется в журнале аудита со статусом и бюджетом")
    @SuppressWarnings("unchecked")
    void creationIsAudited() {
        departmentsExist();
        when(campaignRepository.nextId()).thenReturn(5L);

        service.create(HR, winter(null, List.of(1L)));

        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(auditTrail).record(eq(HR), eq("Создание кампании"), eq("campaign"), eq(5L), details.capture());
        assertThat(details.getValue()).containsEntry("status", "needs_approval").containsEntry("budget", null);
    }

    @Test
    @DisplayName("FR9-1: кампания без бюджета сохраняется со статусом «Требуется утверждение»")
    void withoutBudgetNeedsApprovalRegardlessOfDates() {
        assertThat(CampaignAdminService.statusOf(campaign("2026-11-01", "2026-12-31", null), TODAY))
                .isEqualTo(CampaignStatus.NEEDS_APPROVAL);
    }

    @Test
    @DisplayName("FR9-2: кампания с бюджетом, идущая сегодня, сразу активна и применяется в расчётах")
    void withBudgetRunningTodayIsActive() {
        assertThat(CampaignAdminService.statusOf(campaign("2026-11-25", "2026-11-25", 1000), TODAY))
                .isEqualTo(CampaignStatus.ACTIVE);
        assertThat(CampaignAdminService.statusOf(campaign("2026-12-01", "2026-12-31", 1000), TODAY))
                .isEqualTo(CampaignStatus.PLANNED);
    }

    @Test
    @DisplayName("FR9-3: кампания, срок которой уже истёк, создаётся завершённой (неактивной)")
    void withBudgetStatusFollowsDates() {
        assertThat(CampaignAdminService.statusOf(campaign("2026-10-01", "2026-11-24", 1000), TODAY))
                .isEqualTo(CampaignStatus.FINISHED);
    }

    @Test
    @DisplayName("FR9-3: завершённая и запланированная кампании не идут на дату расчёта, активная — только в свои сроки")
    void runningOnlyWhileActiveAndWithinDates() {
        var autumn = TestEntities.campaign(1, "Осенний релиз 2.0", "2026-09-01", "2026-12-15", "1.50", 60000,
                CampaignStatus.ACTIVE);
        var hackathon = TestEntities.campaign(4, "Летний хакатон", "2026-07-01", "2026-07-31", "1.20", 40000,
                CampaignStatus.FINISHED);
        assertThat(autumn.isRunningOn(LocalDate.of(2026, 9, 1))).isTrue();
        assertThat(autumn.isRunningOn(LocalDate.of(2026, 12, 15))).isTrue();
        assertThat(autumn.isRunningOn(LocalDate.of(2026, 12, 16))).isFalse();
        assertThat(autumn.isRunningOn(LocalDate.of(2026, 8, 31))).isFalse();
        assertThat(hackathon.isRunningOn(LocalDate.of(2026, 7, 15))).isFalse();
    }

    static Stream<Arguments> invalidCommands() {
        LocalDate dec1 = LocalDate.of(2026, 12, 1);
        LocalDate dec31 = LocalDate.of(2026, 12, 31);
        BigDecimal k = BigDecimal.valueOf(1.5);
        return Stream.of(
                Arguments.of("пустое название", new CampaignCommand(" ", "", dec1, dec31, List.of(1L), k, 100),
                        "Укажите название кампании."),
                Arguments.of("нет сроков", new CampaignCommand("К", "", null, dec31, List.of(1L), k, 100),
                        "Укажите сроки кампании."),
                Arguments.of("окончание раньше начала", new CampaignCommand("К", "", dec31, dec1, List.of(1L), k, 100),
                        "Дата окончания раньше даты начала. Проверьте сроки кампании."),
                Arguments.of("нет подразделений", new CampaignCommand("К", "", dec1, dec31, List.of(), k, 100),
                        "Выберите хотя бы одно целевое подразделение."),
                Arguments.of("коэффициент < 1", new CampaignCommand("К", "", dec1, dec31, List.of(1L),
                        new BigDecimal("0.99"), 100), "Коэффициент кампании — от 1 до 5."),
                Arguments.of("коэффициент > 5", new CampaignCommand("К", "", dec1, dec31, List.of(1L),
                        new BigDecimal("5.01"), 100), "Коэффициент кампании — от 1 до 5."),
                Arguments.of("бюджет 0", new CampaignCommand("К", "", dec1, dec31, List.of(1L), k, 0),
                        "Бюджет должен быть больше нуля или не задан."));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCommands")
    @DisplayName("FR9-1: некорректные параметры кампании не сохраняются")
    void invalidParametersAreRejected(String caseName, CampaignCommand command, String message) {
        assertThatThrownBy(() -> service.create(HR, command))
                .isInstanceOf(DomainValidationException.class)
                .hasMessage(message);
        verify(campaignRepository, never()).nextId();
    }

    @Test
    @DisplayName("FR9-1: неизвестное подразделение в целевых — кампания не сохраняется")
    void unknownDepartmentIsRejected() {
        departmentsExist();
        assertThatThrownBy(() -> service.create(HR, winter(1000, List.of(1L, 99L))))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("неизвестное подразделение");
        verify(campaignDepartmentRepository, never()).insert(anyLong(), anyLong());
    }

    @Test
    @DisplayName("FR9-1: создавать кампании может только HR")
    void onlyHrCreatesCampaigns() {
        assertThatThrownBy(() -> service.create(2L, winter(1000, List.of(1L))))
                .isInstanceOf(AccessDeniedDomainException.class);
        verifyNoInteractions(campaignRepository, campaignDepartmentRepository, auditTrail);
    }
}
