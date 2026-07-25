import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import '../../i18n';
import SplashScreen from '../SplashScreen';

// Regression: SplashScreen used to hardcode bg-[#0b1437] (navy) regardless
// of the resolved theme, which is itself a theme-mismatch flash for a
// light-mode user (see the startup-flash fix). It must resolve its
// background/text from the same CSS custom properties the rest of the app's
// theme system sets, never a fixed color.
describe('SplashScreen', () => {
  it('uses theme CSS custom properties instead of a hardcoded color, and fully covers the viewport', () => {
    render(<SplashScreen />);
    const shell = screen.getByRole('status');
    expect(shell).toHaveStyle({ background: 'var(--bg-page)' });
    expect(shell.className).toContain('fixed');
    expect(shell.className).toContain('inset-0');
  });
});
