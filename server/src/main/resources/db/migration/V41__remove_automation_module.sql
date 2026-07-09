-- Removes the Automation Studio module (rules engine) tables.
-- Drop order respects FK dependencies: executions/actions reference rules.
DROP TABLE IF EXISTS automation_executions;
DROP TABLE IF EXISTS automation_actions;
DROP TABLE IF EXISTS automation_rules;
