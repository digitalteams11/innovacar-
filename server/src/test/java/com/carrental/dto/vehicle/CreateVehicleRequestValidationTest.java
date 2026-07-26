package com.carrental.dto.vehicle;

import com.carrental.entity.VehicleStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CreateVehicleRequestValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    void rejectsMissingRequiredFleetFields() {
        CreateVehicleRequest request = new CreateVehicleRequest();
        request.setPrixJour(new BigDecimal("100.00"));
        request.setPlate("12345-A-6");

        var fields = validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(fields).contains("brand", "model", "category", "fuel", "transmission", "seatCount");
    }

    @Test
    void acceptsCompleteVehicleWithFiveSeatsAndDefaultStatus() {
        CreateVehicleRequest request = completeRequest();
        request.setSeatCount(5);
        request.setStatut(VehicleStatus.AVAILABLE);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsZeroSeatCountAtTheApiBoundary() {
        CreateVehicleRequest request = completeRequest();
        request.setSeatCount(0);

        assertThat(validator.validate(request)).anyMatch(v -> v.getPropertyPath().toString().equals("seatCount"));
    }

    private CreateVehicleRequest completeRequest() {
        CreateVehicleRequest request = new CreateVehicleRequest();
        request.setBrand("Dacia");
        request.setModel("Logan");
        request.setPrixJour(new BigDecimal("100.00"));
        request.setCategory("Economy");
        request.setPlate("12345-A-6");
        request.setFuel("Diesel");
        request.setTransmission("Manual");
        request.setSeatCount(5);
        return request;
    }
}