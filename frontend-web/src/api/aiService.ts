import api from './axios';

/**
 * All AI calls go through the backend â€” no provider is ever called from the
 * browser and no API key ever appears in any response these methods read.
 */
const disabledAiStatus = (message = 'AI is not available on your current plan.') => ({
  data: {
    enabled: false,
    configured: false,
    reachable: false,
    fallbackMode: false,
    message,
  },
});

export const aiService = {
  getStatus: async () => {
    try {
      return await api.get('/ai/status');
    } catch (err: any) {
      const status = err?.response?.status;
      const errorCode = err?.response?.data?.errorCode || err?.response?.data?.error;
      if (status === 403 && ['AI_FEATURE_NOT_AVAILABLE', 'FEATURE_NOT_AVAILABLE', 'FEATURE_NOT_AVAILABLE_IN_PLAN'].includes(errorCode)) {
        return disabledAiStatus(err?.response?.data?.message || 'AI is not available on your current plan.');
      }
      throw err;
    }
  },

  chat: (
    message: string,
    options?: {
      module?: string;
      route?: string;
      conversationId?: string;
    }
  ) =>
    api.post('/ai/chat', {
      message,
      conversationId: options?.conversationId,
      pageContext: {
        module: options?.module,
        route: options?.route,
      },
    }),

  executeAutomation: (code: string, payload: { variables?: Record<string, string>; userMessage?: string }) =>
    api.post(`/ai/execute/${code}`, payload),

  // ── Global settings (Super Admin) ─────────────────────────────────────────
  getSettings: () => api.get('/super-admin/ai/settings'),
  updateSettings: (data: any) => api.put('/super-admin/ai/settings', data),

  // ── Providers (Super Admin) ───────────────────────────────────────────────
  getProviders: () => api.get('/super-admin/ai/providers'),
  getProvider: (id: number) => api.get(`/super-admin/ai/providers/${id}`),
  createProvider: (data: any) => api.post('/super-admin/ai/providers', data),
  updateProvider: (id: number, data: any) => api.put(`/super-admin/ai/providers/${id}`, data),
  deleteProvider: (id: number) => api.delete(`/super-admin/ai/providers/${id}`),
  testProviderConnection: (id: number, tempApiKey?: string) =>
    api.post(`/super-admin/ai/providers/${id}/test`, tempApiKey ? { apiKey: tempApiKey } : {}),
  activateProvider: (id: number) => api.post(`/super-admin/ai/providers/${id}/activate`),
  disableProvider: (id: number) => api.post(`/super-admin/ai/providers/${id}/disable`),

  // ── Models (Super Admin) ──────────────────────────────────────────────────
  getModels: (providerId: number) => api.get(`/super-admin/ai/providers/${providerId}/models`),
  addModel: (providerId: number, data: any) => api.post(`/super-admin/ai/providers/${providerId}/models`, data),
  syncModels: (providerId: number) => api.post(`/super-admin/ai/providers/${providerId}/models/sync`),
  setModelEnabled: (modelId: number, enabled: boolean) =>
    api.put(`/super-admin/ai/models/${modelId}`, { enabled }),
  setDefaultModel: (modelId: number) => api.put(`/super-admin/ai/models/${modelId}`, { defaultModel: true }),
  deleteModel: (modelId: number) => api.delete(`/super-admin/ai/models/${modelId}`),

  // ── Automations (Super Admin) ─────────────────────────────────────────────
  getAutomations: () => api.get('/super-admin/ai/automations'),
  getAutomation: (id: number) => api.get(`/super-admin/ai/automations/${id}`),
  updateAutomation: (id: number, data: any) => api.put(`/super-admin/ai/automations/${id}`, data),
  deleteAutomation: (id: number) => api.delete(`/super-admin/ai/automations/${id}`),
  enableAutomation: (id: number) => api.post(`/super-admin/ai/automations/${id}/enable`),
  disableAutomation: (id: number) => api.post(`/super-admin/ai/automations/${id}/disable`),

  // ── Usage & costs (Super Admin) ───────────────────────────────────────────
  getUsageSummary: () => api.get('/super-admin/ai/usage/summary'),
  getUsageLogs: (page = 0, size = 20) => api.get('/super-admin/ai/usage/logs', { params: { page, size } }),
  getUsageErrors: (page = 0, size = 20) => api.get('/super-admin/ai/usage/errors', { params: { page, size } }),
  deleteUsageLog: (id: number) => api.delete(`/super-admin/ai/usage/logs/${id}`),
  clearUsageLogs: () => api.delete('/super-admin/ai/usage/logs'),
};
