-- ============================================================
-- V35 — Performance indexes
-- Safe: CREATE INDEX IF NOT EXISTS only — no data changes.
-- ============================================================

-- tenants
CREATE INDEX IF NOT EXISTS idx_tenant_status         ON tenants (status);
CREATE INDEX IF NOT EXISTS idx_tenant_plan            ON tenants (plan_name);
CREATE INDEX IF NOT EXISTS idx_tenant_sub_active      ON tenants (subscription_active);

-- users
CREATE INDEX IF NOT EXISTS idx_user_tenant            ON users (tenant_id);
CREATE INDEX IF NOT EXISTS idx_user_email             ON users (email);
CREATE INDEX IF NOT EXISTS idx_user_role              ON users (tenant_id, role);

-- branches
CREATE INDEX IF NOT EXISTS idx_branch_tenant          ON branches (tenant_id);

-- vehicles
CREATE INDEX IF NOT EXISTS idx_vehicle_tenant         ON vehicles (tenant_id)         WHERE coalesce(deleted, false) = false;
CREATE INDEX IF NOT EXISTS idx_vehicle_status_tenant  ON vehicles (tenant_id, statut) WHERE coalesce(deleted, false) = false;
CREATE INDEX IF NOT EXISTS idx_vehicle_plate          ON vehicles (tenant_id, plate)  WHERE plate IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_vehicle_gps            ON vehicles (gps_device_id)     WHERE gps_device_id IS NOT NULL;

-- clients
CREATE INDEX IF NOT EXISTS idx_client_tenant          ON clients (tenant_id)                    WHERE coalesce(deleted, false) = false;
CREATE INDEX IF NOT EXISTS idx_client_phone           ON clients (tenant_id, phone)             WHERE phone IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_client_email_idx       ON clients (tenant_id, email)             WHERE email IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_client_name            ON clients (tenant_id, name)              WHERE coalesce(deleted, false) = false;
CREATE INDEX IF NOT EXISTS idx_client_cin             ON clients (tenant_id, cin)               WHERE cin IS NOT NULL;

-- reservations
CREATE INDEX IF NOT EXISTS idx_reservation_tenant     ON reservations (tenant_id)               WHERE coalesce(deleted, false) = false;
CREATE INDEX IF NOT EXISTS idx_reservation_vehicle    ON reservations (vehicle_id);
CREATE INDEX IF NOT EXISTS idx_reservation_client     ON reservations (client_id);
CREATE INDEX IF NOT EXISTS idx_reservation_status     ON reservations (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_reservation_dates      ON reservations (tenant_id, date_start, date_end);

-- contracts
CREATE INDEX IF NOT EXISTS idx_contract_tenant        ON contracts (tenant_id)                  WHERE coalesce(deleted, false) = false;
CREATE INDEX IF NOT EXISTS idx_contract_status        ON contracts (tenant_id, contract_status) WHERE coalesce(deleted, false) = false;
CREATE INDEX IF NOT EXISTS idx_contract_client        ON contracts (client_id);
CREATE INDEX IF NOT EXISTS idx_contract_vehicle       ON contracts (vehicle_id);
CREATE INDEX IF NOT EXISTS idx_contract_dates         ON contracts (tenant_id, start_date, end_date);
CREATE UNIQUE INDEX IF NOT EXISTS idx_contract_qr     ON contracts (qr_token) WHERE qr_token IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_contract_num    ON contracts (contract_number);

-- payments
CREATE INDEX IF NOT EXISTS idx_payment_tenant         ON payments (tenant_id);
CREATE INDEX IF NOT EXISTS idx_payment_contract       ON payments (contract_id);
CREATE INDEX IF NOT EXISTS idx_payment_client         ON payments (client_id);
CREATE INDEX IF NOT EXISTS idx_payment_status         ON payments (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_payment_type           ON payments (tenant_id, type);
CREATE INDEX IF NOT EXISTS idx_payment_date           ON payments (tenant_id, payment_date);

-- deposits
CREATE INDEX IF NOT EXISTS idx_deposit_tenant         ON deposits (tenant_id);
CREATE INDEX IF NOT EXISTS idx_deposit_contract       ON deposits (contract_id);
CREATE INDEX IF NOT EXISTS idx_deposit_reservation    ON deposits (reservation_id);
CREATE INDEX IF NOT EXISTS idx_deposit_status         ON deposits (tenant_id, status);

-- employees
CREATE INDEX IF NOT EXISTS idx_employee_tenant        ON employees (tenant_id)                  WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_employee_status        ON employees (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_employee_user          ON employees (user_id)                    WHERE user_id IS NOT NULL;

-- vehicle_maintenance
CREATE INDEX IF NOT EXISTS idx_maintenance_tenant_vehicle ON vehicle_maintenance (tenant_id, vehicle_id);
CREATE INDEX IF NOT EXISTS idx_maintenance_status         ON vehicle_maintenance (tenant_id, status);

-- notifications
CREATE INDEX IF NOT EXISTS idx_notification_tenant    ON notifications (tenant_id);
CREATE INDEX IF NOT EXISTS idx_notification_read      ON notifications (tenant_id, read);

-- inspections
CREATE INDEX IF NOT EXISTS idx_inspection_tenant      ON inspections (tenant_id);
CREATE INDEX IF NOT EXISTS idx_inspection_vehicle     ON inspections (vehicle_id);
CREATE INDEX IF NOT EXISTS idx_inspection_contract    ON inspections (contract_id);
CREATE INDEX IF NOT EXISTS idx_inspection_token       ON inspections (token);
CREATE INDEX IF NOT EXISTS idx_inspection_expires     ON inspections (media_expires_at);

-- inspection_media
CREATE INDEX IF NOT EXISTS idx_inspection_media_insp  ON inspection_media (inspection_id);

-- gps_alerts
CREATE INDEX IF NOT EXISTS idx_gps_alert_tenant       ON gps_alerts (tenant_id);
CREATE INDEX IF NOT EXISTS idx_gps_alert_vehicle      ON gps_alerts (vehicle_id);
CREATE INDEX IF NOT EXISTS idx_gps_alert_read         ON gps_alerts (tenant_id, read);

-- gps_devices
CREATE INDEX IF NOT EXISTS idx_gps_devices_tenant     ON gps_devices (tenant_id);
CREATE INDEX IF NOT EXISTS idx_gps_devices_vehicle    ON gps_devices (vehicle_id) WHERE vehicle_id IS NOT NULL;

-- audit_logs
CREATE INDEX IF NOT EXISTS idx_audit_log_tenant       ON audit_logs (tenant_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_user         ON audit_logs (performed_by_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_entity       ON audit_logs (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_created      ON audit_logs (created_at);

-- login_attempts
CREATE INDEX IF NOT EXISTS idx_login_attempt_email    ON login_attempts (email, attempted_at);
CREATE INDEX IF NOT EXISTS idx_login_attempt_ip       ON login_attempts (ip_address, attempted_at);
CREATE INDEX IF NOT EXISTS idx_login_attempt_user     ON login_attempts (user_id)      WHERE user_id IS NOT NULL;

-- refresh_tokens
CREATE INDEX IF NOT EXISTS idx_refresh_token_user     ON refresh_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_token_expires  ON refresh_tokens (expires_at)   WHERE revoked = false;

-- password_reset_tokens
CREATE INDEX IF NOT EXISTS idx_pwd_reset_user         ON password_reset_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_pwd_reset_expires      ON password_reset_tokens (expires_at);

-- email_verification_tokens
CREATE INDEX IF NOT EXISTS idx_email_verify_user      ON email_verification_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_email_verify_expires   ON email_verification_tokens (expires_at);

-- email_logs
CREATE INDEX IF NOT EXISTS idx_email_log_tenant       ON email_logs (tenant_id);
CREATE INDEX IF NOT EXISTS idx_email_log_contract     ON email_logs (contract_id);
CREATE INDEX IF NOT EXISTS idx_email_log_recipient    ON email_logs (recipient);
CREATE INDEX IF NOT EXISTS idx_email_log_status       ON email_logs (status, sent_at);

-- plan_features
CREATE INDEX IF NOT EXISTS idx_plan_feature_plan      ON plan_features (plan_id);
CREATE INDEX IF NOT EXISTS idx_plan_feature_code      ON plan_features (feature_code);

-- tenant_feature_overrides
CREATE INDEX IF NOT EXISTS idx_tenant_feature_override ON tenant_feature_overrides (tenant_id, feature_code);

-- agency_balance_transactions
CREATE INDEX IF NOT EXISTS idx_balance_tx_tenant      ON agency_balance_transactions (tenant_id);
CREATE INDEX IF NOT EXISTS idx_balance_tx_created     ON agency_balance_transactions (created_at);

-- ai_audit_logs
CREATE INDEX IF NOT EXISTS idx_ai_audit_tenant        ON ai_audit_logs (agency_id);
CREATE INDEX IF NOT EXISTS idx_ai_audit_user          ON ai_audit_logs (user_id);
CREATE INDEX IF NOT EXISTS idx_ai_audit_created       ON ai_audit_logs (created_at);

-- role_permissions
CREATE INDEX IF NOT EXISTS idx_role_perm_tenant       ON role_permissions (tenant_id, role_name);

-- promo_code_redemptions
CREATE INDEX IF NOT EXISTS idx_promo_redemption_tenant ON promo_code_redemptions (tenant_id);
CREATE INDEX IF NOT EXISTS idx_promo_redemption_promo  ON promo_code_redemptions (promo_code_id);

-- subscription_invoices
CREATE INDEX IF NOT EXISTS idx_sub_invoice_tenant     ON subscription_invoices (tenant_id);

-- cancellation_requests
CREATE INDEX IF NOT EXISTS idx_cancel_req_tenant      ON cancellation_requests (tenant_id);

-- data_reset_audit_logs
CREATE INDEX IF NOT EXISTS idx_data_reset_tenant      ON data_reset_audit_logs (tenant_id);
CREATE INDEX IF NOT EXISTS idx_data_reset_created     ON data_reset_audit_logs (created_at);
