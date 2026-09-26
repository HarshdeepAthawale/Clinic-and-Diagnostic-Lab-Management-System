'use client';

import { useEffect } from 'react';

function isTyping(target) {
  return (
    target instanceof HTMLElement &&
    (target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName))
  );
}

/** Single-key shortcuts (`?`, `[`) that never fire while the user is typing in a field. */
export function useShellShortcuts(handlers) {
  useEffect(() => {
    const onKeyDown = (event) => {
      if (event.ctrlKey || event.metaKey || event.altKey || isTyping(event.target)) return;
      const handler = handlers[event.key];
      if (handler) {
        event.preventDefault();
        handler();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [handlers]);
}
