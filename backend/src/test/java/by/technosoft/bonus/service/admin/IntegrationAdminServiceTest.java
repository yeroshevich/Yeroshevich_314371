package by.technosoft.bonus.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import by.technosoft.bonus.config.SecurityProperties;
import by.technosoft.bonus.domain.AppUser;
import by.technosoft.bonus.domain.DemoData;
import by.technosoft.bonus.domain.Integration;
import by.technosoft.bonus.domain.IntegrationCheck;
import by.technosoft.bonus.domain.IntegrationKind;
import by.technosoft.bonus.domain.TestEntities;
import by.technosoft.bonus.repository.AppUserRepository;
import by.technosoft.bonus.repository.IntegrationCheckRepository;
import by.technosoft.bonus.repository.IntegrationRepository;
import by.technosoft.bonus.service.AccessPolicy;
import by.technosoft.bonus.service.admin.IntegrationAdminService.IntegrationPatch;
import by.technosoft.bonus.service.audit.AuditTrail;
import by.technosoft.bonus.service.exception.AccessDeniedDomainException;
import by.technosoft.bonus.service.exception.ConflictException;
import by.technosoft.bonus.service.exception.ObjectNotFoundException;
import by.technosoft.bonus.service.integration.AesGcmSecretCipher;
import by.technosoft.bonus.service.integration.SimulatedIntegrationProbe;
import by.technosoft.bonus.service.query.IntegrationQueryService;

/**
 * UC13 «Интеграция систем»: проверка соединения, сохранение параметров (только после успешной проверки,
 * ключ — в зашифрованном виде), включение автоматической синхронизации.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IntegrationAdminService — настройка интеграций (UC13)")
class IntegrationAdminServiceTest {

    private static final long ADMIN = 17L;
    static final String KEY = "dGVjaG5vc29mdC1ib251cy1kZXYta2V5LTMyLWJ5dGU=";

    @Mock private AppUserRepository userRepository;
    @Mock private IntegrationRepository integrationRepository;
    @Mock private IntegrationCheckRepository checkRepository;
    @Mock private IntegrationQueryService integrationQueryService;
    @Mock private AuditTrail auditTrail;

    private final DemoData d = new DemoData();
    private final AesGcmSecretCipher cipher = new AesGcmSecretCipher(new SecurityProperties(null, KEY, "demo", List.of()));
    /** ERP из демо-данных: адрес HTTP (не проходит проверку), ключ сохранён не этим шифром. */
    private final Integration erp = TestEntities.integration(2, IntegrationKind.ERP, "ERP",
            "http://erp.technosoft.local/api", "demo-erp-key-2026", false);
    private IntegrationAdminService service;

    @BeforeEach
    void setUp() {
        service = new IntegrationAdminService(new AccessPolicy(userRepository), integrationRepository, checkRepository,
                integrationQueryService, new SimulatedIntegrationProbe(), cipher, auditTrail, TestEntities.clock());
        for (AppUser u : new AppUser[] {d.adminUser, d.hrUser}) {
            lenient().when(userRepository.findWithRoleById(u.getId())).thenReturn(Optional.of(u));
        }
        lenient().when(integrationRepository.findById(2L)).thenReturn(Optional.of(erp));
    }

    // ── FR13-1: параметры интеграции ──────────────────────────────────────────────────

    @Test
    @DisplayName("FR13-1: проверка соединения с адресом HTTPS и ключом проходит, результат сохраняется в журнал проверок")
    void connectionTestSucceeds() {
        IntegrationCheck last = TestEntities.check(10, erp, true, "Соединение установлено");
        when(checkRepository.findFirstByIntegrationIdOrderByCheckedAtDescIdDesc(2L)).thenReturn(Optional.of(last));

        IntegrationCheck result = service.test(ADMIN, 2L, "https://erp.technosoft.by/api", "erp-51c0aa-2026");

        verify(checkRepository).insert(eq(2L), eq(true), eq("Соединение установлено"), any(), eq(TestEntities.now()));
        verify(auditTrail).record(eq(ADMIN), eq("Проверка соединения"), eq("integration"), eq(2L), any());
        assertThat(result).isSameAs(last);
    }

    @Test
    @DisplayName("FR13-1: проверка соединения с адресом HTTP не проходит — «нужен адрес HTTPS»")
    void connectionTestWithHttpFails() {
        when(checkRepository.findFirstByIntegrationIdOrderByCheckedAtDescIdDesc(2L))
                .thenReturn(Optional.of(TestEntities.check(11, erp, false, "x")));

        service.test(ADMIN, 2L, "http://erp.local", "erp-51c0aa-2026");

        verify(checkRepository).insert(eq(2L), eq(false), eq("Сервер отклонил подключение: нужен адрес HTTPS"),
                isNull(), any());
    }

    @Test
    @DisplayName("FR13-1: после успешной проверки адрес и ключ сохраняются; ключ хранится только в зашифрованном виде")
    void parametersAreSavedEncrypted() {
        service.update(ADMIN, 2L, new IntegrationPatch(" https://erp.technosoft.by/api ", " erp-51c0aa-2026 ", null));

        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(integrationRepository).updateConnection(eq(2L), eq("https://erp.technosoft.by/api"), stored.capture());
        assertThat(stored.getValue()).startsWith("enc:gcm:").doesNotContain("erp-51c0aa-2026");
        assertThat(cipher.decrypt(stored.getValue())).contains("erp-51c0aa-2026");
        verify(auditTrail).record(eq(ADMIN), eq("Изменение параметров"), eq("integration"), eq(2L), any());
    }

    @Test
    @DisplayName("FR13-1: при смене только адреса проверяется и сохраняется прежний ключ")
    void urlOnlyKeepsStoredKey() {
        service.update(ADMIN, 2L, new IntegrationPatch("https://erp2.technosoft.by/api", "", null));

        verify(integrationRepository).updateConnection(2L, "https://erp2.technosoft.by/api", "demo-erp-key-2026");
    }

    @Test
    @DisplayName("FR13-1: параметры, не прошедшие проверку соединения, не сохраняются")
    void failedCheckDoesNotSave() {
        assertThatThrownBy(() -> service.update(ADMIN, 2L, new IntegrationPatch("http://erp.local", "erp-51c0aa-2026",
                null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Параметры не сохранены").hasMessageContaining("нужен адрес HTTPS");
        assertThatThrownBy(() -> service.update(ADMIN, 2L, new IntegrationPatch("https://erp.technosoft.by/api",
                "short", null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("не короче 8 символов");
        verify(integrationRepository, never()).updateConnection(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("FR13-1: настраивать интеграции может только администратор; неизвестная интеграция — «не найдена»")
    void onlyAdminAndExistingIntegration() {
        assertThatThrownBy(() -> service.update(20L, 2L, new IntegrationPatch(null, null, true)))
                .isInstanceOf(AccessDeniedDomainException.class);
        when(integrationRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.test(ADMIN, 99L, "https://x.by", "12345678"))
                .isInstanceOf(ObjectNotFoundException.class);
    }

    // ── FR13-3: автоматическая синхронизация ─────────────────────────────────────────

    @Test
    @DisplayName("FR13-3: автоматическая синхронизация включается после успешной проверки, фиксируется время синхронизации")
    void enablesSynchronization() {
        when(checkRepository.findFirstByIntegrationIdOrderByCheckedAtDescIdDesc(2L))
                .thenReturn(Optional.of(TestEntities.check(12, erp, true, "Соединение установлено")));

        service.update(ADMIN, 2L, new IntegrationPatch(null, null, true));

        verify(integrationRepository).updateEnabled(2L, true, TestEntities.now());
        verify(auditTrail).record(eq(ADMIN), eq("Включение интеграции"), eq("integration"), eq(2L), any());
    }

    @Test
    @DisplayName("FR13-3: без успешной проверки соединения синхронизацию включить нельзя")
    void cannotEnableWithoutSuccessfulCheck() {
        when(checkRepository.findFirstByIntegrationIdOrderByCheckedAtDescIdDesc(2L))
                .thenReturn(Optional.of(TestEntities.check(13, erp, false, "нужен адрес HTTPS")));

        assertThatThrownBy(() -> service.update(ADMIN, 2L, new IntegrationPatch(null, null, true)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Сначала добейтесь успешной проверки");
        verify(integrationRepository, never()).updateEnabled(anyLong(), anyBoolean(), any());
    }

    @Test
    @DisplayName("FR13-3: отключение синхронизации сбрасывает время синхронизации; повторное включение ничего не делает")
    void disablesSynchronization() {
        Integration hris = TestEntities.integration(1, IntegrationKind.HRIS, "HRIS", "https://hris.technosoft.by/api/v2",
                "demo-hris-key", true);
        when(integrationRepository.findById(1L)).thenReturn(Optional.of(hris));

        service.update(ADMIN, 1L, new IntegrationPatch(null, null, true));
        verify(integrationRepository, never()).updateEnabled(anyLong(), anyBoolean(), any());

        service.update(ADMIN, 1L, new IntegrationPatch(null, null, false));
        verify(integrationRepository).updateEnabled(1L, false, null);
    }
}
