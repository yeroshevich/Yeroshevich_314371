package by.technosoft.bonus.service.admin;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.technosoft.bonus.domain.AppUser;
import by.technosoft.bonus.domain.Integration;
import by.technosoft.bonus.domain.IntegrationCheck;
import by.technosoft.bonus.domain.RoleCode;
import by.technosoft.bonus.repository.IntegrationCheckRepository;
import by.technosoft.bonus.repository.IntegrationRepository;
import by.technosoft.bonus.service.AccessPolicy;
import by.technosoft.bonus.service.audit.AuditTrail;
import by.technosoft.bonus.service.exception.ConflictException;
import by.technosoft.bonus.service.exception.ObjectNotFoundException;
import by.technosoft.bonus.service.integration.IntegrationProbe;
import by.technosoft.bonus.service.integration.IntegrationProbe.ProbeResult;
import by.technosoft.bonus.service.integration.SecretCipher;
import by.technosoft.bonus.service.query.IntegrationQueryService;
import by.technosoft.bonus.service.query.IntegrationQueryService.IntegrationState;

/**
 * Настройка интеграций администратором (UC13): проверка соединения, смена адреса и ключа API,
 * включение и отключение синхронизации. Ключ хранится только в зашифрованном виде (DATA-4)
 * и клиенту не возвращается.
 */
@Service
public class IntegrationAdminService {

    /**
     * Изменения подключения; {@code null} — поле не меняется.
     *
     * @param apiKey новый ключ; {@code null} или пусто — оставить сохранённый
     */
    public record IntegrationPatch(String url, String apiKey, Boolean enabled) {
    }

    private final AccessPolicy accessPolicy;
    private final IntegrationRepository integrationRepository;
    private final IntegrationCheckRepository checkRepository;
    private final IntegrationQueryService integrationQueryService;
    private final IntegrationProbe probe;
    private final SecretCipher cipher;
    private final AuditTrail auditTrail;
    private final Clock clock;

    public IntegrationAdminService(AccessPolicy accessPolicy,
                                   IntegrationRepository integrationRepository,
                                   IntegrationCheckRepository checkRepository,
                                   IntegrationQueryService integrationQueryService,
                                   IntegrationProbe probe,
                                   SecretCipher cipher,
                                   AuditTrail auditTrail,
                                   Clock clock) {
        this.accessPolicy = accessPolicy;
        this.integrationRepository = integrationRepository;
        this.checkRepository = checkRepository;
        this.integrationQueryService = integrationQueryService;
        this.probe = probe;
        this.cipher = cipher;
        this.auditTrail = auditTrail;
        this.clock = clock;
    }

    /**
     * Проверка соединения с переданными параметрами; результат сохраняется в журнал проверок.
     *
     * @param apiKey ключ; {@code null} или пусто — проверить сохранённый ключ
     */
    @Transactional
    public IntegrationCheck test(Long actorUserId, Long integrationId, String url, String apiKey) {
        requireAdmin(actorUserId);
        Integration integration = requireIntegration(integrationId);
        ProbeResult result = probe.check(integration, url, resolveKey(integration, apiKey));
        OffsetDateTime now = OffsetDateTime.now(clock);
        checkRepository.insert(integrationId, result.ok(), result.message(), result.latencyMs(), now);
        auditTrail.record(actorUserId, "Проверка соединения", "integration", integrationId,
                Map.of("label", integration.getName() + " — " + (result.ok() ? "успех" : "ошибка"), "ok", result.ok()));
        return checkRepository.findFirstByIntegrationIdOrderByCheckedAtDescIdDesc(integrationId).orElseThrow();
    }

    /**
     * Сохранение адреса и ключа (сервер повторно проверяет соединение — неработающие параметры
     * не сохраняются) и/или включение синхронизации (только после успешной проверки).
     */
    @Transactional
    public IntegrationState update(Long actorUserId, Long integrationId, IntegrationPatch patch) {
        requireAdmin(actorUserId);
        Integration integration = requireIntegration(integrationId);
        boolean connectionChanged = patch.url() != null || hasText(patch.apiKey());
        OffsetDateTime now = OffsetDateTime.now(clock);

        if (connectionChanged) {
            String url = patch.url() == null ? integration.getUrl() : patch.url().trim();
            String key = resolveKey(integration, patch.apiKey());
            ProbeResult result = probe.check(integration, url, key);
            checkRepository.insert(integrationId, result.ok(), result.message(), result.latencyMs(), now);
            if (!result.ok()) {
                throw new ConflictException("Параметры не сохранены — проверка соединения не прошла: «"
                        + result.message() + "». Исправьте адрес или ключ и проверьте снова.");
            }
            String storedKey = hasText(patch.apiKey()) ? cipher.encrypt(patch.apiKey().trim())
                    : integration.getApiKeyEncrypted();
            integrationRepository.updateConnection(integrationId, url, storedKey);
            auditTrail.record(actorUserId, "Изменение параметров", "integration", integrationId,
                    Map.of("label", integration.getName(), "url", url));
        }
        if (patch.enabled() != null && patch.enabled() != integration.isEnabled()) {
            if (patch.enabled() && !lastCheckOk(integrationId)) {
                throw new ConflictException(
                        "Сначала добейтесь успешной проверки соединения, потом включайте интеграцию.");
            }
            integrationRepository.updateEnabled(integrationId, patch.enabled(), patch.enabled() ? now : null);
            auditTrail.record(actorUserId, patch.enabled() ? "Включение интеграции" : "Отключение интеграции",
                    "integration", integrationId, Map.of("label", integration.getName()));
        }
        return integrationQueryService.get(integrationId);
    }

    /**
     * Ключ для проверки: новый, если передан; иначе сохранённый. Значения из демо-данных
     * записаны не этим шифром — тогда проверяется сохранённое значение как непрозрачный токен.
     */
    private String resolveKey(Integration integration, String apiKey) {
        if (hasText(apiKey)) {
            return apiKey.trim();
        }
        return cipher.decrypt(integration.getApiKeyEncrypted()).orElse(integration.getApiKeyEncrypted());
    }

    private boolean lastCheckOk(Long integrationId) {
        return checkRepository.findFirstByIntegrationIdOrderByCheckedAtDescIdDesc(integrationId)
                .map(IntegrationCheck::isOk).orElse(false);
    }

    private Integration requireIntegration(Long id) {
        return integrationRepository.findById(id)
                .orElseThrow(() -> new ObjectNotFoundException("Интеграция не найдена."));
    }

    private void requireAdmin(Long actorUserId) {
        AppUser actor = accessPolicy.requireActiveUser(actorUserId);
        accessPolicy.requireRole(actor, RoleCode.ADMIN);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
