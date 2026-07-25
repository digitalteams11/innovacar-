-- Hardens the existing Google OAuth sign-in flow (GoogleOAuthService):
--
-- 1. auth_provider tracks how a user can authenticate — LOCAL (password
--    only), GOOGLE (Google-only signup, no usable password), or
--    LOCAL_AND_GOOGLE (a local account that later linked a Google identity,
--    or vice versa). Backfilled from existing data: a user with a
--    google_id whose password still carries the GoogleOAuthService
--    placeholder prefix ("GOOGLE_OAUTH_...", set by createUserFromGoogle for
--    brand-new Google signups, never a real hash) never had a usable local
--    password, so they're GOOGLE; any other user with a google_id linked
--    their Google identity to an existing local account, so they're
--    LOCAL_AND_GOOGLE; everyone else is LOCAL.
--
-- 2. google_id was never uniquely constrained — two different Google
--    accounts could theoretically end up linked to the same sub value
--    (or a bug could link one Google identity to two users) with nothing
--    at the DB layer to catch it. A partial unique index (NULL allowed,
--    since most users have no Google identity at all) closes that gap.

ALTER TABLE users
    ADD COLUMN auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL';

UPDATE users
SET auth_provider = CASE
    WHEN google_id IS NOT NULL AND password LIKE 'GOOGLE_OAUTH_%' THEN 'GOOGLE'
    WHEN google_id IS NOT NULL THEN 'LOCAL_AND_GOOGLE'
    ELSE 'LOCAL'
END;

ALTER TABLE users
    ADD CONSTRAINT users_auth_provider_check
    CHECK (auth_provider IN ('LOCAL', 'GOOGLE', 'LOCAL_AND_GOOGLE'));

CREATE UNIQUE INDEX users_google_id_key ON users (google_id) WHERE google_id IS NOT NULL;
