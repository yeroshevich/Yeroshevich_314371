#!/usr/bin/env node
/**
 * ЛР 11, NFR-3 и NFR-4 по данным установочной БД после всех прогонов (тест-кейсы, матрица прав, нагрузка):
 *  - NFR-3: каждая операция начисления, списания и корректировки, созданная после установки (id больше, чем у
 *    операций резервной копии), имеет запись в журнале аудита (object_type = 'operation', object_id = id);
 *  - NFR-4: нет двух операций по одному событию внешней системы; повторная регистрация события с тем же внешним
 *    номером отклоняется ограничением БД; число событий, обработанных дважды.
 *   PGDATABASE=technosoft_bonus node release/metrics/nfr-check.mjs
 */
import { execFileSync } from 'node:child_process';
import { round2, save, nowIso } from './lib.mjs';

const DB = process.env.PGDATABASE ?? 'technosoft_bonus';
const SEED_MAX_OPERATION = Number(process.env.SEED_MAX_OPERATION ?? 153);
const q = (sql) => execFileSync('psql', ['-d', DB, '-Atc', sql], { encoding: 'utf8' }).trim();

const byType = q(`select o.type, count(*), count(a.id) from operation o
  left join lateral (select id from audit_log l where l.object_type = 'operation' and l.object_id = o.id limit 1) a on true
  where o.id > ${SEED_MAX_OPERATION} group by o.type order by 1`).split('\n').filter(Boolean)
  .map((l) => { const [type, ops, audited] = l.split('|'); return { type, operations: Number(ops), audited: Number(audited) }; });
const ops = byType.reduce((s, x) => s + x.operations, 0);
const audited = byType.reduce((s, x) => s + x.audited, 0);

const dupByEvent = Number(q('select count(*) from (select event_id from operation where event_id is not null group by 1 having count(*) > 1) d'));
const eventOps = Number(q('select count(*) from operation where event_id is not null'));
let duplicateEventRejected = false;
let duplicateMessage = '';
try {
  q(`insert into business_event (integration_id, external_id, event_type, employee_id, payload, status, received_at)
     select integration_id, external_id, event_type, employee_id, payload, 'new', now() from business_event order by id limit 1`);
} catch (e) {
  duplicateEventRejected = true;
  duplicateMessage = String(e.stderr ?? e.message).split('\n')[0];
}

const summary = {
  date: nowIso(), db: DB,
  nfr3: { operationsAfterInstall: ops, audited, X: round2(audited / ops), byType },
  nfr4: { operationsFromEvents: eventOps, eventsWithSeveralOperations: dupByEvent, duplicateEventRejected, duplicateMessage },
};
save('nfr-check.json', summary);
console.log(JSON.stringify(summary, null, 2));
