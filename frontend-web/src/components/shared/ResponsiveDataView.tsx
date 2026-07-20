interface ResponsiveDataViewProps {
  desktop: React.ReactNode;
  mobile: React.ReactNode;
}

/**
 * Renders a desktop table below `lg` and a mobile card list above it, using
 * CSS visibility (not a JS width check) so there's no layout thrash on
 * resize and no duplicated business logic — both views receive the exact
 * same data from the caller, they only differ in markup/structure.
 *
 * Both trees mount (one hidden via Tailwind's `lg:hidden` / `hidden lg:*`),
 * which is intentional: for the paginated record lists this is used for
 * (contracts, payments, ...) the extra DOM cost is negligible, and it avoids
 * a resize listener plus the flash-of-wrong-view a JS breakpoint check would
 * cause on first paint.
 */
export default function ResponsiveDataView({ desktop, mobile }: ResponsiveDataViewProps) {
  return (
    <>
      <div className="hidden lg:block">{desktop}</div>
      <div className="lg:hidden">{mobile}</div>
    </>
  );
}
