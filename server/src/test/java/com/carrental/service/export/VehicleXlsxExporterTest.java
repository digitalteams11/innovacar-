package com.carrental.service.export;

import com.carrental.dto.export.VehicleExportRow;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleXlsxExporterTest {

    private final VehicleXlsxExporter exporter = new VehicleXlsxExporter();

    @Test
    void write_producesValidWorkbook_withHeaderInSeparateCells() throws Exception {
        VehicleExportRow row = VehicleExportRow.builder()
                .id(1L).brand("Toyota").model("Corolla").category("Catégorie É")
                .pricePerDay(new BigDecimal("199.00")).nextMaintenanceDate(LocalDate.of(2026, 8, 1))
                .build();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.write(out, List.of(row), true);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            Sheet fleet = workbook.getSheet("Fleet");
            assertThat(fleet).isNotNull();

            Row header = fleet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("ID");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Brand");
            int fontIndex = header.getCell(0).getCellStyle().getFontIndexAsInt();
            assertThat(workbook.getFontAt(fontIndex).getBold()).isTrue();

            Row dataRow = fleet.getRow(1);
            assertThat(dataRow.getCell(1).getStringCellValue()).isEqualTo("Toyota");
            assertThat(dataRow.getCell(2).getStringCellValue()).isEqualTo("Corolla");
            assertThat(dataRow.getCell(3).getStringCellValue()).isEqualTo("Catégorie É");
            // Price and date must be real numeric/date cells, not text.
            assertThat(dataRow.getCell(6).getCellType()).isEqualTo(CellType.NUMERIC);
            Cell dateCell = dataRow.getCell(11);
            assertThat(dateCell.getCellType()).isEqualTo(CellType.NUMERIC);

            assertThat(workbook.getSheet("Summary")).isNotNull();
        }
    }

    @Test
    void write_withoutSummary_omitsSummarySheet() throws Exception {
        VehicleExportRow row = VehicleExportRow.builder().id(1L).brand("Toyota").build();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.write(out, List.of(row), false);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            assertThat(workbook.getSheet("Summary")).isNull();
        }
    }
}
