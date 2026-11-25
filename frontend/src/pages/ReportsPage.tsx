import { FileExcelOutlined, FilePdfOutlined } from '@ant-design/icons';
import { App, Button, Card, DatePicker, Empty, Flex, Form, Segmented, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';
import { api, type Report, type ReportRow, type ReportType } from '../api';
import { PageHeader } from '../components/PageHeader';
import { shadowCard } from '../theme/tokens';
import { downloadCsv } from '../utils/exportCsv';
import { formatDate, formatDateTime, formatPoints } from '../utils/format';

const TYPE_LABELS: Record<ReportType, string> = {
  employees: 'По сотрудникам',
  departments: 'По подразделениям',
  campaigns: 'По кампаниям',
};

const NAME_COLUMN: Record<ReportType, string> = {
  employees: 'Сотрудник',
  departments: 'Подразделение',
  campaigns: 'Кампания',
};

const GROUP_COLUMN: Record<ReportType, string> = {
  employees: 'Подразделение',
  departments: 'Численность',
  campaigns: 'Бюджет',
};

const BALANCE_COLUMN: Record<ReportType, string> = {
  employees: 'Остаток на счёте',
  departments: 'Остаток на счетах',
  campaigns: 'Остаток бюджета',
};

const points = (v?: number) => (v === undefined ? '—' : formatPoints(v));

/** Отчёты (UC10): тип, период, таблица, выгрузка в Excel (CSV) и PDF (печать). */
export default function ReportsPage() {
  const { message } = App.useApp();
  const [type, setType] = useState<ReportType>('employees');
  const [range, setRange] = useState<[Dayjs, Dayjs]>([dayjs().subtract(5, 'month').startOf('month'), dayjs()]);
  const [report, setReport] = useState<Report>();
  const [loading, setLoading] = useState(false);

  const build = async () => {
    setLoading(true);
    try {
      const r = await api.buildReport(type, range[0].format('YYYY-MM-DD'), range[1].format('YYYY-MM-DD'));
      setReport(r);
      if (!r.rows.length) message.info('За выбранный период операций нет — отчёт пустой.');
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setLoading(false);
    }
  };

  const columns: ColumnsType<ReportRow> = report
    ? [
        { title: NAME_COLUMN[report.type], dataIndex: 'name', sorter: (a, b) => a.name.localeCompare(b.name, 'ru') },
        { title: GROUP_COLUMN[report.type], dataIndex: 'group', responsive: ['md'] },
        { title: 'Операций', dataIndex: 'operations', align: 'right', width: 110, sorter: (a, b) => a.operations - b.operations },
        { title: 'Начислено', dataIndex: 'accrued', align: 'right', width: 130, sorter: (a, b) => a.accrued - b.accrued, render: points, defaultSortOrder: 'descend' },
        ...(report.type !== 'campaigns'
          ? [{ title: 'Списано', dataIndex: 'spent', align: 'right' as const, width: 120, render: points, sorter: (a: ReportRow, b: ReportRow) => a.spent - b.spent }]
          : []),
        { title: 'На согласовании', dataIndex: 'pending', align: 'right', width: 150, render: points },
        { title: BALANCE_COLUMN[report.type], dataIndex: 'balance', align: 'right', width: 160, render: points },
      ]
    : [];

  const title = report
    ? `Отчёт ${TYPE_LABELS[report.type].toLowerCase()}, ${formatDate(report.from)} – ${formatDate(report.to)}`
    : '';

  const exportExcel = () => {
    if (!report) return;
    const header = [NAME_COLUMN[report.type], GROUP_COLUMN[report.type], 'Операций', 'Начислено, Б', 'Списано, Б', 'На согласовании, Б', `${BALANCE_COLUMN[report.type]}, Б`];
    const rows = report.rows.map((r) => [r.name, r.group ?? '', r.operations, r.accrued, r.spent, r.pending, r.balance ?? '']);
    downloadCsv(`technosoft-bonus-${report.type}-${report.from}_${report.to}.csv`, header, rows);
    message.success('Файл для Excel скачан');
  };

  const totals = report?.rows.reduce(
    (t, r) => ({ ops: t.ops + r.operations, accrued: t.accrued + r.accrued, spent: t.spent + r.spent, pending: t.pending + r.pending }),
    { ops: 0, accrued: 0, spent: 0, pending: 0 },
  );

  return (
    <>
      <PageHeader title="Отчёты" description="Начисления, списания и остатки за период. Отчёт можно скачать для Excel или сохранить в PDF." />

      <Card style={{ boxShadow: shadowCard, marginBottom: 24 }} className="tsb-no-print">
        <Form layout="vertical" onFinish={build}>
          <Flex gap={16} wrap="wrap" align="flex-end">
            <Form.Item label="Тип отчёта" style={{ marginBottom: 0, maxWidth: '100%', overflowX: 'auto' }}>
              <Segmented<ReportType>
                value={type}
                onChange={setType}
                options={(Object.keys(TYPE_LABELS) as ReportType[]).map((t) => ({ value: t, label: TYPE_LABELS[t] }))}
              />
            </Form.Item>
            <Form.Item label="Период" style={{ marginBottom: 0 }}>
              <DatePicker.RangePicker
                value={range}
                allowClear={false}
                format="DD.MM.YYYY"
                onChange={(v) => v && v[0] && v[1] && setRange([v[0], v[1]])}
                presets={[
                  { label: 'Этот месяц', value: [dayjs().startOf('month'), dayjs()] },
                  { label: 'Квартал', value: [dayjs().subtract(2, 'month').startOf('month'), dayjs()] },
                  { label: 'Полгода', value: [dayjs().subtract(5, 'month').startOf('month'), dayjs()] },
                ]}
              />
            </Form.Item>
            <Button type="primary" htmlType="submit" loading={loading}>
              Сформировать
            </Button>
          </Flex>
        </Form>
      </Card>

      <Card
        style={{ boxShadow: shadowCard }}
        title={report ? title : 'Результат'}
        extra={
          report && report.rows.length > 0 && (
            <Flex gap={8} wrap="wrap" className="tsb-no-print">
              <Button icon={<FileExcelOutlined />} onClick={exportExcel}>Скачать Excel</Button>
              <Button icon={<FilePdfOutlined />} onClick={() => window.print()}>Сохранить PDF</Button>
            </Flex>
          )
        }
      >
        {report && (
          <div className="tsb-print-only" style={{ marginBottom: 12 }}>
            <b>TechnoSoft Bonus.</b> {title}. Сформирован {formatDateTime(report.generatedAt)}.
          </div>
        )}
        {!report ? (
          <Empty description="Выберите тип отчёта и период, затем нажмите «Сформировать»" />
        ) : (
          <Table<ReportRow>
            rowKey="key"
            columns={columns}
            dataSource={report.rows}
            loading={loading}
            scroll={{ x: 900 }}
            pagination={{ pageSize: 15, hideOnSinglePage: true, showSizeChanger: false }}
            locale={{ emptyText: <Empty description="За выбранный период операций нет. Попробуйте период шире." /> }}
            summary={() =>
              totals && report.rows.length > 0 ? (
                <Table.Summary.Row style={{ fontWeight: 600 }}>
                  <Table.Summary.Cell index={0}>Итого</Table.Summary.Cell>
                  <Table.Summary.Cell index={1} className="tsb-hide-sm" />
                  <Table.Summary.Cell index={2} align="right">{totals.ops}</Table.Summary.Cell>
                  <Table.Summary.Cell index={3} align="right">{formatPoints(totals.accrued)}</Table.Summary.Cell>
                  {report.type !== 'campaigns' && <Table.Summary.Cell index={4} align="right">{formatPoints(totals.spent)}</Table.Summary.Cell>}
                  <Table.Summary.Cell index={5} align="right">{formatPoints(totals.pending)}</Table.Summary.Cell>
                  <Table.Summary.Cell index={6} />
                </Table.Summary.Row>
              ) : null
            }
          />
        )}
        {report && (
          <Typography.Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 12 }}>
            Сформирован {formatDateTime(report.generatedAt)}. Учитываются операции со статусами «Начислено», «Списано», «Скорректировано»; «На согласовании» — отдельной колонкой.
          </Typography.Text>
        )}
      </Card>
    </>
  );
}
