import { useRef, useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { X, Send, Loader2, Sparkles, AlertTriangle, Trash2, ArrowRight, Settings } from 'lucide-react';
import { aiService } from '../../api/aiService';
import { useAuth } from '../../context/AuthContext';

interface ChatMessage {
  role: 'user' | 'assistant' | 'error';
  text: string;
  errorCode?: string;
  suggestedActions?: Array<{ label: string; route: string }>;
}

const QUICK_PROMPTS = [
  'How do I create a contract?',
  'Why is a vehicle unavailable?',
  'How do I restore a deleted contract?',
  'How do QR signatures work?',
  'How do I add a new client?',
  'How do I track fuel at return?',
  'Explain the dashboard numbers.',
  'How do subscriptions work?',
];

/**
 * Returns a user-facing message for each AI error code. The backend AI
 * provider is configurable per platform (Groq by default, or Gemini/OpenAI/
 * OpenRouter/a custom endpoint — see Super Admin → AI & Automation), so these
 * messages must stay provider-neutral rather than naming a specific vendor.
 */
function errorMessage(code: string | undefined, raw?: string): string {
  switch (code) {
    case 'AI_DISABLED':
      return 'AI is disabled by the platform administrator. Enable it in Super Admin → AI & Automation.';
    case 'AI_API_KEY_MISSING':
    case 'AI_KEY_NOT_CONFIGURED':
    case 'AI_NOT_CONFIGURED':
    case 'AI_NO_ACTIVE_PROVIDER':
      return 'No AI provider is configured. The Super Admin needs to set up and activate a provider in AI & Automation.';
    case 'AI_KEY_DECRYPTION_FAILED':
      return 'The saved AI provider API key is corrupted. The Super Admin needs to re-enter and save it.';
    case 'AI_CHAT_DISABLED':
      return 'AI Chat Assistant is disabled. The Super Admin can enable it in AI & Automation settings.';
    case 'AI_API_KEY_INVALID':
    case 'AI_INVALID_API_KEY':
      return 'The AI provider rejected the configured API key. Super Admin should check the key and re-save it.';
    case 'AI_PROVIDER_AUTH_FORBIDDEN':
      return 'AI provider access is forbidden. The API key may lack required permissions. Super Admin should verify the key.';
    case 'AI_MODEL_NOT_FOUND':
    case 'AI_MODEL_DISABLED':
      return 'The configured AI model is not available. Super Admin should choose another model in AI & Automation.';
    case 'AI_QUOTA_EXCEEDED':
      return 'The AI provider quota has been exceeded. Try again later or contact the platform administrator.';
    case 'AI_PROVIDER_TIMEOUT':
      return 'The AI provider did not respond in time. Try again, or the Super Admin can increase the timeout in AI settings.';
    case 'AI_LIMIT_REACHED':
      return 'You have reached the daily AI usage limit. Try again tomorrow.';
    case 'AI_NETWORK_ERROR':
      return 'The server cannot reach the AI provider. The Super Admin should check outbound internet access.';
    case 'AI_PROVIDER_UNREACHABLE':
    case 'AI_PROVIDER_DISABLED':
      return 'The AI provider connection was refused or is disabled. The Super Admin should check AI & Automation settings.';
    case 'AI_SERVICE_UNAVAILABLE':
      return 'The AI service is currently unavailable. Please try again later.';
    case 'AI_FEATURE_NOT_AVAILABLE':
    case 'FEATURE_NOT_INCLUDED_IN_PLAN':
      return 'AI Assistant is not included in your current subscription plan. Upgrade your plan to unlock it.';
    default:
      return raw || 'AI is currently unavailable. Please try again later.';
  }
}

/** Error codes that indicate a Super Admin settings issue (not a user error). */
const SETTINGS_ERROR_CODES = new Set([
  'AI_DISABLED',
  'AI_API_KEY_MISSING',
  'AI_KEY_NOT_CONFIGURED',
  'AI_NOT_CONFIGURED',
  'AI_NO_ACTIVE_PROVIDER',
  'AI_KEY_DECRYPTION_FAILED',
  'AI_CHAT_DISABLED',
  'AI_API_KEY_INVALID',
  'AI_INVALID_API_KEY',
  'AI_PROVIDER_AUTH_FORBIDDEN',
  'AI_MODEL_NOT_FOUND',
  'AI_MODEL_DISABLED',
  'AI_NETWORK_ERROR',
  'AI_PROVIDER_UNREACHABLE',
  'AI_PROVIDER_DISABLED',
]);

/** Error codes that mean "upgrade your plan" — worth a distinct CTA rather than a dead-end message. */
const PLAN_UPGRADE_ERROR_CODES = new Set(['AI_FEATURE_NOT_AVAILABLE', 'FEATURE_NOT_INCLUDED_IN_PLAN']);

interface Props {
  module?: string;
  onClose: () => void;
  onThinkingChange?: (thinking: boolean) => void;
  /**
   * 'floating' (default): self-positioned card used on desktop (fixed, own
   * width/border/shadow). 'sheet': fills its parent instead — used when
   * embedded inside MobileBottomSheet on mobile, which already provides the
   * sheet's own frame/backdrop/positioning.
   */
  variant?: 'floating' | 'sheet';
}

export default function AiChatPanel({ module, onClose, onThinkingChange, variant = 'floating' }: Props) {
  const navigate = useNavigate();
  const { user } = useAuth();
  const isSuperAdmin = user?.role === 'SUPER_ADMIN';

  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [conversationId, setConversationId] = useState<string | undefined>(undefined);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, sending]);

  const setThinking = useCallback((v: boolean) => {
    setSending(v);
    onThinkingChange?.(v);
  }, [onThinkingChange]);

  const send = async (text?: string) => {
    const msg = (text ?? input).trim();
    if (!msg || sending) return;
    setInput('');
    setMessages((prev) => [...prev, { role: 'user', text: msg }]);
    setThinking(true);
    try {
      const { data } = await aiService.chat(msg, {
        module,
        route: window.location.pathname,
        conversationId,
      });
      const chatData = data?.data || {};
      const reply: string = chatData.answer || chatData.reply || '';
      const actions: Array<{ label: string; route: string }> = chatData.suggestedActions || [];
      if (chatData.conversationId) setConversationId(chatData.conversationId);
      setMessages((prev) => [
        ...prev,
        { role: 'assistant', text: reply, suggestedActions: actions.length > 0 ? actions : undefined },
      ]);
    } catch (err: any) {
      const errCode: string | undefined = err?.response?.data?.errorCode;
      const rawMsg: string | undefined = err?.response?.data?.message;
      setMessages((prev) => [
        ...prev,
        { role: 'error', text: errorMessage(errCode, rawMsg), errorCode: errCode },
      ]);
    } finally {
      setThinking(false);
    }
  };

  const clearChat = () => {
    setMessages([]);
    setConversationId(undefined);
  };

  return (
    <div
      className={
        variant === 'sheet'
          ? 'flex h-full flex-col'
          : 'fixed bottom-[4.5rem] end-4 sm:end-6 z-50 w-[min(380px,calc(100vw-2rem))] rounded-2xl border border-slate-200 dark:border-white/10 bg-white dark:bg-[#0f1a2e] shadow-2xl flex flex-col'
      }
      style={variant === 'floating' ? { maxHeight: 'min(520px, calc(100dvh - 5rem))' } : undefined}
    >
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-slate-100 dark:border-white/10 shrink-0">
        <div className="flex items-center gap-2">
          <Sparkles size={16} className="text-brand-500" />
          <span className="text-sm font-semibold text-slate-700 dark:text-white">AI Assistant</span>
          {messages.length > 0 && (
            <button
              onClick={clearChat}
              title="Clear chat"
              className="ms-1 p-1 text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 rounded transition-colors"
            >
              <Trash2 size={12} />
            </button>
          )}
        </div>
        <button onClick={onClose} className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 transition-colors">
          <X size={16} />
        </button>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto px-4 py-3 space-y-3 min-h-[120px]">
        {messages.length === 0 ? (
          <div className="space-y-3">
            <p className="text-xs text-slate-400 dark:text-slate-500">
              Ask about how to use RentCar, troubleshoot issues, or learn about any feature.
            </p>
            <div className="flex flex-wrap gap-1.5">
              {QUICK_PROMPTS.map((prompt) => (
                <button
                  key={prompt}
                  onClick={() => send(prompt)}
                  disabled={sending}
                  className="text-[11px] px-2.5 py-1.5 rounded-lg border border-slate-200 dark:border-white/10 text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-white/5 hover:border-brand-200 dark:hover:border-brand-400/30 transition-all disabled:opacity-50 text-left"
                >
                  {prompt}
                </button>
              ))}
            </div>
          </div>
        ) : (
          messages.map((msg, idx) => (
            <div key={idx}>
              <div className={`text-sm rounded-xl px-3 py-2 max-w-[88%] whitespace-pre-wrap ${
                msg.role === 'user'
                  ? 'bg-brand-500 text-white ml-auto'
                  : msg.role === 'error'
                  ? 'bg-rose-50 dark:bg-rose-900/30 text-rose-700 dark:text-rose-300'
                  : 'bg-slate-100 dark:bg-white/8 text-slate-700 dark:text-slate-200'
              }`}>
                {msg.role === 'error' && (
                  <div className="flex items-start gap-1.5">
                    <AlertTriangle size={13} className="shrink-0 mt-0.5" />
                    <span>{msg.text}</span>
                  </div>
                )}
                {msg.role !== 'error' && <span>{msg.text}</span>}
              </div>

              {/* "Open AI Settings" shortcut for Super Admin when the error is a settings issue */}
              {msg.role === 'error' && isSuperAdmin && msg.errorCode && SETTINGS_ERROR_CODES.has(msg.errorCode) && (
                <button
                  onClick={() => { navigate('/super-admin/ai-settings'); onClose(); }}
                  className="mt-1.5 ms-1 flex items-center gap-1 text-[11px] px-2.5 py-1 rounded-lg bg-brand-50 dark:bg-brand-900/20 text-brand-600 dark:text-brand-300 border border-brand-100 dark:border-brand-400/20 hover:bg-brand-100 dark:hover:bg-brand-900/40 transition-colors"
                >
                  <Settings size={10} />
                  Open AI Settings
                </button>
              )}

              {/* "Upgrade plan" shortcut for non-Super-Admin users blocked by plan entitlement */}
              {msg.role === 'error' && !isSuperAdmin && msg.errorCode && PLAN_UPGRADE_ERROR_CODES.has(msg.errorCode) && (
                <button
                  onClick={() => { navigate('/subscription'); onClose(); }}
                  className="mt-1.5 ms-1 flex items-center gap-1 text-[11px] px-2.5 py-1 rounded-lg bg-brand-50 dark:bg-brand-900/20 text-brand-600 dark:text-brand-300 border border-brand-100 dark:border-brand-400/20 hover:bg-brand-100 dark:hover:bg-brand-900/40 transition-colors"
                >
                  <ArrowRight size={10} />
                  View Plans
                </button>
              )}

              {msg.suggestedActions && msg.suggestedActions.length > 0 && (
                <div className="flex flex-wrap gap-1.5 mt-1.5 ps-1">
                  {msg.suggestedActions.map((action) => (
                    <button
                      key={action.route}
                      onClick={() => { navigate(action.route); onClose(); }}
                      className="flex items-center gap-1 text-[11px] px-2.5 py-1 rounded-lg bg-brand-50 dark:bg-brand-900/20 text-brand-600 dark:text-brand-300 border border-brand-100 dark:border-brand-400/20 hover:bg-brand-100 dark:hover:bg-brand-900/40 transition-colors"
                    >
                      <ArrowRight size={10} />
                      {action.label}
                    </button>
                  ))}
                </div>
              )}
            </div>
          ))
        )}
        {sending && (
          <div className="flex items-center gap-2 text-xs text-slate-400 dark:text-slate-500">
            <Loader2 size={12} className="animate-spin" /> Thinking...
          </div>
        )}
        <div ref={bottomRef} />
      </div>

      {/* Input */}
      <div className="flex items-center gap-2 px-3 py-3 border-t border-slate-100 dark:border-white/10 shrink-0">
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(); } }}
          placeholder="Ask the AI assistant..."
          disabled={sending}
          className="flex-1 px-3 py-2 rounded-xl border border-slate-200 dark:border-white/10 text-sm bg-white dark:bg-white/5 text-slate-700 dark:text-white outline-none focus:ring-2 ring-brand-100 dark:ring-brand-400/20 focus:border-brand-300 dark:focus:border-brand-400/40 disabled:opacity-60 placeholder:text-slate-400 dark:placeholder:text-slate-500"
        />
        <button
          onClick={() => send()}
          disabled={sending || !input.trim()}
          className="shrink-0 w-9 h-9 rounded-xl bg-brand-500 text-white flex items-center justify-center disabled:opacity-50 hover:bg-brand-600 transition-colors"
        >
          <Send size={15} />
        </button>
      </div>
    </div>
  );
}
