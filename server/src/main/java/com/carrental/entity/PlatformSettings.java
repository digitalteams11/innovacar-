package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Global platform settings for the SaaS application.
 */
@Entity
@Table(name = "platform_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Platform name */
    @Column(name = "platform_name")
    private String platformName;

    /** Legal/registered company name (Innovax Technologies) */
    @Column(name = "company_name")
    private String companyName;

    /** Platform logo URL (path under /uploads/branding/...) */
    @Column(name = "logo_url", length = 2000)
    private String logoUrl;

    /** Platform favicon URL (path under /uploads/branding/...) */
    @Column(name = "favicon_url", length = 2000)
    private String faviconUrl;

    /** Primary brand color */
    @Column(name = "primary_color")
    private String primaryColor;

    /** Secondary brand color */
    @Column(name = "secondary_color")
    private String secondaryColor;

    /** Accent brand color */
    @Column(name = "accent_color")
    private String accentColor;

    /** Default platform timezone (IANA id) */
    @Column(name = "default_timezone")
    private String defaultTimezone;

    /** Public support email shown to agencies/clients; also the SUPPORT channel routing destination */
    @Column(name = "support_email")
    private String supportEmail;

    /** Support Center routing destination for CONTACT channel (sales/general/demo/partnership) */
    @Column(name = "contact_email")
    private String contactEmail;

    /** Support Center routing destination for TECHNICAL channel (GPS/SMTP/PDF/API/bug reports) */
    @Column(name = "technical_email")
    private String technicalEmail;

    /** Support Center routing destination for BILLING/SUBSCRIPTION categories (falls back to supportEmail) */
    @Column(name = "billing_email")
    private String billingEmail;

    /** Support Center routing destination for SECURITY category (falls back to supportEmail) */
    @Column(name = "security_email")
    private String securityEmail;

    /** Support Center default fallback destination when no other rule matches */
    @Column(name = "fallback_email")
    private String fallbackEmail;

    /** Public support phone number */
    @Column(name = "support_phone")
    private String supportPhone;

    /** Legal company name for invoices/contracts footers */
    @Column(name = "legal_company_name")
    private String legalCompanyName;

    /** Legal/registered company address */
    @Column(name = "legal_address", length = 1000)
    private String legalAddress;

    /** Public marketing website URL */
    @Column(name = "website_url")
    private String websiteUrl;

    /** Maintenance mode enabled */
    @Column(name = "maintenance_mode")
    private Boolean maintenanceMode;

    /** Maintenance message */
    @Column(name = "maintenance_message", length = 2000)
    private String maintenanceMessage;

    /** Default language */
    @Column(name = "default_language")
    private String defaultLanguage;

    /** Supported languages as comma-separated string */
    @Column(name = "supported_languages")
    private String supportedLanguages;

    /** Default currency */
    @Column(name = "default_currency")
    private String defaultCurrency;

    /** SMTP host */
    @Column(name = "smtp_host")
    private String smtpHost;

    /** SMTP port */
    @Column(name = "smtp_port")
    private Integer smtpPort;

    /** SMTP username */
    @Column(name = "smtp_username")
    private String smtpUsername;

    /** SMTP password (encrypted) */
    @Column(name = "smtp_password_encrypted")
    private String smtpPasswordEncrypted;

    /** SMTP use TLS (STARTTLS on port 587) */
    @Column(name = "smtp_use_tls")
    private Boolean smtpUseTls;

    /** SMTP implicit SSL (port 465) — mutually exclusive with smtpUseTls */
    @Column(name = "smtp_ssl_enabled")
    private Boolean smtpSslEnabled;

    /** SMTP enabled flag — when false the platform SMTP will not deliver mail */
    @Column(name = "smtp_enabled")
    private Boolean smtpEnabled;

    /** Optional reply-to address for outgoing platform emails */
    @Column(name = "smtp_reply_to", length = 150)
    private String smtpReplyTo;

    /** Status of the last Super Admin test email (SENT / FAILED) */
    @Column(name = "last_smtp_test_status", length = 20)
    private String lastSmtpTestStatus;

    /** When the last Super Admin test email was attempted */
    @Column(name = "last_smtp_test_at")
    private LocalDateTime lastSmtpTestAt;

    /** Machine-readable error code from the last failed SMTP test (e.g. SMTP_AUTH_FAILED) */
    @Column(name = "last_smtp_test_error_code", length = 100)
    private String lastSmtpTestErrorCode;

    /** SMTP provider hint (ZOHO / GMAIL / CUSTOM) — used to auto-fill host/port in the UI */
    @Column(name = "smtp_provider", length = 20)
    private String smtpProvider;

    /** From email address */
    @Column(name = "from_email")
    private String fromEmail;

    /** From name */
    @Column(name = "from_name")
    private String fromName;

    /** API rate limit per minute */
    @Column(name = "api_rate_limit")
    private Integer apiRateLimit;

    /** Session timeout in minutes */
    @Column(name = "session_timeout_minutes")
    private Integer sessionTimeoutMinutes;

    /** Max login attempts before lockout */
    @Column(name = "max_login_attempts")
    private Integer maxLoginAttempts;

    /** Lockout duration in minutes */
    @Column(name = "lockout_duration_minutes")
    private Integer lockoutDurationMinutes;

    /** Enable 2FA globally */
    @Column(name = "require_2fa")
    private Boolean require2fa;

    /** Google Analytics ID */
    @Column(name = "analytics_id")
    private String analyticsId;

    /** Custom CSS for white-labeling */
    @Column(name = "custom_css", length = 5000)
    private String customCss;

    /** Super Admin-managed appearance presets */
    @Lob
    @Column(name = "theme_presets_json")
    private String themePresetsJson;

    /** Super Admin-managed landing and onboarding copy */
    @Lob
    @Column(name = "marketing_onboarding_json")
    private String marketingOnboardingJson;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
