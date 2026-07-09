package com.carrental.service;

import com.carrental.dto.superadmin.BrandingSettingsDto;
import com.carrental.entity.PlatformSettings;
import com.carrental.repository.PlatformSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Owns the Super Admin "Platform Branding" settings: the singleton
 * {@link PlatformSettings} row's branding-related columns, plus logo/favicon
 * file uploads. SMTP, security, and theme-preset fields on the same entity
 * are managed elsewhere and intentionally untouched here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformSettingsService {

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9A-Fa-f]{3,8}$");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/svg+xml");
    private static final long MAX_IMAGE_BYTES = 2L * 1024 * 1024; // 2MB

    private final PlatformSettingsRepository platformSettingsRepository;
    private final AuditLogService auditLogService;

    @Value("${app.branding.storage-dir:uploads/branding}")
    private String storageDir;

    public BrandingSettingsDto getBranding() {
        return toDto(loadOrCreate());
    }

    public BrandingSettingsDto updateBranding(BrandingSettingsDto dto) {
        validate(dto);
        PlatformSettings settings = loadOrCreate();

        settings.setPlatformName(clean(dto.getPlatformName()));
        settings.setCompanyName(clean(dto.getCompanyName()));
        settings.setPrimaryColor(clean(dto.getPrimaryColor()));
        settings.setSecondaryColor(clean(dto.getSecondaryColor()));
        settings.setAccentColor(clean(dto.getAccentColor()));
        settings.setDefaultCurrency(clean(dto.getDefaultCurrency()));
        settings.setDefaultLanguage(clean(dto.getDefaultLanguage()));
        settings.setDefaultTimezone(clean(dto.getDefaultTimezone()));
        settings.setSupportEmail(clean(dto.getSupportEmail()));
        settings.setSupportPhone(clean(dto.getSupportPhone()));
        settings.setLegalCompanyName(clean(dto.getLegalCompanyName()));
        settings.setLegalAddress(clean(dto.getLegalAddress()));
        settings.setWebsiteUrl(clean(dto.getWebsiteUrl()));
        // Logo/favicon URLs are only changed via the dedicated upload endpoints,
        // never accepted as free text here, to avoid a client overwriting them
        // with an arbitrary unvalidated URL.

        platformSettingsRepository.save(settings);
        auditLogService.logSuperAdminAction("UPDATE", "PLATFORM_BRANDING", settings.getId(),
                "Updated platform branding settings");
        return toDto(settings);
    }

    public BrandingSettingsDto uploadLogo(MultipartFile file) {
        String url = storeImage(file, "logo");
        PlatformSettings settings = loadOrCreate();
        settings.setLogoUrl(url);
        platformSettingsRepository.save(settings);
        auditLogService.logSuperAdminAction("UPDATE", "PLATFORM_BRANDING", settings.getId(),
                "Uploaded platform logo");
        return toDto(settings);
    }

    public BrandingSettingsDto uploadFavicon(MultipartFile file) {
        String url = storeImage(file, "favicon");
        PlatformSettings settings = loadOrCreate();
        settings.setFaviconUrl(url);
        platformSettingsRepository.save(settings);
        auditLogService.logSuperAdminAction("UPDATE", "PLATFORM_BRANDING", settings.getId(),
                "Uploaded platform favicon");
        return toDto(settings);
    }

    public BrandingSettingsDto resetToDefault() {
        PlatformSettings settings = loadOrCreate();
        settings.setPlatformName("RentCar");
        settings.setCompanyName("Innovax Technologies");
        settings.setPrimaryColor("#0a0f2c");
        settings.setSecondaryColor("#b69152");
        settings.setAccentColor("#22c55e");
        settings.setDefaultCurrency("MAD");
        settings.setDefaultLanguage("en");
        settings.setDefaultTimezone("Africa/Casablanca");
        settings.setSupportEmail(null);
        settings.setSupportPhone(null);
        settings.setLegalCompanyName("Innovax Technologies");
        settings.setLegalAddress(null);
        settings.setWebsiteUrl(null);
        settings.setLogoUrl(null);
        settings.setFaviconUrl(null);
        platformSettingsRepository.save(settings);
        auditLogService.logSuperAdminAction("RESET", "PLATFORM_BRANDING", settings.getId(),
                "Reset platform branding settings to defaults");
        return toDto(settings);
    }

    private PlatformSettings loadOrCreate() {
        return platformSettingsRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> platformSettingsRepository.save(PlatformSettings.builder().build()));
    }

    private void validate(BrandingSettingsDto dto) {
        if (dto.getPlatformName() != null && dto.getPlatformName().isBlank()) {
            throw new IllegalArgumentException("Platform name cannot be blank");
        }
        requireHexColorIfPresent(dto.getPrimaryColor(), "Primary color");
        requireHexColorIfPresent(dto.getSecondaryColor(), "Secondary color");
        requireHexColorIfPresent(dto.getAccentColor(), "Accent color");
        if (dto.getSupportEmail() != null && !dto.getSupportEmail().isBlank()
                && !EMAIL.matcher(dto.getSupportEmail()).matches()) {
            throw new IllegalArgumentException("Support email is not a valid email address");
        }
        requireUrlIfPresent(dto.getWebsiteUrl(), "Website URL");
    }

    private void requireHexColorIfPresent(String value, String label) {
        if (value != null && !value.isBlank() && !HEX_COLOR.matcher(value).matches()) {
            throw new IllegalArgumentException(label + " must be a valid hex color (e.g. #0a0f2c)");
        }
    }

    private void requireUrlIfPresent(String value, String label) {
        if (value != null && !value.isBlank()
                && !value.toLowerCase(Locale.ROOT).startsWith("http://")
                && !value.toLowerCase(Locale.ROOT).startsWith("https://")) {
            throw new IllegalArgumentException(label + " must start with http:// or https://");
        }
    }

    private String storeImage(MultipartFile file, String kind) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A file is required");
        }
        String contentType = file.getContentType() != null ? file.getContentType().toLowerCase(Locale.ROOT) : "";
        if (!IMAGE_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Only JPG, PNG, WebP, or SVG images are allowed");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Image exceeds the 2MB limit");
        }
        if (contentType.equals("image/svg+xml") && containsScript(file)) {
            throw new IllegalArgumentException("SVG file contains disallowed script content");
        }
        try {
            Path dir = Path.of(storageDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String extension = extensionFor(contentType);
            String timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now());
            // Never use the original filename: avoids path traversal and collisions.
            String fileName = kind + "_" + timestamp + "_" + UUID.randomUUID().toString().replace("-", "") + extension;
            Path destination = dir.resolve(fileName);
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            return publicUrl(destination);
        } catch (IOException exception) {
            log.warn("Unable to store branding {} upload", kind, exception);
            throw new RuntimeException("Unable to upload " + kind + " right now", exception);
        }
    }

    private boolean containsScript(MultipartFile file) {
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            return content.contains("<script") || content.contains("onload=") || content.contains("onerror=");
        } catch (IOException exception) {
            return true;
        }
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "image/svg+xml" -> ".svg";
            default -> throw new IllegalArgumentException("Unsupported image type");
        };
    }

    private String publicUrl(Path destination) {
        Path uploadsRoot = Path.of("uploads").toAbsolutePath().normalize();
        Path absolute = destination.toAbsolutePath().normalize();
        if (absolute.startsWith(uploadsRoot)) {
            return "/uploads/" + uploadsRoot.relativize(absolute).toString().replace('\\', '/');
        }
        return "/" + absolute.normalize().toString().replace('\\', '/');
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BrandingSettingsDto toDto(PlatformSettings settings) {
        return BrandingSettingsDto.builder()
                .platformName(settings.getPlatformName())
                .companyName(settings.getCompanyName())
                .platformLogoUrl(settings.getLogoUrl())
                .faviconUrl(settings.getFaviconUrl())
                .primaryColor(settings.getPrimaryColor())
                .secondaryColor(settings.getSecondaryColor())
                .accentColor(settings.getAccentColor())
                .defaultCurrency(settings.getDefaultCurrency())
                .defaultLanguage(settings.getDefaultLanguage())
                .defaultTimezone(settings.getDefaultTimezone())
                .supportEmail(settings.getSupportEmail())
                .supportPhone(settings.getSupportPhone())
                .legalCompanyName(settings.getLegalCompanyName())
                .legalAddress(settings.getLegalAddress())
                .websiteUrl(settings.getWebsiteUrl())
                .build();
    }
}
