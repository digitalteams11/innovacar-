package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "contract_templates", indexes = {
        @Index(name = "idx_contract_template_tenant", columnList = "tenant_id"),
        @Index(name = "idx_contract_template_default", columnList = "tenant_id, is_default")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_type", nullable = false, length = 40)
    private ContractTemplateType templateType;

    @Column(name = "template_code", length = 80)
    private String templateCode;

    @Column(length = 10)
    private String language;

    @Column(name = "pages_count")
    private Integer pagesCount;

    @Builder.Default
    @Column(name = "has_conditions", nullable = false, columnDefinition = "boolean default false")
    private Boolean hasConditions = false;

    @Column(name = "access_plan", length = 40)
    private String accessPlan;

    @Column(name = "front_file_path", length = 1000)
    private String frontFilePath;

    @Column(name = "front_file_url", length = 1000)
    private String frontFileUrl;

    @Column(name = "back_file_path", length = 1000)
    private String backFilePath;

    @Column(name = "back_file_url", length = 1000)
    private String backFileUrl;

    @Column(name = "preview_image_url", length = 1000)
    private String previewImageUrl;

    @Column(name = "conditions_image_url", length = 1000)
    private String conditionsImageUrl;

    @Column(name = "mapping_json", columnDefinition = "TEXT")
    private String mappingJson;

    @Column(name = "page_size", nullable = false, length = 20)
    private String pageSize;

    @Builder.Default
    @Column(name = "is_default", nullable = false, columnDefinition = "boolean default false")
    private Boolean defaultTemplate = false;

    @Builder.Default
    @Column(name = "is_active", nullable = false, columnDefinition = "boolean default true")
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder.Default
    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("pageNumber asc, yPercent asc, xPercent asc")
    private List<ContractTemplateField> fields = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (templateType == null) templateType = ContractTemplateType.AGENCY_SCAN_TEMPLATE;
        if (pageSize == null) pageSize = "A4";
        if (language == null) language = "FR";
        if (hasConditions == null) hasConditions = false;
        if (defaultTemplate == null) defaultTemplate = false;
        if (active == null) active = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
