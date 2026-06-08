package com.carrental.controller;

import com.carrental.entity.Vehicle;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.VehicleRepository;
import com.carrental.security.TenantContext;
import com.carrental.service.AvailabilityService;
import com.carrental.service.PriceCalculationService;
import com.carrental.service.PriceCalculationService.RentalPrice;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Vehicle availability and pricing APIs.
 */
@RestController
@RequestMapping("/api/availability")
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService availabilityService;
    private final PriceCalculationService priceCalculationService;
    private final VehicleRepository vehicleRepository;

    @GetMapping("/vehicles")
    public ResponseEntity<List<Vehicle>> getAvailableVehicles(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "09:00") @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @RequestParam(defaultValue = "18:00") @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime,
            @RequestParam(required = false) Long excludeReservationId) {
        return ResponseEntity.ok(availabilityService.getAvailableVehicles(
                startDate, startTime, endDate, endTime, excludeReservationId));
    }

    @GetMapping("/vehicles/{vehicleId}")
    public ResponseEntity<Map<String, Boolean>> checkVehicleAvailability(
            @PathVariable Long vehicleId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "09:00") @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @RequestParam(defaultValue = "18:00") @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime,
            @RequestParam(required = false) Long excludeReservationId) {
        boolean available = availabilityService.isVehicleAvailable(
                vehicleId, startDate, startTime, endDate, endTime, excludeReservationId);
        return ResponseEntity.ok(Map.of("available", available));
    }

    @GetMapping("/vehicles/{vehicleId}/price")
    public ResponseEntity<RentalPrice> calculatePrice(
            @PathVariable Long vehicleId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer extraHours,
            @RequestParam(required = false) Integer allowedMileage,
            @RequestParam(required = false) Integer actualMileage,
            @RequestParam(required = false) BigDecimal discountAmount,
            @RequestParam(required = false) BigDecimal discountPercent) {

        Vehicle vehicle = vehicleRepository.findByIdAndTenantId(vehicleId, TenantContext.getCurrentTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + vehicleId));
        RentalPrice price = priceCalculationService.calculatePrice(vehicle, startDate, endDate, extraHours, allowedMileage, actualMileage);
        if (discountAmount != null || discountPercent != null) {
            price = priceCalculationService.applyDiscount(price, discountAmount, discountPercent);
        }
        return ResponseEntity.ok(price);
    }
}
