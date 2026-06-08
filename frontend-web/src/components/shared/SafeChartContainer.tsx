import { useRef, useState, useEffect, type ReactNode } from 'react';

interface SafeChartContainerProps {
  children: ReactNode;
  className?: string;
  style?: React.CSSProperties;
  minHeight?: number;
}

/**
 * Wrapper that prevents Recharts from rendering until the container
 * has positive dimensions. Eliminates console spam:
 * "The width(-1) and height(-1) of chart should be greater than 0"
 */
export default function SafeChartContainer({
  children,
  className = '',
  style,
  minHeight = 200,
}: SafeChartContainerProps) {
  const ref = useRef<HTMLDivElement>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;

    const check = () => {
      const rect = el.getBoundingClientRect();
      if (rect.width > 0 && rect.height > 0) {
        setReady(true);
      }
    };

    check();
    // Small delay to let CSS layout settle (flex/grid parents)
    const timer = setTimeout(check, 50);

    const ro = new ResizeObserver(check);
    ro.observe(el);

    return () => {
      clearTimeout(timer);
      ro.disconnect();
    };
  }, []);

  return (
    <div
      ref={ref}
      className={`w-full ${className}`}
      style={{ ...style, minHeight: style?.height ?? minHeight }}
    >
      {ready ? children : null}
    </div>
  );
}
