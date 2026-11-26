# Результаты автоматизированного тестирования серверной части (ЛР 10)

Команда: `cd backend && mvn verify` (JUnit 5, Mockito, Spring Boot Test, AssertJ; отчёт о покрытии — JaCoCo 0.8.12).
Дата и время прогона: **25.11.2026, 15:11:08–15:11:23 (MSK)** (повторный прогон после исправления дефекта D-1); Java 21, Maven 3.9.10, macOS; БД PostgreSQL 14 `tpsis_bonus_app` с демо-данными (интеграционные тесты выполняются в транзакции с откатом).
Результат сборки: **BUILD SUCCESS**, общее время сборки 14,8 с, время выполнения тестов (сумма по классам surefire) ≈ 11,2 с.

## Итог

| Тестируемый тип | Дата проведения теста | Тестовых классов / методов | Запусков | Успешно | Неуспешно | Пропущено | Время, с | Примечания |
|---|---|---|---:|---:|---:|---:|---:|---|
| UNIT-тесты (JUnit 5 + Mockito, без БД и Spring-контекста) | 25.11.2026 | 31 / 222 | 278 | 278 | 0 | 0 | 2.35 | Все функциональные требования FR1-1…FR13-3 покрыты UNIT-тестами; добавлены тесты исправления D-1 (28 запусков) |
| Интеграционные тесты (`@SpringBootTest`, MockMvc, PostgreSQL) | 25.11.2026 | 4 / 31 | 31 | 31 | 0 | 0 | 8.85 | Тесты дефекта D-1 (`CampaignLifecycleTest`) выполняются без условий и пройдены |
| **Всего** | 25.11.2026 | 35 / 253 | **309** | **309** | **0** | **0** | 11.20 | Доля успешных — 100 %, пропущенных нет |

«Запусков» больше, чем методов: параметризованные тесты (`@ParameterizedTest`) выполняются для каждого набора данных.

## По тестовым классам

| Тестовый класс | Вид | Методов | Запусков | Успешно | Неуспешно | Пропущено | Время, с |
|---|---|---:|---:|---:|---:|---:|---:|
| `config.CampaignLifecycleSchedulingConfigTest` | UNIT | 3 | 3 | 3 | 0 | 0 | 0.031 |
| `config.EventSchedulingConfigTest` | UNIT | 4 | 4 | 4 | 0 | 0 | 0.796 |
| `domain.OperationStatusTest` | UNIT | 9 | 22 | 22 | 0 | 0 | 0.011 |
| `service.AccessPolicyTest` | UNIT | 5 | 5 | 5 | 0 | 0 | 0.015 |
| `service.AccrualServiceTest` | UNIT | 17 | 17 | 17 | 0 | 0 | 0.022 |
| `service.BonusCalculationServiceTest` | UNIT | 20 | 23 | 23 | 0 | 0 | 0.025 |
| `service.CorrectionServiceTest` | UNIT | 14 | 14 | 14 | 0 | 0 | 0.017 |
| `service.EventProcessingServiceTest` | UNIT | 15 | 20 | 20 | 0 | 0 | 0.102 |
| `service.RedemptionServiceTest` | UNIT | 7 | 7 | 7 | 0 | 0 | 0.232 |
| `service.ReviewServiceTest` | UNIT | 10 | 10 | 10 | 0 | 0 | 0.007 |
| `service.admin.CampaignAdminServiceTest` | UNIT | 9 | 15 | 15 | 0 | 0 | 0.114 |
| `service.admin.IntegrationAdminServiceTest` | UNIT | 9 | 9 | 9 | 0 | 0 | 0.161 |
| `service.admin.RuleAdminServiceTest` | UNIT | 9 | 9 | 9 | 0 | 0 | 0.088 |
| `service.admin.UserAdminServiceTest` | UNIT | 11 | 11 | 11 | 0 | 0 | 0.112 |
| `service.auth.AuthenticationServiceTest` | UNIT | 4 | 4 | 4 | 0 | 0 | 0.049 |
| `service.campaign.CampaignLifecycleServiceTest` | UNIT | 11 | 21 | 21 | 0 | 0 | 0.014 |
| `service.integration.AesGcmSecretCipherTest` | UNIT | 4 | 4 | 4 | 0 | 0 | 0.004 |
| `service.integration.SimulatedIntegrationProbeTest` | UNIT | 3 | 13 | 13 | 0 | 0 | 0.020 |
| `service.listener.AuditListenerTest` | UNIT | 3 | 3 | 3 | 0 | 0 | 0.044 |
| `service.listener.NotificationListenerTest` | UNIT | 4 | 4 | 4 | 0 | 0 | 0.123 |
| `service.query.AccountQueryServiceTest` | UNIT | 9 | 9 | 9 | 0 | 0 | 0.040 |
| `service.query.AnalyticsQueryServiceTest` | UNIT | 7 | 7 | 7 | 0 | 0 | 0.005 |
| `service.query.CampaignQueryServiceTest` | UNIT | 2 | 2 | 2 | 0 | 0 | 0.003 |
| `service.query.OperationQueryServiceTest` | UNIT | 6 | 6 | 6 | 0 | 0 | 0.004 |
| `service.query.ReportQueryServiceTest` | UNIT | 7 | 7 | 7 | 0 | 0 | 0.006 |
| `service.strategy.AccrualStrategyRegistryTest` | UNIT | 4 | 7 | 7 | 0 | 0 | 0.003 |
| `service.strategy.FixedEventAccrualStrategyTest` | UNIT | 3 | 3 | 3 | 0 | 0 | 0.001 |
| `service.strategy.KpiAccrualStrategyTest` | UNIT | 6 | 12 | 12 | 0 | 0 | 0.010 |
| `web.error.DomainErrorStatusTest` | UNIT | 3 | 3 | 3 | 0 | 0 | 0.004 |
| `web.mapper.CampaignMapperTest` | UNIT | 2 | 2 | 2 | 0 | 0 | 0.002 |
| `web.security.JwtTokenServiceTest` | UNIT | 2 | 2 | 2 | 0 | 0 | 0.281 |
| `service.BusinessFlowSmokeTest` | интеграционный | 5 | 5 | 5 | 0 | 0 | 0.936 |
| `web.CampaignLifecycleTest` | интеграционный | 2 | 2 | 2 | 0 | 0 | 3.886 |
| `web.RequirementsRestApiTest` | интеграционный | 16 | 16 | 16 | 0 | 0 | 2.433 |
| `web.RestApiSecurityTest` | интеграционный | 8 | 8 | 8 | 0 | 0 | 1.594 |

## Найденные дефекты

**D-1. Статус бонусной кампании не менялся по датам (UC9, FR9-2, FR9-3) — ИСПРАВЛЕН 25.11.2026, повторное тестирование пройдено 25.11.2026.**

*Описание (при обнаружении).* Статус задавался один раз при создании (`CampaignAdminService.statusOf`) и далее не пересчитывался: не было перехода «Запланирована → Активна» при наступлении даты начала и «Активна → Завершена» после даты окончания. Последствия: (1) запланированная кампания с бюджетом никогда не применялась в расчётах — `CampaignRepository.findRunningForDepartment` и `Campaign.isRunningOn` требуют статус `active` (на демо-данных «Неделя наставничества», ×2, 01–07.12.2026, не применялась 03.12.2026); (2) истёкшая кампания «Осенний релиз 2.0» после 15.12.2026 продолжала отображаться как «Активна». Тест-спецификация `web.CampaignLifecycleDefectTest` (2 теста, запуск с `-Dbonus.defects=true`) падала: `expected: 2L but was: 1L` и `JSON path "$[?(@.id == '1')].status" expected:<finished> but was:<active>`.

*Исправление.* Новый сервис `service.campaign.CampaignLifecycleService#synchronize(LocalDate today)` переводит кампании «Запланирована» → «Активна» при `start_date ≤ today ≤ end_date` и «Запланирована»/«Активна» → «Завершена» при `end_date < today`; «Требуется утверждение» и «Завершена» не меняются. Статус меняется условным обновлением `CampaignRepository#updateStatus` (`… where status = <прежний>`), поэтому повторный и одновременный запуск не дают двойных переходов; каждая смена статуса пишется в журнал аудита (действие «Изменение статуса кампании», пользователь — система). Запуск: при старте приложения (`ApplicationReadyEvent`) и ежедневно в 00:00 по `bonus.zone` (`config.CampaignLifecycleSchedulingConfig`, настройки `bonus.campaign-lifecycle.enabled` / `cron` в `application.yml`), перед автоматической обработкой событий (`config.EventSchedulingConfig`) и перед чтением кампаний (`service.query.CampaignQueryService#list/get`). Классы расчёта (`service`, `service.strategy`) и `domain` не изменялись.

*Повторное тестирование.* Тест-спецификация переработана в `web.CampaignLifecycleTest` без условного пропуска: часы приложения переставляются на дату сценария — 03.12.2026 (ежедневный запуск переводит «Неделю наставничества» в «Активна», расчёт по сотруднику 3 даёт кампанию 2 и 2400 Б) и 20.12.2026 (список кампаний через REST: «Осенний релиз 2.0» и «Неделя наставничества» — `finished`, «Квартал без инцидентов» — `needs_approval`; повторное чтение не дублирует записи аудита). Добавлены UNIT-тесты `service.campaign.CampaignLifecycleServiceTest` (11 методов, 21 запуск: переходы, граничные даты, `needs_approval`/`finished` не меняются, аудит, параллельный запуск), `config.CampaignLifecycleSchedulingConfigTest` (3), `service.query.CampaignQueryServiceTest` (2), `config.EventSchedulingConfigTest` (+2). Результат `mvn verify`: 309 запусков, 309 успешно, 0 пропущено. В тестах запуск по расписанию и при старте отключён (`src/test/resources/config/application.yml`), синхронизация выполняется в транзакции теста и откатывается; демо-БД после прогона не изменилась (153 операции, 8 записей аудита, 26 пользователей, статусы кампаний исходные).

**Наблюдение (не дефект).** Сообщения с суммами баллов (`PointsFormat`) используют неразрывный пробел (U+00A0) как разделитель разрядов и перед «Б» — сделано намеренно; в тестах ожидаемые строки записаны с `\u00A0`.

## Проверка того, что тесты обнаруживают ошибки

Выборочная мутационная проверка (код временно изменялся и затем восстанавливался из копии): `RedemptionService` — условие «баланс < стоимости» заменено на «≤» → падает `RedemptionServiceTest.balanceEqualToCostIsEnough`; `CorrectionService` — отключена отправка на согласование при отрицательном балансе → падают `CorrectionServiceTest.negativeBalanceGoesToApproval` и `cancellationAndPendingAreJournaled`; `KpiAccrualStrategy` — порог «<» заменён на «≤» → падает `KpiAccrualStrategyTest.proportionalToKpiFromThreshold`. Для исправления D-1: `CampaignLifecycleService` — отключён переход «Запланирована → Активна» → падают `CampaignLifecycleTest.plannedCampaignAppliesWhenStarted`, `CampaignLifecycleServiceTest.plannedBecomesActiveOnStartDate` и граничные случаи `boundaryDates`; завершение в сам день окончания («≥» вместо «>») → падают `boundaryDates[3]` и `boundaryDates[6]`; `CampaignQueryService.list` без синхронизации → падают `CampaignLifecycleTest.finishedCampaignIsDeactivated` и `CampaignQueryServiceTest.listSynchronizesStatusesBeforeReading`.

## Артефакты

- HTML-отчёт surefire: `surefire/surefire.html`, снимок — `screens/surefire-report.png`;
- отчёт о покрытии JaCoCo: `jacoco/index.html`, сводка — `coverage.md`, снимки — `screens/coverage-*.png`;
- соответствие требований и тестов: `fr-test-matrix.md` (39 из 39 FR);
- тест-кейсы уровня пользовательских требований (прогон в браузере): `test-cases.md`, снимки `screens/tc-01.png`…`tc-30.png`.
