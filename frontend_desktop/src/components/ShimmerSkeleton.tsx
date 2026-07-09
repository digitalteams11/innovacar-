
import { cn } from '../lib/utils';

interface ShimmerSkeletonProps {
  className?: string;
  circle?: boolean;
  width?: string;
  height?: string;
}

export function ShimmerSkeleton({ className, circle = false, width, height }: ShimmerSkeletonProps) {
  return (
    <div
      className={cn(
        'shimmer',
        circle ? 'rounded-full' : 'rounded-xl',
        className
      )}
      style={{ width, height }}
    />
  );
}

interface ShimmerCardProps {
  lines?: number;
  hasImage?: boolean;
  className?: string;
}

export function ShimmerCard({ lines = 3, hasImage = false, className }: ShimmerCardProps) {
  return (
    <div className={cn('glass-card p-5 space-y-4', className)}>
      {hasImage && (
        <ShimmerSkeleton className="w-full h-40 rounded-xl" />
      )}
      <div className="flex items-center gap-3">
        <ShimmerSkeleton circle width="40px" height="40px" />
        <div className="flex-1 space-y-2">
          <ShimmerSkeleton className="h-4 w-3/4" />
          <ShimmerSkeleton className="h-3 w-1/2" />
        </div>
      </div>
      {Array.from({ length: lines }).map((_, i) => (
        <ShimmerSkeleton key={i} className={cn('h-3', i === lines - 1 ? 'w-2/3' : 'w-full')} />
      ))}
    </div>
  );
}

export function ShimmerTable({ rows = 5, cols = 4 }: { rows?: number; cols?: number }) {
  return (
    <div className="space-y-3">
      {/* Header */}
      <div className="flex gap-4 px-4">
        {Array.from({ length: cols }).map((_, i) => (
          <ShimmerSkeleton key={`h-${i}`} className="h-4 flex-1" />
        ))}
      </div>
      {/* Rows */}
      {Array.from({ length: rows }).map((_, rowIdx) => (
        <div key={rowIdx} className="flex gap-4 px-4 py-3">
          {Array.from({ length: cols }).map((_, colIdx) => (
            <ShimmerSkeleton 
              key={`${rowIdx}-${colIdx}`} 
              className="h-3 flex-1" 
            />
          ))}
        </div>
      ))}
    </div>
  );
}
