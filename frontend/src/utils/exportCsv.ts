/**
 * Выгрузка таблицы в CSV, который Excel открывает без мастера импорта:
 * BOM для UTF-8, разделитель «;» (региональные настройки ru/by), CRLF.
 */
export function downloadCsv(fileName: string, header: string[], rows: (string | number)[][]): void {
  const escape = (v: string | number) => {
    const s = String(v);
    return /[";\r\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  const body = [header, ...rows].map((r) => r.map(escape).join(';')).join('\r\n');
  const blob = new Blob(['﻿', body], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
