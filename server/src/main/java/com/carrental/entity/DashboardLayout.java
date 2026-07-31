package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One user's persisted dashboard widget customization (order + visibility),
 * serialized as JSON — the source of truth once loaded; localStorage is only
 * an instant-paint cache on top of this (spec: "Do not rely only on localStorage").
 */
@Entity
@Table(name = "dashboard_layouts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardLayout {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /** JSON-serialized WidgetConfig[] — {id, label, description, visible, order, pinned}. */
    @Column(name = "widgets_json", nullable = false, columnDefinition = "TEXT")
    private String widgetsJson;

    @Column(name = "device_type", length = 20)
    private String deviceType;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
