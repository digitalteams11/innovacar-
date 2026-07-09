-- ============================================================
-- V20 — Plan access control: update limits, seed features, assign to plans
-- Safe: idempotent via ON CONFLICT DO UPDATE and conditional UPDATEs
-- ============================================================

-- ── 1. Update plan limits to match tier definitions ──────────────────────────

UPDATE subscription_plans SET
    max_vehicles    = 25,
    max_employees   = 10,
    max_gps_devices = 10,
    max_reservations= 500,
    client_limit    = 500,
    contract_limit  = 500,
    updated_at      = NOW()
WHERE code = 'BASIC';

UPDATE subscription_plans SET
    max_vehicles    = 75,
    max_employees   = 25,
    max_gps_devices = 30,
    max_reservations= 2000,
    client_limit    = 2000,
    contract_limit  = 2000,
    updated_at      = NOW()
WHERE code = 'STANDARD';

UPDATE subscription_plans SET
    max_vehicles    = 200,
    max_employees   = 60,
    max_gps_devices = 100,
    max_reservations= 10000,
    client_limit    = 10000,
    contract_limit  = 10000,
    api_access      = TRUE,
    white_label     = TRUE,
    priority_support= TRUE,
    updated_at      = NOW()
WHERE code = 'PREMIUM';

-- ── 2. Ensure feature_definitions table exists ───────────────────────────────

CREATE TABLE IF NOT EXISTS feature_definitions (
    id          BIGSERIAL   PRIMARY KEY,
    code        VARCHAR(80) NOT NULL UNIQUE,
    name        VARCHAR(200),
    description VARCHAR(500),
    benefits    VARCHAR(500),
    category    VARCHAR(100),
    active      BOOLEAN     DEFAULT TRUE,
    created_at  TIMESTAMP   DEFAULT NOW(),
    updated_at  TIMESTAMP   DEFAULT NOW()
);

-- ── 3. Upsert feature_definitions (22 features) ──────────────────────────────

INSERT INTO feature_definitions (code, name, description, benefits, category, active) VALUES
    ('VEHICLE_MANAGEMENT',    'Vehicle Management',    'Manage your vehicle fleet',                       'Add, edit, and track all vehicles in your fleet',                      'Core',      TRUE),
    ('CLIENT_MANAGEMENT',     'Client Management',     'Manage your client database',                     'Build and maintain comprehensive client profiles',                      'Core',      TRUE),
    ('RESERVATION_MANAGEMENT','Reservations',          'Manage vehicle reservations and bookings',        'Create, track, and manage all vehicle bookings',                        'Core',      TRUE),
    ('CONTRACT_MANAGEMENT',   'Contracts',             'Create and manage rental contracts',              'Full contract lifecycle: creation, signing, and archiving',             'Core',      TRUE),
    ('INVOICE_GENERATION',    'Invoices',              'Generate professional invoices',                  'Branded invoices with automatic calculation and history',               'Finance',   TRUE),
    ('PAYMENTS',              'Payments',              'Payment collection and management',               'Process, record, and track all rental payments',                        'Finance',   TRUE),
    ('MULTI_EMPLOYEE',        'Team Management',       'Add and manage team members',                     'Multi-user access with role-based permissions',                         'Team',      TRUE),
    ('MULTI_BRANCH',          'Multi-Branch',          'Manage multiple agency branches',                 'Expand operations across multiple locations with shared fleet data',    'Team',      TRUE),
    ('REPORTS_BASIC',         'Basic Reports',         'Operational reports and analytics',               'Revenue, vehicle utilization, and client activity reports',             'Analytics', TRUE),
    ('ADVANCED_REPORTS',      'Advanced Analytics',    'Deep-dive analytics and exports',                 'Custom date ranges, export to CSV/Excel, comparative analysis',         'Analytics', TRUE),
    ('GPS_TRACKING',          'GPS Live Tracking',     'Real-time vehicle GPS tracking',                  'Track vehicle positions, speed, and status on a live map',              'GPS',       TRUE),
    ('GPS_ALERTS',            'GPS Alerts',            'GPS event alerts and notifications',              'Receive instant alerts for movement, geofence exits, and offline events','GPS',       TRUE),
    ('PDF_EXPORT',            'PDF Export',            'Export contracts and documents as PDF',           'Professional PDF documents for all contracts and invoices',              'Documents', TRUE),
    ('QR_SIGNATURE',          'QR Signature',          'Digital contract signing via QR code',            'Send contracts for remote signature via a secure QR link',              'Documents', TRUE),
    ('INSPECTION_MEDIA',      'Inspection Photos',     'Photo capture for vehicle inspections',           'Document vehicle condition with photos at pickup and return',            'Documents', TRUE),
    ('CUSTOM_TEMPLATES',      'Custom Templates',      'Create custom contract templates',                'Design branded contract templates with your own content and layout',    'Documents', TRUE),
    ('AI_ASSISTANT',          'AI Assistant',          'AI-powered fleet management assistant',           'Get intelligent answers and automated insights about your operations',  'AI',        TRUE),
    ('AI_REPORTS',            'AI Business Reports',  'AI-generated business analysis reports',          'Automated narrative reports with trend analysis and recommendations',   'AI',        TRUE),
    ('AI_TRANSLATIONS',       'AI Translations',       'Automatic document translation',                  'Translate contracts and documents instantly into multiple languages',    'AI',        TRUE),
    ('WHITE_LABEL',           'White Label',           'Custom branding for your platform',               'Remove RentCar branding and apply your own logo, colors, and domain',  'Branding',  TRUE),
    ('PRIORITY_SUPPORT',      'Priority Support',      'Dedicated priority customer support',             'Faster response times with a dedicated support channel',                'Support',   TRUE),
    ('API_ACCESS',            'API Access',            'Public REST API for integrations',                'Integrate RentCar with your own tools and workflows via REST API',      'Developer', TRUE)
ON CONFLICT (code) DO UPDATE SET
    name        = EXCLUDED.name,
    description = EXCLUDED.description,
    benefits    = EXCLUDED.benefits,
    category    = EXCLUDED.category,
    active      = EXCLUDED.active,
    updated_at  = NOW();

-- ── 4. Seed plan_features per plan (idempotent via ON CONFLICT DO UPDATE) ────

-- Helper macro via DO block — TRIAL plan
DO $$
DECLARE v_id BIGINT;
BEGIN
    SELECT id INTO v_id FROM subscription_plans WHERE code = 'TRIAL';
    IF v_id IS NULL THEN RETURN; END IF;
    INSERT INTO plan_features (plan_id, feature_code, enabled, created_at, updated_at) VALUES
        (v_id,'VEHICLE_MANAGEMENT',    TRUE, NOW(),NOW()),
        (v_id,'CLIENT_MANAGEMENT',     TRUE, NOW(),NOW()),
        (v_id,'RESERVATION_MANAGEMENT',TRUE, NOW(),NOW()),
        (v_id,'CONTRACT_MANAGEMENT',   TRUE, NOW(),NOW()),
        (v_id,'PDF_EXPORT',            TRUE, NOW(),NOW()),
        (v_id,'MULTI_EMPLOYEE',        TRUE, NOW(),NOW()),
        (v_id,'REPORTS_BASIC',         TRUE, NOW(),NOW()),
        (v_id,'INVOICE_GENERATION',    FALSE,NOW(),NOW()),
        (v_id,'PAYMENTS',              FALSE,NOW(),NOW()),
        (v_id,'MULTI_BRANCH',          FALSE,NOW(),NOW()),
        (v_id,'ADVANCED_REPORTS',      FALSE,NOW(),NOW()),
        (v_id,'GPS_TRACKING',          FALSE,NOW(),NOW()),
        (v_id,'GPS_ALERTS',            FALSE,NOW(),NOW()),
        (v_id,'QR_SIGNATURE',          FALSE,NOW(),NOW()),
        (v_id,'INSPECTION_MEDIA',      FALSE,NOW(),NOW()),
        (v_id,'CUSTOM_TEMPLATES',      FALSE,NOW(),NOW()),
        (v_id,'AI_ASSISTANT',          FALSE,NOW(),NOW()),
        (v_id,'AI_REPORTS',            FALSE,NOW(),NOW()),
        (v_id,'AI_TRANSLATIONS',       FALSE,NOW(),NOW()),
        (v_id,'WHITE_LABEL',           FALSE,NOW(),NOW()),
        (v_id,'PRIORITY_SUPPORT',      FALSE,NOW(),NOW()),
        (v_id,'API_ACCESS',            FALSE,NOW(),NOW())
    ON CONFLICT (plan_id, feature_code) DO UPDATE SET enabled=EXCLUDED.enabled, updated_at=NOW();
END $$;

-- BASIC plan
DO $$
DECLARE v_id BIGINT;
BEGIN
    SELECT id INTO v_id FROM subscription_plans WHERE code = 'BASIC';
    IF v_id IS NULL THEN RETURN; END IF;
    INSERT INTO plan_features (plan_id, feature_code, enabled, created_at, updated_at) VALUES
        (v_id,'VEHICLE_MANAGEMENT',    TRUE, NOW(),NOW()),
        (v_id,'CLIENT_MANAGEMENT',     TRUE, NOW(),NOW()),
        (v_id,'RESERVATION_MANAGEMENT',TRUE, NOW(),NOW()),
        (v_id,'CONTRACT_MANAGEMENT',   TRUE, NOW(),NOW()),
        (v_id,'PDF_EXPORT',            TRUE, NOW(),NOW()),
        (v_id,'MULTI_EMPLOYEE',        TRUE, NOW(),NOW()),
        (v_id,'REPORTS_BASIC',         TRUE, NOW(),NOW()),
        (v_id,'INVOICE_GENERATION',    FALSE,NOW(),NOW()),
        (v_id,'PAYMENTS',              FALSE,NOW(),NOW()),
        (v_id,'MULTI_BRANCH',          FALSE,NOW(),NOW()),
        (v_id,'ADVANCED_REPORTS',      FALSE,NOW(),NOW()),
        (v_id,'GPS_TRACKING',          FALSE,NOW(),NOW()),
        (v_id,'GPS_ALERTS',            FALSE,NOW(),NOW()),
        (v_id,'QR_SIGNATURE',          FALSE,NOW(),NOW()),
        (v_id,'INSPECTION_MEDIA',      FALSE,NOW(),NOW()),
        (v_id,'CUSTOM_TEMPLATES',      FALSE,NOW(),NOW()),
        (v_id,'AI_ASSISTANT',          FALSE,NOW(),NOW()),
        (v_id,'AI_REPORTS',            FALSE,NOW(),NOW()),
        (v_id,'AI_TRANSLATIONS',       FALSE,NOW(),NOW()),
        (v_id,'WHITE_LABEL',           FALSE,NOW(),NOW()),
        (v_id,'PRIORITY_SUPPORT',      FALSE,NOW(),NOW()),
        (v_id,'API_ACCESS',            FALSE,NOW(),NOW())
    ON CONFLICT (plan_id, feature_code) DO UPDATE SET enabled=EXCLUDED.enabled, updated_at=NOW();
END $$;

-- STANDARD plan
DO $$
DECLARE v_id BIGINT;
BEGIN
    SELECT id INTO v_id FROM subscription_plans WHERE code = 'STANDARD';
    IF v_id IS NULL THEN RETURN; END IF;
    INSERT INTO plan_features (plan_id, feature_code, enabled, created_at, updated_at) VALUES
        (v_id,'VEHICLE_MANAGEMENT',    TRUE, NOW(),NOW()),
        (v_id,'CLIENT_MANAGEMENT',     TRUE, NOW(),NOW()),
        (v_id,'RESERVATION_MANAGEMENT',TRUE, NOW(),NOW()),
        (v_id,'CONTRACT_MANAGEMENT',   TRUE, NOW(),NOW()),
        (v_id,'PDF_EXPORT',            TRUE, NOW(),NOW()),
        (v_id,'QR_SIGNATURE',          TRUE, NOW(),NOW()),
        (v_id,'INSPECTION_MEDIA',      TRUE, NOW(),NOW()),
        (v_id,'CUSTOM_TEMPLATES',      TRUE, NOW(),NOW()),
        (v_id,'MULTI_EMPLOYEE',        TRUE, NOW(),NOW()),
        (v_id,'MULTI_BRANCH',          TRUE, NOW(),NOW()),
        (v_id,'INVOICE_GENERATION',    TRUE, NOW(),NOW()),
        (v_id,'PAYMENTS',              TRUE, NOW(),NOW()),
        (v_id,'REPORTS_BASIC',         TRUE, NOW(),NOW()),
        (v_id,'ADVANCED_REPORTS',      TRUE, NOW(),NOW()),
        (v_id,'GPS_TRACKING',          TRUE, NOW(),NOW()),
        (v_id,'GPS_ALERTS',            TRUE, NOW(),NOW()),
        (v_id,'AI_ASSISTANT',          FALSE,NOW(),NOW()),
        (v_id,'AI_REPORTS',            FALSE,NOW(),NOW()),
        (v_id,'AI_TRANSLATIONS',       FALSE,NOW(),NOW()),
        (v_id,'WHITE_LABEL',           FALSE,NOW(),NOW()),
        (v_id,'PRIORITY_SUPPORT',      FALSE,NOW(),NOW()),
        (v_id,'API_ACCESS',            FALSE,NOW(),NOW())
    ON CONFLICT (plan_id, feature_code) DO UPDATE SET enabled=EXCLUDED.enabled, updated_at=NOW();
END $$;

-- PREMIUM plan (all features enabled)
DO $$
DECLARE v_id BIGINT;
BEGIN
    SELECT id INTO v_id FROM subscription_plans WHERE code = 'PREMIUM';
    IF v_id IS NULL THEN RETURN; END IF;
    INSERT INTO plan_features (plan_id, feature_code, enabled, created_at, updated_at) VALUES
        (v_id,'VEHICLE_MANAGEMENT',    TRUE,NOW(),NOW()),
        (v_id,'CLIENT_MANAGEMENT',     TRUE,NOW(),NOW()),
        (v_id,'RESERVATION_MANAGEMENT',TRUE,NOW(),NOW()),
        (v_id,'CONTRACT_MANAGEMENT',   TRUE,NOW(),NOW()),
        (v_id,'PDF_EXPORT',            TRUE,NOW(),NOW()),
        (v_id,'QR_SIGNATURE',          TRUE,NOW(),NOW()),
        (v_id,'INSPECTION_MEDIA',      TRUE,NOW(),NOW()),
        (v_id,'CUSTOM_TEMPLATES',      TRUE,NOW(),NOW()),
        (v_id,'MULTI_EMPLOYEE',        TRUE,NOW(),NOW()),
        (v_id,'MULTI_BRANCH',          TRUE,NOW(),NOW()),
        (v_id,'INVOICE_GENERATION',    TRUE,NOW(),NOW()),
        (v_id,'PAYMENTS',              TRUE,NOW(),NOW()),
        (v_id,'REPORTS_BASIC',         TRUE,NOW(),NOW()),
        (v_id,'ADVANCED_REPORTS',      TRUE,NOW(),NOW()),
        (v_id,'GPS_TRACKING',          TRUE,NOW(),NOW()),
        (v_id,'GPS_ALERTS',            TRUE,NOW(),NOW()),
        (v_id,'AI_ASSISTANT',          TRUE,NOW(),NOW()),
        (v_id,'AI_REPORTS',            TRUE,NOW(),NOW()),
        (v_id,'AI_TRANSLATIONS',       TRUE,NOW(),NOW()),
        (v_id,'WHITE_LABEL',           TRUE,NOW(),NOW()),
        (v_id,'PRIORITY_SUPPORT',      TRUE,NOW(),NOW()),
        (v_id,'API_ACCESS',            TRUE,NOW(),NOW())
    ON CONFLICT (plan_id, feature_code) DO UPDATE SET enabled=EXCLUDED.enabled, updated_at=NOW();
END $$;
