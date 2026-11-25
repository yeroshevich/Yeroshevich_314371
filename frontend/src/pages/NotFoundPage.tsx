import { Button, Result } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { HOME_BY_ROLE } from '../routes';

export default function NotFoundPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  return (
    <Result
      status="404"
      title="Такой страницы нет"
      subTitle="Возможно, ссылка устарела. Разделы, доступные вам, — в меню слева."
      extra={
        <Button type="primary" onClick={() => navigate(user ? HOME_BY_ROLE[user.role] : '/login')}>
          На главную
        </Button>
      }
    />
  );
}
