package by.technosoft.bonus.service.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import by.technosoft.bonus.domain.Campaign;
import by.technosoft.bonus.domain.CampaignStatus;
import by.technosoft.bonus.domain.TestEntities;
import by.technosoft.bonus.repository.CampaignRepository;
import by.technosoft.bonus.service.audit.AuditTrail;
import by.technosoft.bonus.service.campaign.CampaignLifecycleService.StatusChange;

/** UC9: смена статуса кампании по датам — «Запланирована» → «Активна» → «Завершена» (исправление D-1). */
@ExtendWith(MockitoExtension.class)
@DisplayName("CampaignLifecycleService — жизненный цикл кампании по датам (UC9, D-1)")
class CampaignLifecycleServiceTest {

    @Mock private CampaignRepository campaignRepository;
    @Mock private AuditTrail auditTrail;

    private CampaignLifecycleService service;

    /** «Неделя наставничества» демо-данных: 01–07.12.2026. */
    private static Campaign mentoring(CampaignStatus status) {
        return TestEntities.campaign(2, "Неделя наставничества", "2026-12-01", "2026-12-07", "2.00", 15000, status);
    }

    @BeforeEach
    void setUp() {
        service = new CampaignLifecycleService(campaignRepository, auditTrail, TestEntities.clock());
    }

    private void repositoryReturns(LocalDate today, Campaign... campaigns) {
        when(campaignRepository.findStartedInStatuses(CampaignLifecycleService.DATE_DRIVEN, today))
                .thenReturn(List.of(campaigns));
    }

    @Test
    @DisplayName("FR9-2: в дату начала запланированная кампания становится активной")
    void plannedBecomesActiveOnStartDate() {
        LocalDate start = LocalDate.of(2026, 12, 1);
        repositoryReturns(start, mentoring(CampaignStatus.PLANNED));
        when(campaignRepository.updateStatus(2L, "planned", "active")).thenReturn(1);

        List<StatusChange> changes = service.synchronize(start);

        assertThat(changes).containsExactly(
                new StatusChange(2L, "Неделя наставничества", CampaignStatus.PLANNED, CampaignStatus.ACTIVE));
        verify(campaignRepository).updateStatus(2L, "planned", "active");
    }

    @Test
    @DisplayName("FR9-3: на следующий день после даты окончания активная кампания завершается")
    void activeBecomesFinishedAfterEndDate() {
        LocalDate dayAfterEnd = LocalDate.of(2026, 12, 8);
        repositoryReturns(dayAfterEnd, mentoring(CampaignStatus.ACTIVE));
        when(campaignRepository.updateStatus(2L, "active", "finished")).thenReturn(1);

        List<StatusChange> changes = service.synchronize(dayAfterEnd);

        assertThat(changes).extracting(StatusChange::to).containsExactly(CampaignStatus.FINISHED);
        verify(campaignRepository).updateStatus(2L, "active", "finished");
    }

    @Test
    @DisplayName("FR9-3: запланированная кампания, срок которой уже прошёл (приложение было остановлено), сразу завершается")
    void plannedBecomesFinishedWhenPeriodPassed() {
        LocalDate later = LocalDate.of(2026, 12, 20);
        repositoryReturns(later, mentoring(CampaignStatus.PLANNED));
        when(campaignRepository.updateStatus(2L, "planned", "finished")).thenReturn(1);

        List<StatusChange> changes = service.synchronize(later);

        assertThat(changes).extracting(StatusChange::from, StatusChange::to)
                .containsExactly(tuple(CampaignStatus.PLANNED, CampaignStatus.FINISHED));
        verify(campaignRepository, never()).updateStatus(2L, "planned", "active");
    }

    static Stream<Arguments> boundaries() {
        return Stream.of(
                // статус, дата синхронизации, ожидаемый новый статус (null — без изменений)
                Arguments.of(CampaignStatus.PLANNED, "2026-11-30", null),                    // накануне начала
                Arguments.of(CampaignStatus.PLANNED, "2026-12-01", CampaignStatus.ACTIVE),   // день начала
                Arguments.of(CampaignStatus.PLANNED, "2026-12-07", CampaignStatus.ACTIVE),   // день окончания
                Arguments.of(CampaignStatus.PLANNED, "2026-12-08", CampaignStatus.FINISHED), // после окончания
                Arguments.of(CampaignStatus.ACTIVE, "2026-12-01", null),
                Arguments.of(CampaignStatus.ACTIVE, "2026-12-07", null),                     // последний день — ещё активна
                Arguments.of(CampaignStatus.ACTIVE, "2026-12-08", CampaignStatus.FINISHED),
                Arguments.of(CampaignStatus.NEEDS_APPROVAL, "2026-12-03", null),
                Arguments.of(CampaignStatus.NEEDS_APPROVAL, "2026-12-08", null),
                Arguments.of(CampaignStatus.FINISHED, "2026-12-03", null));
    }

    @ParameterizedTest(name = "{0} на {1} → {2}")
    @MethodSource("boundaries")
    @DisplayName("FR9-2, FR9-3: граничные даты — кампания активна с даты начала по дату окончания включительно")
    void boundaryDates(CampaignStatus status, String date, CampaignStatus expected) {
        assertThat(CampaignLifecycleService.targetStatus(status, LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 7), LocalDate.parse(date))).isEqualTo(Optional.ofNullable(expected));
    }

    @Test
    @DisplayName("FR9-2, FR9-3: кампания, которой нечего менять (идёт сейчас), не обновляется и не попадает в журнал")
    void runningActiveCampaignIsUntouched() {
        LocalDate today = LocalDate.of(2026, 12, 3);
        repositoryReturns(today, mentoring(CampaignStatus.ACTIVE));

        assertThat(service.synchronize(today)).isEmpty();

        verify(campaignRepository, never()).updateStatus(anyLong(), anyString(), anyString());
        verifyNoInteractions(auditTrail);
    }

    @ParameterizedTest
    @EnumSource(value = CampaignStatus.class, names = {"NEEDS_APPROVAL", "FINISHED"})
    @DisplayName("FR9-2: «Требуется утверждение» и «Завершена» по датам не меняются (даже если пришли из выборки)")
    void needsApprovalAndFinishedAreNeverChanged(CampaignStatus status) {
        LocalDate today = LocalDate.of(2026, 12, 20);
        repositoryReturns(today, mentoring(status));

        assertThat(service.synchronize(today)).isEmpty();

        verify(campaignRepository, never()).updateStatus(anyLong(), anyString(), anyString());
        verifyNoInteractions(auditTrail);
    }

    @Test
    @DisplayName("FR9-2: по датам выбираются только запланированные и активные кампании, начавшиеся к дате")
    void onlyPlannedAndActiveAreSelected() {
        LocalDate today = LocalDate.of(2026, 12, 3);
        repositoryReturns(today);

        service.synchronize(today);

        assertThat(CampaignLifecycleService.DATE_DRIVEN)
                .containsExactlyInAnyOrder(CampaignStatus.PLANNED, CampaignStatus.ACTIVE);
        verify(campaignRepository).findStartedInStatuses(CampaignLifecycleService.DATE_DRIVEN, today);
    }

    @Test
    @DisplayName("FR9-2, FR9-3: каждая смена статуса записывается в журнал аудита от имени системы")
    @SuppressWarnings("unchecked")
    void statusChangeIsAuditedAsSystem() {
        LocalDate today = LocalDate.of(2026, 12, 20);
        Campaign autumn = TestEntities.campaign(1, "Осенний релиз 2.0", "2026-09-01", "2026-12-15", "1.50", 60000,
                CampaignStatus.ACTIVE);
        repositoryReturns(today, autumn, mentoring(CampaignStatus.PLANNED));
        when(campaignRepository.updateStatus(1L, "active", "finished")).thenReturn(1);
        when(campaignRepository.updateStatus(2L, "planned", "finished")).thenReturn(1);

        assertThat(service.synchronize(today)).hasSize(2);

        ArgumentCaptor<Map<String, ?>> details = ArgumentCaptor.forClass(Map.class);
        verify(auditTrail).record(isNull(), eq("Изменение статуса кампании"), eq("campaign"), eq(1L),
                details.capture());
        assertThat((Map<String, Object>) details.getValue())
                .containsEntry("label", "Осенний релиз 2.0")
                .containsEntry("from", "active")
                .containsEntry("to", "finished")
                .containsEntry("endDate", "2026-12-15")
                .containsEntry("onDate", "2026-12-20");
        verify(auditTrail).record(isNull(), eq("Изменение статуса кампании"), eq("campaign"), eq(2L), anyMap());
    }

    @Test
    @DisplayName("FR9-2, FR9-3: если статус уже изменила параллельная синхронизация — повторной записи в журнал нет")
    void concurrentChangeIsNotAuditedTwice() {
        LocalDate today = LocalDate.of(2026, 12, 1);
        repositoryReturns(today, mentoring(CampaignStatus.PLANNED));
        when(campaignRepository.updateStatus(2L, "planned", "active")).thenReturn(0);

        assertThat(service.synchronize(today)).isEmpty();

        verifyNoInteractions(auditTrail);
    }

    @Test
    @DisplayName("FR9-2, FR9-3: синхронизация «на сегодня» берёт бизнес-дату из часов приложения (25.11.2026)")
    void synchronizeTodayUsesClock() {
        repositoryReturns(TestEntities.TODAY);

        assertThat(service.synchronizeToday()).isEmpty();

        verify(campaignRepository).findStartedInStatuses(any(), eq(LocalDate.of(2026, 11, 25)));
    }

    @Test
    @DisplayName("FR9-2: дата синхронизации обязательна")
    void dateIsRequired() {
        assertThatThrownBy(() -> service.synchronize(null)).isInstanceOf(NullPointerException.class);
        verifyNoInteractions(campaignRepository, auditTrail);
    }
}
