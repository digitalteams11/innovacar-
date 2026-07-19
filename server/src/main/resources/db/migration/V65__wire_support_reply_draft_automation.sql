-- SUPPORT_REPLY_DRAFT was seeded in V48 as an inert catalog entry ("Not yet
-- triggered by the support module"). SupportAiAssistantService now actually
-- calls it from the Super Admin ticket detail "Generate AI Draft" button, so
-- flip it to wired and give it a real description — the system prompt itself
-- is built in code (SupportAiAssistantService.SYSTEM_INSTRUCTION), not here,
-- since it needs to stay in sync with the Java-side prompt-building logic.
UPDATE ai_automations
   SET wired = TRUE,
       description = 'Drafts a support ticket reply for staff to review and edit before sending. Triggered by the "Generate AI Draft" button on the Super Admin ticket detail page.',
       updated_at = NOW()
 WHERE automation_code = 'SUPPORT_REPLY_DRAFT';
