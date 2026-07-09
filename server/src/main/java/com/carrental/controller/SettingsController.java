package com.carrental.controller;

import com.carrental.service.TenantSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SettingsController {

    private final TenantSettingsService settingsService;

    @GetMapping("/settings")
    public ResponseEntity<Map<String, Object>> getSettings() {
        return ResponseEntity.ok(settingsService.getSettings());
    }

    @PutMapping("/settings")
    @PreAuthorize("@rolePermissionService.has('MANAGE_SETTINGS')")
    public ResponseEntity<Map<String, Object>> saveSettings(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(settingsService.save(body));
    }

    @GetMapping("/user-preferences")
    public ResponseEntity<Map<String, Object>> getUserPreferences() {
        Map<String, Object> data = settingsService.data(settingsService.getSettings());
        return ResponseEntity.ok(settingsService.envelope("User preferences loaded successfully", preferenceData(data)));
    }

    @PutMapping("/user-preferences")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> saveUserPreferences(@RequestBody Map<String, Object> body) {
        Map<String, Object> update = new LinkedHashMap<>();
        if (body.containsKey("language")) update.put("language", body.get("language"));
        if (body.containsKey("theme")) update.put("appearance", Map.of("mode", body.get("theme")));
        if (body.containsKey("appearance")) update.put("appearance", body.get("appearance"));
        Map<String, Object> data = settingsService.data(settingsService.save(update));
        return ResponseEntity.ok(settingsService.envelope("User preferences saved successfully", preferenceData(data)));
    }

    @GetMapping("/notification-settings")
    public ResponseEntity<Map<String, Object>> getNotificationSettings() {
        Map<String, Object> data = settingsService.data(settingsService.getSettings());
        return ResponseEntity.ok(settingsService.envelope("Notification settings loaded successfully", notificationData(data)));
    }

    @PutMapping("/notification-settings")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> saveNotificationSettings(@RequestBody Map<String, Object> body) {
        Map<String, Object> update = new LinkedHashMap<>();
        copy(body, update, "notificationInApp");
        copy(body, update, "notificationEmail");
        copy(body, update, "notificationPush");
        Map<String, Object> data = settingsService.data(settingsService.save(update));
        return ResponseEntity.ok(settingsService.envelope("Notification settings saved successfully", notificationData(data)));
    }

    @GetMapping("/sound-settings")
    public ResponseEntity<Map<String, Object>> getSoundSettings() {
        Map<String, Object> data = settingsService.data(settingsService.getSettings());
        return ResponseEntity.ok(settingsService.envelope("Sound settings loaded successfully", soundData(data)));
    }

    @PutMapping("/sound-settings")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> saveSoundSettings(@RequestBody Map<String, Object> body) {
        Map<String, Object> update = Map.of("soundSettings", body);
        Map<String, Object> data = settingsService.data(settingsService.save(update));
        return ResponseEntity.ok(settingsService.envelope("Sound settings saved successfully", soundData(data)));
    }

    @GetMapping("/inspection-settings")
    public ResponseEntity<Map<String, Object>> getInspectionSettings() {
        Map<String, Object> data = settingsService.data(settingsService.getSettings());
        return ResponseEntity.ok(settingsService.envelope("Inspection settings loaded successfully", inspectionData(data)));
    }

    @PutMapping("/inspection-settings")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> saveInspectionSettings(@RequestBody Map<String, Object> body) {
        Map<String, Object> update = new LinkedHashMap<>();
        copy(body, update, "inspectionRetentionDays");
        Map<String, Object> data = settingsService.data(settingsService.save(update));
        return ResponseEntity.ok(settingsService.envelope("Inspection media retention saved successfully", inspectionData(data)));
    }

    private static void copy(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key)) target.put(key, source.get(key));
    }

    private static Map<String, Object> preferenceData(Map<String, Object> data) {
        Map<String, Object> result = new LinkedHashMap<>();
        Object appearance = data.getOrDefault("appearance", Map.of());
        Object theme = appearance instanceof Map<?, ?> appearanceMap ? appearanceMap.get("mode") : null;
        result.put("theme", theme != null ? theme : "light");
        result.put("language", data.getOrDefault("language", "en"));
        result.put("appearance", data.getOrDefault("appearance", Map.of()));
        return result;
    }

    private static Map<String, Object> notificationData(Map<String, Object> data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("notificationInApp", data.getOrDefault("notificationInApp", true));
        result.put("notificationEmail", data.getOrDefault("notificationEmail", true));
        result.put("notificationPush", data.getOrDefault("notificationPush", false));
        result.put("notificationsEnabled", Boolean.TRUE.equals(result.get("notificationInApp")));
        return result;
    }

    private static Map<String, Object> soundData(Map<String, Object> data) {
        Object sound = data.get("soundSettings");
        return sound instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
    }

    private static Map<String, Object> inspectionData(Map<String, Object> data) {
        return Map.of("inspectionRetentionDays", data.getOrDefault("inspectionRetentionDays", 7));
    }
}
