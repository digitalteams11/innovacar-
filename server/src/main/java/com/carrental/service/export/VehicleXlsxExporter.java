package com.carrental.service.export;

import com.carrental.dto.export.VehicleExportRow;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Real .xlsx workbook via Apache POI — one value per cell, bold+frozen
 * header row, real date/number cell types (not text). This replaces the old
 * "Excel" export that was actually a comma-joined string with a .xlsx-shaped
 * lie in the button label — the whole header used to land in cell A1.
 */
@Component
public class VehicleXlsxExporter {

    public void write(OutputStream out, List<VehicleExportRow> rows, boolean includeSummary) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle dateStyle = dateStyle(workbook);
            CellStyle moneyStyle = moneyStyle(workbook);

            Sheet fleet = workbook.createSheet("Fleet");
            String[] headers = {
                    "ID", "Brand", "Model", "Category", "Plate", "Status", "Price/Day",
                    "Fuel", "Transmission", "Mileage", "Branch", "Next Maintenance"
            };
            Row headerRow = fleet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            fleet.createFreezePane(0, 1);

            int rowIdx = 1;
            for (VehicleExportRow r : rows) {
                Row row = fleet.createRow(rowIdx++);
                int col = 0;
                setNumeric(row, col++, r.getId() != null ? r.getId().doubleValue() : null, null);
                setString(row, col++, r.getBrand());
                setString(row, col++, r.getModel());
                setString(row, col++, r.getCategory());
                setString(row, col++, r.getPlate());
                setString(row, col++, r.getStatus());
                setMoney(row, col++, r.getPricePerDay(), moneyStyle);
                setString(row, col++, r.getFuel());
                setString(row, col++, r.getTransmission());
                setNumeric(row, col++, r.getMileage() != null ? r.getMileage().doubleValue() : null, null);
                setString(row, col++, r.getBranch());
                setDate(row, col++, r.getNextMaintenanceDate(), dateStyle);
            }

            for (int i = 0; i < headers.length; i++) {
                fleet.autoSizeColumn(i);
                if (fleet.getColumnWidth(i) < 3000) fleet.setColumnWidth(i, 3000);
            }
            fleet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));

            if (includeSummary) {
                writeSummarySheet(workbook, rows, headerStyle);
            }

            workbook.write(out);
        }
    }

    private void writeSummarySheet(XSSFWorkbook workbook, List<VehicleExportRow> rows, CellStyle headerStyle) {
        Sheet summary = workbook.createSheet("Summary");
        long available = rows.stream().filter(r -> "AVAILABLE".equals(r.getStatus())).count();
        long rented = rows.stream().filter(r -> "RENTED".equals(r.getStatus())).count();
        long maintenance = rows.stream().filter(r -> "IN_MAINTENANCE".equals(r.getStatus()) || "MAINTENANCE".equals(r.getStatus())).count();
        long outOfService = rows.stream().filter(r -> "OUT_OF_SERVICE".equals(r.getStatus())).count();
        long archived = rows.stream().filter(r -> "ARCHIVED".equals(r.getStatus())).count();
        BigDecimal avgPrice = rows.stream().map(VehicleExportRow::getPricePerDay).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long priced = rows.stream().filter(r -> r.getPricePerDay() != null).count();
        BigDecimal average = priced > 0 ? avgPrice.divide(BigDecimal.valueOf(priced), 2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;

        String[][] lines = {
                {"Total vehicles", String.valueOf(rows.size())},
                {"Available", String.valueOf(available)},
                {"Rented", String.valueOf(rented)},
                {"Maintenance", String.valueOf(maintenance)},
                {"Out of service", String.valueOf(outOfService)},
                {"Archived", String.valueOf(archived)},
                {"Average daily price", average.toPlainString()},
        };
        for (int i = 0; i < lines.length; i++) {
            Row row = summary.createRow(i);
            Cell label = row.createCell(0);
            label.setCellValue(lines[i][0]);
            label.setCellStyle(headerStyle);
            row.createCell(1).setCellValue(lines[i][1]);
        }
        summary.autoSizeColumn(0);
        summary.autoSizeColumn(1);
    }

    private void setString(Row row, int col, String value) {
        row.createCell(col).setCellValue(value != null ? value : "");
    }

    private void setNumeric(Row row, int col, Double value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value != null) cell.setCellValue(value);
        if (style != null) cell.setCellStyle(style);
    }

    private void setMoney(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value != null) cell.setCellValue(value.doubleValue());
        cell.setCellStyle(style);
    }

    private void setDate(Row row, int col, LocalDate value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value != null) {
            cell.setCellValue(value);
            cell.setCellStyle(style);
        }
    }

    private CellStyle headerStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setWrapText(true);
        return style;
    }

    private CellStyle dateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
        return style;
    }

    private CellStyle moneyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("#,##0.00"));
        return style;
    }
}
