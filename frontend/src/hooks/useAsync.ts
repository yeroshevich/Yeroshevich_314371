import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../api';

export interface AsyncState<T> {
  data: T | undefined;
  loading: boolean;
  error: ApiError | Error | undefined;
  reload: () => void;
  setData: (updater: T | ((prev: T | undefined) => T)) => void;
}

/**
 * Загрузка данных из API с состояниями «загрузка / ошибка / данные» (UI-4, FR1-2).
 * `deps` перезапускают запрос; устаревшие ответы отбрасываются.
 */
export function useAsync<T>(load: () => Promise<T>, deps: unknown[] = []): AsyncState<T> {
  const [data, setDataState] = useState<T>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | Error>();
  const [tick, setTick] = useState(0);
  const callId = useRef(0);

  useEffect(() => {
    const id = ++callId.current;
    setLoading(true);
    setError(undefined);
    load()
      .then((result) => id === callId.current && setDataState(result))
      .catch((e: Error) => id === callId.current && setError(e))
      .finally(() => id === callId.current && setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);

  const reload = useCallback(() => setTick((t) => t + 1), []);
  const setData = useCallback((updater: T | ((prev: T | undefined) => T)) => {
    setDataState((prev) => (typeof updater === 'function' ? (updater as (p: T | undefined) => T)(prev) : updater));
  }, []);

  return { data, loading, error, reload, setData };
}
