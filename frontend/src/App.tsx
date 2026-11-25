import { App as AntApp, ConfigProvider } from 'antd';
import ruRU from 'antd/locale/ru_RU';
import { lazy, Suspense, useMemo, useSyncExternalStore } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth/AuthContext';
import { RequireAuth, RequireRole } from './auth/RequireAuth';
import { PageSkeleton } from './components/PageSkeleton';
import AppLayout from './layout/AppLayout';
import LoginPage from './pages/LoginPage';
import NotFoundPage from './pages/NotFoundPage';
import { appRoutes, HOME_BY_ROLE } from './routes';
import { antdTheme } from './theme/antdTheme';

const DesignSystemPage = lazy(() => import('./pages/DesignSystemPage'));

const REDUCED_MOTION = '(prefers-reduced-motion: reduce)';
const subscribeMotion = (cb: () => void) => {
  const mq = window.matchMedia(REDUCED_MOTION);
  mq.addEventListener('change', cb);
  return () => mq.removeEventListener('change', cb);
};

/** Пользователь попросил систему уменьшить движение — выключаем анимации Ant Design штатным токеном. */
function usePrefersReducedMotion(): boolean {
  return useSyncExternalStore(subscribeMotion, () => window.matchMedia(REDUCED_MOTION).matches);
}

/** «/» ведёт на стартовый экран роли. */
function HomeRedirect() {
  const { user } = useAuth();
  return <Navigate to={user ? HOME_BY_ROLE[user.role] : '/login'} replace />;
}

export default function App() {
  const reduceMotion = usePrefersReducedMotion();
  const theme = useMemo(
    () => (reduceMotion ? { ...antdTheme, token: { ...antdTheme.token, motion: false } } : antdTheme),
    [reduceMotion],
  );
  return (
    <ConfigProvider theme={theme} locale={ruRU}>
      <AntApp>
        <AuthProvider>
          <BrowserRouter>
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route
                path="/design-system"
                element={
                  <Suspense fallback={<PageSkeleton />}>
                    <DesignSystemPage />
                  </Suspense>
                }
              />
              <Route
                element={
                  <RequireAuth>
                    <AppLayout />
                  </RequireAuth>
                }
              >
                <Route index element={<HomeRedirect />} />
                {appRoutes.map(({ path, roles, page: Page }) => (
                  <Route
                    key={path}
                    path={path}
                    element={
                      <RequireRole roles={roles}>
                        <Page />
                      </RequireRole>
                    }
                  />
                ))}
                <Route path="*" element={<NotFoundPage />} />
              </Route>
            </Routes>
          </BrowserRouter>
        </AuthProvider>
      </AntApp>
    </ConfigProvider>
  );
}
