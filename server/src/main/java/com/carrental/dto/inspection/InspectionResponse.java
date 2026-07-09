package com.carrental.dto.inspection;

import com.carrental.entity.Inspection;
import com.carrental.entity.InspectionStatus;
import com.carrental.entity.InspectionType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class InspectionResponse {
    private Long id;
    private Long agencyId;
    private Long reservationId;
    private Long contractId;
    private Long clientId;
    private Long vehicleId;
    private Long employeeId;
    private InspectionType type;
    private InspectionStatus status;
    private String token;
    private String captureUrl;
    private LocalDateTime tokenExpiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private LocalDateTime mediaExpiresAt;
    private String vehicleName;
    private String plateNumber;
    private String clientName;
    private String reservationNumber;
    private List<InspectionMediaResponse> media;

    public static InspectionResponse from(Inspection inspection) {
        return from(inspection, null);
    }

    public static InspectionResponse from(Inspection inspection, String captureUrl) {
        return InspectionResponse.builder()
                .id(inspection.getId())
                .agencyId(inspection.getTenant() != null ? inspection.getTenant().getId() : null)
                .reservationId(inspection.getReservation() != null ? inspection.getReservation().getId() : null)
                .contractId(inspection.getContract() != null ? inspection.getContract().getId() : null)
                .clientId(inspection.getClient() != null ? inspection.getClient().getId() : null)
                .vehicleId(inspection.getVehicle() != null ? inspection.getVehicle().getId() : null)
                .employeeId(inspection.getEmployee() != null ? inspection.getEmployee().getId() : null)
                .type(inspection.getType())
                .status(inspection.getStatus())
                .token(inspection.getToken())
                .captureUrl(captureUrl)
                .tokenExpiresAt(inspection.getTokenExpiresAt())
                .createdAt(inspection.getCreatedAt())
                .completedAt(inspection.getCompletedAt())
                .mediaExpiresAt(inspection.getMediaExpiresAt())
                .vehicleName(inspection.getVehicle() != null ? inspection.getVehicle().getMarque() : null)
                .plateNumber(inspection.getVehicle() != null ? inspection.getVehicle().getPlate() : null)
                .clientName(inspection.getClient() != null ? inspection.getClient().getName() : null)
                .reservationNumber(inspection.getReservation() != null ? "RES-" + inspection.getReservation().getId() : null)
                .media(inspection.getMedia() != null
                        ? inspection.getMedia().stream()
                                .filter(m -> Boolean.TRUE.equals(m.getActive()))
                                .map(InspectionMediaResponse::from).toList()
                        : List.of())
                .build();
    }
}
