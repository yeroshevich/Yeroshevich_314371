import { Flex, Typography } from 'antd';
import type { ReactNode } from 'react';

interface PageHeaderProps {
  title: string;
  description?: ReactNode;
  /** Действия страницы справа от заголовка (на телефоне переносятся вниз). */
  extra?: ReactNode;
}

/** Заголовок экрана: H1 28/36 и короткое пояснение, что здесь делают. */
export function PageHeader({ title, description, extra }: PageHeaderProps) {
  return (
    <Flex justify="space-between" align="flex-end" gap={16} wrap="wrap" style={{ marginBottom: 24 }}>
      <div style={{ minWidth: 0, maxWidth: 720 }}>
        <Typography.Title level={1} style={{ margin: 0 }}>
          {title}
        </Typography.Title>
        {description && (
          <Typography.Paragraph type="secondary" style={{ margin: '4px 0 0' }}>
            {description}
          </Typography.Paragraph>
        )}
      </div>
      {extra && (
        <Flex gap={8} wrap="wrap" className="tsb-no-print">
          {extra}
        </Flex>
      )}
    </Flex>
  );
}
