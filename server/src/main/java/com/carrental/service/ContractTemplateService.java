package com.carrental.service;

import com.carrental.entity.*;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.exception.TemplatePlanRequiredException;
import com.carrental.repository.*;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ContractTemplateService {
    private static final long MAX_TEMPLATE_FILE_SIZE = 20L * 1024L * 1024L;
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/jpg", "image/png", "image/webp", "image/jfif", "image/pjpeg");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".pdf", ".jpg", ".jpeg", ".png", ".webp", ".jfif");

    private final ContractTemplateRepository templateRepository;
    private final ContractTemplateFieldRepository fieldRepository;
    private final ContractTermsRepository termsRepository;
    private final TenantRepository tenantRepository;
    private final ContractTemplatePdfService contractTemplatePdfService;
    private final FeatureAccessService featureAccessService;

    private record SystemTemplateDefinition(
            String code,
            String name,
            String description,
            String language,
            int pagesCount,
            boolean hasConditions,
            String accessPlan,
            String featureCode
    ) {}

    private static final List<SystemTemplateDefinition> SYSTEM_TEMPLATE_CATALOG = List.of(
            new SystemTemplateDefinition("classic-moroccan", "Classic Moroccan Rental Contract",
                    "A4 rental contract with client info, vehicle info, rental dates, fuel, documents, signatures, and terms.",
                    "FR", 2, true, "STARTER", "CONTRACT_TEMPLATES"),
            new SystemTemplateDefinition("modern-a4", "Modern A4 Rental Contract",
                    "Clean professional contract for daily rentals with payment and signature blocks.",
                    "FR", 1, false, "BASIC", "CONTRACT_TEMPLATES"),
            new SystemTemplateDefinition("compact-one-page", "Compact One Page Contract",
                    "Short printable layout for fast counter rentals and small agencies.",
                    "FR", 1, false, "STARTER", "CONTRACT_TEMPLATES"),
            new SystemTemplateDefinition("detailed-agency", "Detailed Agency Contract",
                    "Detailed contract with client, vehicle, pricing, deposit, payment, documents, and signatures.",
                    "FR", 2, true, "STANDARD", "CONTRACT_TEMPLATE_MAPPING"),
            new SystemTemplateDefinition("vehicle-inspection", "Contract with Vehicle Inspection",
                    "Contract including vehicle inspection diagram, mileage, fuel level, documents, and condition notes.",
                    "FR", 2, true, "STANDARD", "CONTRACT_TEMPLATE_MAPPING"),
            new SystemTemplateDefinition("conditions-page", "Contract with Conditions",
                    "Professional rental contract with full terms and conditions page.",
                    "FR", 2, true, "BASIC", "CONTRACT_CONDITIONS_PAGE"),
            new SystemTemplateDefinition("premium-luxury", "Premium Luxury Contract",
                    "Premium layout for luxury vehicle rental agencies with detailed guarantees and inspection.",
                    "FR", 3, true, "PREMIUM", "PREMIUM_CONTRACT_TEMPLATES"),
            new SystemTemplateDefinition("enterprise-custom", "Enterprise Custom Contract",
                    "Advanced contract template for agencies with custom clauses, multi-branch support, and audit records.",
                    "FR", 0, true, "ENTERPRISE", "ENTERPRISE_CONTRACT_TEMPLATES")
    );

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTemplates() {
        Long tenantId = currentTenantId();
        if (tenantId == null) {
            return List.of();
        }
        return templateRepository.findAllByTenantIdOrderByDefaultTemplateDescUpdatedAtDesc(tenantId)
                .stream().map(this::templateResponse).toList();
    }

    public List<Map<String, Object>> listSystemTemplates() {
        // Each entry resolves plan/feature access independently (tenant lookup,
        // feature flags). A single broken lookup (missing agency, transient
        // plan-resolution error) must never 500 the whole catalog — fall back
        // to a locked entry for that one definition and keep listing the rest.
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (SystemTemplateDefinition definition : SYSTEM_TEMPLATE_CATALOG) {
            try {
                result.add(systemTemplate(definition));
            } catch (RuntimeException ex) {
                result.add(lockedSystemTemplate(definition));
            }
        }
        return result;
    }

    private Map<String, Object> lockedSystemTemplate(SystemTemplateDefinition definition) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("key", definition.code());
        response.put("templateCode", definition.code());
        response.put("name", definition.name());
        response.put("description", definition.description());
        response.put("templateType", ContractTemplateType.SYSTEM_DEFAULT);
        response.put("source", "SYSTEM");
        response.put("language", definition.language());
        response.put("pagesCount", definition.pagesCount());
        response.put("pages", definition.pagesCount() == 0 ? "Custom" : definition.pagesCount() + (definition.pagesCount() == 1 ? " page" : " pages"));
        response.put("hasConditions", definition.hasConditions());
        response.put("includesConditions", definition.hasConditions());
        response.put("accessPlan", definition.accessPlan());
        response.put("requiredPlan", definition.accessPlan());
        response.put("featureCode", definition.featureCode());
        response.put("locked", true);
        response.put("unlocked", false);
        response.put("active", true);
        return response;
    }

    @Transactional
    public Map<String, Object> createTemplate(Map<String, Object> body) {
        ContractTemplateType templateType = enumValue(body.get("templateType"), ContractTemplateType.AGENCY_SCAN_TEMPLATE);
        if (templateType == ContractTemplateType.AGENCY_SCAN_TEMPLATE) {
            requireFeature("CUSTOM_CONTRACT_TEMPLATES", "STANDARD");
        }
        Tenant tenant = tenantRepository.findById(tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Agency not found"));
        ContractTemplate template = ContractTemplate.builder()
                .tenant(tenant)
                .name(string(body.get("name"), "Agency scanned contract"))
                .description(string(body.get("description"), "Agency uploaded contract paper"))
                .templateType(templateType)
                .templateCode(string(body.get("templateCode"), null))
                .language(string(body.get("language"), "FR"))
                .pagesCount(intValue(body.get("pagesCount"), 1))
                .hasConditions(bool(body.get("hasConditions"), false))
                .accessPlan(string(body.get("accessPlan"), templateType == ContractTemplateType.AGENCY_SCAN_TEMPLATE ? "STANDARD" : "STARTER"))
                .pageSize(string(body.get("pageSize"), "A4"))
                .active(bool(body.get("active"), true))
                .defaultTemplate(bool(body.get("default"), false))
                .build();
        ContractTemplate saved = templateRepository.save(template);
        if (Boolean.TRUE.equals(saved.getDefaultTemplate())) setDefault(saved.getId());
        return templateResponse(saved);
    }

    @Transactional
    public Map<String, Object> useSystemTemplate(String code) {
        SystemTemplateDefinition definition = findSystemTemplate(code);
        assertTemplateAccess(definition);
        Tenant tenant = tenantRepository.findById(tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Agency not found"));

        ContractTemplate existing = findExistingSystemTemplate(tenant.getId(), definition.code());
        if (existing != null) {
            existing.setActive(true);
            ContractTemplate saved = templateRepository.save(existing);
            // "Use Template" must make this template the agency's default —
            // otherwise contract creation/PDF generation never picks it up,
            // since both only ever resolve the template flagged isDefault=true.
            return setDefault(saved.getId());
        }

        ContractTemplate template = ContractTemplate.builder()
                .tenant(tenant)
                .name(definition.name())
                .description(definition.description())
                .templateType(ContractTemplateType.SYSTEM_DEFAULT)
                .templateCode(definition.code())
                .language(definition.language())
                .pagesCount(definition.pagesCount())
                .hasConditions(definition.hasConditions())
                .accessPlan(definition.accessPlan())
                .pageSize("A4")
                .active(true)
                .defaultTemplate(false)
                .build();
        try {
            ContractTemplate saved = templateRepository.save(template);
            return setDefault(saved.getId());
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // A concurrent click (double "Use Template", or two tabs) can race
            // both requests past the existing-check above. Treat the resulting
            // constraint violation as "already used" and return that record
            // with 200 instead of letting the race surface as a 409/500.
            ContractTemplate raced = findExistingSystemTemplate(tenant.getId(), definition.code());
            if (raced != null) {
                raced.setActive(true);
                ContractTemplate saved = templateRepository.save(raced);
                return setDefault(saved.getId());
            }
            throw ex;
        }
    }

    private ContractTemplate findExistingSystemTemplate(Long tenantId, String code) {
        return templateRepository.findAllByTenantIdOrderByDefaultTemplateDescUpdatedAtDesc(tenantId)
                .stream()
                .filter(template -> code.equals(template.getTemplateCode())
                        && template.getTemplateType() == ContractTemplateType.SYSTEM_DEFAULT)
                .findFirst()
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTemplate(Long id) {
        return templateResponse(template(id));
    }

    @Transactional
    public Map<String, Object> updateTemplate(Long id, Map<String, Object> body) {
        ContractTemplate template = template(id);
        if (body.containsKey("name")) template.setName(string(body.get("name"), template.getName()));
        if (body.containsKey("templateType")) template.setTemplateType(enumValue(body.get("templateType"), template.getTemplateType()));
        if (body.containsKey("pageSize")) template.setPageSize(string(body.get("pageSize"), template.getPageSize()));
        if (body.containsKey("active")) template.setActive(bool(body.get("active"), template.getActive()));
        ContractTemplate saved = templateRepository.save(template);
        if (Boolean.TRUE.equals(body.get("default"))) setDefault(saved.getId());
        return templateResponse(saved);
    }

    @Transactional
    public void deleteTemplate(Long id) {
        templateRepository.delete(template(id));
    }

    @Transactional
    public Map<String, Object> upload(Long id, MultipartFile file, boolean front) {
        requireFeature(front ? "CUSTOM_CONTRACT_TEMPLATES" : "CONTRACT_CONDITIONS_PAGE", front ? "STANDARD" : "BASIC");
        ContractTemplate template = template(id);
        validateFile(file);
        try {
            Long tenantId = tenantId();
            Path dir = Path.of("uploads", "contract-templates", String.valueOf(tenantId), String.valueOf(template.getId()));
            Files.createDirectories(dir);
            String extension = extension(file);
            String fileName = (front ? "front" : "back") + "_" + LocalDateTime.now().format(FILE_TIMESTAMP) + extension;
            Path destination = dir.resolve(fileName).normalize();
            if (!destination.startsWith(dir.normalize())) {
                throw new IllegalArgumentException("Invalid template file path");
            }
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            String path = destination.toAbsolutePath().normalize().toString();
            String url = "/uploads/contract-templates/" + tenantId + "/" + template.getId() + "/" + fileName;
            if (front) {
                template.setFrontFilePath(path);
                template.setFrontFileUrl(url);
            } else {
                template.setBackFilePath(path);
                template.setBackFileUrl(url);
            }
            return templateResponse(templateRepository.save(template));
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to save file. Upload folder could not be created.", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listFields(Long templateId) {
        ContractTemplate template = template(templateId);
        return fieldRepository.findFieldsForTemplate(template.getId())
                .stream().map(this::fieldResponse).toList();
    }

    @Transactional
    public Map<String, Object> createField(Long templateId, Map<String, Object> body) {
        requireFeature("CONTRACT_TEMPLATE_MAPPING", "STANDARD");
        ContractTemplate template = template(templateId);
        ContractTemplateField field = new ContractTemplateField();
        field.setTemplate(template);
        applyField(field, body);
        return fieldResponse(fieldRepository.save(field));
    }

    @Transactional
    public Map<String, Object> updateField(Long templateId, Long fieldId, Map<String, Object> body) {
        requireFeature("CONTRACT_TEMPLATE_MAPPING", "STANDARD");
        template(templateId);
        ContractTemplateField field = fieldRepository.findByIdAndTemplateId(fieldId, templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Template field not found"));
        applyField(field, body);
        return fieldResponse(fieldRepository.save(field));
    }

    @Transactional
    public void deleteField(Long templateId, Long fieldId) {
        requireFeature("CONTRACT_TEMPLATE_MAPPING", "STANDARD");
        template(templateId);
        ContractTemplateField field = fieldRepository.findByIdAndTemplateId(fieldId, templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Template field not found"));
        fieldRepository.delete(field);
    }

    /**
     * Replace the full field mapping of a template in one call.
     * Existing fields are removed and the provided list is persisted, returning the saved fields.
     */
    @Transactional
    public List<Map<String, Object>> replaceFields(Long templateId, List<Map<String, Object>> fields) {
        requireFeature("CONTRACT_TEMPLATE_MAPPING", "STANDARD");
        ContractTemplate template = template(templateId);
        fieldRepository.deleteByTemplateId(template.getId());
        fieldRepository.flush();
        List<Map<String, Object>> saved = new java.util.ArrayList<>();
        if (fields != null) {
            for (Map<String, Object> body : fields) {
                ContractTemplateField field = new ContractTemplateField();
                field.setTemplate(template);
                applyField(field, body);
                saved.add(fieldResponse(fieldRepository.save(field)));
            }
        }
        return saved;
    }

    /**
     * Generate a preview PDF for a template (tenant-scoped) using sample contract data.
     */
    @Transactional(readOnly = true)
    public byte[] previewPdf(Long templateId) {
        return contractTemplatePdfService.generatePreview(template(templateId));
    }

    @Transactional
    public Map<String, Object> setDefault(Long id) {
        ContractTemplate selected = template(id);
        templateRepository.findAllByTenantIdAndDefaultTemplateTrue(tenantId())
                .forEach(item -> {
                    if (!item.getId().equals(selected.getId())) {
                        item.setDefaultTemplate(false);
                        templateRepository.save(item);
                    }
                });
        selected.setDefaultTemplate(true);
        selected.setActive(true);
        return templateResponse(templateRepository.save(selected));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTerms() {
        Long tenantId = currentTenantId();
        if (tenantId == null) {
            return List.of();
        }
        return termsRepository.findAllByTenantIdOrderByDefaultTermsDescUpdatedAtDesc(tenantId)
                .stream().map(this::termsResponse).toList();
    }

    @Transactional
    public Map<String, Object> createTerms(Map<String, Object> body) {
        Tenant tenant = tenantRepository.findById(tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Agency not found"));
        ContractTerms terms = ContractTerms.builder()
                .tenant(tenant)
                .title(string(body.get("title"), "Conditions generales"))
                .content(string(body.get("content"), defaultTerms()))
                .language(string(body.get("language"), "fr"))
                .version(intValue(body.get("version"), 1))
                .defaultTerms(bool(body.get("default"), false))
                .build();
        ContractTerms saved = termsRepository.save(terms);
        if (Boolean.TRUE.equals(saved.getDefaultTerms())) setDefaultTerms(saved);
        return termsResponse(saved);
    }

    @Transactional
    public Map<String, Object> updateTerms(Long id, Map<String, Object> body) {
        ContractTerms terms = termsRepository.findByIdAndTenantId(id, tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Contract terms not found"));
        if (body.containsKey("title")) terms.setTitle(string(body.get("title"), terms.getTitle()));
        if (body.containsKey("content")) terms.setContent(string(body.get("content"), terms.getContent()));
        if (body.containsKey("language")) terms.setLanguage(string(body.get("language"), terms.getLanguage()));
        if (body.containsKey("version")) terms.setVersion(intValue(body.get("version"), terms.getVersion()));
        ContractTerms saved = termsRepository.save(terms);
        if (Boolean.TRUE.equals(body.get("default"))) setDefaultTerms(saved);
        return termsResponse(saved);
    }

    private void applyField(ContractTemplateField field, Map<String, Object> body) {
        field.setFieldKey(string(body.get("fieldKey"), field.getFieldKey()));
        field.setLabel(string(body.get("label"), field.getLabel() != null ? field.getLabel() : field.getFieldKey()));
        field.setPageNumber(intValue(body.get("pageNumber"), field.getPageNumber() != null ? field.getPageNumber() : 1));
        field.setXPercent(decimal(body.get("xPercent"), field.getXPercent(), BigDecimal.ZERO));
        field.setYPercent(decimal(body.get("yPercent"), field.getYPercent(), BigDecimal.ZERO));
        field.setWidthPercent(decimal(body.get("widthPercent"), field.getWidthPercent(), BigDecimal.valueOf(20)));
        field.setHeightPercent(decimal(body.get("heightPercent"), field.getHeightPercent(), BigDecimal.valueOf(4)));
        field.setFontSize(intValue(body.get("fontSize"), field.getFontSize() != null ? field.getFontSize() : 10));
        field.setFontFamily(string(body.get("fontFamily"), field.getFontFamily() != null ? field.getFontFamily() : "Helvetica"));
        field.setFontWeight(string(body.get("fontWeight"), field.getFontWeight() != null ? field.getFontWeight() : "normal"));
        field.setTextAlign(string(body.get("textAlign"), field.getTextAlign() != null ? field.getTextAlign() : "left"));
        field.setColor(string(body.get("color"), field.getColor() != null ? field.getColor() : "#000000"));
        field.setMultiline(bool(body.get("multiline"), field.getMultiline() != null ? field.getMultiline() : false));
        field.setDateFormat(string(body.get("dateFormat"), field.getDateFormat()));
        field.setEnabled(bool(body.get("enabled"), field.getEnabled() != null ? field.getEnabled() : true));
    }

    private ContractTemplate template(Long id) {
        return templateRepository.findByIdAndTenantId(id, tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Contract template not found"));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Template file is required");
        if (file.getSize() > MAX_TEMPLATE_FILE_SIZE) {
            throw new IllegalArgumentException("Template file is too large. Maximum size is 20MB");
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String ext = originalExtension(file);
        if (!ALLOWED_TYPES.contains(type) && !ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("Only PDF, JPG, JPEG, PNG, WEBP, or JFIF files are allowed.");
        }
    }

    private String extension(MultipartFile file) {
        String ext = originalExtension(file);
        if (ALLOWED_EXTENSIONS.contains(ext)) {
            return ext;
        }
        return switch (file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT)) {
            case "application/pdf" -> ".pdf";
            case "image/jpeg", "image/jpg", "image/pjpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/jfif" -> ".jfif";
            default -> throw new IllegalArgumentException("Only PDF, JPG, JPEG, PNG, WEBP, or JFIF files are allowed.");
        };
    }

    private String originalExtension(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return "";
        }
        String normalized = name.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        int dot = normalized.lastIndexOf('.');
        return dot >= 0 ? normalized.substring(dot) : "";
    }

    private void setDefaultTerms(ContractTerms selected) {
        termsRepository.findAllByTenantIdAndDefaultTermsTrue(tenantId()).forEach(item -> {
            if (!item.getId().equals(selected.getId())) {
                item.setDefaultTerms(false);
                termsRepository.save(item);
            }
        });
        selected.setDefaultTerms(true);
        termsRepository.save(selected);
    }

    private Map<String, Object> templateResponse(ContractTemplate template) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", template.getId());
        response.put("tenantId", template.getTenant() == null ? null : template.getTenant().getId());
        response.put("name", safe(template.getName()));
        response.put("description", safe(template.getDescription()));
        response.put("templateCode", safe(template.getTemplateCode()));
        response.put("templateType", template.getTemplateType());
        response.put("source", template.getTemplateType() == ContractTemplateType.SYSTEM_DEFAULT ? "SYSTEM" : "AGENCY_SCAN");
        response.put("language", safe(template.getLanguage()));
        response.put("pagesCount", template.getPagesCount());
        response.put("pages", template.getPagesCount() == null || template.getPagesCount() == 0 ? "Custom" : template.getPagesCount() + (template.getPagesCount() == 1 ? " page" : " pages"));
        response.put("hasConditions", Boolean.TRUE.equals(template.getHasConditions()));
        response.put("accessPlan", safe(template.getAccessPlan()));
        response.put("frontFileUrl", safe(template.getFrontFileUrl()));
        response.put("backFileUrl", safe(template.getBackFileUrl()));
        response.put("previewImageUrl", safe(template.getPreviewImageUrl()));
        response.put("conditionsImageUrl", safe(template.getConditionsImageUrl()));
        response.put("mappingJson", safe(template.getMappingJson()));
        response.put("pageSize", safe(template.getPageSize()));
        response.put("default", Boolean.TRUE.equals(template.getDefaultTemplate()));
        response.put("active", Boolean.TRUE.equals(template.getActive()));
        response.put("fields", template.getFields() == null ? List.of() : template.getFields().stream().map(this::fieldResponse).toList());
        response.put("createdAt", template.getCreatedAt());
        response.put("updatedAt", template.getUpdatedAt());
        return response;
    }

    private Map<String, Object> systemTemplate(SystemTemplateDefinition definition) {
        boolean unlocked = isTemplateAccessible(definition);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("key", definition.code());
        response.put("templateCode", definition.code());
        response.put("name", definition.name());
        response.put("description", definition.description());
        response.put("templateType", ContractTemplateType.SYSTEM_DEFAULT);
        response.put("source", "SYSTEM");
        response.put("language", definition.language());
        response.put("pagesCount", definition.pagesCount());
        response.put("pages", definition.pagesCount() == 0 ? "Custom" : definition.pagesCount() + (definition.pagesCount() == 1 ? " page" : " pages"));
        response.put("hasConditions", definition.hasConditions());
        response.put("includesConditions", definition.hasConditions());
        response.put("accessPlan", definition.accessPlan());
        response.put("requiredPlan", definition.accessPlan());
        response.put("featureCode", definition.featureCode());
        response.put("locked", !unlocked);
        response.put("unlocked", unlocked);
        response.put("active", true);
        return response;
    }

    public boolean canUseTemplate(ContractTemplate template) {
        if (template == null) return false;
        if (template.getTemplateType() == ContractTemplateType.SYSTEM_DEFAULT) {
            String code = template.getTemplateCode();
            SystemTemplateDefinition definition = SYSTEM_TEMPLATE_CATALOG.stream()
                    .filter(item -> item.code().equals(code))
                    .findFirst()
                    .orElse(null);
            return definition == null || isTemplateAccessible(definition);
        }
        return isFeatureEnabled("CUSTOM_CONTRACT_TEMPLATES") && meetsPlan("STANDARD");
    }

    public void assertCanUseTemplate(ContractTemplate template) {
        if (!canUseTemplate(template)) {
            String requiredPlan = template != null && template.getAccessPlan() != null ? template.getAccessPlan() : "STANDARD";
            throw new TemplatePlanRequiredException(requiredPlan);
        }
    }

    private SystemTemplateDefinition findSystemTemplate(String code) {
        return SYSTEM_TEMPLATE_CATALOG.stream()
                .filter(item -> item.code().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));
    }

    private void assertTemplateAccess(SystemTemplateDefinition definition) {
        if (!isTemplateAccessible(definition)) {
            throw new TemplatePlanRequiredException(definition.accessPlan());
        }
    }

    private boolean isTemplateAccessible(SystemTemplateDefinition definition) {
        return meetsPlan(definition.accessPlan()) && isFeatureEnabled(definition.featureCode());
    }

    private void requireFeature(String featureCode, String requiredPlan) {
        if (!meetsPlan(requiredPlan) || !isFeatureEnabled(featureCode)) {
            throw new TemplatePlanRequiredException(requiredPlan);
        }
    }

    private boolean isFeatureEnabled(String featureCode) {
        try {
            return featureAccessService.isEnabledForCurrentTenant(featureCode);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private boolean meetsPlan(String requiredPlan) {
        Tenant tenant = tenantRepository.findById(tenantId()).orElse(null);
        String current = tenant == null ? null : tenant.getPlanName();
        return planRank(current) >= planRank(requiredPlan);
    }

    private int planRank(String plan) {
        if (plan == null || plan.isBlank()) return -1;
        String normalized = plan.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("enterprise")) return 5;
        if (normalized.contains("premium")) return 4;
        if (normalized.contains("standard")) return 3;
        if (normalized.contains("basic")) return 2;
        if (normalized.contains("starter") || normalized.contains("trial") || normalized.contains("free")) return 1;
        return -1;
    }

    private Map<String, Object> fieldResponse(ContractTemplateField field) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", field.getId());
        response.put("fieldKey", safe(field.getFieldKey()));
        response.put("label", safe(field.getLabel()));
        response.put("pageNumber", field.getPageNumber());
        response.put("xPercent", field.getXPercent());
        response.put("yPercent", field.getYPercent());
        response.put("widthPercent", field.getWidthPercent());
        response.put("heightPercent", field.getHeightPercent());
        response.put("fontSize", field.getFontSize());
        response.put("fontFamily", safe(field.getFontFamily()));
        response.put("fontWeight", safe(field.getFontWeight()));
        response.put("textAlign", safe(field.getTextAlign()));
        response.put("color", safe(field.getColor()));
        response.put("multiline", Boolean.TRUE.equals(field.getMultiline()));
        response.put("dateFormat", safe(field.getDateFormat()));
        response.put("enabled", Boolean.TRUE.equals(field.getEnabled()));
        return response;
    }

    private Map<String, Object> termsResponse(ContractTerms terms) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", terms.getId());
        response.put("tenantId", terms.getTenant() == null ? null : terms.getTenant().getId());
        response.put("title", safe(terms.getTitle()));
        response.put("content", safe(terms.getContent()));
        response.put("language", safe(terms.getLanguage()));
        response.put("version", terms.getVersion());
        response.put("default", Boolean.TRUE.equals(terms.getDefaultTerms()));
        response.put("createdAt", terms.getCreatedAt());
        response.put("updatedAt", terms.getUpdatedAt());
        return response;
    }

    private Long currentTenantId() {
        return TenantContext.getCurrentTenantId();
    }

    private Long tenantId() {
        Long tenantId = currentTenantId();
        if (tenantId == null) throw new IllegalArgumentException("No agency is selected for this user.");
        return tenantId;
    }

    private String string(Object value, String fallback) {
        return value == null || value.toString().isBlank() ? fallback : value.toString().trim();
    }

    private <E extends Enum<E>> E enumValue(Object value, E fallback) {
        if (value == null) return fallback;
        @SuppressWarnings("unchecked")
        Class<E> enumType = (Class<E>) fallback.getDeclaringClass();
        return Enum.valueOf(enumType, value.toString());
    }

    private boolean bool(Object value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(value.toString());
    }

    private int intValue(Object value, int fallback) {
        if (value == null) return fallback;
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(value.toString());
    }

    private BigDecimal decimal(Object value, BigDecimal current, BigDecimal fallback) {
        if (value == null) return current != null ? current : fallback;
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        return new BigDecimal(value.toString());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public static String defaultTerms() {
        return """
                ART 1: UTILISATION DU VEHICULE
                Le locataire s'engage a utiliser le vehicule conformement aux lois en vigueur.

                ART 2: ETAT DU VEHICULE
                Le vehicule est remis en bon etat apparent. Toute anomalie doit etre signalee avant le depart.

                ART 3: CARBURANTS ET LUBRIFIANTS
                Le vehicule doit etre restitue avec le niveau de carburant convenu au contrat.

                ART 4: ENTRETIEN ET REPARATIONS
                Toute intervention non autorisee par l'agence est interdite.

                ART 5: ASSURANCES
                Le locataire reste responsable des exclusions, franchises et infractions.

                ART 6: REGLEMENT - PROLONGATION - RETOUR
                Toute prolongation doit etre approuvee par l'agence avant l'echeance du contrat.

                ART 7: DOCUMENTS DE LA VOITURE
                Les documents remis avec le vehicule doivent etre restitues.

                ART 8: RESPONSABILITE
                Le locataire est responsable des contraventions, dommages et pertes non couverts.

                ART 9: JURIDICTION
                Tout litige releve des juridictions competentes selon la loi applicable.

                Please review your legal terms before using them officially.
                """;
    }
}
