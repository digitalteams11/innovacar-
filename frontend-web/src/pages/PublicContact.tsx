import { useState } from 'react';
import { CheckCircle2, Mail } from 'lucide-react';
import api from '../api/axios';
import SeoHead from '../components/seo/SeoHead';
import { ROBOTS_PUBLIC_INDEXABLE } from '../components/seo/robotsPresets';

const CATEGORIES = [
  { value: 'GENERAL', label: 'General question' },
  { value: 'SALES', label: 'Sales' },
  { value: 'SUBSCRIPTION', label: 'Pricing / Demo request' },
  { value: 'OTHER', label: 'Other' },
];

export default function PublicContact() {
  const [form, setForm] = useState({
    requesterName: '', requesterEmail: '', requesterPhone: '',
    category: 'GENERAL', subject: '', message: '',
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [ticketNumber, setTicketNumber] = useState<string | null>(null);

  const submit = async () => {
    setError(null);
    if (form.subject.trim().length < 5) {
      setError('Subject must be at least 5 characters.');
      return;
    }
    if (form.message.trim().length < 10) {
      setError('Message must be at least 10 characters.');
      return;
    }
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(form.requesterEmail)) {
      setError('Please enter a valid email address.');
      return;
    }
    setSubmitting(true);
    try {
      const response = await api.post('/public/contact', form);
      const data = response.data as { ticketNumber?: string };
      setTicketNumber(data.ticketNumber ?? null);
    } catch {
      setError('Unable to submit your request. Please try again later.');
    } finally {
      setSubmitting(false);
    }
  };

  if (ticketNumber) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-[#f7f7f4] px-4 dark:bg-[#101418]">
        <div className="w-full max-w-md rounded-2xl border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-8 text-center shadow-soft">
          <CheckCircle2 size={40} className="mx-auto text-emerald-500" />
          <h1 className="mt-4 text-xl font-bold text-[var(--text-primary)]">We sent your request to the right team</h1>
          <p className="mt-2 text-sm text-[var(--text-muted)]">Your ticket number is</p>
          <p className="mt-1 font-mono text-lg text-[var(--brand-primary)]">{ticketNumber}</p>
          <p className="mt-4 text-xs text-[var(--text-muted)]">We&apos;ll follow up by email shortly.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-[#f7f7f4] px-4 py-10 dark:bg-[#101418]">
      <SeoHead
        title="Contact Us"
        description="Get in touch with the Innovacar team for sales questions, demo requests or general support."
        canonical="https://innovacar.app/#/contact"
        robots={ROBOTS_PUBLIC_INDEXABLE}
      />
      <div className="w-full max-w-lg rounded-2xl border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-8 shadow-soft">
        <div className="flex items-center gap-2">
          <Mail size={20} className="text-[var(--brand-primary)]" />
          <h1 className="text-xl font-bold text-[var(--text-primary)]">Contact Us</h1>
        </div>
        <p className="mt-2 text-sm text-[var(--text-muted)]">
          Have a question before signing up, or want a demo? Send us a message and we&apos;ll reply by email.
        </p>

        {error && <p className="mt-4 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-500/10 dark:text-red-300">{error}</p>}

        <div className="mt-5 space-y-3">
          <div className="grid gap-3 sm:grid-cols-2">
            <div>
              <label className="mb-1 block text-xs font-semibold text-[var(--text-secondary)]">Your name</label>
              <input
                value={form.requesterName}
                onChange={(e) => setForm({ ...form, requesterName: e.target.value })}
                className="w-full rounded-lg border border-[var(--border-subtle)] bg-transparent px-3 py-2 text-sm"
                placeholder="Full name"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold text-[var(--text-secondary)]">Phone (optional)</label>
              <input
                value={form.requesterPhone}
                onChange={(e) => setForm({ ...form, requesterPhone: e.target.value })}
                className="w-full rounded-lg border border-[var(--border-subtle)] bg-transparent px-3 py-2 text-sm"
                placeholder="+212..."
              />
            </div>
          </div>
          <div>
            <label className="mb-1 block text-xs font-semibold text-[var(--text-secondary)]">Email</label>
            <input
              type="email"
              value={form.requesterEmail}
              onChange={(e) => setForm({ ...form, requesterEmail: e.target.value })}
              className="w-full rounded-lg border border-[var(--border-subtle)] bg-transparent px-3 py-2 text-sm"
              placeholder="you@example.com"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-semibold text-[var(--text-secondary)]">Topic</label>
            <select
              value={form.category}
              onChange={(e) => setForm({ ...form, category: e.target.value })}
              className="w-full rounded-lg border border-[var(--border-subtle)] bg-transparent px-3 py-2 text-sm"
            >
              {CATEGORIES.map((cat) => <option key={cat.value} value={cat.value}>{cat.label}</option>)}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-semibold text-[var(--text-secondary)]">Subject</label>
            <input
              value={form.subject}
              onChange={(e) => setForm({ ...form, subject: e.target.value })}
              maxLength={150}
              className="w-full rounded-lg border border-[var(--border-subtle)] bg-transparent px-3 py-2 text-sm"
              placeholder="Short summary"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-semibold text-[var(--text-secondary)]">Message</label>
            <textarea
              value={form.message}
              onChange={(e) => setForm({ ...form, message: e.target.value })}
              maxLength={5000}
              rows={5}
              className="w-full rounded-lg border border-[var(--border-subtle)] bg-transparent px-3 py-2 text-sm"
              placeholder="Tell us more..."
            />
          </div>
        </div>

        <button
          onClick={submit}
          disabled={submitting}
          className="mt-6 w-full rounded-lg bg-[var(--brand-primary)] px-4 py-3 text-sm font-semibold text-[#171817] disabled:opacity-60"
        >
          {submitting ? 'Sending...' : 'Send Message'}
        </button>
      </div>
    </div>
  );
}
