import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import Modal from '../Modal';
// Vite's `?raw` import gives a .tsx file's text content at transform time —
// used below to check that fixed-position components reference a --z-*
// token instead of a hardcoded number. (A `.css?raw` import would be the
// obvious way to also pull the token *values* straight from index.css, but
// vitest's `css: false` test config stubs .css module imports — including
// ?raw ones — to an empty string, so those values are asserted as literal
// constants below instead; keep them in sync with index.css's :root block
// if that scale ever changes.)
import bottomNavigationSource from '../BottomNavigation.tsx?raw';
import mobileAssistantFabSource from '../shared/MobileAssistantFab.tsx?raw';
import mobileBottomSheetSource from '../MobileBottomSheet.tsx?raw';
import modalSource from '../Modal.tsx?raw';

/**
 * Regression coverage for the shared Modal's sticky-header / scrollable-body
 * / sticky-footer architecture — every modal fixed across the app (see the
 * mobile-responsiveness pass this covers) relies on funneling its action
 * buttons through Modal's `footer` prop instead of leaving them inside
 * `children`. If this component's own structural contract ever regresses,
 * every one of those fixes silently regresses with it, so this is tested
 * once here rather than per-consumer.
 *
 * jsdom doesn't run real layout/overflow, so this can't assert that the body
 * actually scrolls or that the footer stays pinned on screen — it asserts
 * the DOM/class contract the CSS depends on: footer is a sibling of the
 * scrollable body (never a descendant of it, so it's never carried away by
 * the body's scroll), the body carries the classes that make it the only
 * scrollable region, and the footer renders regardless of how much content
 * children contains.
 */
describe('Modal — sticky header/body/footer contract', () => {
  it('renders header, scrollable body, and footer as three separate sibling regions', () => {
    render(
      <Modal isOpen onClose={() => {}} title="Test modal" footer={<button>Save</button>}>
        <p>Body content</p>
      </Modal>
    );

    const dialog = screen.getByRole('dialog');
    // Direct children of the dialog section: header row, body, footer — in
    // that order, each a sibling of the others (not nested inside each other).
    const regions = Array.from(dialog.children);
    expect(regions.length).toBe(3);

    const [header, body, footer] = regions;
    expect(header.textContent).toContain('Test modal');
    expect(body.textContent).toContain('Body content');
    expect(footer.textContent).toContain('Save');

    // The body is the only scrollable region — flex-1 (grows to fill
    // remaining space) + min-h-0 (allows it to actually shrink/scroll inside
    // a flex column, the classic flexbox overflow trap) + overflow-y-auto.
    expect(body.className).toContain('flex-1');
    expect(body.className).toContain('min-h-0');
    expect(body.className).toContain('overflow-y-auto');

    // Header and footer are shrink-0 — never compressed/scrolled away by the
    // body growing, which is exactly the historical bug (Save button pushed
    // below the viewport by a long form).
    expect(header.className).toContain('shrink-0');
    expect(footer.className).toContain('shrink-0');
  });

  it('the footer button is not inside the scrollable body — never carried off-screen by body scroll', () => {
    render(
      <Modal isOpen onClose={() => {}} title="Test modal" footer={<button>Save changes</button>}>
        <p>Body content</p>
      </Modal>
    );

    const saveButton = screen.getByRole('button', { name: 'Save changes' });
    const bodyContent = screen.getByText('Body content');
    // Nearest ancestor with overflow-y-auto (the scrollable body) — the save
    // button must not be inside it.
    const scrollBody = bodyContent.closest('.overflow-y-auto');
    expect(scrollBody).not.toBeNull();
    expect(scrollBody?.contains(saveButton)).toBe(false);
  });

  it('the footer still renders when the body content is very long (simulating a long form on a small screen)', () => {
    const longBody = Array.from({ length: 40 }, (_, i) => <p key={i}>Field {i}</p>);
    render(
      <Modal isOpen onClose={() => {}} title="Long form" footer={<button>Save</button>}>
        {longBody}
      </Modal>
    );
    // A save button that scrolled out of the viewport would still be in the
    // DOM in a broken implementation too — the real fix is structural (see
    // the sibling-region test above), but this at minimum guards against the
    // footer being conditionally dropped for long content.
    expect(screen.getByRole('button', { name: 'Save' })).toBeInTheDocument();
    expect(screen.getByText('Field 39')).toBeInTheDocument();
  });

  it('renders no footer region at all when no footer prop is passed (back-compat for header/body-only modals)', () => {
    render(
      <Modal isOpen onClose={() => {}} title="No footer">
        <p>Body content</p>
      </Modal>
    );
    const dialog = screen.getByRole('dialog');
    expect(dialog.children.length).toBe(2); // header + body only
  });

  it('the dialog never exceeds the viewport height (max-h bounded, not free to grow with content)', () => {
    render(
      <Modal isOpen onClose={() => {}} title="Bounded height" footer={<button>Save</button>}>
        <p>Body content</p>
      </Modal>
    );
    const dialog = screen.getByRole('dialog');
    // h-[100dvh] on mobile, sm:max-h-[calc(100dvh-2rem)] on larger screens —
    // both bound the dialog to the viewport rather than letting it grow with
    // content, which is what makes the body (not the whole dialog) the thing
    // that scrolls.
    expect(dialog.className).toMatch(/h-\[100dvh\]/);
    expect(dialog.className).toMatch(/max-h-\[calc\(100dvh-2rem\)\]/);
  });

  it('renders through a document.body portal, not inline where the component is mounted', () => {
    const { container } = render(
      <div data-testid="mount-point">
        <Modal isOpen onClose={() => {}} title="Portal check" footer={<button>Save</button>}>
          <p>Body content</p>
        </Modal>
      </div>
    );
    // The bug this whole pass fixes: a bespoke modal rendered inline (not
    // through a portal) at the same numeric z-index as BottomNavigation lost
    // the stacking tie-break because BottomNavigation renders later in the
    // DOM (Layout.tsx). A portal makes the modal's DOM position irrelevant to
    // its stacking — it's appended directly to document.body, as a sibling
    // of the app root, not nested under wherever <Modal> was written.
    expect(container.querySelector('[role="dialog"]')).toBeNull();
    expect(document.body.querySelector('[role="dialog"]')).not.toBeNull();
  });

  it('uses the documented z-index token, not a magic number, for its overlay', () => {
    render(
      <Modal isOpen onClose={() => {}} title="Z-index check" footer={<button>Save</button>}>
        <p>Body content</p>
      </Modal>
    );
    const overlay = screen.getByRole('dialog').parentElement;
    expect(overlay?.className).toContain('z-[var(--z-modal-overlay)]');
  });
});

describe('Z-index scale — modal must always outrank the mobile bottom navigation', () => {
  // Mirrors the :root custom-property values declared in index.css (see the
  // import comment above for why this can't just read that file directly in
  // this test environment). If that scale ever changes, update both places.
  const Z = {
    content: 0,
    stickyHeader: 30,
    bottomNav: 50,
    floatingFab: 55,
    bottomSheetOverlay: 60,
    bottomSheetPanel: 70,
    modalOverlay: 1000,
    modalDialog: 1000,
    toast: 1100,
  } as const;

  it('modal overlay/dialog outrank the bottom nav, the floating AI FAB, and bottom sheets', () => {
    expect(Z.modalOverlay).toBe(Z.modalDialog);
    expect(Z.modalOverlay).toBeGreaterThan(Z.bottomNav);
    expect(Z.modalOverlay).toBeGreaterThan(Z.floatingFab);
    expect(Z.modalOverlay).toBeGreaterThan(Z.bottomSheetOverlay);
    expect(Z.modalOverlay).toBeGreaterThan(Z.bottomSheetPanel);
    expect(Z.modalOverlay).toBeGreaterThan(Z.stickyHeader);
  });

  it('the floating AI FAB and bottom sheets outrank the bottom nav they sit above', () => {
    expect(Z.floatingFab).toBeGreaterThan(Z.bottomNav);
    expect(Z.bottomSheetOverlay).toBeGreaterThan(Z.bottomNav);
    expect(Z.bottomSheetPanel).toBeGreaterThan(Z.bottomSheetOverlay);
  });

  it('every fixed-position component that must clear the bottom nav references a token, not a hardcoded number', () => {
    const filesThatMustUseTokens: Array<[string, string]> = [
      ['BottomNavigation.tsx', bottomNavigationSource],
      ['MobileAssistantFab.tsx', mobileAssistantFabSource],
      ['MobileBottomSheet.tsx', mobileBottomSheetSource],
      ['Modal.tsx', modalSource],
    ];
    for (const [name, source] of filesThatMustUseTokens) {
      expect(source, `${name} should reference a --z-* token`).toMatch(/z-\[var\(--z-/);
    }
  });
});
