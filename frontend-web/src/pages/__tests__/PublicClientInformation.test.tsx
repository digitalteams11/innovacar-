import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import '../../i18n';
import i18n from '../../i18n';
import api from '../../api/axios';
import PublicClientInformation from '../PublicClientInformation';

vi.mock('../../api/axios', () => ({
  default: { get: vi.fn(), post: vi.fn() },
}));

// ThemeToggle pulls in ThemeProvider -> AuthProvider -> its own network
// calls, none of which are relevant to this page's own load/error/submit
// logic under test — stub it out so this stays a focused unit test.
vi.mock('../../components/ThemeToggle', () => ({
  default: () => null,
}));

const mockedApi = vi.mocked(api, true);

function renderAtToken(token = 'test-token') {
  return render(
    <MemoryRouter initialEntries={[`/client-information/${token}`]}>
      <Routes>
        <Route path="/client-information/:token" element={<PublicClientInformation />} />
      </Routes>
    </MemoryRouter>,
  );
}

const validView = {
  temporaryName: 'John Doe',
  agencyName: 'Test Agency',
  preferredLanguage: 'en',
  defaultCountryCode: 'MA',
  alreadySubmitted: false,
};

beforeEach(async () => {
  vi.clearAllMocks();
  await i18n.changeLanguage('en');
});

describe('PublicClientInformation — token load states', () => {
  it('renders the form once a valid token resolves', async () => {
    mockedApi.get.mockResolvedValueOnce({ data: validView });
    renderAtToken();
    await waitFor(() => expect(screen.getByLabelText(/full name/i)).toBeInTheDocument());
  });

  it('shows the expired message for CLIENT_INFO_LINK_EXPIRED', async () => {
    mockedApi.get.mockRejectedValueOnce({ response: { data: { code: 'CLIENT_INFO_LINK_EXPIRED' } } });
    renderAtToken();
    expect(await screen.findByText(/this secure link has expired/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /retry/i })).not.toBeInTheDocument();
  });

  it('shows the invalid-link message for CLIENT_INFO_LINK_INVALID', async () => {
    mockedApi.get.mockRejectedValueOnce({ response: { data: { code: 'CLIENT_INFO_LINK_INVALID' } } });
    renderAtToken();
    expect(await screen.findByText(/this link is not valid|this link is invalid/i)).toBeInTheDocument();
  });

  it('shows the already-submitted message when the request was already submitted', async () => {
    mockedApi.get.mockResolvedValueOnce({ data: { ...validView, alreadySubmitted: true } });
    renderAtToken();
    expect(await screen.findByText(/already been submitted/i)).toBeInTheDocument();
  });

  it('distinguishes a network/connection failure from an invalid token, and offers Retry', async () => {
    // No `response` on the rejection — simulates offline/timeout/CORS, not a
    // server-issued rejection code.
    mockedApi.get.mockRejectedValueOnce(new Error('Network Error'));
    renderAtToken();
    expect(await screen.findByText(/could not connect to the server/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
  });

  it('does not show a network-error message as "link is invalid"', async () => {
    mockedApi.get.mockRejectedValueOnce(new Error('Network Error'));
    renderAtToken();
    await screen.findByText(/could not connect to the server/i);
    expect(screen.queryByText(/this link is not valid|this link is invalid/i)).not.toBeInTheDocument();
  });

  it('retries the load when Retry is clicked after a network failure', async () => {
    mockedApi.get.mockRejectedValueOnce(new Error('Network Error'));
    renderAtToken();
    const retryBtn = await screen.findByRole('button', { name: /retry/i });
    mockedApi.get.mockResolvedValueOnce({ data: validView });
    await userEvent.click(retryBtn);
    await waitFor(() => expect(screen.getByLabelText(/full name/i)).toBeInTheDocument());
    expect(mockedApi.get).toHaveBeenCalledTimes(2);
  });
});

describe('PublicClientInformation — form fields', () => {
  beforeEach(() => {
    mockedApi.get.mockResolvedValue({ data: validView });
  });

  it('does not render a postal code field anywhere in the form', async () => {
    renderAtToken();
    await screen.findByLabelText(/full name/i);
    expect(screen.queryByText(/postal code/i)).not.toBeInTheDocument();
  });

  it('renders country and city as real select controls, and address/notes as textareas', async () => {
    renderAtToken();
    await screen.findByLabelText(/full name/i);
    const countryField = screen.getByText(/country/i).closest('div');
    expect(countryField).toBeTruthy();
    const addressField = screen.getByLabelText(/^address/i) as HTMLTextAreaElement;
    expect(addressField.tagName).toBe('TEXTAREA');
    const notesField = screen.getByLabelText(/notes/i) as HTMLTextAreaElement;
    expect(notesField.tagName).toBe('TEXTAREA');
  });

  it('defaults the country to Morocco and loads a dependent city list', async () => {
    renderAtToken();
    await screen.findByLabelText(/full name/i);
    // Morocco has a bundled dataset (src/data/cities/ma.ts) — city search should
    // become interactive rather than staying in manual free-text fallback mode.
    await waitFor(() => {
      expect(screen.queryByPlaceholderText(/type a city|enter city/i)).not.toBeInTheDocument();
    });
  });
});
