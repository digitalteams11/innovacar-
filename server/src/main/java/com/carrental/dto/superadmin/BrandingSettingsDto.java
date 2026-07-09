package com.carrental.dto.superadmin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Platform branding settings exposed to the Super Admin Settings UI.
 * Deliberately excludes SMTP/security fields that live on the same
 * {@link com.carrental.entity.PlatformSettings} row.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandingSettingsDto {
    private String platformName;
    private String companyName;
    private String platformLogoUrl;
    private String faviconUrl;
    private String primaryColor;
    private String secondaryColor;
    private String accentColor;
    private String defaultCurrency;
    private String defaultLanguage;
    private String defaultTimezone;
    private String supportEmail;
    private String supportPhone;
    private String legalCompanyName;
    private String legalAddress;
    private String websiteUrl;
}
