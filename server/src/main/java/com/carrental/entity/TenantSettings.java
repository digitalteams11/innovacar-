package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_settings", uniqueConstraints = {
        @UniqueConstraint(name = "uk_tenant_settings_tenant", columnNames = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(nullable = false, length = 10)
    private String language;

    @Column(nullable = false, length = 80)
    private String timezone;

    @Column(name = "smtp_host")
    private String smtpHost;

    @Column(name = "smtp_port")
    private Integer smtpPort;

    @Column(name = "smtp_username")
    private String smtpUsername;

    @Column(name = "smtp_password_encrypted", length = 1000)
    private String smtpPasswordEncrypted;

    @Column(name = "smtp_tls", nullable = false)
    private Boolean smtpTls;

    @Column(name = "notification_in_app", nullable = false)
    private Boolean notificationInApp;

    @Column(name = "notification_email", nullable = false)
    private Boolean notificationEmail;

    @Column(name = "notification_push", nullable = false)
    private Boolean notificationPush;

    @Builder.Default
    @Column(name = "inspection_retention_days", nullable = false, columnDefinition = "integer default 7")
    private Integer inspectionRetentionDays = 7;

    public Integer getInspectionRetentionDays() {
        return inspectionRetentionDays;
    }

    public void setInspectionRetentionDays(Integer inspectionRetentionDays) {
        this.inspectionRetentionDays = inspectionRetentionDays;
    }

    @Lob
    @Column(name = "appearance_json")
    private String appearanceJson;

    @Lob
    @Column(name = "sound_settings_json")
    private String soundSettingsJson;

    @Lob
    @Column(name = "security_settings_json")
    private String securitySettingsJson;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        if (currency == null) currency = "MAD";
        if (language == null) language = "fr";
        if (timezone == null) timezone = "Africa/Casablanca";
        if (smtpTls == null) smtpTls = true;
        if (notificationInApp == null) notificationInApp = true;
        if (notificationEmail == null) notificationEmail = true;
        if (notificationPush == null) notificationPush = false;
        if (inspectionRetentionDays == null) inspectionRetentionDays = 7;
        updatedAt = LocalDateTime.now();
    }
}
