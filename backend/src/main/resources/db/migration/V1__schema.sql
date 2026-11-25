-- =====================================================================
-- TechnoSoft Bonus — физическая модель данных (PostgreSQL 14+)
-- ЛР 7 «Организация системы хранения данных». Миграция Flyway V1.
-- Требования: CON-2 (реляционная СУБД), DATA-1 (полная история),
-- DATA-3 (метки времени), NFR-3/SEC-4 (аудит), NFR-4 (без дублей).
-- =====================================================================

-- ---------- Справочники ----------
CREATE TABLE role (
    code        VARCHAR(20)  PRIMARY KEY,               -- employee, manager, hr, finance, admin
    name        VARCHAR(50)  NOT NULL UNIQUE
);

CREATE TABLE department (
    id               BIGSERIAL    PRIMARY KEY,
    name             VARCHAR(100) NOT NULL UNIQUE,
    head_employee_id BIGINT UNIQUE                       -- FK добавляется после employee; 1:1 «возглавляет»
);

CREATE TABLE kpi (
    id          BIGSERIAL    PRIMARY KEY,
    code        VARCHAR(30)  NOT NULL UNIQUE,
    name        VARCHAR(150) NOT NULL,
    unit        VARCHAR(20)  NOT NULL DEFAULT '%'
);

-- ---------- Сотрудники, учётные записи, счета ----------
CREATE TABLE employee (
    id            BIGSERIAL    PRIMARY KEY,
    full_name     VARCHAR(150) NOT NULL,
    email         VARCHAR(150) NOT NULL UNIQUE,
    position      VARCHAR(100) NOT NULL,
    department_id BIGINT       NOT NULL REFERENCES department (id),
    manager_id    BIGINT       REFERENCES employee (id),
    hired_at      DATE         NOT NULL,
    external_id   VARCHAR(50)  UNIQUE                   -- идентификатор в HRIS (DATA-2)
);

ALTER TABLE department
    ADD CONSTRAINT fk_department_head FOREIGN KEY (head_employee_id) REFERENCES employee (id);

CREATE TABLE app_user (
    id            BIGSERIAL    PRIMARY KEY,
    employee_id   BIGINT       NOT NULL UNIQUE REFERENCES employee (id),
    role_code     VARCHAR(20)  NOT NULL REFERENCES role (code),
    password_hash VARCHAR(100) NOT NULL,                -- BCrypt (DATA-4)
    status        VARCHAR(10)  NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'blocked')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_login_at TIMESTAMPTZ
);

CREATE TABLE bonus_account (                            -- единый бонусный счёт (BR-6)
    id          BIGSERIAL   PRIMARY KEY,
    employee_id BIGINT      NOT NULL UNIQUE REFERENCES employee (id),
    balance     INTEGER     NOT NULL DEFAULT 0 CHECK (balance >= 0),
    status      VARCHAR(10) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'blocked')),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version     INTEGER     NOT NULL DEFAULT 0         -- оптимистичная блокировка (JPA @Version)
);

CREATE TABLE kpi_result (
    id            BIGSERIAL    PRIMARY KEY,
    employee_id   BIGINT       NOT NULL REFERENCES employee (id),
    kpi_id        BIGINT       NOT NULL REFERENCES kpi (id),
    period_start  DATE         NOT NULL,
    period_end    DATE         NOT NULL,
    value_pct     NUMERIC(5,2) NOT NULL CHECK (value_pct BETWEEN 0 AND 150),
    recorded_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (period_end >= period_start),
    UNIQUE (employee_id, kpi_id, period_start)
);

-- ---------- Правила, кампании, программы ----------
CREATE TABLE accrual_rule (                             -- UC8, BR-4
    id           BIGSERIAL    PRIMARY KEY,
    name         VARCHAR(150) NOT NULL,
    kpi_id       BIGINT       NOT NULL REFERENCES kpi (id),
    weight       NUMERIC(3,2) NOT NULL CHECK (weight > 0 AND weight <= 1),
    base_points  INTEGER      NOT NULL CHECK (base_points > 0),
    periodicity  VARCHAR(10)  NOT NULL CHECK (periodicity IN ('month', 'quarter', 'year', 'event')),
    threshold    NUMERIC(5,2) NOT NULL DEFAULT 0 CHECK (threshold BETWEEN 0 AND 150),
    params       JSONB        NOT NULL DEFAULT '{}'::jsonb,   -- доп. условия правила (ЛР5: JSONB)
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    effective_from DATE       NOT NULL DEFAULT CURRENT_DATE,  -- альт. поток UC8
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE campaign (                                 -- UC9
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    description TEXT         NOT NULL DEFAULT '',
    start_date  DATE         NOT NULL,
    end_date    DATE         NOT NULL,
    coefficient NUMERIC(3,2) NOT NULL CHECK (coefficient >= 1 AND coefficient <= 5),
    budget      INTEGER      CHECK (budget > 0),        -- NULL: бюджет не выделен
    status      VARCHAR(15)  NOT NULL CHECK (status IN ('planned', 'active', 'needs_approval', 'finished')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (end_date >= start_date)
);

CREATE TABLE campaign_department (                      -- целевые подразделения кампании (M:N)
    campaign_id   BIGINT NOT NULL REFERENCES campaign (id) ON DELETE CASCADE,
    department_id BIGINT NOT NULL REFERENCES department (id),
    PRIMARY KEY (campaign_id, department_id)
);

CREATE TABLE program (                                  -- программы мотивации (UC3)
    id              BIGSERIAL    PRIMARY KEY,
    name            VARCHAR(150) NOT NULL UNIQUE,
    category        VARCHAR(15)  NOT NULL CHECK (category IN ('gift', 'education', 'merch', 'dayoff', 'health', 'event')),
    description     TEXT         NOT NULL DEFAULT '',
    cost            INTEGER      NOT NULL CHECK (cost > 0),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    inactive_reason VARCHAR(200)
);

-- ---------- Интеграции и бизнес-события ----------
CREATE TABLE integration (                              -- UC13
    id                BIGSERIAL    PRIMARY KEY,
    kind              VARCHAR(15)  NOT NULL UNIQUE CHECK (kind IN ('hris', 'erp', 'ad', 'notifications')),
    name              VARCHAR(100) NOT NULL,
    url               VARCHAR(255) NOT NULL,
    api_key_encrypted VARCHAR(512) NOT NULL,
    enabled           BOOLEAN      NOT NULL DEFAULT FALSE,
    last_sync_at      TIMESTAMPTZ
);

CREATE TABLE integration_check (
    id             BIGSERIAL    PRIMARY KEY,
    integration_id BIGINT       NOT NULL REFERENCES integration (id) ON DELETE CASCADE,
    ok             BOOLEAN      NOT NULL,
    message        VARCHAR(255) NOT NULL,
    latency_ms     INTEGER,
    checked_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE business_event (                           -- UC4, SI-4 (очередь событий)
    id             BIGSERIAL    PRIMARY KEY,
    integration_id BIGINT       REFERENCES integration (id),  -- NULL: внутреннее событие/таймер
    external_id    VARCHAR(100) NOT NULL,
    event_type     VARCHAR(50)  NOT NULL,                     -- kpi_closed, task_done, anniversary …
    employee_id    BIGINT       NOT NULL REFERENCES employee (id),
    payload        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    status         VARCHAR(10)  NOT NULL DEFAULT 'new' CHECK (status IN ('new', 'processed', 'failed')),
    error_message  VARCHAR(255),
    received_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at   TIMESTAMPTZ,
    UNIQUE (integration_id, external_id)                       -- повторная доставка не создаёт дубль
);

-- ---------- Операции по счёту ----------
CREATE TABLE operation (                                -- DATA-1: полная история движений
    id              BIGSERIAL    PRIMARY KEY,
    number          VARCHAR(12)  NOT NULL UNIQUE,       -- «ОП-000123»
    account_id      BIGINT       NOT NULL REFERENCES bonus_account (id),
    type            VARCHAR(10)  NOT NULL CHECK (type IN ('accrual', 'writeoff', 'correction')),
    status          VARCHAR(12)  NOT NULL CHECK (status IN ('accrued', 'written_off', 'corrected', 'pending', 'blocked', 'cancelled')),
    basis           VARCHAR(12)  NOT NULL CHECK (basis IN ('kpi', 'project', 'achievement', 'campaign', 'program', 'correction')),
    amount          INTEGER      NOT NULL CHECK (amount <> 0),   -- «+» начисление, «−» списание
    original_amount INTEGER,                                     -- сумма до корректировки (UC7)
    reason          VARCHAR(300) NOT NULL,
    rule_id         BIGINT       REFERENCES accrual_rule (id),
    campaign_id     BIGINT       REFERENCES campaign (id),
    program_id      BIGINT       REFERENCES program (id),
    event_id        BIGINT       UNIQUE REFERENCES business_event (id),  -- NFR-4: одно событие — одно начисление
    corrects_id     BIGINT       REFERENCES operation (id),     -- корректируемая операция
    created_by      BIGINT       REFERENCES app_user (id),      -- NULL: автоматическое начисление
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE operation_review (                         -- согласование/блокировка финотделом
    id          BIGSERIAL    PRIMARY KEY,
    operation_id BIGINT      NOT NULL REFERENCES operation (id),
    reviewer_id BIGINT       NOT NULL REFERENCES app_user (id),
    decision    VARCHAR(10)  NOT NULL CHECK (decision IN ('approved', 'blocked', 'disputed')),
    comment     VARCHAR(300),
    decided_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------- Аудит и уведомления ----------
CREATE TABLE audit_log (                                -- NFR-3, SEC-4, DATA-3
    id          BIGSERIAL    PRIMARY KEY,
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    user_id     BIGINT       REFERENCES app_user (id),   -- NULL: действие системы
    action      VARCHAR(100) NOT NULL,
    object_type VARCHAR(50)  NOT NULL,
    object_id   BIGINT,
    details     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    ip          INET
);

CREATE TABLE notification (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    title       VARCHAR(150) NOT NULL,
    text        VARCHAR(500) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    read_at     TIMESTAMPTZ
);

-- ---------- Индексы под частые запросы ----------
CREATE INDEX ix_employee_department   ON employee (department_id);
CREATE INDEX ix_employee_manager      ON employee (manager_id);
CREATE INDEX ix_operation_account_dt  ON operation (account_id, created_at DESC);   -- UC2, PERF-1
CREATE INDEX ix_operation_status      ON operation (status) WHERE status = 'pending'; -- контроль финотдела
CREATE INDEX ix_operation_campaign    ON operation (campaign_id);
CREATE INDEX ix_kpi_result_employee   ON kpi_result (employee_id, period_start);
CREATE INDEX ix_event_status          ON business_event (status) WHERE status <> 'processed';
CREATE INDEX ix_audit_occurred        ON audit_log (occurred_at DESC);
CREATE INDEX ix_notification_user     ON notification (user_id, read_at);
