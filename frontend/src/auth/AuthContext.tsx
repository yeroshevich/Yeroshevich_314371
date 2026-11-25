import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, SESSION_TOKEN_KEY, type Role, type User } from '../api';

const STORAGE_KEY = SESSION_TOKEN_KEY;

interface AuthContextValue {
  user: User | null;
  /** Идёт восстановление сессии из sessionStorage. */
  restoring: boolean;
  login: (email: string, password: string) => Promise<User>;
  logout: () => Promise<void>;
  hasRole: (...roles: Role[]) => boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * Аутентификация (SEC-1): вход по email и паролю, токен в sessionStorage,
 * восстановление сессии после перезагрузки вкладки.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [restoring, setRestoring] = useState(() => Boolean(sessionStorage.getItem(STORAGE_KEY)));

  useEffect(() => {
    const token = sessionStorage.getItem(STORAGE_KEY);
    if (!token) return;
    api
      .restoreSession(token)
      .then((s) => setUser(s.user))
      .catch(() => sessionStorage.removeItem(STORAGE_KEY))
      .finally(() => setRestoring(false));
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const session = await api.login(email, password);
    sessionStorage.setItem(STORAGE_KEY, session.token);
    setUser(session.user);
    return session.user;
  }, []);

  const logout = useCallback(async () => {
    await api.logout();
    sessionStorage.removeItem(STORAGE_KEY);
    setUser(null);
  }, []);

  const hasRole = useCallback((...roles: Role[]) => Boolean(user && roles.includes(user.role)), [user]);

  const value = useMemo(() => ({ user, restoring, login, logout, hasRole }), [user, restoring, login, logout, hasRole]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth вызывается вне <AuthProvider>');
  return ctx;
}

/** Текущий пользователь внутри защищённых маршрутов (гарантированно не null). */
export function useCurrentUser(): User {
  const { user } = useAuth();
  if (!user) throw new Error('useCurrentUser вызывается вне защищённого маршрута');
  return user;
}

export { STORAGE_KEY as SESSION_STORAGE_KEY };
