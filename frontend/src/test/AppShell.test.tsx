import { beforeEach, describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AppShell } from '../components/AppShell';
import { useAuthStore } from '../store/authStore';
import { useLocaleStore } from '../store/localeStore';
import type { MeResponse } from '../features/auth/types';

/**
 * Mobile AppShell drawer (Faz 5): hamburger opens the off-canvas nav dialog,
 * nav taps navigate AND close, Escape/backdrop close, focus returns to the
 * hamburger, body scroll locks while open.
 */

const me: MeResponse = {
  id: 'u1',
  email: 'admin@forge.dev',
  username: 'admin',
  firstName: 'Ada',
  lastName: 'Admin',
  phoneNumber: null,
  address: null,
  city: null,
  country: null,
  zipCode: null,
  enabled: true,
  emailVerified: true,
  lockedUntil: null,
  roles: [],
  groups: [],
  authorities: ['iam:user:read'],
};

function HomePage() {
  return <div>home-page</div>;
}

function UsersPage() {
  return <div>users-page</div>;
}

function renderShell(initialEntry = '/') {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route element={<AppShell />}>
          <Route index element={<HomePage />} />
          <Route path="users" element={<UsersPage />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

/** jsdom does not process Tailwind, so the `hidden lg:flex` desktop aside stays in
 *  the DOM — nav queries must be scoped to the drawer dialog. */
async function openDrawer(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: 'Menu' }));
  return screen.getByRole('dialog', { name: 'Menu' });
}

describe('AppShell mobile drawer', () => {
  beforeEach(() => {
    window.localStorage.clear();
    useLocaleStore.setState({ locale: 'en' });
    useAuthStore.setState({ user: me, hasAuthority: () => true });
  });

  it('renders a collapsed hamburger that opens the drawer with the nav links', async () => {
    const user = userEvent.setup();
    renderShell();

    const menuButton = screen.getByRole('button', { name: 'Menu' });
    expect(menuButton).toHaveAttribute('aria-controls', 'mobile-nav-drawer');
    expect(menuButton).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    const drawer = await openDrawer(user);

    expect(menuButton).toHaveAttribute('aria-expanded', 'true');
    expect(within(drawer).getByRole('link', { name: 'Projects' })).toBeInTheDocument();
    expect(within(drawer).getByRole('link', { name: 'Users' })).toBeInTheDocument();
  });

  it('closes the drawer after a nav link navigates', async () => {
    const user = userEvent.setup();
    renderShell();

    const drawer = await openDrawer(user);
    await user.click(within(drawer).getByRole('link', { name: 'Users' }));

    expect(screen.getByText('users-page')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('closes on Escape and returns focus to the hamburger', async () => {
    const user = userEvent.setup();
    renderShell();

    await openDrawer(user);
    await user.keyboard('{Escape}');

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Menu' })).toHaveFocus();
  });

  it('closes on backdrop click', async () => {
    const user = userEvent.setup();
    renderShell();

    const drawer = await openDrawer(user);
    const backdrop = drawer.previousElementSibling as HTMLElement;
    await user.click(backdrop);

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('locks body scroll while open and restores it on close', async () => {
    const user = userEvent.setup();
    renderShell();

    await openDrawer(user);
    expect(document.body.style.overflow).toBe('hidden');

    await user.keyboard('{Escape}');
    expect(document.body.style.overflow).toBe('');
  });
});
