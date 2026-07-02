import { useEffect } from 'react';

function isTypingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  const tagName = target.tagName.toLowerCase();
  return tagName === 'input'
    || tagName === 'textarea'
    || tagName === 'select'
    || target.isContentEditable;
}

export function useKeyboardShortcut(callback: () => void) {
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const isShortcut = (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k';
      if (!isShortcut) return;
      const target = event.target;
      if (isTypingTarget(target) && !(target instanceof HTMLElement && target.dataset.globalSearchTrigger === 'true')) {
        return;
      }
      event.preventDefault();
      callback();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [callback]);
}
