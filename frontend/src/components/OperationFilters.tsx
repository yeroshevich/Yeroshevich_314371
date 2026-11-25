import { Button, DatePicker, Flex, Segmented } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import type { ReactNode } from 'react';
import { useSearchParams } from 'react-router-dom';
import type { OperationType } from '../api';

export type TypeFilter = OperationType | 'all';

const TYPE_OPTIONS: { label: string; value: TypeFilter }[] = [
  { label: 'Все', value: 'all' },
  { label: 'Начисления', value: 'accrual' },
  { label: 'Списания', value: 'writeoff' },
  { label: 'Корректировки', value: 'correction' },
];

const PRESETS: { label: string; value: [Dayjs, Dayjs] }[] = [
  { label: 'Этот месяц', value: [dayjs().startOf('month'), dayjs()] },
  { label: 'Прошлый месяц', value: [dayjs().subtract(1, 'month').startOf('month'), dayjs().subtract(1, 'month').endOf('month')] },
  { label: '3 месяца', value: [dayjs().subtract(2, 'month').startOf('month'), dayjs()] },
  { label: 'Полгода', value: [dayjs().subtract(5, 'month').startOf('month'), dayjs()] },
];

/**
 * Фильтры по периоду и типу (FR2-3). Хранятся в адресной строке —
 * ссылкой можно поделиться, «Назад» в браузере возвращает прошлый фильтр.
 */
export function useOperationFilters() {
  const [params, setParams] = useSearchParams();
  const type = (params.get('type') as TypeFilter | null) ?? 'all';
  const from = params.get('from') ?? undefined;
  const to = params.get('to') ?? undefined;
  const query = params.get('q') ?? '';

  const update = (patch: Record<string, string | undefined>) => {
    const next = new URLSearchParams(params);
    Object.entries(patch).forEach(([k, v]) => (v ? next.set(k, v) : next.delete(k)));
    setParams(next, { replace: false });
  };

  return {
    type,
    from,
    to,
    query,
    isFiltered: type !== 'all' || Boolean(from) || Boolean(query),
    setType: (t: TypeFilter) => update({ type: t === 'all' ? undefined : t }),
    setRange: (r: [Dayjs, Dayjs] | null) =>
      update({ from: r?.[0].format('YYYY-MM-DD'), to: r?.[1].format('YYYY-MM-DD') }),
    setQuery: (q: string) => update({ q: q || undefined }),
    reset: () => setParams(new URLSearchParams()),
  };
}

export function OperationFilters({
  filters,
  children,
}: {
  filters: ReturnType<typeof useOperationFilters>;
  children?: ReactNode;
}) {
  const range: [Dayjs, Dayjs] | null = filters.from && filters.to ? [dayjs(filters.from), dayjs(filters.to)] : null;
  return (
    <Flex gap={12} wrap="wrap" align="center" style={{ marginBottom: 16 }} className="tsb-no-print">
      {children}
      <DatePicker.RangePicker
        value={range}
        onChange={(v) => filters.setRange(v && v[0] && v[1] ? [v[0], v[1]] : null)}
        presets={PRESETS}
        format="DD.MM.YYYY"
        allowClear
        placeholder={['Период с', 'по']}
        aria-label="Период"
        style={{ width: 260, maxWidth: '100%' }}
      />
      <div style={{ maxWidth: '100%', overflowX: 'auto' }}>
        <Segmented<TypeFilter> options={TYPE_OPTIONS} value={filters.type} onChange={filters.setType} aria-label="Тип операции" />
      </div>
      {filters.isFiltered && (
        <Button type="link" onClick={filters.reset}>
          Сбросить фильтры
        </Button>
      )}
    </Flex>
  );
}
