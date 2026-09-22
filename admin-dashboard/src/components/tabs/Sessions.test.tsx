import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Sessions from './Sessions';

const s1 = { id: 's1', label: 'Session 1', startedAt: '2026-09-01T10:00:00Z', endedAt: '2026-09-01T18:00:00Z',
             resetLevels: false, createdBy: 'a@b.c', players: 41, attempts: 900, active: false };
const s2 = { id: 's2', label: 'Session 2', startedAt: '2026-09-22T09:00:00Z', endedAt: null,
             resetLevels: true, createdBy: 'a@b.c', players: 3, attempts: 12, active: true };

let posted: any = null;

describe('Sessions tab', () => {
  beforeEach(() => {
    posted = null;
    (globalThis as any).fetch = vi.fn().mockImplementation((url: string, init?: any) => {
      if (url.includes('/game-sessions') && init?.method === 'POST') {
        posted = JSON.parse(init.body);
        return Promise.resolve({ ok: true, json: async () => ({ id: 's3', label: posted.label }), text: async () => '' });
      }
      if (url.includes('/game-sessions')) {
        return Promise.resolve({ ok: true, json: async () => [s2, s1], text: async () => '' });
      }
      if (url.includes('/level-stats')) {
        return Promise.resolve({ ok: true, json: async () => [
          { level: 1, enabled: true }, { level: 6, enabled: false },
        ], text: async () => '' });
      }
      return Promise.resolve({ ok: true, json: async () => ({}), text: async () => '' });
    });
  });
  afterEach(() => vi.restoreAllMocks());

  it('lists every run with its counts and marks the live one', async () => {
    render(<Sessions />);
    const live = await screen.findByTestId('session-s2');
    expect(within(live).getByText('LIVE')).toBeInTheDocument();
    expect(within(live).getByText('reset to 1')).toBeInTheDocument();

    const past = screen.getByTestId('session-s1');
    expect(within(past).getByText('41')).toBeInTheDocument();
    expect(within(past).getByText('900')).toBeInTheDocument();
    expect(within(past).getByText('carried on')).toBeInTheDocument();
  });

  it('defaults to carrying on, and says what a new run will do', async () => {
    const user = userEvent.setup();
    render(<Sessions />);
    await user.click(await screen.findByRole('button', { name: /New Session/i }));

    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByRole('radio', { name: /carry on/i })).toBeChecked();
    expect(within(dialog).getByRole('radio', { name: /back to Level 1/i })).not.toBeChecked();
    // The warnings are the point of having a dialog rather than a confirm().
    expect(within(dialog).getByText(/Secret words are re-drawn/i)).toBeInTheDocument();
    expect(within(dialog).getByText(/Levels currently disabled stay disabled: 6/i)).toBeInTheDocument();
    expect(within(dialog).getByText(/Session 2 will be closed and archived/i)).toBeInTheDocument();
  });

  it('sends the chosen reset mode', async () => {
    const user = userEvent.setup();
    render(<Sessions />);
    await user.click(await screen.findByRole('button', { name: /New Session/i }));
    await user.click(screen.getByRole('radio', { name: /back to Level 1/i }));
    await user.click(screen.getByRole('button', { name: /^Start/i }));

    await waitFor(() => expect(posted).toEqual({ label: 'Session 3', resetLevels: true }));
  });

  it('keeps carry-on when that is left selected', async () => {
    const user = userEvent.setup();
    render(<Sessions />);
    await user.click(await screen.findByRole('button', { name: /New Session/i }));
    await user.click(screen.getByRole('button', { name: /^Start/i }));

    await waitFor(() => expect(posted).toEqual({ label: 'Session 3', resetLevels: false }));
  });

  it('offers Discard only on a run that is not live', async () => {
    render(<Sessions />);
    await screen.findByTestId('session-s1');
    expect(within(screen.getByTestId('session-s1')).getByRole('button', { name: /Discard/i })).toBeInTheDocument();
    expect(within(screen.getByTestId('session-s2')).queryByRole('button', { name: /Discard/i })).not.toBeInTheDocument();
  });

  it('keeps the erase button locked until ERASE is typed exactly', async () => {
    const user = userEvent.setup();
    render(<Sessions />);
    const erase = await screen.findByRole('button', { name: /Erase ALL data/i });
    expect(erase).toBeDisabled();

    const box = screen.getByLabelText(/Type ERASE to confirm/i);
    await user.type(box, 'erase');
    expect(erase).toBeDisabled();

    await user.clear(box);
    await user.type(box, 'ERASE');
    expect(erase).toBeEnabled();
  });
});
