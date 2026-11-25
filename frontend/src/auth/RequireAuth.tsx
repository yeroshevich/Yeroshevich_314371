import { Spin } from 'antd';
import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import type { Role } from '../api';
import ForbiddenPage from '../pages/ForbiddenPage';
import { useAuth } from './AuthContext';

/** Пускает только вошедших пользователей; остальных отправляет на /login (SEC-1). */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { user, restoring } = useAuth();
  const location = useLocation();
  if (restoring) {
    return (
      <div className="tsb-fullscreen-center">
        <Spin size="large" tip="Восстанавливаем сессию">
          <div style={{ width: 160, height: 80 }} />
        </Spin>
      </div>
    );
  }
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <>{children}</>;
}

/** Ролевая проверка маршрута (SEC-3): чужой маршрут — экран 403, а не тихий редирект. */
export function RequireRole({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { user } = useAuth();
  if (!user || !roles.includes(user.role)) return <ForbiddenPage />;
  return <>{children}</>;
}
