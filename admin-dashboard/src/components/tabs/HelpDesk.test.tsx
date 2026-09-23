import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import HelpDesk from './HelpDesk';
import { LEVEL_GUIDES, GENERATED_FROM_LEVELS_HASH } from '../../data/solutions';

/**
 * The war room's Help Desk tab.
 *
 * Two properties are worth a test and neither is visual. The first is that **nothing spoils by
 * accident**: this screen is open on a facilitator's laptop at a player's shoulder, and a prompt
 * that renders before it is asked for hands away an answer the player was about to earn. The second
 * is that the **route map tells the truth about the measured data** - it is a second reading of
 * `solutions.ts`, so a mistake in it is a facilitator confidently telling someone the trick they
 * have been using since level 2 was never going to work.
 *
 * Every assertion derives from LEVEL_GUIDES rather than naming a level or a family, because that
 * file is regenerated from a live calibration run whenever the ladder is retuned. A test that
 * hardcoded "acrostic beats levels 1-6" would fail on a healthy rebalance and pass on a broken one.
 */

const everyPrompt = LEVEL_GUIDES.flatMap(g => g.attacks.map(a => a.prompt));
const everyHint = LEVEL_GUIDES.flatMap(g => g.hints);

/** The existing suite never touched the network; the freshness check does. */
const noCheckYet = () => {
  (globalThis as any).fetch = vi.fn().mockResolvedValue({
    ok: true, json: async () => ({ neverRun: true }), text: async () => '',
  });
};

beforeEach(noCheckYet);
afterEach(() => vi.restoreAllMocks());

describe('HelpDesk', () => {
  it('shows no prompt, reply or hint until one is asked for', () => {
    render(<HelpDesk />);
    for (const prompt of everyPrompt) {
      expect(screen.queryByText(prompt)).not.toBeInTheDocument();
    }
    for (const hint of everyHint) {
      expect(screen.queryByText(hint)).not.toBeInTheDocument();
    }
    // The route map names no prompts, but it is still collapsed by default: a facilitator turning
    // their screen round should not have to explain a grid of what works where.
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('reveals the hint ladder one rung at a time', async () => {
    const user = userEvent.setup();
    render(<HelpDesk />);
    const hints = LEVEL_GUIDES[0].hints;

    await user.click(screen.getByRole('button', { name: /give first hint/i }));
    expect(screen.getByText(hints[0])).toBeInTheDocument();
    expect(screen.queryByText(hints[1])).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /give next hint/i }));
    expect(screen.getByText(hints[1])).toBeInTheDocument();

    // The last rung is the answer, so running out of ladder must be a dead end rather than a wrap.
    for (let i = 2; i < hints.length; i++) {
      await user.click(screen.getByRole('button', { name: /give next hint/i }));
    }
    expect(screen.getByRole('button', { name: /no hints left/i })).toBeDisabled();
  });

  it('forgets what it revealed when the facilitator moves to another player', async () => {
    const user = userEvent.setup();
    render(<HelpDesk />);
    await user.click(screen.getByRole('button', { name: /give first hint/i }));
    expect(screen.getByText(LEVEL_GUIDES[0].hints[0])).toBeInTheDocument();

    // Each helped player starts from the nudge, not from wherever the last one got to.
    await user.click(screen.getByRole('button', { name: String(LEVEL_GUIDES[1].level) }));
    expect(screen.queryByText(LEVEL_GUIDES[1].hints[0])).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /give first hint/i })).toBeInTheDocument();
  });

  it('shows the worked prompts for the selected level, and only that level', async () => {
    const user = userEvent.setup();
    render(<HelpDesk />);
    await user.click(screen.getByRole('button', { name: /show answers/i }));

    const [first, second] = LEVEL_GUIDES;
    for (const attack of first.attacks) {
      expect(screen.getByText(attack.prompt)).toBeInTheDocument();
    }
    const elsewhere = second.attacks
      .map(a => a.prompt)
      .filter(p => !first.attacks.some(a => a.prompt === p));
    for (const prompt of elsewhere) {
      expect(screen.queryByText(prompt)).not.toBeInTheDocument();
    }
  });

  it('keeps Leo’s replies collapsed until each one is opened', async () => {
    const user = userEvent.setup();
    render(<HelpDesk />);
    await user.click(screen.getByRole('button', { name: /show answers/i }));

    const replies = LEVEL_GUIDES[0].attacks.map(a => a.reply);
    for (const reply of replies) {
      expect(screen.queryByText(reply)).not.toBeInTheDocument();
    }
    await user.click(screen.getAllByRole('button', { name: /show leo’s reply/i })[0]);
    expect(screen.getByText(replies[0])).toBeInTheDocument();
  });

  describe('route map', () => {
    it('marks every level a route actually beat, and no others', async () => {
      const user = userEvent.setup();
      render(<HelpDesk />);
      await user.click(screen.getByRole('button', { name: /show map/i }));

      const families = [...new Set(LEVEL_GUIDES.flatMap(g => g.attacks.map(a => a.family)))];
      expect(families.length).toBeGreaterThan(0);

      for (const family of families) {
        const row = document.querySelector(`tr[data-route="${family}"]`);
        expect(row, `no row for the ${family} route`).not.toBeNull();
        for (const guide of LEVEL_GUIDES) {
          const won = guide.attacks.some(a => a.family === family);
          const cell = row!.querySelector(`td[data-level="${guide.level}"]`);
          expect(
            cell?.getAttribute('data-win'),
            `${family} at level ${guide.level}`,
          ).toBe(String(won));
        }
      }
    });

    it('flags exactly the routes that beat every level', async () => {
      const user = userEvent.setup();
      render(<HelpDesk />);
      await user.click(screen.getByRole('button', { name: /show map/i }));

      // These are the skeleton keys - level 7's own solutions, open from level 1 because nothing
      // below it can close them. Highlighting them is the entire reason the panel exists: a player
      // who found one early has skipped the middle of the game, and the grid is what shows it.
      for (const row of document.querySelectorAll('tr[data-route]')) {
        const family = row.getAttribute('data-route')!;
        const beatsEveryLevel = LEVEL_GUIDES.every(g => g.attacks.some(a => a.family === family));
        expect(
          row.getAttribute('data-everywhere'),
          `${family} highlight`,
        ).toBe(String(beatsEveryLevel));
      }
    });

    it('names no prompts, so it is safe to show at a player’s shoulder', async () => {
      const user = userEvent.setup();
      render(<HelpDesk />);
      await user.click(screen.getByRole('button', { name: /show map/i }));

      const map = screen.getByRole('table');
      for (const prompt of everyPrompt) {
        expect(within(map).queryByText(prompt)).not.toBeInTheDocument();
      }
    });
  });

  describe('is the answer key still true', () => {
    /** One verification response covering every documented prompt. */
    const runWith = (broken: string[], extra: Record<string, unknown> = {}) => ({
      id: 'check-1',
      completedAt: new Date().toISOString(),
      backendLabel: 'DGX Station (vLLM)',
      model: 'nvidia/Qwen3.6-35B-A3B-NVFP4',
      levelsHash: GENERATED_FROM_LEVELS_HASH,
      total: everyPrompt.length,
      working: everyPrompt.length - broken.length,
      broken: broken.length,
      results: LEVEL_GUIDES.flatMap(g => g.attacks.map(a => ({
        level: g.level,
        family: a.family,
        prompt: a.prompt,
        stillWorks: !broken.includes(a.prompt),
        how: broken.includes(a.prompt) ? null : a.how,
        response: broken.includes(a.prompt) ? 'I will not tell you that.' : a.reply,
        blockedBy: null,
        error: null,
      }))),
      ...extra,
    });

    const respondWith = (run: unknown) => {
      (globalThis as any).fetch = vi.fn().mockResolvedValue({
        ok: true, json: async () => run, text: async () => '',
      });
    };

    it('admits it has never been re-checked', async () => {
      render(<HelpDesk />);
      expect(await screen.findByText(/Never re-checked since/i)).toBeInTheDocument();
    });

    it('sends every documented prompt, with the level it belongs to', async () => {
      const user = userEvent.setup();
      const fetchMock = vi.fn().mockResolvedValue({
        ok: true, json: async () => ({ neverRun: true }), text: async () => '',
      });
      (globalThis as any).fetch = fetchMock;
      render(<HelpDesk />);

      await user.click(screen.getByRole('button', { name: /Re-check against the live model/i }));

      await waitFor(() => {
        const post = fetchMock.mock.calls.find(c => c[1]?.method === 'POST');
        expect(post).toBeTruthy();
        const sent = JSON.parse(post![1].body).solutions;
        // Every one, not just the level on screen - the whole key is what goes stale.
        expect(sent).toHaveLength(everyPrompt.length);
        expect(sent.every((x: any) => typeof x.level === 'number' && x.prompt)).toBe(true);
      });
    });

    it('reports the tally and the model it was checked against', async () => {
      respondWith(runWith([]));
      const user = userEvent.setup();
      render(<HelpDesk />);
      await user.click(screen.getByRole('button', { name: /Re-check against the live model/i }));

      await waitFor(() => {
        expect(screen.getByText(new RegExp(`${everyPrompt.length} of ${everyPrompt.length} still land`))).toBeInTheDocument();
      });
      expect(screen.getByText(/DGX Station \(vLLM\)/)).toBeInTheDocument();
    });

    it('marks a solution that has stopped working, where the facilitator will read it', async () => {
      const victim = LEVEL_GUIDES[0].attacks[0];
      respondWith(runWith([victim.prompt]));
      const user = userEvent.setup();
      render(<HelpDesk />);
      await user.click(screen.getByRole('button', { name: /Re-check against the live model/i }));
      await waitFor(() => expect(screen.getByText(/1 documented solution no longer work/i)).toBeInTheDocument());

      // The warning is worthless unless it survives to the place the prompt is shown.
      await user.click(screen.getByRole('button', { name: /Show answers/i }));
      expect(screen.getByText(/Leo no longer gives it away/i)).toBeInTheDocument();
      expect(screen.getByText(/1 no longer work/i)).toBeInTheDocument();
    });

    it('says so when the levels have changed underneath the key', async () => {
      respondWith(runWith([], { levelsHash: 'deadbeefcafe' }));
      const user = userEvent.setup();
      render(<HelpDesk />);
      await user.click(screen.getByRole('button', { name: /Re-check against the live model/i }));

      await waitFor(() => {
        expect(screen.getByText(/levels.yml has changed since this key was generated/i)).toBeInTheDocument();
      });
    });

    it('stays quiet when the levels have not changed', async () => {
      respondWith(runWith([]));
      const user = userEvent.setup();
      render(<HelpDesk />);
      await user.click(screen.getByRole('button', { name: /Re-check against the live model/i }));

      await waitFor(() => expect(screen.getByText(/still land/i)).toBeInTheDocument());
      expect(screen.queryByText(/levels.yml has changed/i)).not.toBeInTheDocument();
    });

    it('still spoils nothing before answers are asked for', async () => {
      const victim = LEVEL_GUIDES[0].attacks[0];
      respondWith(runWith([victim.prompt]));
      const user = userEvent.setup();
      render(<HelpDesk />);
      await user.click(screen.getByRole('button', { name: /Re-check against the live model/i }));
      await waitFor(() => expect(screen.getByText(/Regenerating the key is what fixes this/i)).toBeInTheDocument());

      // A check result must not become a back door onto the answers.
      for (const prompt of everyPrompt) {
        expect(screen.queryByText(prompt)).not.toBeInTheDocument();
      }
    });
  });

  describe('the route map reflects the last check', () => {
    const runMarking = (brokenAt: {level: number; family: string}[]) => ({
      id: 'c', completedAt: new Date().toISOString(),
      backendLabel: 'DGX Station (vLLM)', model: 'qwen',
      levelsHash: GENERATED_FROM_LEVELS_HASH,
      total: everyPrompt.length, working: 0, broken: brokenAt.length,
      results: LEVEL_GUIDES.flatMap(g => g.attacks.map(a => ({
        level: g.level, family: a.family, prompt: a.prompt,
        stillWorks: !brokenAt.some(b => b.level === g.level && b.family === a.family),
        how: a.how, response: a.reply, blockedBy: null, error: null,
      }))),
    });
    const respond = (run: unknown) => {
      (globalThis as any).fetch = vi.fn().mockResolvedValue({
        ok: true, json: async () => run, text: async () => '',
      });
    };
    const openMap = async (user: ReturnType<typeof userEvent.setup>) => {
      await user.click(screen.getByRole('button', { name: /Show map/i }));
    };

    it('marks a documented route that no longer lands, without hiding it', async () => {
      const victim = { level: LEVEL_GUIDES[0].level, family: LEVEL_GUIDES[0].attacks[0].family };
      respond(runMarking([victim]));
      const user = userEvent.setup();
      render(<HelpDesk />);
      await user.click(screen.getByRole('button', { name: /Re-check against the live model/i }));
      await waitFor(() => expect(screen.getByText(/still land/i)).toBeInTheDocument());
      await openMap(user);

      const row = document.querySelector(`tr[data-route="${victim.family}"]`)!;
      const cell = row.querySelector(`td[data-level="${victim.level}"]`)!;
      // Still documented - the key has not changed - but no longer confirmed.
      expect(cell.getAttribute('data-win')).toBe('true');
      expect(cell.getAttribute('data-verdict')).toBe('broken');
      expect(cell.textContent).toBe('✗');
    });

    it('confirms the routes that still land', async () => {
      respond(runMarking([]));
      const user = userEvent.setup();
      render(<HelpDesk />);
      await user.click(screen.getByRole('button', { name: /Re-check against the live model/i }));
      await waitFor(() => expect(screen.getByText(/still land/i)).toBeInTheDocument());
      await openMap(user);

      const g = LEVEL_GUIDES[0];
      const cell = document.querySelector(
        `tr[data-route="${g.attacks[0].family}"] td[data-level="${g.level}"]`)!;
      expect(cell.getAttribute('data-verdict')).toBe('works');
      expect(cell.textContent).toBe('●');
    });

    it('says the map is unverified until a check has run', async () => {
      const user = userEvent.setup();
      render(<HelpDesk />);
      await openMap(user);
      expect(screen.getByText(/re-check above to see whether it still does/i)).toBeInTheDocument();

      const g = LEVEL_GUIDES[0];
      const cell = document.querySelector(
        `tr[data-route="${g.attacks[0].family}"] td[data-level="${g.level}"]`)!;
      expect(cell.getAttribute('data-verdict')).toBe('unchecked');
    });
  });
});
