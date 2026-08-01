import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '../../i18n';
import DownloadDesktopButton from '../DownloadDesktopButton';

vi.mock('../../api/axios', () => ({
  default: { post: vi.fn().mockResolvedValue({ data: { success: true } }) },
}));

describe('DownloadDesktopButton', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('coming-soon state: renders no download button when release is unavailable', () => {
    render(<DownloadDesktopButton release={{ available: false }} source="LANDING" />);

    expect(screen.queryByRole('button', { name: /download for windows/i })).not.toBeInTheDocument();
    expect(screen.getByText(/coming soon/i)).toBeInTheDocument();
    expect(screen.getByText(/notify me/i)).toBeInTheDocument();
  });

  it('coming-soon state: renders when release prop is null', () => {
    render(<DownloadDesktopButton release={null} source="LANDING" />);
    expect(screen.getByText(/coming soon/i)).toBeInTheDocument();
  });

  it('available state: renders a real download button with version and size in its label', () => {
    render(
      <DownloadDesktopButton
        release={{
          available: true,
          releaseId: 7,
          version: '1.2.0',
          downloadUrl: 'https://github.com/innovacar/desktop/releases/download/v1.2.0/setup.exe',
          fileName: 'Innovacar-Setup-1.2.0.exe',
          fileSizeBytes: 98452311,
        }}
        source="DESKTOP_PAGE"
      />,
    );

    const button = screen.getByRole('button', { name: /download for windows/i });
    expect(button).toHaveAccessibleName(expect.stringContaining('1.2.0'));
  });

  it('clicking download records analytics and shows "started" state', async () => {
    const user = userEvent.setup();
    const axios = (await import('../../api/axios')).default as unknown as { post: ReturnType<typeof vi.fn> };

    // jsdom doesn't implement real navigation for a synthetic <a> click — stub it out.
    const originalCreateElement = document.createElement.bind(document);
    vi.spyOn(document, 'createElement').mockImplementation((tag: string) => {
      const el = originalCreateElement(tag);
      if (tag === 'a') el.click = vi.fn();
      return el;
    });

    render(
      <DownloadDesktopButton
        release={{
          available: true,
          releaseId: 7,
          version: '1.2.0',
          downloadUrl: 'https://github.com/innovacar/desktop/releases/download/v1.2.0/setup.exe',
          fileName: 'Innovacar-Setup-1.2.0.exe',
          fileSizeBytes: 98452311,
        }}
        source="LANDING"
      />,
    );

    await user.click(screen.getByRole('button', { name: /download for windows/i }));

    expect(axios.post).toHaveBeenCalledWith('/public/desktop/downloads', expect.objectContaining({
      releaseId: 7,
      source: 'LANDING',
      status: 'STARTED',
    }));
    expect(await screen.findByText(/download started/i)).toBeInTheDocument();

    vi.restoreAllMocks();
  });
});
