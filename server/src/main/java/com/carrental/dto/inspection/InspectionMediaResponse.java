package com.carrental.dto.inspection;

import com.carrental.entity.InspectionMedia;
import com.carrental.entity.InspectionMediaType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class InspectionMediaResponse {
    private Long id;
    private InspectionMediaType type;
    private String fileUrl;
    private String url;
    /** Always a relative path (e.g. /uploads/inspections/...) — frontend builds full URL from its own origin. */
    private String fullUrl;
    private String thumbnailUrl;
    private String label;
    private String notes;
    private LocalDateTime uploadedAt;
    private Long size;
    private Integer duration;
    private String contentType;

    public static InspectionMediaResponse from(InspectionMedia media) {
        // Use the token-authenticated download endpoint instead of a static-file URL.
        // The endpoint resolves the file via its absolute storagePath, so it works
        // regardless of the server's working directory, static-resource config, or OS.
        String downloadUrl = "/api/public/inspection-media/" + media.getId() + "/" + media.getAccessToken();
        return InspectionMediaResponse.builder()
                .id(media.getId())
                .type(media.getType())
                .fileUrl(downloadUrl)
                .url(downloadUrl)
                .fullUrl(downloadUrl)
                .thumbnailUrl(media.getThumbnailUrl())
                .label(media.getLabel())
                .notes(media.getNotes())
                .uploadedAt(media.getUploadedAt())
                .size(media.getSize())
                .duration(media.getDuration())
                .contentType(media.getContentType())
                .build();
    }
}
