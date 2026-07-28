import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, fireEvent } from '@testing-library/react';
import type { MouseEvent } from 'react';
import { MARKETING_PAGES, handOffToApp } from '../pages';

// Regression coverage for the "URL changes but the landing page never does"
// production bug: MarketingApp (see ../MarketingApp.tsx) is mounted once by
// main.tsx with NO router of its own — a plain `<a href="/#/login">` click
// only changes the URL's fragment, which browsers never reload the page
// for, so nothing would ever re-render. handOffToApp must intercept a plain
// left-click and force a real reload so main.tsx's bootstrap logic re-runs
// with the new hash present and mounts the real HashRouter app instead.

function makeClickEvent(overrides: Partial<{
  button: number; metaKey: boolean; ctrlKey: boolean; shiftKey: boolean; altKey: boolean; href: string;
}> = {}) {
  const href = overrides.href ?? '/#/login';
  const anchor = document.createElement('a');
  anchor.setAttribute('href', href);
  const preventDefault = vi.fn();
  return {
    defaultPrevented: false,
    button: overrides.button ?? 0,
    metaKey: overrides.metaKey ?? false,
    ctrlKey: overrides.ctrlKey ?? false,
    shiftKey: overrides.shiftKey ?? false,
    altKey: overrides.altKey ?? false,
    currentTarget: anchor,
    preventDefault,
  } as unknown as MouseEvent<HTMLAnchorElement>;
}

/** Replaces window.location with a plain spy object for the duration of a test — jsdom
 *  doesn't implement real navigation for `location.href = ...` (it logs "Not implemented"
 *  and can throw in some setups), and vi.stubGlobal sidesteps assigning to the readonly,
 *  oddly-typed `window.location` property directly. */
function stubLocation() {
  const hrefSetter = vi.fn();
  const reloadSpy = vi.fn();
  const original = window.location;
  vi.stubGlobal('location', {
    ...original,
    reload: reloadSpy,
    set href(value: string) { hrefSetter(value); },
    get href() { return original.href; },
  });
  return { hrefSetter, reloadSpy };
}

describe('handOffToApp', () => {
  let hrefSetter: ReturnType<typeof vi.fn>;
  let reloadSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    ({ hrefSetter, reloadSpy } = stubLocation());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('a plain left-click forces a reload after setting the href', () => {
    const event = makeClickEvent({ href: '/#/register?plan=pro' });
    handOffToApp(event);

    expect(event.preventDefault).toHaveBeenCalledTimes(1);
    expect(hrefSetter).toHaveBeenCalledWith('/#/register?plan=pro');
    expect(reloadSpy).toHaveBeenCalledTimes(1);
  });

  it('leaves ctrl/cmd/shift/middle-click alone so "open in new tab" still works natively', () => {
    for (const overrides of [{ ctrlKey: true }, { metaKey: true }, { shiftKey: true }, { altKey: true }, { button: 1 }]) {
      const event = makeClickEvent(overrides);
      handOffToApp(event);
      expect(event.preventDefault).not.toHaveBeenCalled();
    }
    expect(hrefSetter).not.toHaveBeenCalled();
    expect(reloadSpy).not.toHaveBeenCalled();
  });

  it('does nothing if the event was already handled elsewhere', () => {
    const event = makeClickEvent();
    Object.defineProperty(event, 'defaultPrevented', { value: true });
    handOffToApp(event);
    expect(hrefSetter).not.toHaveBeenCalled();
    expect(reloadSpy).not.toHaveBeenCalled();
  });
});

describe('marketing homepage CTAs', () => {
  let reloadSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    ({ reloadSpy } = stubLocation());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('clicking a "Start free trial" CTA hands off to the app instead of leaving the landing page mounted', () => {
    const HomePage = MARKETING_PAGES['/'].Component;
    const { container } = render(<HomePage />);

    // Query by href rather than translated text — this must hold regardless
    // of which language the page happens to render in by default.
    const trialLinks = container.querySelectorAll('a[href^="/#/register"]');
    expect(trialLinks.length).toBeGreaterThan(0);
    fireEvent.click(trialLinks[0], { button: 0 });

    expect(reloadSpy).toHaveBeenCalled();
  });

  it('clicking a "Log in" CTA hands off to the app instead of leaving the landing page mounted', () => {
    const HomePage = MARKETING_PAGES['/'].Component;
    const { container } = render(<HomePage />);

    const loginLinks = container.querySelectorAll('a[href="/#/login"]');
    expect(loginLinks.length).toBeGreaterThan(0);
    fireEvent.click(loginLinks[0], { button: 0 });

    expect(reloadSpy).toHaveBeenCalled();
  });

  it('clicking a "Contact us" CTA hands off to the app instead of leaving the landing page mounted', () => {
    const HomePage = MARKETING_PAGES['/'].Component;
    const { container } = render(<HomePage />);

    const contactLinks = container.querySelectorAll('a[href="/#/contact"]');
    expect(contactLinks.length).toBeGreaterThan(0);
    fireEvent.click(contactLinks[0], { button: 0 });

    expect(reloadSpy).toHaveBeenCalled();
  });
});
