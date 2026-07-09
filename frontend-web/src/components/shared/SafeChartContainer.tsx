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
    let frame = 0;

    const check = () => {
      window.cancelAnimationFrame(frame);
      frame = window.requestAnimationFrame(() => {
        const rect = el.getBoundingClientRect();
        const width = Math.floor(rect.width);
        const height = Math.floor(rect.height);
        setReady(width > 1 && height > 1);
      });
    };

    check();
    // Small delay to let CSS layout settle (flex/grid parents)
    const timer = setTimeout(check, 50);

    const ro = new ResizeObserver(check);
    ro.observe(el);

    return () => {
      window.cancelAnimationFrame(frame);
      clearTimeout(timer);
      ro.disconnect();
    };
  }, []);

  return (
    <div
      ref={ref}
      className={`w-full ${className}`}
      style={{ minWidth: 1, minHeight, height: style?.height ?? minHeight, ...style }}
    >
      {ready ? children : null}
    </div>
  );
}
