CREATE TABLE ai_automations (
    id                    BIGSERIAL PRIMARY KEY,
    automation_code       VARCHAR(60) NOT NULL UNIQUE,
    name                  VARCHAR(160) NOT NULL,
    description           TEXT,
    feature_type          VARCHAR(60),
    enabled               BOOLEAN NOT NULL DEFAULT TRUE,
    wired                 BOOLEAN NOT NULL DEFAULT FALSE,
    provider_id           BIGINT REFERENCES ai_providers(id) ON DELETE SET NULL,
    model_id              BIGINT REFERENCES ai_models(id) ON DELETE SET NULL,
    system_prompt         TEXT,
    user_prompt_template  TEXT,
    temperature           NUMERIC(3, 2),
    max_output_tokens     INTEGER,
    allowed_roles         TEXT,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP
);

-- CHAT_ASSISTANT is the only automation wired to a real backend flow today
-- (AiAssistantService / POST /api/ai/chat). All other seeded codes are
-- configurable catalog entries only — no other service in the app calls
-- them yet. See AiAutomation entity javadoc.
INSERT INTO ai_automations (automation_code, name, description, feature_type, enabled, wired, system_prompt, user_prompt_template) VALUES
('CHAT_ASSISTANT', 'AI Chat Assistant', 'General-purpose in-app assistant.', 'CHAT', TRUE, TRUE, NULL, NULL),
('CONTRACT_SUMMARY', 'Contract Summary', 'Summarizes an already-loaded contract for a human to review. Not yet triggered by ContractService.', 'CONTRACT', TRUE, FALSE,
 'You are a rental contract summarization assistant. Summarize only the facts provided. Never invent prices, dates, or terms.',
 'Client: {{clientName}}\nVehicle: {{vehicleName}}\nStart date: {{startDate}}\nEnd date: {{endDate}}\nAmount due: {{amountDue}}'),
('CONTRACT_GENERATION', 'Contract Generation Draft', 'Drafts contract language for admin review. Not yet triggered by ContractService.', 'CONTRACT', TRUE, FALSE, NULL, NULL),
('WHATSAPP_REMINDER', 'WhatsApp Reminder Draft', 'Drafts a WhatsApp reminder message. Not yet triggered by any notification service.', 'NOTIFICATION', TRUE, FALSE, NULL, NULL),
('SUPPORT_REPLY_DRAFT', 'Customer Support Reply Draft', 'Drafts a support reply for agent review. Not yet triggered by the support module.', 'SUPPORT', TRUE, FALSE, NULL, NULL),
('TRANSLATION', 'Translation', 'Translates provided text. Not yet triggered by any module.', 'UTILITY', TRUE, FALSE, NULL, NULL),
('GUIDE_GENERATOR', 'Guide Generator', 'Generates a how-to guide. Not yet triggered by any module.', 'UTILITY', TRUE, FALSE, NULL, NULL),
('REPORT_SUMMARY', 'Report Summary', 'Summarizes already-computed report data. Not yet triggered by the reporting module.', 'REPORTING', TRUE, FALSE, NULL, NULL),
('AUTOMATION_SUGGESTION', 'Automation Suggestion', 'Suggests platform automations. Not yet triggered by any module.', 'UTILITY', TRUE, FALSE, NULL, NULL);
