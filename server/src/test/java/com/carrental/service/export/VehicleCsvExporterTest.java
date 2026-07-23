package com.carrental.service.export;

import com.carrental.dto.export.VehicleExportRow;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleCsvExporterTest {

    private final VehicleCsvExporter exporter = new VehicleCsvExporter();

    @Test
    void write_prependsUtf8Bom_andKeepsAccentsIntact() throws Exception {
        VehicleExportRow row = VehicleExportRow.builder().id(1L).brand("Toyota").model("Corolla").category("Catégorie É").build();
        List<Function<VehicleExportRow, Object>> columns = List.of(VehicleExportRow::getId, VehicleExportRow::getCategory);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.write(out, List.of(row), new String[]{"ID", "Catégorie"}, columns, ',');
        byte[] bytes = out.toByteArray();

        assertThat(bytes[0] & 0xFF).isEqualTo(0xEF);
        assertThat(bytes[1] & 0xFF).isEqualTo(0xBB);
        assertThat(bytes[2] & 0xFF).isEqualTo(0xBF);
        String content = new String(bytes, StandardCharsets.UTF_8);
        assertThat(content).contains("Catégorie É");
        assertThat(content).doesNotContain("ModÃ¨le", "CatÃ©gorie");
    }

    @Test
    void write_headersAppearInSeparateColumns_notOneConcatenatedCell() throws Exception {
        VehicleExportRow row = VehicleExportRow.builder().id(1L).brand("Toyota").model("Corolla").build();
        List<Function<VehicleExportRow, Object>> columns = List.of(VehicleExportRow::getId, VehicleExportRow::getBrand, VehicleExportRow::getModel);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.write(out, List.of(row), new String[]{"ID", "Brand", "Model"}, columns, ',');
        String content = new String(out.toByteArray(), StandardCharsets.UTF_8).replace("﻿", "");
        String headerLine = content.split("\r\n")[0];

        assertThat(headerLine).isEqualTo("ID,Brand,Model");
        assertThat(headerLine.split(",")).hasSize(3);
    }

    @Test
    void write_valueContainingDelimiter_isQuoted() throws Exception {
        VehicleExportRow row = VehicleExportRow.builder().id(1L).branch("Casablanca, Centre").build();
        List<Function<VehicleExportRow, Object>> columns = List.of(VehicleExportRow::getId, VehicleExportRow::getBranch);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.write(out, List.of(row), new String[]{"ID", "Branch"}, columns, ',');
        String content = new String(out.toByteArray(), StandardCharsets.UTF_8);

        assertThat(content).contains("\"Casablanca, Centre\"");
    }

    @Test
    void write_semicolonDelimiter_isRespected() throws Exception {
        VehicleExportRow row = VehicleExportRow.builder().id(1L).pricePerDay(new BigDecimal("199.00")).build();
        List<Function<VehicleExportRow, Object>> columns = List.of(VehicleExportRow::getId, VehicleExportRow::getPricePerDay);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.write(out, List.of(row), new String[]{"ID", "Price"}, columns, ';');
        String content = new String(out.toByteArray(), StandardCharsets.UTF_8);

        assertThat(content).contains("ID;Price").contains("1;199.00");
    }
}
