-- Tracks which delivery provider actually attempted each email send, now
-- that all mail goes through ZeptoMail's HTTPS API instead of SMTP.
-- Nullable and unbackfilled: historical rows genuinely went through SMTP
-- (or predate provider tracking entirely), so leaving them NULL is more
-- accurate than retroactively labeling them ZEPTOMAIL.
ALTER TABLE email_logs ADD COLUMN IF NOT EXISTS provider VARCHAR(20);
ALTER TABLE email_logs ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0;
