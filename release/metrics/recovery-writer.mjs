#!/usr/bin/env node
/**
 * Нагрузка записи для проверки восстанавливаемости: 4 параллельных потока HR непрерывно проводят ручные начисления
 * по 50 Б сотрудникам (в пределах лимита → сразу «accrued»). Номер каждой подтверждённой сервером операции
 * (ответ 201) записывается. Скрипт работает, пока сервер не перестанет отвечать (его останавливают kill -9),
 * затем сохраняет список подтверждённых операций в out/recovery-acked.json.
 *   BASE_URL=http://localhost:8081 node recovery-writer.mjs
 */
import { http, login, save } from './lib.mjs';

const token = await login('hr');
const EMPLOYEES = ['1', '3', '4', '5', '6', '7'];
const acked = [];
let errors = 0;
let stop = false;
let n = 0;

async function worker(w) {
  while (!stop) {
    const employeeId = EMPLOYEES[(n++ + w) % EMPLOYEES.length];
    const r = await http('POST', '/accruals', {
      token, body: { employeeIds: [employeeId], amount: 50, basis: 'achievement', reason: `Проверка восстановления #${n}` },
    });
    if (r.status === 201) {
      for (const op of r.json.operations) acked.push({ id: op.id, number: op.number, employeeId, amount: op.amount });
      if (acked.length === 1) console.log('first-ack');
    } else if (r.status === 0) {
      errors++;
      if (errors > 3) stop = true;
    } else {
      console.error('unexpected', r.status, r.text?.slice(0, 200));
    }
  }
}

await Promise.all([0, 1, 2, 3].map(worker));
save('recovery-acked.json', { acked: acked.length, operations: acked });
console.log(`acked=${acked.length} connectionErrors=${errors}`);
