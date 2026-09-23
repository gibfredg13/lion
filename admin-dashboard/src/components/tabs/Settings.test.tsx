import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Settings from './Settings';

let state = { code: 'LIONHACK2026', required: true };
let putBody: any = null;
let putFails = false;

const jsonOk = (body: any) => Promise.resolve({ ok: true, json: async () => body, text: async () => '' });

describe('Settings tab — access code', () => {
  beforeEach(() => {
    state = { code: 'LIONHACK2026', required: true };
    putBody = null;
    putFails = false;
    (globalThis as any).fetch = vi.fn().mockImplementation((url: string, init?: any) => {
      if (url.includes('/access-code/required') && init?.method === 'PUT') {
        if (putFails) return Promise.resolve({ ok: false, status: 500, json: async () => ({}), text: async () => 'nope' });
        putBody = JSON.parse(init.body);
        return jsonOk({ required: putBody.required });
      }
      if (url.includes('/access-code')) return jsonOk(state);
      return jsonOk({});
    });
  });
  afterEach(() => vi.restoreAllMocks());

  it('shows the code and that it is required', async () => {
    render(<Settings />);
    expect(await screen.findByText('LIONHACK2026')).toBeInTheDocument();
    expect(screen.getByText('REQUIRED')).toBeInTheDocument();
    expect(screen.getByText(/Players need this code to register/i)).toBeInTheDocument();
  });

  it('turns the gate off and says who can still register', async () => {
    const user = userEvent.setup();
    render(<Settings />);

    await user.click(await screen.findByRole('button', { name: /Stop requiring a code/i }));

    await waitFor(() => expect(putBody).toEqual({ required: false }));
    expect(screen.getByText('NOT REQUIRED')).toBeInTheDocument();
    // "Off" is wider than it sounds, so the copy has to name what is still enforced.
    expect(screen.getByText(/@ing\.com address can register without a code/i)).toBeInTheDocument();
    // The code stays on screen so turning the gate back on does not mean retyping it.
    expect(screen.getByText('LIONHACK2026')).toBeInTheDocument();
  });

  it('turns it back on', async () => {
    const user = userEvent.setup();
    state = { code: 'LIONHACK2026', required: false };
    render(<Settings />);

    await user.click(await screen.findByRole('button', { name: /Require a code/i }));

    await waitFor(() => expect(putBody).toEqual({ required: true }));
    expect(screen.getByText('REQUIRED')).toBeInTheDocument();
  });

  it('reverts the switch when the server refuses', async () => {
    const user = userEvent.setup();
    putFails = true;
    render(<Settings />);

    await user.click(await screen.findByRole('button', { name: /Stop requiring a code/i }));

    await waitFor(() => expect(screen.getByText(/Could not change the access code requirement/i)).toBeInTheDocument());
    // The badge must go back, or the dashboard claims a gate is open when it is not.
    expect(screen.getByText('REQUIRED')).toBeInTheDocument();
  });

  it('treats a missing required flag as required', async () => {
    state = { code: 'LIONHACK2026' } as any;
    render(<Settings />);
    expect(await screen.findByText('REQUIRED')).toBeInTheDocument();
  });
});
