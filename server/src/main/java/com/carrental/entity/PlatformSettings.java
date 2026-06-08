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

    /** Platform logo URL (supports base64 data URLs) */
    @Column(name = "logo_url", length = 5000)
    private String logoUrl;

    /** Primary brand color */
    @Column(name = "primary_color")
    private String primaryColor;

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

    /** SMTP use TLS */
    @Column(name = "smtp_use_tls")
    private Boolean smtpUseTls;

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

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
