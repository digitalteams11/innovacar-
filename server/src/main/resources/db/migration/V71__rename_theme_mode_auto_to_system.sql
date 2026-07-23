-- Renames the "auto" theme-mode value to "system" (pure relabeling — the
-- resolved behavior is unchanged, this only aligns the stored string with
-- the app's canonical light/dark/system contract). Does NOT touch rows
-- already storing 'light' or 'dark' explicitly.
UPDATE users SET theme_mode = 'system' WHERE theme_mode = 'auto';
