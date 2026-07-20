import api from './axios';

/** Automation Center (Phase 1) — every call is Premium-gated server-side; a 403
 * with code PREMIUM_FEATURE_REQUIRED means the tenant's plan doesn't include it. */
export const automationApi = {
  getOverview: () => api.get('/automation/overview'),
  getAgents: () => api.get('/automation/agents'),
  setAgentEnabled: (key: string, enabled: boolean) => api.patch(`/automation/agents/${key}`, { enabled }),
  getRuns: () => api.get('/automation/runs'),
  getAlerts: () => api.get('/automation/alerts'),
  acknowledgeAlert: (id: number) => api.post(`/automation/alerts/${id}/acknowledge`),
};
