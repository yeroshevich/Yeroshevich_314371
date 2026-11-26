package by.technosoft.bonus.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import by.technosoft.bonus.domain.Campaign;
import by.technosoft.bonus.domain.CampaignStatus;
import by.technosoft.bonus.domain.TestEntities;
import by.technosoft.bonus.repository.AmountByKey;
import by.technosoft.bonus.repository.CampaignRepository;
import by.technosoft.bonus.repository.OperationRepository;
import by.technosoft.bonus.service.campaign.CampaignLifecycleService;
import by.technosoft.bonus.service.query.CampaignQueryService.CampaignEntry;

/** Чтение кампаний (UC9): статус актуализируется по датам до чтения (исправление D-1). */
@ExtendWith(MockitoExtension.class)
@DisplayName("CampaignQueryService — список кампаний с актуальным статусом")
class CampaignQueryServiceTest {

    @Mock private CampaignRepository campaignRepository;
    @Mock private OperationRepository operationRepository;
    @Mock private CampaignLifecycleService campaignLifecycleService;

    private final Campaign autumn = TestEntities.campaign(1, "Осенний релиз 2.0", "2026-09-01", "2026-12-15", "1.50",
            60000, CampaignStatus.ACTIVE);
    private CampaignQueryService service;

    @BeforeEach
    void setUp() {
        service = new CampaignQueryService(campaignRepository, operationRepository, campaignLifecycleService);
    }

    @Test
    @DisplayName("FR9-3: перед выводом списка статусы кампаний приводятся к текущей дате, затем список читается из БД")
    void listSynchronizesStatusesBeforeReading() {
        when(operationRepository.sumAmountGroupedByCampaign(CampaignQueryService.SPENT_STATUSES))
                .thenReturn(List.of(new AmountByKey(1L, 5000L)));
        when(campaignRepository.findAllWithTargets()).thenReturn(List.of(autumn));

        List<CampaignEntry> list = service.list();

        assertThat(list).containsExactly(new CampaignEntry(autumn, 5000));
        InOrder order = inOrder(campaignLifecycleService, campaignRepository);
        order.verify(campaignLifecycleService).synchronizeToday();
        order.verify(campaignRepository).findAllWithTargets();
    }

    @Test
    @DisplayName("FR9-3: карточка кампании также показывает статус на текущую дату")
    void getSynchronizesStatusesBeforeReading() {
        when(campaignRepository.findWithTargetsById(1L)).thenReturn(Optional.of(autumn));
        when(operationRepository.sumAmountByCampaign(eq(1L), any())).thenReturn(700L);

        assertThat(service.get(1L)).isEqualTo(new CampaignEntry(autumn, 700));

        InOrder order = inOrder(campaignLifecycleService, campaignRepository);
        order.verify(campaignLifecycleService).synchronizeToday();
        order.verify(campaignRepository).findWithTargetsById(1L);
    }
}
