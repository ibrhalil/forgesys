import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

// Auto-cleanup only self-registers when Vitest globals are on; we run with
// explicit imports (globals: false), so unmount after each test here.
afterEach(() => {
  cleanup();
});
