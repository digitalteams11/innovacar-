import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import api from '../api/axios';
import { useAuth } from '../context/AuthContext';
import type { GlobalSearchResponse, GlobalSearchResult } from '../types/globalSearch';

const RECENT_LIMIT = 6;

function recentKey(userId?: number | string) {
  return `rentcar_recent_search_${userId || 'anonymous'}`;
}

function readRecent(userId?: number | string): GlobalSearchResult[] {
  try {
    const raw = localStorage.getItem(recentKey(userId));
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed.slice(0, RECENT_LIMIT) : [];
  } catch {
    return [];
  }
}

function writeRecent(userId: number | string | undefined, results: GlobalSearchResult[]) {
  localStorage.setItem(recentKey(userId), JSON.stringify(results.slice(0, RECENT_LIMIT)));
}

export function useGlobalSearch(query: string, open: boolean) {
  const { user } = useAuth();
  const [results, setResults] = useState<GlobalSearchResult[]>([]);
  const [recentResults, setRecentResults] = useState<GlobalSearchResult[]>(() => readRecent(user?.id));
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const trimmedQuery = query.trim();

  useEffect(() => {
    setRecentResults(readRecent(user?.id));
  }, [user?.id]);

  useEffect(() => {
    if (!open) {
      abortRef.current?.abort();
      setLoading(false);
      return;
    }

    if (trimmedQuery.length < 2) {
      abortRef.current?.abort();
      setResults([]);
      setError(null);
      setLoading(false);
      return;
    }

    const timer = window.setTimeout(async () => {
      abortRef.current?.abort();
      const controller = new AbortController();
      abortRef.current = controller;
      setLoading(true);
      setError(null);
      try {
        const response = await api.get<{ success: boolean; data: GlobalSearchResponse }>('/search/global', {
          params: { q: trimmedQuery, limit: 30 },
          signal: controller.signal,
        });
        setResults(response.data?.data?.results || []);
      } catch (err: any) {
        if (err?.name === 'CanceledError' || err?.code === 'ERR_CANCELED') return;
        const status = err?.response?.status;
        if (status === 401) setError('Session expired. Please login again.');
        else if (status === 403) setError('You do not have permission to search this data.');
        else setError(err?.userMessage || 'Search is temporarily unavailable.');
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }, 250);

    return () => window.clearTimeout(timer);
  }, [open, trimmedQuery]);

  const rememberResult = useCallback((result: GlobalSearchResult) => {
    if (result.type === 'ACTION') return;
    const safeResult = {
      id: result.id,
      type: result.type,
      entityId: result.entityId,
      title: result.title,
      route: result.route,
      icon: result.icon,
    } as GlobalSearchResult;
    setRecentResults((current) => {
      const next = [safeResult, ...current.filter((item) => item.id !== safeResult.id)].slice(0, RECENT_LIMIT);
      writeRecent(user?.id, next);
      return next;
    });
  }, [user?.id]);

  return useMemo(() => ({
    results,
    recentResults,
    loading,
    error,
    rememberResult,
  }), [results, recentResults, loading, error, rememberResult]);
}
