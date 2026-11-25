import { ReloadOutlined } from '@ant-design/icons';
import { Button, Flex, Progress } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { BonusAccount, Program } from '../api';
import { colors, typeScale } from '../theme/tokens';
import { formatDateTime, formatPoints } from '../utils/format';
import { PointsMark } from './PointsMark';

interface BalanceCardProps {
  account: BonusAccount;
  /** Ближайшая программа, на которую пока не хватает баллов. */
  nextProgram?: Program;
  /** Программы, на которые баллов уже хватает (до трёх самых дорогих). */
  affordable?: Program[];
  onRefresh?: () => void;
  refreshing?: boolean;
}

const ON_DARK_MUTED = 'rgba(255,255,255,.72)';

/** Плавный «добег» числа баланса при загрузке — единственная анимация экрана. */
function useCountUp(target: number, durationMs = 700): number {
  const [value, setValue] = useState(target);
  const from = useRef(0);
  useEffect(() => {
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduce) {
      setValue(target);
      from.current = target;
      return;
    }
    const start = performance.now();
    const origin = from.current;
    let raf = 0;
    const step = (now: number) => {
      const t = Math.min(1, (now - start) / durationMs);
      const eased = 1 - Math.pow(1 - t, 3);
      setValue(Math.round(origin + (target - origin) * eased));
      if (t < 1) raf = requestAnimationFrame(step);
      else from.current = target;
    };
    raf = requestAnimationFrame(step);
    return () => cancelAnimationFrame(raf);
  }, [target, durationMs]);
  return value;
}

/**
 * Карточка баланса — главный элемент кабинета сотрудника (UC1).
 * Тёмная поверхность siderBg нужна не для эффекта: цифра цвета colorBonus
 * на белом не проходит контраст, а на тёмно-синем — 8:1.
 */
export function BalanceCard({ account, nextProgram, affordable = [], onRefresh, refreshing }: BalanceCardProps) {
  const navigate = useNavigate();
  const shown = useCountUp(account.balance);
  const left = nextProgram ? nextProgram.cost - account.balance : 0;
  const percent = nextProgram ? Math.min(100, Math.round((account.balance / nextProgram.cost) * 100)) : 100;

  return (
    <section
      aria-labelledby="balance-label"
      style={{
        background: colors.siderBg,
        color: colors.colorBgContainer,
        borderRadius: 12,
        padding: '24px 24px 20px',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        gap: 16,
      }}
    >
      <Flex justify="space-between" align="center" gap={8}>
        <span id="balance-label" style={{ color: ON_DARK_MUTED }}>
          Доступно на счёте
        </span>
        {onRefresh && (
          <Button
            type="text"
            size="small"
            icon={<ReloadOutlined spin={refreshing} />}
            onClick={onRefresh}
            style={{ color: ON_DARK_MUTED }}
          >
            Обновить
          </Button>
        )}
      </Flex>

      <Flex align="center" gap={12}>
        <PointsMark size={36} onDark />
        <span
          className="tsb-num"
          aria-live="polite"
          aria-label={formatPoints(account.balance)}
          style={{
            fontSize: typeScale.display.fontSize,
            lineHeight: `${typeScale.display.lineHeight}px`,
            fontWeight: typeScale.display.fontWeight,
            color: colors.colorBonus,
            letterSpacing: '-0.02em',
          }}
        >
          {formatPoints(shown)}
        </span>
      </Flex>

      <Flex wrap="wrap" style={{ color: ON_DARK_MUTED, fontSize: 12, lineHeight: '20px', gap: '4px 24px' }}>
        <span>
          На согласовании{' '}
          <b className="tsb-num" style={{ color: colors.colorBgContainer, fontWeight: 600 }}>
            {formatPoints(account.pending)}
          </b>
        </span>
        <span>Обновлено {formatDateTime(account.updatedAt)}</span>
      </Flex>

      {affordable.length > 0 && (
        <div>
          <div style={{ color: ON_DARK_MUTED, marginBottom: 4 }}>Уже хватает на</div>
          <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
            {affordable.map((p) => (
              <li key={p.id}>
                <Flex justify="space-between" gap={16} style={{ padding: '6px 0', borderBottom: '1px solid rgba(255,255,255,.08)' }}>
                  <span style={{ minWidth: 0 }}>{p.name}</span>
                  <span className="tsb-num" style={{ color: ON_DARK_MUTED }}>{formatPoints(p.cost)}</span>
                </Flex>
              </li>
            ))}
          </ul>
        </div>
      )}

      <div style={{ marginTop: 'auto', paddingTop: 16, borderTop: '1px solid rgba(255,255,255,.12)' }}>
        {nextProgram ? (
          <>
            <div style={{ marginBottom: 4 }}>
              До программы «{nextProgram.name}» осталось{' '}
              <b className="tsb-num" style={{ color: colors.colorBonus }}>{formatPoints(left)}</b>
            </div>
            <Progress
              percent={percent}
              showInfo={false}
              strokeColor={colors.colorBonus}
              trailColor="rgba(255,255,255,.16)"
              aria-label={`Накоплено ${percent}% стоимости программы`}
            />
          </>
        ) : (
          <div style={{ marginBottom: 8 }}>Баллов хватает на любую программу каталога.</div>
        )}
        <Button type="primary" style={{ marginTop: 8 }} onClick={() => navigate('/programs')}>
          Потратить баллы
        </Button>
      </div>
    </section>
  );
}
