import api from './axios';

/**
 * All AI calls go through the backend â€” Gemini is never called from the
 * browser and the API key never appears in any response these methods read.
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

  // Super Admin only
  getSettings: () => api.get('/super-admin/ai/settings'),
  updateSettings: (data: any) => api.put('/super-admin/ai/settings', data),
  testConnection: (tempApiKey?: string) =>
    api.post('/super-admin/ai/test', tempApiKey ? { apiKey: tempApiKey } : {}),
  clearApiKey: () => api.put('/super-admin/ai/settings', { apiKey: '**CLEAR**' }),
  getAuditLogs: (page = 0, size = 20) =>
    api.get('/super-admin/ai/audit-logs', { params: { page, size } }),
  deleteAuditLog: (id: number) => api.delete(`/super-admin/ai/audit-logs/${id}`),
  clearAuditLogs: () => api.delete('/super-admin/ai/audit-logs'),
};
