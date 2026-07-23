package com.carrental.service.export;

import com.carrental.dto.export.VehicleExportRow;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.OutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Fleet report PDF via OpenPDF (the same library already used for contract
 * PDFs). Built with the standard Helvetica base font — this renders English
 * and French (Latin + accented characters) correctly, but does NOT embed an
 * Arabic-capable font, so Arabic report text will not shape/connect
 * correctly yet. That is a known, flagged limitation of this pass, not a
 * silent gap: full Arabic PDF support needs a bundled Unicode font
 * (e.g. Noto Sans Arabic) registered via BaseFont.createFont(..., IDENTITY_H,
 * EMBEDDED) plus a bidi/shaping check, which is follow-up work.
 */
@Component
public class VehiclePdfExporter {

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 16, Font.BOLD, new Color(0x0a, 0x0f, 0x2c));
    private static final Font META_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(0x64, 0x74, 0x8b));
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
    private static final Font CELL_FONT = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.BLACK);
    private static final Color BRAND = new Color(0x0a, 0x0f, 0x2c);

    public void write(OutputStream out, List<VehicleExportRow> rows, Map<String, Object> reportMeta) throws Exception {
        Document document = new Document(PageSize.A4.rotate(), 24, 24, 24, 36);
        PdfWriter writer = PdfWriter.getInstance(document, out);
        writer.setPageEvent(new FooterEvent());
        document.open();

        document.add(new Paragraph("Innovacar — Fleet Report", TITLE_FONT));
        document.add(Chunk.NEWLINE);

        String agencyName = String.valueOf(reportMeta.getOrDefault("agencyName", ""));
        String generatedBy = String.valueOf(reportMeta.getOrDefault("generatedBy", ""));
        String generatedAt = String.valueOf(reportMeta.getOrDefault("generatedAt", ""));
        String filters = String.valueOf(reportMeta.getOrDefault("filters", "None"));

        Paragraph meta = new Paragraph();
        meta.setFont(META_FONT);
        meta.add("Agency: " + agencyName + "   |   Generated: " + generatedAt + "   |   By: " + generatedBy + "\n");
        meta.add("Filters: " + filters + "   |   Total vehicles: " + rows.size());
        document.add(meta);
        document.add(Chunk.NEWLINE);

        String[] headers = {"ID", "Brand", "Model", "Category", "Plate", "Status", "Price/Day", "Fuel", "Transmission", "Mileage", "Branch", "Next Maint."};
        PdfPTable table = new PdfPTable(headers.length);
        table.setWidthPercentage(100);
        table.setHeaderRows(1);

        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, HEADER_FONT));
            cell.setBackgroundColor(BRAND);
            cell.setPadding(5);
            table.addCell(cell);
        }

        DateTimeFormatter dateFmt = DateTimeFormatter.ISO_LOCAL_DATE;
        for (VehicleExportRow r : rows) {
            addCell(table, String.valueOf(r.getId()));
            addCell(table, r.getBrand());
            addCell(table, r.getModel());
            addCell(table, r.getCategory());
            addCell(table, r.getPlate());
            addCell(table, r.getStatus());
            addCell(table, r.getPricePerDay() != null ? r.getPricePerDay().toPlainString() : "");
            addCell(table, r.getFuel());
            addCell(table, r.getTransmission());
            addCell(table, r.getMileage() != null ? String.valueOf(r.getMileage()) : "");
            addCell(table, r.getBranch());
            addCell(table, r.getNextMaintenanceDate() != null ? r.getNextMaintenanceDate().format(dateFmt) : "");
        }

        document.add(table);
        document.close();
    }

    private void addCell(PdfPTable table, String value) {
        PdfPCell cell = new PdfPCell(new Phrase(value != null ? value : "", CELL_FONT));
        cell.setPadding(4);
        table.addCell(cell);
    }

    /** Page number + confidentiality note on every page. */
    private static class FooterEvent extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Font footerFont = new Font(Font.HELVETICA, 7, Font.NORMAL, new Color(0x94, 0xa3, 0xb8));
            Phrase footer = new Phrase(
                    "Innovacar / Innovax Technologies — Confidential, for internal agency use only — Page "
                            + writer.getPageNumber(), footerFont);
            com.lowagie.text.pdf.ColumnText.showTextAligned(
                    writer.getDirectContent(), Element.ALIGN_CENTER, footer,
                    (document.right() + document.left()) / 2, document.bottom() - 18, 0);
        }
    }
}
