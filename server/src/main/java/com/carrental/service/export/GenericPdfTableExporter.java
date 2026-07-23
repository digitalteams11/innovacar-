package com.carrental.service.export;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * Reusable A4 report-table PDF renderer via OpenPDF — the same layout
 * VehiclePdfExporter established (branded header, meta line, bordered
 * table with repeating header row, page-numbered footer), generalized so
 * every list-report export (contracts, payments, clients, ...) shares one
 * rendering path instead of re-implementing table/footer layout per entity.
 *
 * Built with the standard Helvetica base font — renders English and French
 * (Latin + accented characters) correctly, but does NOT embed an
 * Arabic-capable font, so Arabic report text will not shape/connect
 * correctly yet. Known, flagged limitation (same as VehiclePdfExporter):
 * full Arabic PDF support needs a bundled Unicode font registered via
 * BaseFont.createFont(..., IDENTITY_H, EMBEDDED) plus a bidi/shaping check.
 */
@Component
public class GenericPdfTableExporter {

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 16, Font.BOLD, new Color(0x0a, 0x0f, 0x2c));
    private static final Font META_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(0x64, 0x74, 0x8b));
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
    private static final Font CELL_FONT = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.BLACK);
    private static final Color BRAND = new Color(0x0a, 0x0f, 0x2c);

    /**
     * @param reportTitle e.g. "Contracts Report", "Payments Report"
     * @param headers column headers, in display order
     * @param rows each row is a String[] matching headers.length, one already-formatted cell per column
     * @param reportMeta agencyName / generatedBy / generatedAt / filters / entityLabel (e.g. "contracts") — all optional, rendered if present
     */
    public void write(OutputStream out, String reportTitle, String[] headers, List<String[]> rows, Map<String, Object> reportMeta) throws Exception {
        boolean landscape = headers.length > 6;
        Document document = new Document(landscape ? PageSize.A4.rotate() : PageSize.A4, 24, 24, 24, 36);
        PdfWriter writer = PdfWriter.getInstance(document, out);
        writer.setPageEvent(new FooterEvent());
        document.open();

        document.add(new Paragraph("Innovacar — " + reportTitle, TITLE_FONT));
        document.add(Chunk.NEWLINE);

        String agencyName = String.valueOf(reportMeta.getOrDefault("agencyName", ""));
        String generatedBy = String.valueOf(reportMeta.getOrDefault("generatedBy", ""));
        String generatedAt = String.valueOf(reportMeta.getOrDefault("generatedAt", ""));
        String filters = String.valueOf(reportMeta.getOrDefault("filters", "None"));
        String entityLabel = String.valueOf(reportMeta.getOrDefault("entityLabel", "records"));

        Paragraph meta = new Paragraph();
        meta.setFont(META_FONT);
        meta.add("Agency: " + agencyName + "   |   Generated: " + generatedAt + "   |   By: " + generatedBy + "\n");
        meta.add("Filters: " + filters + "   |   Total " + entityLabel + ": " + rows.size());
        document.add(meta);
        document.add(Chunk.NEWLINE);

        PdfPTable table = new PdfPTable(headers.length);
        table.setWidthPercentage(100);
        table.setHeaderRows(1);

        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, HEADER_FONT));
            cell.setBackgroundColor(BRAND);
            cell.setPadding(5);
            table.addCell(cell);
        }

        for (String[] row : rows) {
            for (String value : row) {
                PdfPCell cell = new PdfPCell(new Phrase(value != null ? value : "", CELL_FONT));
                cell.setPadding(4);
                table.addCell(cell);
            }
        }

        document.add(table);
        document.close();
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
