package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "contract_template_fields", indexes = {
        @Index(name = "idx_contract_template_field_template", columnList = "template_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractTemplateField {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private ContractTemplate template;

    @Column(name = "field_key", nullable = false, length = 120)
    private String fieldKey;

    @Column(nullable = false, length = 160)
    private String label;

    @Column(name = "page_number", nullable = false)
    private Integer pageNumber;

    @Column(name = "x_percent", nullable = false, precision = 8, scale = 4)
    private BigDecimal xPercent;

    @Column(name = "y_percent", nullable = false, precision = 8, scale = 4)
    private BigDecimal yPercent;

    @Column(name = "width_percent", nullable = false, precision = 8, scale = 4)
    private BigDecimal widthPercent;

    @Column(name = "height_percent", nullable = false, precision = 8, scale = 4)
    private BigDecimal heightPercent;

    @Column(name = "font_size", nullable = false)
    private Integer fontSize;

    @Column(name = "font_family", nullable = false, length = 80)
    private String fontFamily;

    @Column(name = "font_weight", nullable = false, length = 30)
    private String fontWeight;

    @Column(name = "text_align", nullable = false, length = 20)
    private String textAlign;

    @Column(nullable = false, length = 20)
    private String color;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default false")
    private Boolean multiline = false;

    @Column(name = "date_format", length = 40)
    private String dateFormat;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default true")
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (pageNumber == null) pageNumber = 1;
        if (xPercent == null) xPercent = BigDecimal.ZERO;
        if (yPercent == null) yPercent = BigDecimal.ZERO;
        if (widthPercent == null) widthPercent = BigDecimal.valueOf(20);
        if (heightPercent == null) heightPercent = BigDecimal.valueOf(4);
        if (fontSize == null) fontSize = 10;
        if (fontFamily == null) fontFamily = "Helvetica";
        if (fontWeight == null) fontWeight = "normal";
        if (textAlign == null) textAlign = "left";
        if (color == null) color = "#000000";
        if (multiline == null) multiline = false;
        if (enabled == null) enabled = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
