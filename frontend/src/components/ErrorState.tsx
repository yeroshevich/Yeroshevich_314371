import { ReloadOutlined } from '@ant-design/icons';
import { Alert, Button } from 'antd';

/** Ошибка загрузки (FR1-2): что случилось и что сделать. */
export function ErrorState({ error, onRetry }: { error: Error; onRetry: () => void }) {
  return (
    <Alert
      type="error"
      showIcon
      message="Данные не загрузились"
      description={error.message || 'Сервер не ответил. Проверьте подключение и повторите.'}
      action={
        <Button icon={<ReloadOutlined />} onClick={onRetry}>
          Повторить
        </Button>
      }
    />
  );
}
