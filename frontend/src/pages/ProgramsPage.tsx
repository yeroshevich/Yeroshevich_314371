import {
  CoffeeOutlined,
  GiftOutlined,
  HeartOutlined,
  ReadOutlined,
  SkinOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { Alert, App, Button, Card, Col, Flex, Row, Segmented, Skeleton, Typography } from 'antd';
import { useState, type ReactNode } from 'react';
import { api, ApiError, PROGRAM_CATEGORY_LABELS, type Program, type ProgramCategory } from '../api';
import { ErrorState } from '../components/ErrorState';
import { ToneTag } from '../components/OperationStatusTag';
import { PageHeader } from '../components/PageHeader';
import { PointsMark } from '../components/PointsMark';
import { useAsync } from '../hooks/useAsync';
import { colors, shadowCard } from '../theme/tokens';
import { formatPoints } from '../utils/format';

const CATEGORY_ICONS: Record<ProgramCategory, ReactNode> = {
  gift: <GiftOutlined />,
  education: <ReadOutlined />,
  merch: <SkinOutlined />,
  dayoff: <CoffeeOutlined />,
  health: <HeartOutlined />,
  event: <TeamOutlined />,
};

type CategoryFilter = ProgramCategory | 'all';

/** Каталог программ мотивации и списание баллов (UC3, FR3-1…3). */
export default function ProgramsPage() {
  const { modal, message } = App.useApp();
  const programs = useAsync(() => api.getPrograms(), []);
  const account = useAsync(() => api.getMyAccount(), []);
  const [category, setCategory] = useState<CategoryFilter>('all');
  const [redeeming, setRedeeming] = useState<string | null>(null);

  const blocked = account.error instanceof ApiError && account.error.code === 'ACCOUNT_BLOCKED';
  const balance = account.data?.account.balance;

  const categories = Array.from(new Set(programs.data?.map((p) => p.category) ?? []));
  const visible = (programs.data ?? [])
    .filter((p) => category === 'all' || p.category === category)
    .sort((a, b) => Number(b.active) - Number(a.active) || a.cost - b.cost);

  const redeem = async (p: Program) => {
    setRedeeming(p.id);
    try {
      const result = await api.redeemProgram(p.id);
      account.setData((prev) => ({ ...prev!, account: result.account }));
      message.success(`Списано ${formatPoints(p.cost)}. Подтверждение участия придёт на почту.`);
    } catch (e) {
      message.error((e as Error).message);
      account.reload();
    } finally {
      setRedeeming(null);
    }
  };

  const confirm = (p: Program) => {
    const rest = (balance ?? 0) - p.cost;
    modal.confirm({
      title: `Потратить ${formatPoints(p.cost)} на «${p.name}»?`,
      content: (
        <>
          <p style={{ margin: '8px 0' }}>{p.description}</p>
          <p style={{ margin: 0 }}>
            После списания на счёте останется <b className="tsb-num">{formatPoints(rest)}</b>. Вернуть баллы может только HR.
          </p>
        </>
      ),
      okText: `Списать ${formatPoints(p.cost)}`,
      cancelText: 'Не сейчас',
      onOk: () => redeem(p),
    });
  };

  return (
    <>
      <PageHeader
        title="Программы мотивации"
        description="Обменяйте баллы на обучение, отдых или подарок. Баллы не конвертируются в зарплату."
        extra={
          balance !== undefined && (
            <Flex
              align="center"
              gap={8}
              style={{ background: colors.siderBg, color: colors.colorBgContainer, padding: '8px 16px', borderRadius: 8 }}
            >
              <PointsMark size={20} onDark />
              <span>На счёте</span>
              <b className="tsb-num" style={{ color: colors.colorBonus, fontSize: 18 }}>{formatPoints(balance)}</b>
            </Flex>
          )
        }
      />

      {blocked && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message="Баланс недоступен, обратитесь к HR"
          description="Пока счёт заблокирован, программы недоступны."
        />
      )}
      {programs.error && <ErrorState error={programs.error} onRetry={programs.reload} />}

      {categories.length > 0 && (
        <div style={{ overflowX: 'auto', marginBottom: 16 }}>
          <Segmented<CategoryFilter>
            value={category}
            onChange={setCategory}
            aria-label="Категория"
            options={[{ label: 'Все', value: 'all' }, ...categories.map((c) => ({ label: PROGRAM_CATEGORY_LABELS[c], value: c }))]}
          />
        </div>
      )}

      <Row gutter={[24, 24]}>
        {!programs.data
          ? Array.from({ length: 4 }, (_, i) => (
              <Col key={i} xs={24} sm={12} xl={6}>
                <Card><Skeleton active /></Card>
              </Col>
            ))
          : visible.map((p) => {
              const shortBy = balance !== undefined ? p.cost - balance : 0;
              const affordable = balance !== undefined && shortBy <= 0;
              const canUse = p.active && affordable && !blocked;
              return (
                <Col key={p.id} xs={24} sm={12} xl={6}>
                  <Card
                    style={{ height: '100%', boxShadow: shadowCard, opacity: p.active ? 1 : 0.85 }}
                    styles={{ body: { height: '100%', display: 'flex', flexDirection: 'column', gap: 12 } }}
                  >
                    <Flex justify="space-between" align="center">
                      <Typography.Text type="secondary">
                        <span aria-hidden style={{ marginRight: 6 }}>{CATEGORY_ICONS[p.category]}</span>
                        {PROGRAM_CATEGORY_LABELS[p.category]}
                      </Typography.Text>
                      {!p.active && <ToneTag tone="default">Недоступна</ToneTag>}
                    </Flex>
                    <Typography.Title level={3} style={{ margin: 0 }}>{p.name}</Typography.Title>
                    <Typography.Paragraph type="secondary" style={{ margin: 0, flex: 1 }}>
                      {p.active ? p.description : p.inactiveReason}
                    </Typography.Paragraph>
                    <Flex align="center" gap={8}>
                      <PointsMark size={20} />
                      <span className="tsb-num" style={{ fontSize: 18, fontWeight: 600 }}>{formatPoints(p.cost)}</span>
                    </Flex>
                    <div>
                      <Button
                        type={canUse ? 'primary' : 'default'}
                        block
                        disabled={!canUse}
                        loading={redeeming === p.id}
                        onClick={() => confirm(p)}
                      >
                        {p.active ? 'Использовать' : 'Недоступна'}
                      </Button>
                      {p.active && !affordable && balance !== undefined && (
                        <Typography.Text type="secondary" style={{ display: 'block', fontSize: 12, marginTop: 6 }}>
                          Не хватает {formatPoints(shortBy)}
                        </Typography.Text>
                      )}
                    </div>
                  </Card>
                </Col>
              );
            })}
      </Row>
    </>
  );
}
