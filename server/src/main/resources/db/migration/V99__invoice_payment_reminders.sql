-- Financial Control System, Phase 2: payment reminder tracking. Each
-- reminder must fire at most once per invoice (see PaymentReminderJob) —
-- these columns are how it avoids notifying the same slow-paying client
-- every single day.

ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS due_soon_reminder_sent_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS overdue_reminder_sent_at TIMESTAMP;
