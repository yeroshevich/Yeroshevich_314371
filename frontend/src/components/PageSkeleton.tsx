import { Card, Skeleton } from 'antd';

/** Заглушка на время загрузки экрана (Skeleton из системы дизайна). */
export function PageSkeleton() {
  return (
    <div style={{ padding: 24 }} aria-busy="true" aria-label="Загрузка">
      <Skeleton.Input active style={{ width: 280, height: 36, marginBottom: 24 }} />
      <Card>
        <Skeleton active paragraph={{ rows: 6 }} />
      </Card>
    </div>
  );
}
