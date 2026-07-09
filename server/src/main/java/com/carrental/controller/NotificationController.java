package com.carrental.controller;

import com.carrental.entity.Notification;
import com.carrental.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Primary endpoint — returns notifications ordered by severity then date,
     * with the unread count embedded so the frontend needs only one round-trip.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAll(@RequestParam(defaultValue = "50") int limit) {
        List<Notification> items = notificationService.getRecentForCurrentTenant(limit);
        long unreadCount = notificationService.getUnreadCountForCurrentTenant();
        List<Map<String, Object>> dtoList = items.stream()
                .map(notificationService::toResponseMap)
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", dtoList);
        data.put("unreadCount", unreadCount);
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    /** Lightweight count-only endpoint — called by the bell badge poller. */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> getUnreadCount() {
        long count = notificationService.getUnreadCountForCurrentTenant();
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of("count", count)));
    }

    // ── Legacy endpoints (kept for backward compat with existing frontend) ─────

    @GetMapping("/unread")
    public ResponseEntity<List<Notification>> getUnread() {
        return ResponseEntity.ok(notificationService.getUnreadForCurrentTenant());
    }

    @GetMapping("/recent")
    public ResponseEntity<List<Notification>> getRecent(@RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(notificationService.getRecentForCurrentTenant(limit));
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> getLegacyCount() {
        return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCountForCurrentTenant()));
    }

    // ── Mutations ──────────────────────────────────────────────────────────────

    @PatchMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markAsRead(@PathVariable Long id) {
        try {
            notificationService.markAsRead(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Notification marked as read"));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    @PatchMapping("/mark-all-read")
    public ResponseEntity<Map<String, Object>> markAllRead() {
        notificationService.markAllAsRead();
        return ResponseEntity.ok(Map.of("success", true, "message", "All notifications marked as read"));
    }

    /** Legacy alias for mark-all-read */
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllReadLegacy() {
        notificationService.markAllAsRead();
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        boolean deleted = notificationService.delete(id);
        if (deleted) {
            return ResponseEntity.ok(Map.of("success", true, "message", "Notification deleted"));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("success", false, "message", "Notification not found"));
    }

    @DeleteMapping("/clear-read")
    public ResponseEntity<Map<String, Object>> clearRead() {
        int count = notificationService.clearRead();
        return ResponseEntity.ok(Map.of("success", true, "message", count + " read notifications cleared", "cleared", count));
    }
}
