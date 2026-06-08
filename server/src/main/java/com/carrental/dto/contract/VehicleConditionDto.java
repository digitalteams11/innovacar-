package com.carrental.dto.contract;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class VehicleConditionDto {

    private Long id;
    private Boolean frontDamage;
    private Boolean rearDamage;
    private Boolean leftSideDamage;
    private Boolean rightSideDamage;
    private Boolean windshieldDamage;
    private Boolean interiorDamage;
    private Boolean roofDamage;
    private Boolean bumperFrontDamage;
    private Boolean bumperRearDamage;
    private Boolean hoodDamage;
    private Boolean trunkDamage;
    private String tireCondition;
    private String scratchDescription;
    private String dentDescription;
    private String generalNotes;
    private String conditionPhotos;
    private LocalDateTime inspectionDate;
    private String inspectedBy;
    private Boolean isPickupInspection;
}
