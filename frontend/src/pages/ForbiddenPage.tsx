import { Button, Result } from 'antd';
import { useNavigate } from 'react-router-dom';
import { ROLE_LABELS } from '../api';
import { useAuth } from '../auth/AuthContext';
import { HOME_BY_ROLE } from '../routes';

/** 403: экран есть, но роли пользователя он не положен (SEC-3). */
export default function ForbiddenPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  return (
    <Result
      status="403"
      title="Нет доступа к этому разделу"
      subTitle={
        user
          ? `Роль «${ROLE_LABELS[user.role]}» не открывает этот экран. Если доступ нужен для работы, напишите администратору системы.`
          : 'Войдите, чтобы продолжить.'
      }
      extra={
        <Button type="primary" onClick={() => navigate(user ? HOME_BY_ROLE[user.role] : '/login')}>
          На главную
        </Button>
      }
    />
  );
}
