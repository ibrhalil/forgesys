import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CheckboxList } from '../components/ui/CheckboxList';
import { useLocaleStore } from '../store/localeStore';

describe('CheckboxList', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('toggles selection through the item checkboxes', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <CheckboxList
        items={[
          { id: 'a', label: 'Alpha' },
          { id: 'b', label: 'Beta' },
        ]}
        selectedIds={['a']}
        onChange={onChange}
      />,
    );

    await user.click(screen.getByRole('checkbox', { name: /beta/i }));
    expect(onChange).toHaveBeenCalledWith(['a', 'b']);
  });

  it('renders the locale-default empty message when there is nothing to select', () => {
    useLocaleStore.setState({ locale: 'en' });
    const { rerender } = render(<CheckboxList items={[]} selectedIds={[]} onChange={vi.fn()} />);
    expect(screen.getByText('Nothing to select')).toBeInTheDocument();

    useLocaleStore.setState({ locale: 'tr' });
    rerender(<CheckboxList items={[]} selectedIds={[]} onChange={vi.fn()} />);
    expect(screen.getByText('Seçilecek öğe yok')).toBeInTheDocument();
  });

  it('prefers an explicit emptyMessage prop', () => {
    render(<CheckboxList items={[]} selectedIds={[]} onChange={vi.fn()} emptyMessage="No roles" />);
    expect(screen.getByText('No roles')).toBeInTheDocument();
  });
});
