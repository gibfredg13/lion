import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LlmBackends from './LlmBackends';

const station = {
  id: 'station', label: 'DGX Station (vLLM)', kind: 'dgxspark',
  baseUrl: 'http://192.168.1.129:8000', model: 'nvidia/Qwen3.6-35B-A3B-NVFP4',
  maxConcurrent: 32, maxTokens: 320, waitSeconds: 30, mergeSystemMessages: true,
  active: true, healthy: true, avgLatencyMs: 270, inFlight: 0, availableSlots: 32,
  availableModels: ['nvidia/Qwen3.6-35B-A3B-NVFP4'], checkedAt: new Date().toISOString(), error: null,
};
const spark = {
  id: 'spark', label: 'DGX Spark (llama.cpp)', kind: 'dgxspark',
  baseUrl: 'http://192.168.1.145:42000', model: '',
  maxConcurrent: 4, maxTokens: 320, waitSeconds: 45, mergeSystemMessages: false,
  active: false, healthy: false, avgLatencyMs: 0, inFlight: 0, availableSlots: 4,
  availableModels: [], checkedAt: new Date().toISOString(),
  error: 'No answer from http://192.168.1.145:42000',
};

let backends = [station, spark];
let pinned = { running: false, backendId: 'station', backendLabel: 'DGX Station (vLLM)' };
let activateResponse: any;

const jsonOk = (body: any) => Promise.resolve({ ok: true, json: async () => body, text: async () => '' });

describe('LLM Backends tab', () => {
  beforeEach(() => {
    backends = [station, spark];
    pinned = { running: false, backendId: 'station', backendLabel: 'DGX Station (vLLM)' };
    activateResponse = null;
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    (globalThis as any).fetch = vi.fn().mockImplementation((url: string, init?: any) => {
      if (url.includes('/activate')) {
        if (activateResponse?.fail) {
          return Promise.resolve({ ok: false, status: 500, text: async () => 'backend exploded', json: async () => ({}) });
        }
        const id = url.split('/backends/')[1].split('/')[0];
        backends = backends.map((b) => ({ ...b, active: b.id === id }));
        return jsonOk({ activeBackendId: id, persisted: true, note: activateResponse?.note ?? null, backends });
      }
      if (url.includes('/test')) {
        const id = url.split('/backends/')[1].split('/')[0];
        return jsonOk(id === 'spark'
          ? { backendId: id, success: false, response: null, inputTokens: 0, outputTokens: 0, latencyMs: 5001, error: 'LlmUnavailableException: Cannot reach the LLM' }
          : { backendId: id, success: true, response: 'I guard the word.', inputTokens: 30, outputTokens: 9, latencyMs: 271, error: null });
      }
      if (url.includes('/calibration/pinned')) return jsonOk(pinned);
      if (url.includes('/llm/backends')) return jsonOk(backends);
      return jsonOk({});
    });
  });

  afterEach(() => vi.restoreAllMocks());

  it('lists every backend with its endpoint, model and health', async () => {
    render(<LlmBackends />);

    const stationCard = await screen.findByTestId('backend-station');
    expect(within(stationCard).getByText('DGX Station (vLLM)')).toBeInTheDocument();
    expect(within(stationCard).getByText('http://192.168.1.129:8000')).toBeInTheDocument();
    expect(within(stationCard).getByText(/🟢 Online/)).toBeInTheDocument();
    expect(within(stationCard).getByText('ACTIVE')).toBeInTheDocument();

    const sparkCard = screen.getByTestId('backend-spark');
    expect(within(sparkCard).getByText(/🔴 Offline/)).toBeInTheDocument();
    expect(within(sparkCard).getByText(/No answer from/)).toBeInTheDocument();
  });

  it('offers Activate only on the backend that is not active', async () => {
    render(<LlmBackends />);

    const stationCard = await screen.findByTestId('backend-station');
    expect(within(stationCard).queryByRole('button', { name: /Activate/i })).not.toBeInTheDocument();
    expect(within(screen.getByTestId('backend-spark')).getByRole('button', { name: /Activate/i })).toBeInTheDocument();
  });

  it('switches backend and moves the ACTIVE badge', async () => {
    const user = userEvent.setup();
    render(<LlmBackends />);

    const sparkCard = await screen.findByTestId('backend-spark');
    await user.click(within(sparkCard).getByRole('button', { name: /Activate/i }));

    await waitFor(() => {
      expect(within(screen.getByTestId('backend-spark')).getByText('ACTIVE')).toBeInTheDocument();
    });
    expect((globalThis as any).fetch).toHaveBeenCalledWith(
      '/api/admin/llm/backends/spark/activate',
      expect.objectContaining({ method: 'POST' })
    );
  });

  it('asks before redirecting players, and does nothing if declined', async () => {
    const user = userEvent.setup();
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    render(<LlmBackends />);

    const sparkCard = await screen.findByTestId('backend-spark');
    await user.click(within(sparkCard).getByRole('button', { name: /Activate/i }));

    expect((globalThis as any).fetch).not.toHaveBeenCalledWith(
      expect.stringContaining('/activate'),
      expect.anything()
    );
    expect(within(screen.getByTestId('backend-station')).getByText('ACTIVE')).toBeInTheDocument();
  });

  it('surfaces a failed switch and leaves the active backend alone', async () => {
    const user = userEvent.setup();
    activateResponse = { fail: true };
    render(<LlmBackends />);

    const sparkCard = await screen.findByTestId('backend-spark');
    await user.click(within(sparkCard).getByRole('button', { name: /Activate/i }));

    await waitFor(() => expect(screen.getByText(/backend exploded/)).toBeInTheDocument());
    expect(within(screen.getByTestId('backend-station')).getByText('ACTIVE')).toBeInTheDocument();
  });

  it('warns when the choice could not be persisted', async () => {
    const user = userEvent.setup();
    activateResponse = { note: 'Switched, but the choice could not be saved, so a restart will revert it: down' };
    render(<LlmBackends />);

    await user.click(within(await screen.findByTestId('backend-spark')).getByRole('button', { name: /Activate/i }));

    await waitFor(() => expect(screen.getByText(/could not be saved/)).toBeInTheDocument());
  });

  it('tests one backend without switching to it', async () => {
    const user = userEvent.setup();
    render(<LlmBackends />);

    const sparkCard = await screen.findByTestId('backend-spark');
    await user.click(within(sparkCard).getByRole('button', { name: /^Test$/i }));

    await waitFor(() => {
      expect(within(screen.getByTestId('backend-spark')).getByText(/Cannot reach the LLM/)).toBeInTheDocument();
    });
    // Testing must never move production traffic.
    expect(within(screen.getByTestId('backend-station')).getByText('ACTIVE')).toBeInTheDocument();
  });

  it('says a calibration run is pinned and will not follow the switch', async () => {
    pinned = { running: true, backendId: 'station', backendLabel: 'DGX Station (vLLM)' };
    render(<LlmBackends />);

    await waitFor(() => {
      expect(screen.getByText(/pinned to/i)).toBeInTheDocument();
    });
  });
});
