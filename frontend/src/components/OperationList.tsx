import { Flex, Typography } from 'antd';
import { BASIS_LABELS, type Operation } from '../api';
import { colors } from '../theme/tokens';
import { formatDate, lowerFirst } from '../utils/format';
import { Amount } from './Amount';
import { OperationStatusTag } from './OperationStatusTag';

/** Компактный список операций для карточек (главная сотрудника). */
export function OperationList({ operations }: { operations: Operation[] }) {
  return (
    <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
      {operations.map((o, i) => (
        <li key={o.id} style={{ padding: '12px 0', borderTop: i ? `1px solid ${colors.colorBorder}` : undefined }}>
          <Flex justify="space-between" align="flex-start" gap={16}>
            <div style={{ minWidth: 0 }}>
              <div style={{ fontWeight: 500 }}>{o.reason}</div>
              <Flex gap={8} wrap="wrap" align="center" style={{ marginTop: 4 }}>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {formatDate(o.createdAt)}, {lowerFirst(BASIS_LABELS[o.basis])}
                </Typography.Text>
                <OperationStatusTag status={o.status} />
              </Flex>
            </div>
            <Amount value={o.amount} original={o.originalAmount} />
          </Flex>
        </li>
      ))}
    </ul>
  );
}
