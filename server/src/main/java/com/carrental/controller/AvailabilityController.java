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
import java.time.format.DateTimeParseException;
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
    public ResponseEntity<Map<String, Object>> getAvailableVehicles(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "09:00") String startTime,
            @RequestParam(defaultValue = "18:00") String endTime,
            @RequestParam(required = false) Long excludeReservationId) {
        LocalDate parsedStartDate;
        LocalDate parsedEndDate;
        LocalTime parsedStartTime;
        LocalTime parsedEndTime;
        try {
            parsedStartDate = LocalDate.parse(startDate);
            parsedEndDate = LocalDate.parse(endDate);
            parsedStartTime = LocalTime.parse(startTime);
            parsedEndTime = LocalTime.parse(endTime);
        } catch (DateTimeParseException ex) {
            return availabilityValidation("Dates must use YYYY-MM-DD and times must use HH:mm.", "INVALID_DATE_FORMAT");
        }

        if (parsedStartDate.isAfter(parsedEndDate)
                || (parsedStartDate.equals(parsedEndDate) && !parsedStartTime.isBefore(parsedEndTime))) {
            return availabilityValidation("Start date must be before end date.", "INVALID_DATE_RANGE");
        }

        List<Map<String, Object>> vehicles;
        try {
            vehicles = availabilityService.getAvailableVehicles(
                            parsedStartDate, parsedStartTime, parsedEndDate, parsedEndTime, excludeReservationId).stream()
                    .map(this::toAvailabilityVehicle)
                    .toList();
        } catch (RuntimeException ex) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "No available vehicles for selected dates.",
                    "data", List.of()));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", vehicles.isEmpty()
                        ? "No available vehicles for selected dates."
                        : "Available vehicles loaded successfully.",
                "data", vehicles));
    }

    @GetMapping("/vehicles/{vehicleId}")
    public ResponseEntity<Map<String, Object>> checkVehicleAvailability(
            @PathVariable Long vehicleId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "09:00") @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @RequestParam(defaultValue = "18:00") @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime,
            @RequestParam(required = false) Long excludeReservationId) {
        boolean available = availabilityService.isVehicleAvailable(
                vehicleId, startDate, startTime, endDate, endTime, excludeReservationId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", available ? "Vehicle is available." : "Vehicle is unavailable for the selected dates.",
                "data", Map.of("available", available),
                "available", available));
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

    private ResponseEntity<Map<String, Object>> availabilityValidation(String message, String errorCode) {
        return ResponseEntity.ok(Map.of(
                "success", false,
                "message", message,
                "errorCode", errorCode,
                "data", List.of()));
    }

    private Map<String, Object> toAvailabilityVehicle(Vehicle vehicle) {
        String marque = vehicle.getMarque() == null ? "" : vehicle.getMarque();
        String[] parts = marque.split(" ", 2);
        String brand = parts.length > 0 ? parts[0] : "";
        String model = parts.length > 1 ? parts[1] : "";
        return Map.ofEntries(
                Map.entry("id", vehicle.getId()),
                Map.entry("brand", brand),
                Map.entry("model", model),
                Map.entry("marque", marque),
                Map.entry("category", vehicle.getCategory() == null ? "" : vehicle.getCategory()),
                Map.entry("plateNumber", vehicle.getPlate() == null ? "" : vehicle.getPlate()),
                Map.entry("plate", vehicle.getPlate() == null ? "" : vehicle.getPlate()),
                Map.entry("dailyPrice", vehicle.getPrixJour() == null ? BigDecimal.ZERO : vehicle.getPrixJour()),
                Map.entry("prixJour", vehicle.getPrixJour() == null ? BigDecimal.ZERO : vehicle.getPrixJour()),
                Map.entry("status", vehicle.getStatut() == null ? "" : vehicle.getStatut().name()),
                Map.entry("statut", vehicle.getStatut() == null ? "" : vehicle.getStatut().name()),
                Map.entry("fuel", vehicle.getFuel() == null ? "" : vehicle.getFuel()),
                Map.entry("transmission", vehicle.getTransmission() == null ? "" : vehicle.getTransmission()),
                Map.entry("year", vehicle.getYear() == null ? 0 : vehicle.getYear()),
                Map.entry("color", vehicle.getColor() == null ? "" : vehicle.getColor()),
                Map.entry("depositAmount", vehicle.getDepositAmount() == null ? BigDecimal.ZERO : vehicle.getDepositAmount()),
                Map.entry("insuranceFees", vehicle.getInsuranceFees() == null ? BigDecimal.ZERO : vehicle.getInsuranceFees()),
                Map.entry("deliveryFees", vehicle.getDeliveryFees() == null ? BigDecimal.ZERO : vehicle.getDeliveryFees()),
                Map.entry("gpsEnabled", Boolean.TRUE.equals(vehicle.getGpsEnabled())));
    }
}
