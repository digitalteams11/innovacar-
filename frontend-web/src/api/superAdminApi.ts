import api from './axios';

export const superAdminApi = {
  // ═══════════════════════════════════════════════════════════════
  // DASHBOARD & OVERVIEW
  // ═══════════════════════════════════════════════════════════════
  getDashboardStats: () => api.get('/super-admin/dashboard'),
  getRecentActivity: () => api.get('/super-admin/dashboard/activity'),
  getPlatformHealth: () => api.get('/super-admin/health'),

  // ═══════════════════════════════════════════════════════════════
  // AGENCIES MANAGEMENT
  // ═══════════════════════════════════════════════════════════════
  getAgencies: (params?: { status?: string; search?: string; page?: number; pageSize?: number }) =>
    api.get('/super-admin/agencies', { params }),
  getAgency: (id: number) => api.get(`/super-admin/agencies/${id}`),
  createAgency: (data: any) => api.post('/super-admin/agencies', data),
  updateAgency: (id: number, data: any) => api.put(`/super-admin/agencies/${id}`, data),
  updateAgencyStatus: (id: number, status: string) =>
    api.patch(`/super-admin/agencies/${id}/status`, { status }),
  deleteAgency: (id: number) => api.delete(`/super-admin/agencies/${id}`),
  restoreAgency: (id: number) => api.post(`/super-admin/agencies/${id}/restore`),
  verifyAgency: (id: number) => api.post(`/super-admin/agencies/${id}/verify`),
  getAgencyUsers: (id: number) => api.get(`/super-admin/agencies/${id}/users`),
  getAgencyActivity: (id: number) => api.get(`/super-admin/agencies/${id}/activity`),
  getAgencyInvoices: (id: number) => api.get(`/super-admin/agencies/${id}/invoices`),
  getAgencySecurityLogs: (id: number) => api.get(`/super-admin/agencies/${id}/security-logs`),
  subscribeAgency: (id: number, planCode: string) =>
    api.post(`/super-admin/agencies/${id}/subscribe`, { planCode }),
  extendTrial: (id: number, days: number) =>
    api.post(`/super-admin/agencies/${id}/extend-trial`, { days }),
  forceRenew: (id: number) => api.post(`/super-admin/agencies/${id}/force-renew`),
  setCustomPrice: (id: number, data: any) => api.post(`/super-admin/agencies/${id}/custom-price`, data),

  // ═══════════════════════════════════════════════════════════════
  // SUBSCRIPTION & BILLING SYSTEM
  // ═══════════════════════════════════════════════════════════════
  getPlans: () => api.get('/super-admin/plans'),
  createPlan: (data: any) => api.post('/super-admin/plans', data),
  updatePlan: (id: number, data: any) => api.put(`/super-admin/plans/${id}`, data),
  deletePlan: (id: number) => api.delete(`/super-admin/plans/${id}`),
  getPromoCodes: () => api.get('/super-admin/promo-codes'),
  createPromoCode: (data: any) => api.post('/super-admin/promo-codes', data),
  updatePromoCode: (id: number, data: any) => api.put(`/super-admin/promo-codes/${id}`, data),
  deletePromoCode: (id: number) => api.delete(`/super-admin/promo-codes/${id}`),
  getTrialAnalytics: () => api.get('/super-admin/trial-analytics'),

  // ═══════════════════════════════════════════════════════════════
  // GPS MANAGEMENT CENTER
  // ═══════════════════════════════════════════════════════════════
  getGlobalGpsStats: () => api.get('/super-admin/gps'),
  getGlobalGpsAlerts: () => api.get('/super-admin/gps/alerts'),
  updateGpsProvider: (id: number, data: any) => api.put(`/super-admin/gps/providers/${id}`, data),

  // ═══════════════════════════════════════════════════════════════
  // USER & ROLE MANAGEMENT
  // ═══════════════════════════════════════════════════════════════
  getAllUsers: (params?: { role?: string; search?: string }) =>
    api.get('/super-admin/users', { params }),
  createUser: (data: any) => api.post('/super-admin/users', data),
  updateUserRole: (id: number, role: string) =>
    api.patch(`/super-admin/users/${id}/role`, { role }),
  deleteUser: (id: number) => api.delete(`/super-admin/users/${id}`),
  getUserSessions: (id: number) => api.get(`/super-admin/users/${id}/sessions`),

  // ═══════════════════════════════════════════════════════════════
  // PAYMENTS & FINANCE
  // ═══════════════════════════════════════════════════════════════
  getRevenueStats: () => api.get('/super-admin/revenue'),
  getAllPayments: () => api.get('/super-admin/payments'),
  getAllInvoices: () => api.get('/super-admin/invoices'),
  retryPayment: (id: number) => api.post(`/super-admin/payments/${id}/retry`),

  // ═══════════════════════════════════════════════════════════════
  // SUPPORT & TICKETS
  // ═══════════════════════════════════════════════════════════════
  getTickets: (status?: string) =>
    api.get('/super-admin/tickets', { params: status ? { status } : undefined }),
  getTicket: (id: number) => api.get(`/super-admin/tickets/${id}`),
  updateTicket: (id: number, data: any) => api.patch(`/super-admin/tickets/${id}`, data),
  createTicket: (data: any) => api.post('/super-admin/tickets', data),
  getTicketNotes: (id: number) => api.get(`/super-admin/tickets/${id}/notes`),
  addTicketNote: (id: number, data: any) => api.post(`/super-admin/tickets/${id}/notes`, data),
  getSupportAnalytics: () => api.get('/super-admin/support/analytics'),

  // ═══════════════════════════════════════════════════════════════
  // NOTIFICATIONS CENTER
  // ═══════════════════════════════════════════════════════════════
  getNotifications: () => api.get('/super-admin/notifications'),
  markNotificationRead: (id: number) => api.patch(`/super-admin/notifications/${id}/read`),

  // ═══════════════════════════════════════════════════════════════
  // ANALYTICS & BUSINESS INTELLIGENCE
  // ═══════════════════════════════════════════════════════════════
  getGrowthAnalytics: () => api.get('/super-admin/analytics/growth'),
  getAgencyAnalytics: () => api.get('/super-admin/analytics/agencies'),
  getReservationAnalytics: () => api.get('/super-admin/analytics/reservations'),

  // ═══════════════════════════════════════════════════════════════
  // EMAIL & COMMUNICATION CENTER
  // ═══════════════════════════════════════════════════════════════
  getEmailTemplates: () => api.get('/super-admin/email/templates'),
  createEmailTemplate: (data: any) => api.post('/super-admin/email/templates', data),
  updateEmailTemplate: (id: number, data: any) => api.put(`/super-admin/email/templates/${id}`, data),
  deleteEmailTemplate: (id: number) => api.delete(`/super-admin/email/templates/${id}`),
  sendTestEmail: (data: any) => api.post('/super-admin/email/test', data),
  getEmailLogs: () => api.get('/super-admin/email/logs'),
  getEmailAnalytics: () => api.get('/super-admin/email/analytics'),

  // ═══════════════════════════════════════════════════════════════
  // MARKETING & ONBOARDING
  // ═══════════════════════════════════════════════════════════════
  getMarketingOnboarding: () => api.get('/super-admin/marketing/onboarding'),
  updateMarketingOnboarding: (data: any) => api.put('/super-admin/marketing/onboarding', data),
  getMarketingConversion: () => api.get('/super-admin/marketing/conversion'),

  // ═══════════════════════════════════════════════════════════════
  // CONTRACTS
  // ═══════════════════════════════════════════════════════════════
  getAllContracts: () => api.get('/super-admin/contracts'),

  // ═══════════════════════════════════════════════════════════════
  // REPORTS
  // ═══════════════════════════════════════════════════════════════
  getReports: (type: string, params?: any) => api.get(`/super-admin/reports/${type}`, { params }),

  // ═══════════════════════════════════════════════════════════════
  // PLATFORM SETTINGS
  // ═══════════════════════════════════════════════════════════════
  getPlatformSettings: () => api.get('/super-admin/settings'),
  updatePlatformSettings: (data: any) => api.put('/super-admin/settings', data),

  // ═══════════════════════════════════════════════════════════════
  // SECURITY & AUDIT CENTER
  // ═══════════════════════════════════════════════════════════════
  getAuditLogs: (params?: { action?: string; startDate?: string; endDate?: string }) =>
    api.get('/super-admin/security/audit-logs', { params }),
  createAuditLog: (data: any) => api.post('/super-admin/security/audit-logs', data),
  getSecuritySummary: () => api.get('/super-admin/security/summary'),
  getLoginHistory: () => api.get('/super-admin/security/login-history'),
  getSessions: () => api.get('/super-admin/security/sessions'),
  revokeSession: (id: number) => api.delete(`/super-admin/security/sessions/${id}`),
  getFailedLogins: () => api.get('/super-admin/security/failed-logins'),
};

export default superAdminApi;
