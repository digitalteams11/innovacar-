import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import BrandLockup from '../BrandLockup';

describe('BrandLockup', () => {
  it('renders the complete subtitle text — regression: this used to clip to "BY INNOVAX TECHNOL"', () => {
    render(<BrandLockup size="md" />);
    const company = screen.getByText(/INNOVAX/).closest('.brand-lockup__company');
    expect(company?.textContent).toContain('BY');
    expect(company?.textContent).toContain('INNOVAX');
    expect(company?.textContent?.replace(/\s/g, '')).toContain('TECHNOLOGIES');
    // The exact regression: an element whose own text is the partial word "TECHNOL" must never exist.
    expect(screen.queryByText(/^TECHNOL$/)).not.toBeInTheDocument();
  });

  it('never uses a clipping utility class (truncate / overflow-hidden / line-clamp) anywhere in the lockup', () => {
    const { container } = render(<BrandLockup size="md" />);
    const html = container.innerHTML;
    expect(html).not.toMatch(/\btruncate\b/);
    expect(html).not.toMatch(/\boverflow-hidden\b/);
    expect(html).not.toMatch(/\bline-clamp/);
  });

  it('splits the wordmark and subtitle into separate colored spans, not one text node', () => {
    render(<BrandLockup size="lg" />);
    expect(screen.getByText('Innova')).toBeInTheDocument();
    expect(screen.getByText('Car')).toBeInTheDocument();
    expect(screen.getByText('INNOVAX')).toBeInTheDocument();
    expect(screen.getByText('TECHNOLOGIES')).toBeInTheDocument();
  });

  it('forces white text for both segments in the dark variant', () => {
    render(<BrandLockup variant="dark" size="md" />);
    expect(screen.getByText('Innova')).toHaveStyle({ color: '#ffffff' });
  });

  it('renders the accessible label regardless of visible sub-parts', () => {
    render(<BrandLockup compact showSubtitle={false} />);
    expect(screen.getByLabelText('Innovacar by Innovax Technologies')).toBeInTheDocument();
    // compact mode still doesn't show the subtitle text, by design
    expect(screen.queryByText('TECHNOLOGIES')).not.toBeInTheDocument();
  });

  it('always forces dir="ltr" so the brand order survives in an RTL (Arabic) document', () => {
    render(<BrandLockup size="md" />);
    expect(screen.getByLabelText('Innovacar by Innovax Technologies')).toHaveAttribute('dir', 'ltr');
  });

  it('horizontal orientation keeps both name and subtitle in the accessible label', () => {
    render(<BrandLockup orientation="horizontal" size="sm" showSubtitle />);
    const root = screen.getByLabelText('Innovacar by Innovax Technologies');
    expect(root).toHaveAttribute('dir', 'ltr');
    expect(root.textContent?.replace(/\s/g, '')).toContain('TECHNOLOGIES');
  });
});
