package com.carrental.dto.contract;

import lombok.Data;
import java.math.BigDecimal;

/**
 * Request body for POST /api/contracts/{id}/return-inspection.
 * Captures end-of-rental fuel level, mileage, condition notes, and optional fees.
 */
@Data
public class ReturnInspectionRequest {

    /** Fuel level at return: EMPTY / QUARTER / HALF / THREE_QUARTERS / FULL */
    private String fuelLevelEnd;

    /** Odometer reading at return */
    private Integer mileageEnd;

    /** General vehicle condition note at return */
    private String conditionEndNote;

    /** Damage description at return (new damage observed) */
    private String damageEndNote;

    /** Optional extra fuel charge if returned with less fuel */
    private BigDecimal extraFuelFee;

    /** Optional extra mileage charge */
    private BigDecimal extraMileageFee;

    /** Optional damage fee */
    private BigDecimal damageFee;
}
