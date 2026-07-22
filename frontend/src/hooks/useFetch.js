import { useCallback, useEffect, useRef, useState } from "react";

/**
 * Fetch-on-mount with optional polling.
 *
 * Polling matters for this app: because the backend is event-driven, reminders
 * and analytics update asynchronously *after* an application changes. Polling is
 * what makes that visible in the UI — you create an application and watch the
 * reminder appear a moment later, which is the whole point of the demo.
 *
 * @param {Function} fetcher  async () => data
 * @param {object}   options  { intervalMs = 0, enabled = true, deps = [] }
 */
export function useFetch(fetcher, { intervalMs = 0, enabled = true, deps = [] } = {}) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const mounted = useRef(true);
  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;

  const load = useCallback(
    async ({ quiet = false } = {}) => {
      if (!quiet) setLoading(true);
      try {
        const result = await fetcherRef.current();
        if (!mounted.current) return;
        setData(result);
        setError(null);
      } catch (err) {
        if (!mounted.current) return;
        setError(err);
      } finally {
        if (mounted.current) setLoading(false);
      }
    },
    []
  );

  useEffect(() => {
    mounted.current = true;
    if (!enabled) {
      setLoading(false);
      return () => {
        mounted.current = false;
      };
    }

    load();

    let timer;
    if (intervalMs > 0) {
      // Quiet refreshes so the UI doesn't flash a spinner on every poll.
      timer = setInterval(() => load({ quiet: true }), intervalMs);
    }

    return () => {
      mounted.current = false;
      if (timer) clearInterval(timer);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled, intervalMs, load, ...deps]);

  return { data, error, loading, reload: load };
}
