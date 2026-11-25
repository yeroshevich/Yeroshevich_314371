# Соответствие функциональных требований и тестов (ЛР 10)

Формулировки требований — дословно из таблицы 2.1 «Функциональные требования» (ЛР 4). Тесты — серверная часть `backend/src/test/java/by/technosoft/bonus/`; результат — по отчётам surefire последнего прогона `mvn verify` (`target/surefire-reports`). «UNIT» — JUnit 5 + Mockito без БД и Spring-контекста; «интеграционный» — `@SpringBootTest` + MockMvc на демо-данных, транзакция откатывается. Параметризованные тесты учитываются как один метод (в скобках — число запусков).

| FR-ID | Требование (таблица 2.1) | Тестовый класс#метод | Вид | Результат |
|---|---|---|---|---|
| FR1-1 | Если пользователь авторизован, система должна отображать текущий бонусный баланс | `AccountQueryServiceTest#showsCurrentBalance`<br>`AccountQueryServiceTest#showsBalanceHistory`<br>`RequirementsRestApiTest#balanceOfAuthorizedEmployee` | UNIT 2, интегр. 1 | пройдено 3 из 3 |
| FR1-2 | Если данные недоступны, система должна отображать сообщение об ошибке | `AccountQueryServiceTest#blockedAccountShowsError`<br>`AccountQueryServiceTest#missingAccountShowsError`<br>`RequirementsRestApiTest#unavailableBalanceShowsError` | UNIT 2, интегр. 1 | пройдено 3 из 3 |
| FR1-3 | Если баланс обновлён, система должна отображать актуальное значение | `NotificationListenerTest#titlesForAllActions`<br>`AccountQueryServiceTest#showsUpdatedBalance`<br>`RequirementsRestApiTest#redemptionUpdatesShownBalance` | UNIT 2, интегр. 1 | пройдено 3 из 3 |
| FR2-1 | Если пользователь авторизован, система должна отображать историю операций | `AccountQueryServiceTest#showsHistory`<br>`OperationQueryServiceTest#getById`<br>`RequirementsRestApiTest#historyIsFilteredByPeriod` | UNIT 2, интегр. 1 | пройдено 3 из 3 |
| FR2-2 | Если операций нет, система должна отображать пустой список | `AccountQueryServiceTest#emptyHistory`<br>`RequirementsRestApiTest#emptyHistory` | UNIT 1, интегр. 1 | пройдено 2 из 2 |
| FR2-3 | Если указан период, система должна фильтровать операции по датам | `AccountQueryServiceTest#periodIsValidated`<br>`AccountQueryServiceTest#singleDayAndOpenPeriods`<br>`OperationQueryServiceTest#searchValidatesPeriod`<br>`RequirementsRestApiTest#historyIsFilteredByPeriod` | UNIT 3, интегр. 1 | пройдено 4 из 4 |
| FR3-1 | Если баланс достаточен, система должна разрешить списание бонусов | `RedemptionServiceTest#redeemsWhenBalanceIsEnough`<br>`RedemptionServiceTest#balanceEqualToCostIsEnough`<br>`RequirementsRestApiTest#redemptionUpdatesShownBalance` | UNIT 2, интегр. 1 | пройдено 3 из 3 |
| FR3-2 | Если баланс недостаточен, система должна отклонить операцию | `OperationStatusTest#balanceNeverNegative`<br>`RedemptionServiceTest#rejectsWhenBalanceIsInsufficient`<br>`RedemptionServiceTest#rejectsBlockedAccount`<br>`RedemptionServiceTest#rejectsInactiveOrUnknownProgram`<br>`RequirementsRestApiTest#insufficientBalanceRejected` | UNIT 4, интегр. 1 | пройдено 5 из 5 |
| FR3-3 | После подтверждения система должна обновить баланс | `RedemptionServiceTest#balanceIsUpdatedAfterRedemption`<br>`RedemptionServiceTest#redemptionIsPublished`<br>`RequirementsRestApiTest#redemptionUpdatesShownBalance` | UNIT 2, интегр. 1 | пройдено 3 из 3 |
| FR4-1 | Если поступило KPI-событие, система должна выполнить расчёт бонусов | `EventProcessingServiceTest#kpiEventTriggersCalculation`<br>`EventProcessingServiceTest#businessDateUsesCompanyZone`<br>`EventProcessingServiceTest#belowThresholdProcessedWithoutBonus`<br>`EventProcessingServiceTest#invalidEventIsPostponed`<br>`EventProcessingServiceTest#unknownEventIsNotFound` | UNIT 5, интегр. 0 | пройдено 5 из 5 |
| FR4-2 | Если правило активно, система должна применить коэффициенты | `EventProcessingServiceTest#activeRuleAppliesCoefficients`<br>`EventProcessingServiceTest#noActiveRuleNoAccrual` | UNIT 2, интегр. 0 | пройдено 2 из 2 |
| FR4-3 | После расчёта система должна сохранить начисление | `OperationStatusTest#businessEventStatus`<br>`EventProcessingServiceTest#savesAccrualAfterCalculation`<br>`EventProcessingServiceTest#processingIsIdempotent`<br>`EventProcessingServiceTest#blockedAccountPostponesEvent`<br>`AuditListenerTest#autoAccrualIsRecordedAsSystem` | UNIT 5, интегр. 0 | пройдено 5 из 5 |
| FR5-1 | Если есть права доступа, система должна разрешить создание начисления | `AccessPolicyTest#activeUserIsLoaded`<br>`AccessPolicyTest#missingSessionIsDenied`<br>`AccessPolicyTest#manageScope`<br>`AccrualServiceTest#managerAccruesOwnTeam`<br>`AccrualServiceTest#hrAccruesAnyEmployee`<br>`AccrualServiceTest#withoutRightsAccrualIsDenied`<br>`AccrualServiceTest#managerCannotAccrueForeignEmployee`<br>`AccrualServiceTest#blockedOrUnknownActorIsDenied`<br>`ReviewServiceTest#onlyFinanceReviews`<br>`RequirementsRestApiTest#managerAccrualUpdatesBalance` | UNIT 9, интегр. 1 | пройдено 10 из 10 |
| FR5-2 | Если указана сумма, система должна сохранить начисление | `OperationStatusTest#operationInvariants`<br>`AccrualServiceTest#savesAccrualWithAmount`<br>`AccrualServiceTest#aboveLimitIsSavedAsPending`<br>`AccrualServiceTest#exactlyLimitIsAccrued`<br>`AccrualServiceTest#multipleEmployeesAreDeduplicated`<br>`AccrualServiceTest#savesCampaignAndRuleLinks`<br>`AccrualServiceTest#wrongBasisIsRejected`<br>`AccrualServiceTest#blankReasonOrZeroAmountIsRejected`<br>`AccrualServiceTest#unknownEmployeeIsRejected`<br>`ReviewServiceTest#pendingQueueForFinance`<br>`OperationQueryServiceTest#pendingOperationsAreExplained`<br>`OperationQueryServiceTest#controlQueueContainsPendingAndDisputed`<br>`RequirementsRestApiTest#managerAccrualUpdatesBalance` | UNIT 12, интегр. 1 | пройдено 13 из 13 |
| FR5-3 | После операции система должна обновить баланс | `OperationStatusTest#settledStatuses`<br>`AccrualServiceTest#balanceGrowsAfterAccrual`<br>`AccrualServiceTest#pendingDoesNotChangeBalance`<br>`AccrualServiceTest#blockedRecipientCancelsWholeAccrual`<br>`ReviewServiceTest#approvedAccrualUpdatesBalance`<br>`ReviewServiceTest#blockedAccrualKeepsBalance`<br>`ReviewServiceTest#secondApprovalIsRejected`<br>`ReviewServiceTest#approvalOnBlockedAccount`<br>`NotificationListenerTest#accrualNotification`<br>`NotificationListenerTest#notificationFailureIsSwallowed`<br>`RequirementsRestApiTest#managerAccrualUpdatesBalance` | UNIT 10, интегр. 1 | пройдено 11 из 11 |
| FR6-1 | Если есть входные данные, система должна выполнить расчёт | `BonusCalculationServiceTest#calculatesWhenInputPresent`<br>`BonusCalculationServiceTest#rejectsIncompleteInput` (4)<br>`BonusCalculationServiceTest#rejectsUnknownEmployeeOrRule`<br>`BonusCalculationServiceTest#manualRejectsInvalidInput`<br>`KpiAccrualStrategyTest#missingKpiIsRejected`<br>`KpiAccrualStrategyTest#outOfRangeKpiIsRejected` (3) | UNIT 6, интегр. 0 | пройдено 6 из 6 |
| FR6-2 | Если применяются правила, система должна учитывать KPI коэффициенты | `BonusCalculationServiceTest#appliesKpiCoefficients`<br>`BonusCalculationServiceTest#belowThresholdIsZero`<br>`FixedEventAccrualStrategyTest#basePointsTimesWeight`<br>`FixedEventAccrualStrategyTest#kpiValueIsIgnored`<br>`KpiAccrualStrategyTest#appliesWeightAndKpiLevel`<br>`KpiAccrualStrategyTest#proportionalToKpiFromThreshold` (5)<br>`KpiAccrualStrategyTest#belowThresholdGivesZero` | UNIT 7, интегр. 0 | пройдено 7 из 7 |
| FR6-3 | После расчёта система должна вернуть итоговую сумму | `AccrualServiceTest#previewReturnsTotalsWithoutSaving`<br>`BonusCalculationServiceTest#roundsToRuleStep`<br>`BonusCalculationServiceTest#defaultRoundingIsFifty`<br>`BonusCalculationServiceTest#brokenParamsAreReported`<br>`BonusCalculationServiceTest#roundToStepHalfUp` | UNIT 5, интегр. 0 | пройдено 5 из 5 |
| FR7-1 | Если обнаружена ошибка, система должна разрешить корректировку | `CorrectionServiceTest#hrCorrectsAccrual`<br>`CorrectionServiceTest#managerCorrectsOnlyOwnTeam`<br>`CorrectionServiceTest#otherRolesCannotCorrect`<br>`CorrectionServiceTest#writeoffAndCancelledCannotBeCorrected`<br>`CorrectionServiceTest#secondCorrectionWhilePendingIsRejected`<br>`CorrectionServiceTest#newAmountIsValidated`<br>`CorrectionServiceTest#unknownOperationOrBlockedAccount`<br>`OperationQueryServiceTest#searchByNameIgnoresCase`<br>`RequirementsRestApiTest#correctionIsJournaled` | UNIT 8, интегр. 1 | пройдено 9 из 9 |
| FR7-2 | После изменения система должна пересчитать баланс | `OperationStatusTest#transitions` (14)<br>`OperationStatusTest#illegalTransitionIsRejected`<br>`OperationStatusTest#correctionDelta`<br>`CorrectionServiceTest#decreaseRecalculatesBalance`<br>`CorrectionServiceTest#increaseRecalculatesBalance`<br>`CorrectionServiceTest#cancellationRecalculatesBalance`<br>`CorrectionServiceTest#negativeBalanceGoesToApproval`<br>`CorrectionServiceTest#correctionOfCorrection`<br>`ReviewServiceTest#approvedCancellationRecalculatesBalance`<br>`ReviewServiceTest#approvalCannotMakeBalanceNegative`<br>`ReviewServiceTest#approvalOfStaleCorrectionIsRejected`<br>`RequirementsRestApiTest#correctionIsJournaled` | UNIT 11, интегр. 1 | пройдено 12 из 12 |
| FR7-3 | Система должна зафиксировать изменение в журнале | `CorrectionServiceTest#correctionIsJournaled`<br>`CorrectionServiceTest#cancellationAndPendingAreJournaled`<br>`ReviewServiceTest#commentLengthIsLimited`<br>`AuditListenerTest#correctionIsRecorded`<br>`NotificationListenerTest#correctionText`<br>`OperationQueryServiceTest#reviewDecisionIsShown`<br>`RequirementsRestApiTest#correctionIsJournaled` | UNIT 6, интегр. 1 | пройдено 7 из 7 |
| FR8-1 | Если HR авторизован, система должна позволить создание правил | `RuleAdminServiceTest#hrCanCreateRule`<br>`RuleAdminServiceTest#nonHrCannotManageRules`<br>`RequirementsRestApiTest#hrCreatesRule` | UNIT 2, интегр. 1 | пройдено 3 из 3 |
| FR8-2 | После создания система должна сохранить правила | `RuleAdminServiceTest#createdRuleIsSaved`<br>`RuleAdminServiceTest#creationIsAudited`<br>`RuleAdminServiceTest#duplicateRuleForKpiIsRejected`<br>`RequirementsRestApiTest#hrCreatesRule` | UNIT 3, интегр. 1 | пройдено 4 из 4 |
| FR8-3 | Активные правила должны применяться при расчётах | `OperationStatusTest#ruleApplicability`<br>`BonusCalculationServiceTest#inactiveRuleIsNotApplied`<br>`BonusCalculationServiceTest#futureRuleVersionIsNotApplied`<br>`RuleAdminServiceTest#usedRuleChangesFromNextPeriod`<br>`RuleAdminServiceTest#unusedRuleChangesImmediately`<br>`RuleAdminServiceTest#kpiOfUsedRuleIsLocked`<br>`RuleAdminServiceTest#nextPeriodStart`<br>`AccrualStrategyRegistryTest#everyPeriodicityHasStrategy` (4)<br>`AccrualStrategyRegistryTest#picksStrategyByPeriodicity`<br>`AccrualStrategyRegistryTest#missingStrategyFailsFast`<br>`AccrualStrategyRegistryTest#duplicateStrategyFailsFast`<br>`FixedEventAccrualStrategyTest#supportsEventAndYear`<br>`KpiAccrualStrategyTest#supportsMonthAndQuarter` | UNIT 13, интегр. 0 | пройдено 13 из 13 |
| FR9-1 | Если создана кампания, система должна сохранить параметры | `CampaignAdminServiceTest#createdCampaignParametersAreSaved`<br>`CampaignAdminServiceTest#creationIsAudited`<br>`CampaignAdminServiceTest#withoutBudgetNeedsApprovalRegardlessOfDates`<br>`CampaignAdminServiceTest#invalidParametersAreRejected` (7)<br>`CampaignAdminServiceTest#unknownDepartmentIsRejected`<br>`CampaignAdminServiceTest#onlyHrCreatesCampaigns`<br>`RequirementsRestApiTest#campaignIsSaved` | UNIT 6, интегр. 1 | пройдено 7 из 7 |
| FR9-2 | Если кампания активна, система должна применять её правила | `BonusCalculationServiceTest#appliesActiveCampaignCoefficient`<br>`BonusCalculationServiceTest#skipsCampaignWithoutBudgetLeft`<br>`BonusCalculationServiceTest#noCampaignFitsBudget`<br>`BonusCalculationServiceTest#unlimitedCampaignDoesNotCheckSpent`<br>`BonusCalculationServiceTest#manualAmountWithCampaign`<br>`BonusCalculationServiceTest#manualAmountCampaignNotTargeted`<br>`CampaignAdminServiceTest#withBudgetRunningTodayIsActive`<br>`CampaignLifecycleDefectTest#plannedCampaignAppliesWhenStarted` | UNIT 7, интегр. 1 | пройдено 7 из 7; дефект D-1: 1 тест(а) отключено |
| FR9-3 | После завершения система должна деактивировать кампанию | `BonusCalculationServiceTest#campaignIsInactiveAfterEnd`<br>`BonusCalculationServiceTest#finishedCampaignIsNotApplied`<br>`CampaignAdminServiceTest#withBudgetStatusFollowsDates`<br>`CampaignAdminServiceTest#runningOnlyWhileActiveAndWithinDates`<br>`CampaignLifecycleDefectTest#finishedCampaignIsDeactivated` | UNIT 4, интегр. 1 | пройдено 4 из 4; дефект D-1: 1 тест(а) отключено |
| FR10-1 | Система должна формировать отчёт по начислениям | `ReportQueryServiceTest#employeeReport`<br>`ReportQueryServiceTest#departmentReport`<br>`ReportQueryServiceTest#campaignReport`<br>`RequirementsRestApiTest#reportByPeriod` | UNIT 3, интегр. 1 | пройдено 4 из 4 |
| FR10-2 | Система должна фильтровать данные по периоду | `ReportQueryServiceTest#periodIsValidated`<br>`ReportQueryServiceTest#reportKeepsPeriod`<br>`RequirementsRestApiTest#reportByPeriod` | UNIT 2, интегр. 1 | пройдено 3 из 3 |
| FR10-3 | Система должна предоставлять готовый отчёт пользователю | `ReportQueryServiceTest#readyReportIsProvided`<br>`ReportQueryServiceTest#emptyPeriodGivesEmptyReport`<br>`RequirementsRestApiTest#reportByPeriod` | UNIT 2, интегр. 1 | пройдено 3 из 3 |
| FR11-1 | Система должна рассчитывать показатели эффективности | `AnalyticsQueryServiceTest#calculatesEfficiencyIndicators`<br>`AnalyticsQueryServiceTest#emptyScope`<br>`RequirementsRestApiTest#analyticsDashboard` | UNIT 2, интегр. 1 | пройдено 3 из 3 |
| FR11-2 | Система должна формировать метрики анализа | `AnalyticsQueryServiceTest#buildsComparisonMetrics`<br>`AnalyticsQueryServiceTest#quarterComparison`<br>`AnalyticsQueryServiceTest#partialDataIsFlagged`<br>`RequirementsRestApiTest#analyticsDashboard` | UNIT 3, интегр. 1 | пройдено 4 из 4 |
| FR11-3 | Система должна отображать результаты анализа | `AnalyticsQueryServiceTest#managerSeesOwnDepartment`<br>`AnalyticsQueryServiceTest#employeeCannotSeeAnalytics`<br>`RequirementsRestApiTest#analyticsDashboard` | UNIT 2, интегр. 1 | пройдено 3 из 3 |
| FR12-1 | Система должна создавать пользователей | `UserAdminServiceTest#createsUser`<br>`UserAdminServiceTest#duplicateEmailIsRejected`<br>`UserAdminServiceTest#departmentIsRequired`<br>`UserAdminServiceTest#onlyAdminManagesUsers`<br>`RequirementsRestApiTest#userLifecycle` | UNIT 4, интегр. 1 | пройдено 5 из 5 |
| FR12-2 | Система должна обновлять роли и права | `OperationStatusTest#persistentEnum`<br>`AccessPolicyTest#roleIsChecked`<br>`UserAdminServiceTest#roleIsUpdated`<br>`UserAdminServiceTest#dataUpdatedWithoutRoleChange`<br>`UserAdminServiceTest#updateConflicts`<br>`RequirementsRestApiTest#roleIsChanged` | UNIT 5, интегр. 1 | пройдено 6 из 6 |
| FR12-3 | Система должна деактивировать пользователей | `AccessPolicyTest#blockedUserIsDenied`<br>`UserAdminServiceTest#deactivatesUser`<br>`UserAdminServiceTest#statusChangeIsIdempotent`<br>`UserAdminServiceTest#cannotBlockSelf`<br>`UserAdminServiceTest#blockedUserIsDenied`<br>`AuthenticationServiceTest#blockedUserCannotLogin`<br>`RequirementsRestApiTest#userLifecycle` | UNIT 6, интегр. 1 | пройдено 7 из 7 |
| FR13-1 | Система должна сохранять параметры интеграции | `IntegrationAdminServiceTest#connectionTestSucceeds`<br>`IntegrationAdminServiceTest#connectionTestWithHttpFails`<br>`IntegrationAdminServiceTest#parametersAreSavedEncrypted`<br>`IntegrationAdminServiceTest#urlOnlyKeepsStoredKey`<br>`IntegrationAdminServiceTest#failedCheckDoesNotSave`<br>`IntegrationAdminServiceTest#onlyAdminAndExistingIntegration`<br>`AesGcmSecretCipherTest#roundTrip`<br>`AesGcmSecretCipherTest#randomIv`<br>`AesGcmSecretCipherTest#tamperedOrForeignValues`<br>`AesGcmSecretCipherTest#keyLengthIsChecked`<br>`SimulatedIntegrationProbeTest#httpsAndKeyPass`<br>`SimulatedIntegrationProbeTest#nonHttpsRejected` (8)<br>`SimulatedIntegrationProbeTest#shortKeyRejected` (4)<br>`RequirementsRestApiTest#integrationSettings` | UNIT 13, интегр. 1 | пройдено 14 из 14 |
| FR13-2 | Система должна обрабатывать внешние события | `EventProcessingServiceTest#erpStageEventIsProcessed`<br>`EventProcessingServiceTest#eventTypeMapsToKpi` (3)<br>`EventProcessingServiceTest#malformedExternalEventsFail` (4)<br>`AuditListenerTest#failedEventIsRecorded`<br>`RequirementsRestApiTest#externalEventIsProcessed` | UNIT 4, интегр. 1 | пройдено 5 из 5 |
| FR13-3 | Система должна синхронизировать данные автоматически | `EventSchedulingConfigTest#scheduledRunProcessesQueue`<br>`EventSchedulingConfigTest#scheduleIsConfigurable`<br>`EventProcessingServiceTest#batchProcessesAllNewEvents`<br>`EventProcessingServiceTest#emptyBatch`<br>`IntegrationAdminServiceTest#enablesSynchronization`<br>`IntegrationAdminServiceTest#cannotEnableWithoutSuccessfulCheck`<br>`IntegrationAdminServiceTest#disablesSynchronization`<br>`RequirementsRestApiTest#integrationSettings` | UNIT 7, интегр. 1 | пройдено 8 из 8 |

Покрыто требований: **39 из 39**.

## Расшифровка тестов по требованиям

### FR1-1 — Если пользователь авторизован, система должна отображать текущий бонусный баланс

- `AccountQueryServiceTest#showsCurrentBalance` — FR1-1: авторизованному сотруднику отображается текущий бонусный баланс и сумма на согласовании — UNIT, пройден
- `AccountQueryServiceTest#showsBalanceHistory` — FR1-1: динамика баланса за 6 месяцев рассчитывается по журналу проведённых операций — UNIT, пройден
- `RequirementsRestApiTest#balanceOfAuthorizedEmployee` — FR1-1: авторизованный сотрудник получает текущий баланс своего счёта — интеграционный (Spring, БД), пройден

### FR1-2 — Если данные недоступны, система должна отображать сообщение об ошибке

- `AccountQueryServiceTest#blockedAccountShowsError` — FR1-2: если счёт заблокирован (данные недоступны) — сообщение «Баланс недоступен, обратитесь к HR.» — UNIT, пройден
- `AccountQueryServiceTest#missingAccountShowsError` — FR1-2: если к учётной записи не привязан счёт — сообщение об ошибке — UNIT, пройден
- `RequirementsRestApiTest#unavailableBalanceShowsError` — FR1-2: если баланс недоступен (счёт заблокирован) — 423 и сообщение об ошибке — интеграционный (Spring, БД), пройден

### FR1-3 — Если баланс обновлён, система должна отображать актуальное значение

- `NotificationListenerTest#titlesForAllActions` — FR1-3: заголовки уведомлений отражают изменение баланса по каждому виду операции — UNIT, пройден
- `AccountQueryServiceTest#showsUpdatedBalance` — FR1-3: после изменения баланса отображается актуальное значение — UNIT, пройден
- `RequirementsRestApiTest#redemptionUpdatesShownBalance` — FR3-1, FR3-3, FR1-3: списание при достаточном балансе; после него отображается обновлённый баланс — интеграционный (Spring, БД), пройден

### FR2-1 — Если пользователь авторизован, система должна отображать историю операций

- `AccountQueryServiceTest#showsHistory` — FR2-1: авторизованному сотруднику отображается история операций его счёта с пояснениями — UNIT, пройден
- `OperationQueryServiceTest#getById` — FR2-1: операция загружается по номеру; неизвестная — сообщение «не найдена» — UNIT, пройден
- `RequirementsRestApiTest#historyIsFilteredByPeriod` — FR2-1, FR2-3: история операций фильтруется по датам периода (включительно) — интеграционный (Spring, БД), пройден

### FR2-2 — Если операций нет, система должна отображать пустой список

- `AccountQueryServiceTest#emptyHistory` — FR2-2: если операций нет — отображается пустой список — UNIT, пройден
- `RequirementsRestApiTest#emptyHistory` — FR2-2: если операций за период нет — пустой список — интеграционный (Spring, БД), пройден

### FR2-3 — Если указан период, система должна фильтровать операции по датам

- `AccountQueryServiceTest#periodIsValidated` — FR2-3: период фильтрации проверяется — начало позже окончания отклоняется — UNIT, пройден
- `AccountQueryServiceTest#singleDayAndOpenPeriods` — FR2-3: период из одного дня и открытые границы периода допустимы — UNIT, пройден
- `OperationQueryServiceTest#searchValidatesPeriod` — FR2-3: журнал не принимает период, у которого начало позже окончания — UNIT, пройден
- `RequirementsRestApiTest#historyIsFilteredByPeriod` — FR2-1, FR2-3: история операций фильтруется по датам периода (включительно) — интеграционный (Spring, БД), пройден

### FR3-1 — Если баланс достаточен, система должна разрешить списание бонусов

- `RedemptionServiceTest#redeemsWhenBalanceIsEnough` — FR3-1: при достаточном балансе списание разрешено — создаётся операция «Списано» на −стоимость — UNIT, пройден
- `RedemptionServiceTest#balanceEqualToCostIsEnough` — FR3-1: баланс, равный стоимости программы, достаточен для списания — UNIT, пройден
- `RequirementsRestApiTest#redemptionUpdatesShownBalance` — FR3-1, FR3-3, FR1-3: списание при достаточном балансе; после него отображается обновлённый баланс — интеграционный (Spring, БД), пройден

### FR3-2 — Если баланс недостаточен, система должна отклонить операцию

- `OperationStatusTest#balanceNeverNegative` — FR3-2: баланс счёта не может стать отрицательным (CHECK balance >= 0) — UNIT, пройден
- `RedemptionServiceTest#rejectsWhenBalanceIsInsufficient` — FR3-2: при недостаточном балансе списание отклоняется с указанием недостающей суммы — UNIT, пройден
- `RedemptionServiceTest#rejectsBlockedAccount` — FR3-2: списание с заблокированного счёта отклоняется — UNIT, пройден
- `RedemptionServiceTest#rejectsInactiveOrUnknownProgram` — FR3-2: недоступная или неизвестная программа — списание отклоняется — UNIT, пройден
- `RequirementsRestApiTest#insufficientBalanceRejected` — FR3-2: при недостаточном балансе списание отклоняется (409), баланс не меняется — интеграционный (Spring, БД), пройден

### FR3-3 — После подтверждения система должна обновить баланс

- `RedemptionServiceTest#balanceIsUpdatedAfterRedemption` — FR3-3: после подтверждения списания баланс уменьшается на стоимость программы — UNIT, пройден
- `RedemptionServiceTest#redemptionIsPublished` — FR3-3: о списании публикуется событие для журнала аудита и уведомления сотрудника — UNIT, пройден
- `RequirementsRestApiTest#redemptionUpdatesShownBalance` — FR3-1, FR3-3, FR1-3: списание при достаточном балансе; после него отображается обновлённый баланс — интеграционный (Spring, БД), пройден

### FR4-1 — Если поступило KPI-событие, система должна выполнить расчёт бонусов

- `EventProcessingServiceTest#kpiEventTriggersCalculation` — FR4-1: при поступлении KPI-события выполняется расчёт бонуса по правилу показателя — UNIT, пройден
- `EventProcessingServiceTest#businessDateUsesCompanyZone` — FR4-1: бизнес-дата расчёта — дата получения события по времени Минска — UNIT, пройден
- `EventProcessingServiceTest#belowThresholdProcessedWithoutBonus` — FR4-1: KPI ниже порога — событие обработано без начисления (BR-5) — UNIT, пройден
- `EventProcessingServiceTest#invalidEventIsPostponed` — FR4-1: событие без значения KPI откладывается с причиной ошибки и записью в аудит — UNIT, пройден
- `EventProcessingServiceTest#unknownEventIsNotFound` — FR4-1: неизвестное событие — ошибка «не найдено» — UNIT, пройден

### FR4-2 — Если правило активно, система должна применить коэффициенты

- `EventProcessingServiceTest#activeRuleAppliesCoefficients` — FR4-2: при активном правиле применяются коэффициенты KPI и кампании (1140 × 1,5 = 1710 → 1700) — UNIT, пройден
- `EventProcessingServiceTest#noActiveRuleNoAccrual` — FR4-2: без действующего (активного) правила для показателя начисление не создаётся — UNIT, пройден

### FR4-3 — После расчёта система должна сохранить начисление

- `OperationStatusTest#businessEventStatus` — FR4-3: ошибка обработки события сохраняется не длиннее 255 символов; успешная обработка её очищает — UNIT, пройден
- `EventProcessingServiceTest#savesAccrualAfterCalculation` — FR4-3: после расчёта сохраняется начисление со ссылкой на событие, баланс растёт, событие обработано — UNIT, пройден
- `EventProcessingServiceTest#processingIsIdempotent` — FR4-3: повторная обработка события не создаёт второе начисление (NFR-4) — UNIT, пройден
- `EventProcessingServiceTest#blockedAccountPostponesEvent` — FR4-3: на заблокированный счёт начисление не сохраняется — событие отложено — UNIT, пройден
- `AuditListenerTest#autoAccrualIsRecordedAsSystem` — FR4-3: автоматическое начисление фиксируется в журнале от имени системы с формулой расчёта — UNIT, пройден

### FR5-1 — Если есть права доступа, система должна разрешить создание начисления

- `AccessPolicyTest#activeUserIsLoaded` — FR5-1: активный пользователь загружается вместе с ролью — UNIT, пройден
- `AccessPolicyTest#missingSessionIsDenied` — FR5-1: без сессии или с неизвестной учётной записью — отказ «Сессия закончилась» — UNIT, пройден
- `AccessPolicyTest#manageScope` — FR5-1: HR управляет баллами любого сотрудника, руководитель — только непосредственных подчинённых — UNIT, пройден
- `AccrualServiceTest#managerAccruesOwnTeam` — FR5-1: руководитель создаёт начисление сотруднику своей команды — UNIT, пройден
- `AccrualServiceTest#hrAccruesAnyEmployee` — FR5-1: HR создаёт начисление сотруднику любого подразделения — UNIT, пройден
- `AccrualServiceTest#withoutRightsAccrualIsDenied` — FR5-1: без прав (роль «сотрудник», «финотдел») начисление не создаётся — UNIT, пройден
- `AccrualServiceTest#managerCannotAccrueForeignEmployee` — FR5-1: руководитель не может начислить сотруднику чужой команды — UNIT, пройден
- `AccrualServiceTest#blockedOrUnknownActorIsDenied` — FR5-1: заблокированная или неизвестная учётная запись инициатора — отказ — UNIT, пройден
- `ReviewServiceTest#onlyFinanceReviews` — FR5-1: подтверждать и блокировать начисления может только финотдел — UNIT, пройден
- `RequirementsRestApiTest#managerAccrualUpdatesBalance` — FR5-1, FR5-2, FR5-3: руководитель начисляет баллы своей команде, баланс получателя растёт — интеграционный (Spring, БД), пройден

### FR5-2 — Если указана сумма, система должна сохранить начисление

- `OperationStatusTest#operationInvariants` — FR5-2: операция с нулевой суммой или причиной длиннее 300 символов не создаётся — UNIT, пройден
- `AccrualServiceTest#savesAccrualWithAmount` — FR5-2: указанная сумма сохраняется операцией начисления с основанием, причиной и автором — UNIT, пройден
- `AccrualServiceTest#aboveLimitIsSavedAsPending` — FR5-2: сумма выше лимита 1 000 Б сохраняется на согласование финотдела (pending) — UNIT, пройден
- `AccrualServiceTest#exactlyLimitIsAccrued` — FR5-2: сумма ровно на лимите (1 000 Б) проводится сразу — UNIT, пройден
- `AccrualServiceTest#multipleEmployeesAreDeduplicated` — FR5-2: начисление нескольким сотрудникам — по операции на каждого, повторы в списке убираются — UNIT, пройден
- `AccrualServiceTest#savesCampaignAndRuleLinks` — FR5-2: начисление с кампанией сохраняет сумму с коэффициентом и ссылку на кампанию и правило — UNIT, пройден
- `AccrualServiceTest#wrongBasisIsRejected` — FR5-2: основание не из списка KPI/проект/достижение отклоняется — UNIT, пройден
- `AccrualServiceTest#blankReasonOrZeroAmountIsRejected` — FR5-2: без причины или с нулевой суммой начисление не сохраняется — UNIT, пройден
- `AccrualServiceTest#unknownEmployeeIsRejected` — FR5-2: неизвестный сотрудник в списке получателей — ошибка проверки — UNIT, пройден
- `ReviewServiceTest#pendingQueueForFinance` — FR5-2: очередь финотдела содержит операции на согласовании — UNIT, пройден
- `OperationQueryServiceTest#pendingOperationsAreExplained` — FR5-2: операции на согласовании получают пояснение причины (лимит или отрицательный баланс) — UNIT, пройден
- `OperationQueryServiceTest#controlQueueContainsPendingAndDisputed` — FR5-2: очередь контроля — операции на согласовании и проведённые спорные — UNIT, пройден
- `RequirementsRestApiTest#managerAccrualUpdatesBalance` — FR5-1, FR5-2, FR5-3: руководитель начисляет баллы своей команде, баланс получателя растёт — интеграционный (Spring, БД), пройден

### FR5-3 — После операции система должна обновить баланс

- `OperationStatusTest#settledStatuses` — FR5-3: в баланс входят только начисленные, списанные и скорректированные операции — UNIT, пройден
- `AccrualServiceTest#balanceGrowsAfterAccrual` — FR5-3: после проведённого начисления баланс сотрудника увеличивается на сумму — UNIT, пройден
- `AccrualServiceTest#pendingDoesNotChangeBalance` — FR5-3: начисление на согласовании баланс не меняет — UNIT, пройден
- `AccrualServiceTest#blockedRecipientCancelsWholeAccrual` — FR5-3: заблокированный счёт любого получателя отменяет всё начисление — балансы не меняются — UNIT, пройден
- `ReviewServiceTest#approvedAccrualUpdatesBalance` — FR5-3: подтверждённое финотделом начисление проводится и увеличивает баланс — UNIT, пройден
- `ReviewServiceTest#blockedAccrualKeepsBalance` — FR5-3: заблокированное финотделом начисление баланс не меняет, причина обязательна — UNIT, пройден
- `ReviewServiceTest#secondApprovalIsRejected` — FR5-3: повторное подтверждение уже проведённой операции запрещено — UNIT, пройден
- `ReviewServiceTest#approvalOnBlockedAccount` — FR5-3: подтверждение на заблокированный счёт невозможно — UNIT, пройден
- `NotificationListenerTest#accrualNotification` — FR5-3: после начисления сотрудник получает уведомление «Начислено N Б» — UNIT, пройден
- `NotificationListenerTest#notificationFailureIsSwallowed` — FR5-3: сбой записи уведомления не прерывает операцию; без учётной записи уведомление не создаётся — UNIT, пройден
- `RequirementsRestApiTest#managerAccrualUpdatesBalance` — FR5-1, FR5-2, FR5-3: руководитель начисляет баллы своей команде, баланс получателя растёт — интеграционный (Spring, БД), пройден

### FR6-1 — Если есть входные данные, система должна выполнить расчёт

- `BonusCalculationServiceTest#calculatesWhenInputPresent` — FR6-1: при наличии сотрудника, правила, KPI и даты система выполняет расчёт (ОП-000001: 1392 → 1400) — UNIT, пройден
- `BonusCalculationServiceTest#rejectsIncompleteInput` — FR6-1: без обязательных входных данных расчёт не выполняется — UNIT, пройден
- `BonusCalculationServiceTest#rejectsUnknownEmployeeOrRule` — FR6-1: неизвестный сотрудник или правило — некорректные входные данные — UNIT, пройден
- `BonusCalculationServiceTest#manualRejectsInvalidInput` — FR6-1: ручной расчёт с нулевой суммой или неизвестной кампанией не выполняется — UNIT, пройден
- `KpiAccrualStrategyTest#missingKpiIsRejected` — FR6-1: без значения KPI расчёт не выполняется — некорректные входные данные — UNIT, пройден
- `KpiAccrualStrategyTest#outOfRangeKpiIsRejected` — FR6-1: значение KPI вне диапазона 0–150 % отклоняется — UNIT, пройден

### FR6-2 — Если применяются правила, система должна учитывать KPI коэффициенты

- `BonusCalculationServiceTest#appliesKpiCoefficients` — FR6-2: итог учитывает вес показателя и уровень достижения KPI (KPI 91 %: 3000 × 0,4 × 0,91 = 1092 → 1100) — UNIT, пройден
- `BonusCalculationServiceTest#belowThresholdIsZero` — FR6-2: KPI ниже порога — итог 0, кампании не запрашиваются (BR-5) — UNIT, пройден
- `FixedEventAccrualStrategyTest#basePointsTimesWeight` — FR6-2: сумма по событию = базовые баллы × вес (6000 × 0,5 = 3000) — UNIT, пройден
- `FixedEventAccrualStrategyTest#kpiValueIsIgnored` — FR6-2: значение KPI для правила по событию не влияет на сумму — UNIT, пройден
- `KpiAccrualStrategyTest#appliesWeightAndKpiLevel` — FR6-2: база = базовые баллы × вес × KPI / 100 (3000 × 0,4 × 116 % = 1392) — UNIT, пройден
- `KpiAccrualStrategyTest#proportionalToKpiFromThreshold` — FR6-2: KPI на пороге и выше порога даёт бонус пропорционально уровню достижения — UNIT, пройден
- `KpiAccrualStrategyTest#belowThresholdGivesZero` — FR6-2: KPI ниже порога правила — бонус не положен, база 0 (BR-5) — UNIT, пройден

### FR6-3 — После расчёта система должна вернуть итоговую сумму

- `AccrualServiceTest#previewReturnsTotalsWithoutSaving` — FR6-3: предварительный расчёт возвращает итог по строкам и число согласований, ничего не сохраняя — UNIT, пройден
- `BonusCalculationServiceTest#roundsToRuleStep` — FR6-3: итоговая сумма округляется до шага roundTo из параметров правила — UNIT, пройден
- `BonusCalculationServiceTest#defaultRoundingIsFifty` — FR6-3: без roundTo в параметрах итог округляется до 50 баллов — UNIT, пройден
- `BonusCalculationServiceTest#brokenParamsAreReported` — FR6-3: повреждённые параметры правила — итог не возвращается, ошибка входных данных — UNIT, пройден
- `BonusCalculationServiceTest#roundToStepHalfUp` — FR6-3: округление до ближайшего кратного шагу, половина — вверх — UNIT, пройден

### FR7-1 — Если обнаружена ошибка, система должна разрешить корректировку

- `CorrectionServiceTest#hrCorrectsAccrual` — FR7-1: HR исправляет ошибочную сумму — создаётся операция корректировки со ссылкой на исходную — UNIT, пройден
- `CorrectionServiceTest#managerCorrectsOnlyOwnTeam` — FR7-1: руководитель корректирует начисления только своей команды — UNIT, пройден
- `CorrectionServiceTest#otherRolesCannotCorrect` — FR7-1: сотрудник и финотдел корректировать начисления не могут — UNIT, пройден
- `CorrectionServiceTest#writeoffAndCancelledCannotBeCorrected` — FR7-1: списание и уже отменённую операцию корректировать нельзя — UNIT, пройден
- `CorrectionServiceTest#secondCorrectionWhilePendingIsRejected` — FR7-1: повторная корректировка, пока предыдущая на согласовании, запрещена — UNIT, пройден
- `CorrectionServiceTest#newAmountIsValidated` — FR7-1: новая сумма должна быть больше нуля и отличаться от текущей — UNIT, пройден
- `CorrectionServiceTest#unknownOperationOrBlockedAccount` — FR7-1: неизвестная операция или заблокированный счёт — корректировка невозможна — UNIT, пройден
- `OperationQueryServiceTest#searchByNameIgnoresCase` — FR7-1: поиск в журнале по ФИО без учёта регистра (кириллица) — найти ошибочное начисление — UNIT, пройден
- `RequirementsRestApiTest#correctionIsJournaled` — FR7-1, FR7-2, FR7-3: корректировка HR сохраняется, баланс пересчитывается, изменение видно в журнале аудита — интеграционный (Spring, БД), пройден

### FR7-2 — После изменения система должна пересчитать баланс

- `OperationStatusTest#transitions` — FR7-2: переходы статуса операции соответствуют диаграмме состояний — UNIT, пройден
- `OperationStatusTest#illegalTransitionIsRejected` — FR7-2: недопустимый переход операции отклоняется, статус не меняется — UNIT, пройден
- `OperationStatusTest#correctionDelta` — FR7-2: разница корректировки — новая сумма минус исходная; для отмены — минус исходная — UNIT, пройден
- `CorrectionServiceTest#decreaseRecalculatesBalance` — FR7-2: уменьшение суммы 1 000 → 700 уменьшает баланс на 300 — UNIT, пройден
- `CorrectionServiceTest#increaseRecalculatesBalance` — FR7-2: увеличение суммы 1 000 → 1 500 увеличивает баланс на 500 — UNIT, пройден
- `CorrectionServiceTest#cancellationRecalculatesBalance` — FR7-2: отмена начисления вычитает его сумму из баланса, корректировка получает статус «Отменено» — UNIT, пройден
- `CorrectionServiceTest#negativeBalanceGoesToApproval` — FR7-2: если баланс уйдёт в минус — корректировка на согласование, баланс и исходная операция не меняются — UNIT, пройден
- `CorrectionServiceTest#correctionOfCorrection` — FR7-2: повторная корректировка уже скорректированной суммы пересчитывает баланс от неё — UNIT, пройден
- `ReviewServiceTest#approvedCancellationRecalculatesBalance` — FR7-2: подтверждённая отмена начисления пересчитывает баланс, исходная операция отменяется — UNIT, пройден
- `ReviewServiceTest#approvalCannotMakeBalanceNegative` — FR7-2: подтвердить корректировку, уводящую баланс в минус, нельзя — баланс не меняется — UNIT, пройден
- `ReviewServiceTest#approvalOfStaleCorrectionIsRejected` — FR7-2: корректировку уже изменённой исходной операции подтвердить нельзя — UNIT, пройден
- `RequirementsRestApiTest#correctionIsJournaled` — FR7-1, FR7-2, FR7-3: корректировка HR сохраняется, баланс пересчитывается, изменение видно в журнале аудита — интеграционный (Spring, БД), пройден

### FR7-3 — Система должна зафиксировать изменение в журнале

- `CorrectionServiceTest#correctionIsJournaled` — FR7-3: корректировка публикуется для журнала с исходной и новой суммой и автором — UNIT, пройден
- `CorrectionServiceTest#cancellationAndPendingAreJournaled` — FR7-3: отмена и корректировка на согласовании фиксируются с пояснением для финотдела — UNIT, пройден
- `ReviewServiceTest#commentLengthIsLimited` — FR7-3: решение финотдела с комментарием длиннее 300 символов отклоняется — UNIT, пройден
- `AuditListenerTest#correctionIsRecorded` — FR7-3: корректировка фиксируется в журнале: автор, номер, исходная и новая сумма, статус — UNIT, пройден
- `NotificationListenerTest#correctionText` — FR7-3: уведомление о корректировке содержит исходную и новую сумму — UNIT, пройден
- `OperationQueryServiceTest#reviewDecisionIsShown` — FR7-3: последнее решение финотдела отображается в журнале как комментарий; спорные помечаются — UNIT, пройден
- `RequirementsRestApiTest#correctionIsJournaled` — FR7-1, FR7-2, FR7-3: корректировка HR сохраняется, баланс пересчитывается, изменение видно в журнале аудита — интеграционный (Spring, БД), пройден

### FR8-1 — Если HR авторизован, система должна позволить создание правил

- `RuleAdminServiceTest#hrCanCreateRule` — FR8-1: авторизованный HR может создать правило начисления — UNIT, пройден
- `RuleAdminServiceTest#nonHrCannotManageRules` — FR8-1: руководитель и неавторизованный пользователь создавать и менять правила не могут — UNIT, пройден
- `RequirementsRestApiTest#hrCreatesRule` — FR8-1, FR8-2: HR создаёт правило, оно сохраняется и отображается в списке правил — интеграционный (Spring, БД), пройден

### FR8-2 — После создания система должна сохранить правила

- `RuleAdminServiceTest#createdRuleIsSaved` — FR8-2: после создания правило сохраняется с параметрами и вступает в силу сегодня — UNIT, пройден
- `RuleAdminServiceTest#creationIsAudited` — FR8-2: создание правила фиксируется в журнале аудита — UNIT, пройден
- `RuleAdminServiceTest#duplicateRuleForKpiIsRejected` — FR8-2: для показателя, у которого уже есть правило, второе не создаётся (RULE_EXISTS) — UNIT, пройден
- `RequirementsRestApiTest#hrCreatesRule` — FR8-1, FR8-2: HR создаёт правило, оно сохраняется и отображается в списке правил — интеграционный (Spring, БД), пройден

### FR8-3 — Активные правила должны применяться при расчётах

- `OperationStatusTest#ruleApplicability` — FR8-3: правило действует, только если активно и дата вступления в силу наступила — UNIT, пройден
- `BonusCalculationServiceTest#inactiveRuleIsNotApplied` — FR8-3: неактивное правило при расчёте не применяется — UNIT, пройден
- `BonusCalculationServiceTest#futureRuleVersionIsNotApplied` — FR8-3: версия правила с датой вступления в силу позже даты расчёта не применяется — UNIT, пройден
- `RuleAdminServiceTest#usedRuleChangesFromNextPeriod` — FR8-3: правило, применённое в текущем периоде, меняется новой версией со следующего периода — UNIT, пройден
- `RuleAdminServiceTest#unusedRuleChangesImmediately` — FR8-3: правило, не применявшееся в текущем периоде, меняется сразу — UNIT, пройден
- `RuleAdminServiceTest#kpiOfUsedRuleIsLocked` — FR8-3: у применённого правила нельзя сменить показатель; неизвестное правило — «не найдено» — UNIT, пройден
- `RuleAdminServiceTest#nextPeriodStart` — FR8-3: следующий период правила — начало следующего месяца, квартала или года — UNIT, пройден
- `AccrualStrategyRegistryTest#everyPeriodicityHasStrategy` — FR8-3: для каждой периодичности правила есть ровно одна стратегия расчёта — UNIT, пройден
- `AccrualStrategyRegistryTest#picksStrategyByPeriodicity` — FR8-3: правила по KPI считаются стратегией KPI, по событию и ежегодные — фиксированной — UNIT, пройден
- `AccrualStrategyRegistryTest#missingStrategyFailsFast` — FR8-3: при запуске обнаруживается периодичность без стратегии расчёта — UNIT, пройден
- `AccrualStrategyRegistryTest#duplicateStrategyFailsFast` — FR8-3: две стратегии на одну периодичность — ошибка конфигурации — UNIT, пройден
- `FixedEventAccrualStrategyTest#supportsEventAndYear` — FR8-3: стратегия обслуживает правила «по событию» и ежегодные — UNIT, пройден
- `KpiAccrualStrategyTest#supportsMonthAndQuarter` — FR8-3: стратегия обслуживает периодические правила по KPI — месяц и квартал — UNIT, пройден

### FR9-1 — Если создана кампания, система должна сохранить параметры

- `CampaignAdminServiceTest#createdCampaignParametersAreSaved` — FR9-1: созданная кампания сохраняется с названием, описанием, сроками, коэффициентом и бюджетом — UNIT, пройден
- `CampaignAdminServiceTest#creationIsAudited` — FR9-1: создание кампании фиксируется в журнале аудита со статусом и бюджетом — UNIT, пройден
- `CampaignAdminServiceTest#withoutBudgetNeedsApprovalRegardlessOfDates` — FR9-1: кампания без бюджета сохраняется со статусом «Требуется утверждение» — UNIT, пройден
- `CampaignAdminServiceTest#invalidParametersAreRejected` — FR9-1: некорректные параметры кампании не сохраняются — UNIT, пройден
- `CampaignAdminServiceTest#unknownDepartmentIsRejected` — FR9-1: неизвестное подразделение в целевых — кампания не сохраняется — UNIT, пройден
- `CampaignAdminServiceTest#onlyHrCreatesCampaigns` — FR9-1: создавать кампании может только HR — UNIT, пройден
- `RequirementsRestApiTest#campaignIsSaved` — FR9-1: кампания сохраняется с параметрами; без бюджета — статус «Требуется утверждение» — интеграционный (Spring, БД), пройден

### FR9-2 — Если кампания активна, система должна применять её правила

- `BonusCalculationServiceTest#appliesActiveCampaignCoefficient` — FR9-2: активная кампания подразделения применяет свой коэффициент (ОП-000144: 1092 × 1,5 = 1638 → 1650) — UNIT, пройден
- `BonusCalculationServiceTest#skipsCampaignWithoutBudgetLeft` — FR9-2: кампания с исчерпанным бюджетом пропускается — применяется следующая подходящая — UNIT, пройден
- `BonusCalculationServiceTest#noCampaignFitsBudget` — FR9-2: если бюджет ни одной кампании не позволяет начисление — коэффициент 1 — UNIT, пройден
- `BonusCalculationServiceTest#unlimitedCampaignDoesNotCheckSpent` — FR9-2: кампания без ограничения бюджета применяется без проверки израсходованного — UNIT, пройден
- `BonusCalculationServiceTest#manualAmountWithCampaign` — FR9-2: ручное начисление в активную кампанию подразделения умножается на её коэффициент (600 × 1,5 = 900) — UNIT, пройден
- `BonusCalculationServiceTest#manualAmountCampaignNotTargeted` — FR9-2: кампания, не охватывающая подразделение сотрудника, не применяется — UNIT, пройден
- `CampaignAdminServiceTest#withBudgetRunningTodayIsActive` — FR9-2: кампания с бюджетом, идущая сегодня, сразу активна и применяется в расчётах — UNIT, пройден
- `CampaignLifecycleDefectTest#plannedCampaignAppliesWhenStarted` — FR9-2 [D-1]: запланированная кампания «Неделя наставничества» (×2, 01–07.12) применяется в свои даты — интеграционный (Spring, БД), отключён (дефект D-1)

### FR9-3 — После завершения система должна деактивировать кампанию

- `BonusCalculationServiceTest#campaignIsInactiveAfterEnd` — FR9-3: после даты окончания кампания не применяется — коэффициент 1 — UNIT, пройден
- `BonusCalculationServiceTest#finishedCampaignIsNotApplied` — FR9-3: завершённая кампания не применяется даже в пределах своих дат — UNIT, пройден
- `CampaignAdminServiceTest#withBudgetStatusFollowsDates` — FR9-3: кампания, срок которой уже истёк, создаётся завершённой (неактивной) — UNIT, пройден
- `CampaignAdminServiceTest#runningOnlyWhileActiveAndWithinDates` — FR9-3: завершённая и запланированная кампании не идут на дату расчёта, активная — только в свои сроки — UNIT, пройден
- `CampaignLifecycleDefectTest#finishedCampaignIsDeactivated` — FR9-3 [D-1]: после даты окончания кампания «Осенний релиз 2.0» отображается завершённой — интеграционный (Spring, БД), отключён (дефект D-1)

### FR10-1 — Система должна формировать отчёт по начислениям

- `ReportQueryServiceTest#employeeReport` — FR10-1: отчёт по сотрудникам содержит начислено, потрачено, на согласовании и баланс — UNIT, пройден
- `ReportQueryServiceTest#departmentReport` — FR10-1: отчёт по подразделениям суммирует начисления и балансы сотрудников подразделения — UNIT, пройден
- `ReportQueryServiceTest#campaignReport` — FR10-1: отчёт по кампаниям показывает начисленное по кампании и остаток бюджета — UNIT, пройден
- `RequirementsRestApiTest#reportByPeriod` — FR10-1, FR10-2, FR10-3: отчёт за период включает только операции этого периода и выдаётся готовым — интеграционный (Spring, БД), пройден

### FR10-2 — Система должна фильтровать данные по периоду

- `ReportQueryServiceTest#periodIsValidated` — FR10-2: период отчёта обязателен, начало не позже окончания, длина не более трёх лет — UNIT, пройден
- `ReportQueryServiceTest#reportKeepsPeriod` — FR10-2: отчёт строится по операциям периода и сохраняет границы периода — UNIT, пройден
- `RequirementsRestApiTest#reportByPeriod` — FR10-1, FR10-2, FR10-3: отчёт за период включает только операции этого периода и выдаётся готовым — интеграционный (Spring, БД), пройден

### FR10-3 — Система должна предоставлять готовый отчёт пользователю

- `ReportQueryServiceTest#readyReportIsProvided` — FR10-3: пользователю предоставляется готовый отчёт с видом, периодом и временем формирования — UNIT, пройден
- `ReportQueryServiceTest#emptyPeriodGivesEmptyReport` — FR10-3: за период без операций формируется пустой отчёт (без ошибки) — UNIT, пройден
- `RequirementsRestApiTest#reportByPeriod` — FR10-1, FR10-2, FR10-3: отчёт за период включает только операции этого периода и выдаётся готовым — интеграционный (Spring, БД), пройден

### FR11-1 — Система должна рассчитывать показатели эффективности

- `AnalyticsQueryServiceTest#calculatesEfficiencyIndicators` — FR11-1: рассчитываются показатели эффективности — средний бонус и конверсия программ — UNIT, пройден
- `AnalyticsQueryServiceTest#emptyScope` — FR11-1: при отсутствии сотрудников в области анализа показатели равны нулю, данные неполные — UNIT, пройден
- `RequirementsRestApiTest#analyticsDashboard` — FR11-1, FR11-2, FR11-3: дашборд эффективности отображается HR с метриками и сравнением периодов — интеграционный (Spring, БД), пройден

### FR11-2 — Система должна формировать метрики анализа

- `AnalyticsQueryServiceTest#buildsComparisonMetrics` — FR11-2: формируются метрики сравнения по подразделениям и динамика за 6 месяцев — UNIT, пройден
- `AnalyticsQueryServiceTest#quarterComparison` — FR11-2: период «квартал» сравнивается с предыдущим кварталом той же длины — UNIT, пройден
- `AnalyticsQueryServiceTest#partialDataIsFlagged` — FR11-2: при нехватке истории для сравнения дашборд помечается как неполный — UNIT, пройден
- `RequirementsRestApiTest#analyticsDashboard` — FR11-1, FR11-2, FR11-3: дашборд эффективности отображается HR с метриками и сравнением периодов — интеграционный (Spring, БД), пройден

### FR11-3 — Система должна отображать результаты анализа

- `AnalyticsQueryServiceTest#managerSeesOwnDepartment` — FR11-3: результаты анализа отображаются руководителю только по его подразделению — UNIT, пройден
- `AnalyticsQueryServiceTest#employeeCannotSeeAnalytics` — FR11-3: результаты анализа доступны только HR и руководителю — UNIT, пройден
- `RequirementsRestApiTest#analyticsDashboard` — FR11-1, FR11-2, FR11-3: дашборд эффективности отображается HR с метриками и сравнением периодов — интеграционный (Spring, БД), пройден

### FR12-1 — Система должна создавать пользователей

- `UserAdminServiceTest#createsUser` — FR12-1: создаётся сотрудник, его бонусный счёт и учётная запись с временным паролем — UNIT, пройден
- `UserAdminServiceTest#duplicateEmailIsRejected` — FR12-1: пользователь с уже существующим email не создаётся (EMAIL_EXISTS) — UNIT, пройден
- `UserAdminServiceTest#departmentIsRequired` — FR12-1: без подразделения или с неизвестным подразделением пользователь не создаётся — UNIT, пройден
- `UserAdminServiceTest#onlyAdminManagesUsers` — FR12-1: управлять пользователями может только администратор — UNIT, пройден
- `RequirementsRestApiTest#userLifecycle` — FR12-1, FR12-3: администратор создаёт пользователя (дубль email — 409) и деактивирует его — вход закрыт — интеграционный (Spring, БД), пройден

### FR12-2 — Система должна обновлять роли и права

- `OperationStatusTest#persistentEnum` — FR12-2: код роли из БД преобразуется в перечисление; неизвестный код — ошибка — UNIT, пройден
- `AccessPolicyTest#roleIsChecked` — FR12-2: действие разрешено только ролям из списка — UNIT, пройден
- `UserAdminServiceTest#roleIsUpdated` — FR12-2: смена роли сохраняется и фиксируется в аудите как «Изменение роли» — UNIT, пройден
- `UserAdminServiceTest#dataUpdatedWithoutRoleChange` — FR12-2: без смены роли меняются только данные сотрудника; при переводе руководитель — глава нового отдела — UNIT, пройден
- `UserAdminServiceTest#updateConflicts` — FR12-2: нельзя снять роль администратора с себя и занять чужой email — UNIT, пройден
- `RequirementsRestApiTest#roleIsChanged` — FR12-2: администратор меняет роль пользователя — интеграционный (Spring, БД), пройден

### FR12-3 — Система должна деактивировать пользователей

- `AccessPolicyTest#blockedUserIsDenied` — FR12-3: деактивированная учётная запись не может выполнять действия — UNIT, пройден
- `UserAdminServiceTest#deactivatesUser` — FR12-3: администратор деактивирует (блокирует) пользователя; действие фиксируется в аудите — UNIT, пройден
- `UserAdminServiceTest#statusChangeIsIdempotent` — FR12-3: повторная установка того же статуса ничего не меняет; разблокировка возвращает доступ — UNIT, пройден
- `UserAdminServiceTest#cannotBlockSelf` — FR12-3: администратор не может деактивировать собственную учётную запись — UNIT, пройден
- `UserAdminServiceTest#blockedUserIsDenied` — FR12-3: деактивированный пользователь не может выполнять действия в системе — UNIT, пройден
- `AuthenticationServiceTest#blockedUserCannotLogin` — FR12-3: деактивированный пользователь не может войти в систему — UNIT, пройден
- `RequirementsRestApiTest#userLifecycle` — FR12-1, FR12-3: администратор создаёт пользователя (дубль email — 409) и деактивирует его — вход закрыт — интеграционный (Spring, БД), пройден

### FR13-1 — Система должна сохранять параметры интеграции

- `IntegrationAdminServiceTest#connectionTestSucceeds` — FR13-1: проверка соединения с адресом HTTPS и ключом проходит, результат сохраняется в журнал проверок — UNIT, пройден
- `IntegrationAdminServiceTest#connectionTestWithHttpFails` — FR13-1: проверка соединения с адресом HTTP не проходит — «нужен адрес HTTPS» — UNIT, пройден
- `IntegrationAdminServiceTest#parametersAreSavedEncrypted` — FR13-1: после успешной проверки адрес и ключ сохраняются; ключ хранится только в зашифрованном виде — UNIT, пройден
- `IntegrationAdminServiceTest#urlOnlyKeepsStoredKey` — FR13-1: при смене только адреса проверяется и сохраняется прежний ключ — UNIT, пройден
- `IntegrationAdminServiceTest#failedCheckDoesNotSave` — FR13-1: параметры, не прошедшие проверку соединения, не сохраняются — UNIT, пройден
- `IntegrationAdminServiceTest#onlyAdminAndExistingIntegration` — FR13-1: настраивать интеграции может только администратор; неизвестная интеграция — «не найдена» — UNIT, пройден
- `AesGcmSecretCipherTest#roundTrip` — FR13-1: сохранённый ключ интеграции расшифровывается в исходное значение — UNIT, пройден
- `AesGcmSecretCipherTest#randomIv` — FR13-1: один и тот же ключ шифруется каждый раз по-разному (случайный вектор инициализации) — UNIT, пройден
- `AesGcmSecretCipherTest#tamperedOrForeignValues` — FR13-1: изменённое, чужое или незашифрованное значение не расшифровывается — UNIT, пройден
- `AesGcmSecretCipherTest#keyLengthIsChecked` — FR13-1: ключ шифрования должен быть длиной 32 байта — UNIT, пройден
- `SimulatedIntegrationProbeTest#httpsAndKeyPass` — FR13-1: адрес HTTPS с именем хоста и ключ от 8 символов — соединение установлено — UNIT, пройден
- `SimulatedIntegrationProbeTest#nonHttpsRejected` — FR13-1: адрес не HTTPS или без хоста отклоняется — «нужен адрес HTTPS» — UNIT, пройден
- `SimulatedIntegrationProbeTest#shortKeyRejected` — FR13-1: ключ API короче 8 символов отклоняется — UNIT, пройден
- `RequirementsRestApiTest#integrationSettings` — FR13-1, FR13-3: параметры интеграции сохраняются только после успешной проверки; затем включается синхронизация — интеграционный (Spring, БД), пройден

### FR13-2 — Система должна обрабатывать внешние события

- `EventProcessingServiceTest#erpStageEventIsProcessed` — FR13-2: событие ERP «этап принят» обрабатывается по правилу STAGE (6000 × 0,5 = 3000) — UNIT, пройден
- `EventProcessingServiceTest#eventTypeMapsToKpi` — FR13-2: тип внешнего события сопоставляется с показателем правила — UNIT, пройден
- `EventProcessingServiceTest#malformedExternalEventsFail` — FR13-2: некорректное внешнее событие не приводит к начислению и помечается ошибкой — UNIT, пройден
- `AuditListenerTest#failedEventIsRecorded` — FR13-2: ошибка обработки внешнего события фиксируется в журнале аудита — UNIT, пройден
- `RequirementsRestApiTest#externalEventIsProcessed` — FR13-2: внешнее событие ERP «этап принят» обрабатывается и даёт начисление сотруднику — интеграционный (Spring, БД), пройден

### FR13-3 — Система должна синхронизировать данные автоматически

- `EventSchedulingConfigTest#scheduledRunProcessesQueue` — FR13-3: по расписанию запускается пакетная обработка поступивших событий — UNIT, пройден
- `EventSchedulingConfigTest#scheduleIsConfigurable` — FR13-3: расписание задаётся настройкой (интервал по умолчанию 1 минута), включается свойством — UNIT, пройден
- `EventProcessingServiceTest#batchProcessesAllNewEvents` — FR13-3: пакетная обработка берёт все новые события; сбой одного не останавливает остальные — UNIT, пройден
- `EventProcessingServiceTest#emptyBatch` — FR13-3: пустая очередь событий обрабатывается без ошибок — UNIT, пройден
- `IntegrationAdminServiceTest#enablesSynchronization` — FR13-3: автоматическая синхронизация включается после успешной проверки, фиксируется время синхронизации — UNIT, пройден
- `IntegrationAdminServiceTest#cannotEnableWithoutSuccessfulCheck` — FR13-3: без успешной проверки соединения синхронизацию включить нельзя — UNIT, пройден
- `IntegrationAdminServiceTest#disablesSynchronization` — FR13-3: отключение синхронизации сбрасывает время синхронизации; повторное включение ничего не делает — UNIT, пройден
- `RequirementsRestApiTest#integrationSettings` — FR13-1, FR13-3: параметры интеграции сохраняются только после успешной проверки; затем включается синхронизация — интеграционный (Spring, БД), пройден

## Тесты без привязки к FR (безопасность, нефункциональные требования, инфраструктура)

- `AuthenticationServiceTest#loginSucceeds` — SEC-1: пользователь авторизуется по email и паролю; вход фиксируется в аудите — UNIT, passed
- `AuthenticationServiceTest#wrongCredentialsAreRejected` — SEC-1: неверный пароль или неизвестный email — одинаковый отказ «Неверный email или пароль» — UNIT, passed
- `AuthenticationServiceTest#logoutIsAudited` — SEC-1: выход из системы фиксируется в аудите — UNIT, passed
