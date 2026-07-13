export interface AiProviderRow {
  id: number;
  name: string;
  providerType: string;
  baseUrl?: string;
  apiKeyConfigured: boolean;
  apiKeyMasked: string;
  organizationId?: string;
  enabled: boolean;
  isActive: boolean;
  isFallback: boolean;
  connectionStatus: 'NOT_TESTED' | 'CONNECTED' | 'FAILED' | 'DISABLED';
  lastConnectionTestAt?: string;
  lastConnectionError?: string;
  lastTestLatencyMs?: number;
  enabledModelCount: number;
}

export interface AiModelRow {
  id: number;
  providerId: number;
  modelId: string;
  displayName?: string;
  enabled: boolean;
  defaultModel: boolean;
  defaultVisionModel: boolean;
  contextWindow?: number;
  inputPricePerMillion?: number;
  outputPricePerMillion?: number;
  supportsStreaming: boolean;
  supportsJsonMode: boolean;
  supportsToolCalling: boolean;
  source: 'SYNCED' | 'MANUAL';
}

export interface AiAutomationRow {
  id: number;
  code: string;
  name: string;
  description?: string;
  featureType?: string;
  enabled: boolean;
  wired: boolean;
  providerId?: number;
  modelId?: number;
  systemPrompt?: string;
  userPromptTemplate?: string;
  temperature?: number;
  maxOutputTokens?: number;
  allowedRoles?: string;
}

export interface AiSettingsPayload {
  globalEnabled?: boolean;
  activeProviderId?: number;
  activeProviderName?: string;
  activeModelId?: number;
  activeModelName?: string;
  fallbackProviderId?: number;
  fallbackModelId?: number;
  fallbackEnabled?: boolean;
  temperature?: number;
  maxOutputTokens?: number;
  requestTimeoutSeconds?: number;
  maxRetries?: number;
  systemPrompt?: string;
  enableChat?: boolean;
  enableReports?: boolean;
  enableTranslations?: boolean;
  enableSupportAssistant?: boolean;
  enableGuideGenerator?: boolean;
  enableAutomationSuggestions?: boolean;
  enableImageGeneration?: boolean;
  monthlyTokenLimit?: number;
  dailyRequestLimit?: number;
  auditAllActions?: boolean;
}

export interface AiUsageSummary {
  globalEnabled: boolean;
  activeProviderName?: string;
  activeProviderType?: string;
  activeModel?: string;
  connectionStatus: string;
  requestsToday: number;
  successfulToday: number;
  failedToday: number;
  averageLatencyMs?: number;
  mostUsedAutomation?: string;
}

export interface AiUsageLogRow {
  id: number;
  providerId?: number;
  modelId?: number;
  automationCode?: string;
  agencyId?: number;
  userId?: number;
  role?: string;
  status: string;
  inputTokens?: number;
  outputTokens?: number;
  totalTokens?: number;
  estimatedCost?: number;
  latencyMs?: number;
  errorCode?: string;
  createdAt: string;
}

export const PROVIDER_TYPES = ['GROQ', 'GEMINI', 'OPENAI', 'OPENROUTER', 'CUSTOM_OPENAI_COMPATIBLE'] as const;

export const PROVIDER_DEFAULT_BASE_URL: Record<string, string> = {
  GROQ: 'https://api.groq.com/openai/v1',
  OPENAI: 'https://api.openai.com/v1',
  OPENROUTER: 'https://openrouter.ai/api/v1',
  GEMINI: '',
  CUSTOM_OPENAI_COMPATIBLE: '',
};
