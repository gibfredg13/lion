import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import HelpDesk from './HelpDesk';
import { LEVEL_GUIDES } from '../../data/solutions';

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
});
