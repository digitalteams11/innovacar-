-- ============================================================
-- V84 — Persist dashboard widget customization server-side.
--
-- Originally authored as V82, but that version number was already recorded
-- as applied in production by the time this migration reached main (V83
-- shipped and deployed first — see MigrationOrderingTest for the same class
-- of incident) — Flyway rejected it with "Detected resolved migration not
-- applied to database: 82" since out-of-order is intentionally disabled.
-- Renumbered to V84, the next version above everything already resolved;
-- the SQL itself is unchanged.
--
-- The "Customize Dashboard" feature previously stored its WidgetConfig[]
-- array only in localStorage (rentcar_dashboard_layout_<userId>) — it did
-- not survive a new browser, a new device, or a fresh profile. This table is
-- keyed per-user (not per-tenant, since layout is a personal preference, not
-- an agency setting) and stores the widget order/visibility as a JSON array
-- string; the frontend keeps localStorage as an instant-paint cache but this
-- table is the source of truth on load.
-- ============================================================

CREATE TABLE IF NOT EXISTS dashboard_layouts (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    widgets_json TEXT         NOT NULL,
    device_type  VARCHAR(20),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_dashboard_layouts_user ON dashboard_layouts(user_id);
