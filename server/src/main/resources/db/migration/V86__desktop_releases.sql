-- ============================================================
-- V86 — Desktop Center: release metadata, download analytics, and the
-- announcement-system extensions needed to promote the Windows desktop app
-- (type/platform/version targeting + backend-persisted per-user dismissal,
-- replacing the client-only sessionStorage dismissal in AnnouncementBanner).
-- ============================================================

CREATE TABLE desktop_releases (
    id                BIGSERIAL PRIMARY KEY,
    platform          VARCHAR(20)  NOT NULL,
    architecture      VARCHAR(20)  NOT NULL,
    version            VARCHAR(50)  NOT NULL,
    semantic_version  VARCHAR(50)  NOT NULL,
    channel           VARCHAR(20)  NOT NULL DEFAULT 'STABLE',
    status            VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    file_name         VARCHAR(255) NOT NULL,
    download_url      VARCHAR(1000) NOT NULL,
    file_size_bytes   BIGINT,
    sha256            VARCHAR(64),
    minimum_os        VARCHAR(100),
    mandatory_update  BOOLEAN NOT NULL DEFAULT FALSE,
    published_at      TIMESTAMP,
    release_notes_en  TEXT,
    release_notes_fr  TEXT,
    release_notes_ar  TEXT,
    created_by        VARCHAR(255),
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_desktop_releases_lookup
    ON desktop_releases (platform, architecture, status, published_at DESC);

CREATE TABLE desktop_download_events (
    id             BIGSERIAL PRIMARY KEY,
    release_id     BIGINT REFERENCES desktop_releases(id),
    version        VARCHAR(50),
    platform       VARCHAR(20),
    architecture   VARCHAR(20),
    agency_id      BIGINT REFERENCES tenants(id),
    source         VARCHAR(30) NOT NULL,
    status         VARCHAR(20) NOT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_desktop_download_events_release ON desktop_download_events (release_id);
CREATE INDEX idx_desktop_download_events_created ON desktop_download_events (created_at);

ALTER TABLE announcements
    ADD COLUMN type          VARCHAR(40) NOT NULL DEFAULT 'GENERIC',
    ADD COLUMN platform      VARCHAR(20),
    ADD COLUMN version       VARCHAR(50),
    ADD COLUMN dismissible   BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN cooldown_days INTEGER NOT NULL DEFAULT 30,
    ADD COLUMN action_url    VARCHAR(1000);

CREATE TABLE announcement_dismissals (
    id               BIGSERIAL PRIMARY KEY,
    announcement_id  BIGINT NOT NULL REFERENCES announcements(id),
    user_id          BIGINT NOT NULL REFERENCES users(id),
    dismissed_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (announcement_id, user_id)
);
