import dayjs from 'dayjs';

const NBSP = ' ';
const numberFormat = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 0 });

/** Баллы в формате «1 250 Б»; с `signed` — «+1 250 Б» / «−900 Б». */
export function formatPoints(value: number, options: { signed?: boolean } = {}): string {
  const abs = numberFormat.format(Math.abs(Math.round(value)));
  const sign = value < 0 ? '−' : options.signed && value > 0 ? '+' : '';
  return `${sign}${abs}${NBSP}Б`;
}

export const formatNumber = (value: number) => numberFormat.format(value);

export const formatDate = (iso: string) => dayjs(iso).format('DD.MM.YYYY');
export const formatDateTime = (iso: string) => dayjs(iso).format('DD.MM.YYYY HH:mm');
export const formatMonth = (yyyyMm: string) => dayjs(`${yyyyMm}-01`).format('MMM');

/** «через 3 дня», «2 часа назад» — без плагина relativeTime, достаточно для уведомлений. */
export function formatAgo(iso: string): string {
  const minutes = dayjs().diff(dayjs(iso), 'minute');
  if (minutes < 1) return 'только что';
  if (minutes < 60) return `${minutes} мин назад`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} ч назад`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days} дн. назад`;
  return formatDate(iso);
}

/** «Выполнение KPI» → «выполнение KPI»: строчная только первая буква. */
export const lowerFirst = (s: string) => s.charAt(0).toLowerCase() + s.slice(1);

export const initials = (fullName: string) =>
  fullName.split(' ').slice(0, 2).map((p) => p[0]).join('').toUpperCase();
