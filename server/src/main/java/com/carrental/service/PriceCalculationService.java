package com.carrental.service;

import com.carrental.entity.Vehicle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Live price calculation engine for rental contracts.
 * Automatically calculates totals based on vehicle pricing, dates, and add-ons.
 */
@Slf4j
@Service
public class PriceCalculationService {

    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("0.20"); // 20% VAT

    /**
     * Calculate rental price based on vehicle pricing and duration.
     * Uses weekly/monthly discounts when applicable.
     */
    public RentalPrice calculatePrice(Vehicle vehicle, LocalDate startDate, LocalDate endDate,
                                       Integer extraHours, Integer allowedMileage, Integer actualMileage) {
        if (vehicle == null || startDate == null || endDate == null) {
            return RentalPrice.zero();
        }

        long totalDays = ChronoUnit.DAYS.between(startDate, endDate);
        if (totalDays <= 0) totalDays = 1;

        BigDecimal dailyPrice = vehicle.getPrixJour() != null ? vehicle.getPrixJour() : BigDecimal.ZERO;
        BigDecimal weeklyPrice = vehicle.getPrixSemaine() != null ? vehicle.getPrixSemaine() : dailyPrice.multiply(new BigDecimal("7"));
        BigDecimal monthlyPrice = vehicle.getPrixMois() != null ? vehicle.getPrixMois() : dailyPrice.multiply(new BigDecimal("30"));

        // Calculate base rental price with tiered pricing
        BigDecimal basePrice = calculateBasePrice(totalDays, dailyPrice, weeklyPrice, monthlyPrice);

        // Insurance fees
        BigDecimal insuranceFees = vehicle.getInsuranceFees() != null
                ? vehicle.getInsuranceFees().multiply(BigDecimal.valueOf(totalDays))
                : BigDecimal.ZERO;

        // Delivery fees
        BigDecimal deliveryFees = vehicle.getDeliveryFees() != null ? vehicle.getDeliveryFees() : BigDecimal.ZERO;

        // Extra hours (charged at 1/4 daily rate per hour)
        BigDecimal extraHoursCost = BigDecimal.ZERO;
        if (extraHours != null && extraHours > 0) {
            BigDecimal hourlyRate = dailyPrice.divide(new BigDecimal("4"), 2, RoundingMode.HALF_UP);
            extraHoursCost = hourlyRate.multiply(BigDecimal.valueOf(extraHours));
        }

        // Extra mileage
        BigDecimal extraMileageCost = BigDecimal.ZERO;
        if (allowedMileage != null && actualMileage != null && actualMileage > allowedMileage) {
            BigDecimal perKmRate = vehicle.getExtraMileageCost() != null ? vehicle.getExtraMileageCost() : BigDecimal.ZERO;
            extraMileageCost = perKmRate.multiply(BigDecimal.valueOf(actualMileage - allowedMileage));
        }

        // Subtotal before tax
        BigDecimal subtotal = basePrice.add(insuranceFees).add(deliveryFees).add(extraHoursCost).add(extraMileageCost);

        // Tax
        BigDecimal taxAmount = subtotal.multiply(DEFAULT_TAX_RATE).setScale(2, RoundingMode.HALF_UP);

        // Total
        BigDecimal totalPrice = subtotal.add(taxAmount);

        // Deposit
        BigDecimal deposit = vehicle.getDepositAmount() != null ? vehicle.getDepositAmount() : BigDecimal.ZERO;

        return RentalPrice.builder()
                .totalDays((int) totalDays)
                .dailyPrice(dailyPrice)
                .basePrice(basePrice)
                .insuranceFees(insuranceFees)
                .deliveryFees(deliveryFees)
                .extraHoursCost(extraHoursCost)
                .extraMileageCost(extraMileageCost)
                .subtotal(subtotal)
                .taxRate(DEFAULT_TAX_RATE)
                .taxAmount(taxAmount)
                .totalPrice(totalPrice)
                .depositAmount(deposit)
                .build();
    }

    /**
     * Apply discount to a price calculation.
     */
    public RentalPrice applyDiscount(RentalPrice price, BigDecimal discountAmount, BigDecimal discountPercent) {
        BigDecimal discount = BigDecimal.ZERO;
        if (discountAmount != null && discountAmount.compareTo(BigDecimal.ZERO) > 0) {
            discount = discountAmount;
        } else if (discountPercent != null && discountPercent.compareTo(BigDecimal.ZERO) > 0) {
            discount = price.getSubtotal().multiply(discountPercent.divide(new BigDecimal("100")))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal newSubtotal = price.getSubtotal().subtract(discount).max(BigDecimal.ZERO);
        BigDecimal newTax = newSubtotal.multiply(DEFAULT_TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal newTotal = newSubtotal.add(newTax);

        return RentalPrice.builder()
                .totalDays(price.getTotalDays())
                .dailyPrice(price.getDailyPrice())
                .basePrice(price.getBasePrice())
                .insuranceFees(price.getInsuranceFees())
                .deliveryFees(price.getDeliveryFees())
                .extraHoursCost(price.getExtraHoursCost())
                .extraMileageCost(price.getExtraMileageCost())
                .discountAmount(discount)
                .subtotal(newSubtotal)
                .taxRate(DEFAULT_TAX_RATE)
                .taxAmount(newTax)
                .totalPrice(newTotal)
                .depositAmount(price.getDepositAmount())
                .build();
    }

    private BigDecimal calculateBasePrice(long days, BigDecimal daily, BigDecimal weekly, BigDecimal monthly) {
        BigDecimal total = BigDecimal.ZERO;
        long remaining = days;

        // Apply monthly rate
        long months = remaining / 30;
        if (months > 0) {
            total = total.add(monthly.multiply(BigDecimal.valueOf(months)));
            remaining -= months * 30;
        }

        // Apply weekly rate
        long weeks = remaining / 7;
        if (weeks > 0) {
            total = total.add(weekly.multiply(BigDecimal.valueOf(weeks)));
            remaining -= weeks * 7;
        }

        // Apply daily rate for remaining days
        if (remaining > 0) {
            total = total.add(daily.multiply(BigDecimal.valueOf(remaining)));
        }

        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * DTO for price calculation results.
     */
    @lombok.Builder
    @lombok.Data
    public static class RentalPrice {
        private int totalDays;
        private BigDecimal dailyPrice;
        private BigDecimal basePrice;
        private BigDecimal insuranceFees;
        private BigDecimal deliveryFees;
        private BigDecimal extraHoursCost;
        private BigDecimal extraMileageCost;
        private BigDecimal discountAmount;
        private BigDecimal subtotal;
        private BigDecimal taxRate;
        private BigDecimal taxAmount;
        private BigDecimal totalPrice;
        private BigDecimal depositAmount;

        public static RentalPrice zero() {
            return builder()
                    .totalDays(0)
                    .dailyPrice(BigDecimal.ZERO)
                    .basePrice(BigDecimal.ZERO)
                    .insuranceFees(BigDecimal.ZERO)
                    .deliveryFees(BigDecimal.ZERO)
                    .extraHoursCost(BigDecimal.ZERO)
                    .extraMileageCost(BigDecimal.ZERO)
                    .discountAmount(BigDecimal.ZERO)
                    .subtotal(BigDecimal.ZERO)
                    .taxRate(BigDecimal.ZERO)
                    .taxAmount(BigDecimal.ZERO)
                    .totalPrice(BigDecimal.ZERO)
                    .depositAmount(BigDecimal.ZERO)
                    .build();
        }
    }
}
