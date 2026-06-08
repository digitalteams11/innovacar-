package com.carrental.service;

import com.carrental.entity.Contract;
import com.carrental.entity.Tenant;
import com.lowagie.text.*;
import java.awt.Color;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfService {

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 18, Font.BOLD, new java.awt.Color(0x1e, 0x29, 0x3b));
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 12, Font.BOLD, new java.awt.Color(0x1e, 0x29, 0x3b));
    private static final Font LABEL_FONT = new Font(Font.HELVETICA, 9, Font.BOLD, new java.awt.Color(0x64, 0x70, 0x48));
    private static final Font VALUE_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, new java.awt.Color(0x1e, 0x29, 0x3b));
    private static final Font SMALL_FONT = new Font(Font.HELVETICA, 8, Font.NORMAL, new java.awt.Color(0x64, 0x70, 0x48));
    private static final Font BRAND_FONT = new Font(Font.HELVETICA, 10, Font.BOLD, new java.awt.Color(0x3b, 0x82, 0xf6));
    private static final java.awt.Color BRAND_BG = new java.awt.Color(0xf0, 0xf7, 0xff);
    private static final java.awt.Color BORDER_COLOR = new java.awt.Color(0xe8, 0xe6, 0xe1);

    private static final String PDF_STORAGE_DIR = "pdfs/contracts";

    public byte[] generateContractPdf(Contract contract, Tenant tenant, com.carrental.entity.Deposit deposit) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, baos);
            document.open();

            // ── Header ───────────────────────────────────────────────────────────
            PdfPTable header = new PdfPTable(2);
            header.setWidthPercentage(100);
            header.setWidths(new float[]{1, 1});

            PdfPCell agencyCell = new PdfPCell();
            agencyCell.setBorder(Rectangle.NO_BORDER);
            agencyCell.addElement(new Paragraph(tenant.getName() != null ? tenant.getName() : "Agency", TITLE_FONT));
            agencyCell.addElement(new Paragraph(tenant.getAddress() != null ? tenant.getAddress() : "", VALUE_FONT));
            agencyCell.addElement(new Paragraph(
                (tenant.getCity() != null ? tenant.getCity() : "") + " " +
                (tenant.getCountry() != null ? tenant.getCountry() : ""), VALUE_FONT));
            agencyCell.addElement(new Paragraph("Phone: " + (tenant.getPhone() != null ? tenant.getPhone() : "N/A"), VALUE_FONT));
            header.addCell(agencyCell);

            PdfPCell metaCell = new PdfPCell();
            metaCell.setBorder(Rectangle.NO_BORDER);
            metaCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            metaCell.addElement(rightAlign(new Paragraph("RENTAL AGREEMENT", HEADER_FONT)));
            metaCell.addElement(rightAlign(new Paragraph("Contract: " + contract.getContractNumber(), VALUE_FONT)));
            metaCell.addElement(rightAlign(new Paragraph("Date: " +
                (contract.getCreatedAt() != null ? contract.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "N/A"), SMALL_FONT)));
            header.addCell(metaCell);

            document.add(header);
            document.add(new Paragraph(" ", SMALL_FONT));
            document.add(line());
            document.add(new Paragraph(" ", SMALL_FONT));

            // ── Client Section ───────────────────────────────────────────────────
            document.add(sectionTitle("CLIENT INFORMATION"));
            PdfPTable clientTable = infoTable(new String[][]{
                {"Full Name", contract.getClientFullName(), "Phone", contract.getClientPhone()},
                {"Email", contract.getClientEmail() != null ? contract.getClientEmail() : "N/A", "CIN / Passport", contract.getClientCin() != null ? contract.getClientCin() : contract.getClientPassportNumber()},
                {"Driver License", contract.getClientDriverLicense() != null ? contract.getClientDriverLicense() : "N/A", "Nationality", contract.getClientNationality() != null ? contract.getClientNationality() : "N/A"},
                {"Address", (contract.getClientAddress() != null ? contract.getClientAddress() : "") + " " + (contract.getClientCity() != null ? contract.getClientCity() : ""), "Emergency Contact", (contract.getEmergencyContactName() != null ? contract.getEmergencyContactName() : "") + " " + (contract.getEmergencyContactPhone() != null ? contract.getEmergencyContactPhone() : "")},
            });
            document.add(clientTable);
            document.add(new Paragraph(" ", SMALL_FONT));

            // ── Vehicle Section ──────────────────────────────────────────────────
            document.add(sectionTitle("VEHICLE INFORMATION"));
            PdfPTable vehicleTable = infoTable(new String[][]{
                {"Brand / Model", (contract.getVehicleBrand() != null ? contract.getVehicleBrand() : "") + " " + (contract.getVehicleModel() != null ? contract.getVehicleModel() : ""), "Category", contract.getVehicleCategory() != null ? contract.getVehicleCategory() : "N/A"},
                {"Year", contract.getVehicleYear() != null ? String.valueOf(contract.getVehicleYear()) : "N/A", "Color", contract.getVehicleColor() != null ? contract.getVehicleColor() : "N/A"},
                {"Registration", contract.getVehicleRegistration() != null ? contract.getVehicleRegistration() : "N/A", "Transmission", contract.getVehicleTransmission() != null ? contract.getVehicleTransmission() : "N/A"},
                {"Fuel Type", contract.getFuelType() != null ? contract.getFuelType() : "N/A", "Fuel Level", contract.getFuelLevelStart() != null ? contract.getFuelLevelStart() : "Full"},
            });
            document.add(vehicleTable);
            document.add(new Paragraph(" ", SMALL_FONT));

            // ── Rental Details ───────────────────────────────────────────────────
            document.add(sectionTitle("RENTAL DETAILS"));
            PdfPTable rentalTable = infoTable(new String[][]{
                {"Start Date", contract.getStartDate() != null ? contract.getStartDate().toString() : "N/A", "End Date", contract.getEndDate() != null ? contract.getEndDate().toString() : "N/A"},
                {"Pickup Location", contract.getPickupLocation() != null ? contract.getPickupLocation() : "N/A", "Return Location", contract.getReturnLocation() != null ? contract.getReturnLocation() : "N/A"},
                {"Daily Rate", formatMAD(contract.getDailyPrice()), "Rental Days", String.valueOf(contract.getRentalDays() != null ? contract.getRentalDays() : "N/A")},
            });
            document.add(rentalTable);
            document.add(new Paragraph(" ", SMALL_FONT));

            // ── Pricing ──────────────────────────────────────────────────────────
            document.add(sectionTitle("PRICING BREAKDOWN"));
            PdfPTable priceTable = new PdfPTable(2);
            priceTable.setWidthPercentage(100);
            priceTable.setWidths(new float[]{3, 1});

            BigDecimal basePrice = safe(contract.getDailyPrice()).multiply(BigDecimal.valueOf(safeInt(contract.getRentalDays(), 1)));
            BigDecimal insurance = safe(contract.getInsuranceProvider() != null ? new BigDecimal("50") : BigDecimal.ZERO).multiply(BigDecimal.valueOf(safeInt(contract.getRentalDays(), 1)));
            BigDecimal delivery = safe(contract.getDeliveryFees());
            BigDecimal subtotal = basePrice.add(insurance).add(delivery);
            BigDecimal discount = safe(contract.getDiscountAmount());
            BigDecimal afterDiscount = subtotal.subtract(discount).max(BigDecimal.ZERO);
            BigDecimal tax = afterDiscount.multiply(new BigDecimal("0.20"));
            BigDecimal total = afterDiscount.add(tax);

            priceRow(priceTable, "Base Price (" + safeInt(contract.getRentalDays(), 1) + " days x " + formatMAD(contract.getDailyPrice()) + ")", formatMAD(basePrice));
            priceRow(priceTable, "Insurance", formatMAD(insurance));
            if (delivery.compareTo(BigDecimal.ZERO) > 0) priceRow(priceTable, "Delivery Fees", formatMAD(delivery));
            priceRow(priceTable, "Subtotal", formatMAD(subtotal), true);
            if (discount.compareTo(BigDecimal.ZERO) > 0) priceRow(priceTable, "Discount", "-" + formatMAD(discount));
            priceRow(priceTable, "Tax (20%)", formatMAD(tax));
            priceRow(priceTable, "TOTAL", formatMAD(total), true);
            document.add(priceTable);
            document.add(new Paragraph(" ", SMALL_FONT));

            // ── Security Deposit ─────────────────────────────────────────────────
            if (contract.getDepositAmount() != null && contract.getDepositAmount().compareTo(BigDecimal.ZERO) > 0) {
                document.add(sectionTitle("SECURITY DEPOSIT"));
                PdfPTable depositTable = new PdfPTable(2);
                depositTable.setWidthPercentage(100);
                depositTable.setWidths(new float[]{1, 2});

                String depositType = deposit != null && deposit.getDepositType() != null
                        ? deposit.getDepositType().name() : "CASH";
                String depositReference = deposit != null && deposit.getReference() != null
                        ? deposit.getReference() : "N/A";
                String depositStatus = deposit != null && deposit.getStatus() != null
                        ? deposit.getStatus().name() : "PENDING";
                String conditions = deposit != null && deposit.getConditionsText() != null
                        ? deposit.getConditionsText()
                        : "The deposit will be returned after inspection of the vehicle and validation of all contractual obligations.";

                depositRow(depositTable, "Deposit Type", depositType);
                depositRow(depositTable, "Amount", formatMAD(contract.getDepositAmount()));
                depositRow(depositTable, "Reference", depositReference);
                depositRow(depositTable, "Status", depositStatus);
                document.add(depositTable);

                Paragraph conditionsPara = new Paragraph("Conditions: " + conditions, SMALL_FONT);
                conditionsPara.setSpacingBefore(6);
                document.add(conditionsPara);

                // Client acceptance checkbox area
                Paragraph acceptance = new Paragraph(
                    "\u2610 I understand and accept the deposit conditions.",
                    SMALL_FONT);
                acceptance.setSpacingBefore(8);
                document.add(acceptance);

                document.add(new Paragraph(" ", SMALL_FONT));
            }

            // ── Terms ────────────────────────────────────────────────────────────
            document.add(sectionTitle("TERMS & CONDITIONS"));
            String[] terms = {
                "1. The vehicle must be returned with the same fuel level as received.",
                "2. Any damage to the vehicle during the rental period is the responsibility of the client.",
                "3. Late return will incur additional charges as per the company policy.",
                "4. The vehicle is insured for third-party liability only.",
                "5. Smoking is strictly prohibited inside the vehicle.",
                "6. The client must present a valid driver's license at pickup.",
            };
            for (String term : terms) {
                document.add(new Paragraph(term, SMALL_FONT));
            }
            document.add(new Paragraph(" ", SMALL_FONT));

            // ── Signatures ───────────────────────────────────────────────────────
            document.add(sectionTitle("SIGNATURES"));
            PdfPTable sigTable = new PdfPTable(2);
            sigTable.setWidthPercentage(100);
            sigTable.setWidths(new float[]{1, 1});

            PdfPCell clientSig = new PdfPCell();
            clientSig.setBorder(Rectangle.NO_BORDER);
            clientSig.setFixedHeight(80);
            clientSig.addElement(new Paragraph("Client Signature", LABEL_FONT));
            if (contract.getClientSignature() != null && !contract.getClientSignature().isEmpty()) {
                try {
                    byte[] imgBytes = decodeBase64Image(contract.getClientSignature());
                    Image img = Image.getInstance(imgBytes);
                    img.scaleToFit(120, 60);
                    clientSig.addElement(img);
                } catch (Exception e) {
                    log.warn("Failed to embed client signature in PDF for contract {}", contract.getContractNumber(), e);
                    clientSig.addElement(new Paragraph("[Signature on file]", SMALL_FONT));
                }
            } else {
                clientSig.addElement(new Paragraph(" ", SMALL_FONT));
                clientSig.addElement(new Paragraph("_____________________________", SMALL_FONT));
            }
            sigTable.addCell(clientSig);

            PdfPCell ownerSig = new PdfPCell();
            ownerSig.setBorder(Rectangle.NO_BORDER);
            ownerSig.setFixedHeight(80);
            ownerSig.addElement(new Paragraph("Agency Representative", LABEL_FONT));
            if (contract.getOwnerSignature() != null && !contract.getOwnerSignature().isEmpty()) {
                try {
                    byte[] imgBytes = decodeBase64Image(contract.getOwnerSignature());
                    Image img = Image.getInstance(imgBytes);
                    img.scaleToFit(120, 60);
                    ownerSig.addElement(img);
                } catch (Exception e) {
                    log.warn("Failed to embed owner signature in PDF for contract {}", contract.getContractNumber(), e);
                    ownerSig.addElement(new Paragraph("[Signature on file]", SMALL_FONT));
                }
            } else {
                ownerSig.addElement(new Paragraph(" ", SMALL_FONT));
                ownerSig.addElement(new Paragraph("_____________________________", SMALL_FONT));
            }
            sigTable.addCell(ownerSig);

            document.add(sigTable);

            // ── Footer ───────────────────────────────────────────────────────────
            document.add(new Paragraph(" ", SMALL_FONT));
            document.add(line());
            Paragraph footer = new Paragraph("This contract was generated electronically by " +
                (tenant.getName() != null ? tenant.getName() : "the agency") + " on " +
                java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".", SMALL_FONT);
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate PDF for contract {}", contract.getContractNumber(), e);
            throw new RuntimeException("PDF generation failed", e);
        }
    }

    // ── PDF File Storage ─────────────────────────────────────────────────────

    /**
     * Saves a generated PDF to disk and returns the API URL path to access it.
     */
    public String saveContractPdf(Contract contract, byte[] pdfBytes) {
        try {
            Path dir = Paths.get(PDF_STORAGE_DIR);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            String fileName = sanitizeFileName(contract.getContractNumber() + "_" + contract.getId() + ".pdf");
            Path filePath = dir.resolve(fileName);

            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                fos.write(pdfBytes);
            }

            log.info("PDF saved for contract [id={}, number={}] at {}", contract.getId(), contract.getContractNumber(), filePath);
            return "/api/contracts/" + contract.getId() + "/pdf-file";
        } catch (IOException e) {
            log.error("Failed to save PDF for contract {}", contract.getContractNumber(), e);
            throw new RuntimeException("PDF save failed", e);
        }
    }

    /**
     * Reads a saved PDF file from disk.
     */
    public byte[] getContractPdfFile(Long contractId, String contractNumber) {
        try {
            String fileName = sanitizeFileName(contractNumber + "_" + contractId + ".pdf");
            Path filePath = Paths.get(PDF_STORAGE_DIR).resolve(fileName);
            if (!Files.exists(filePath)) {
                log.warn("PDF file not found for contract [id={}, number={}] at {}", contractId, contractNumber, filePath);
                return null;
            }
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            log.error("Failed to read PDF for contract [id={}]", contractId, e);
            return null;
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Decodes a base64 data URL (e.g. data:image/png;base64,...) into raw image bytes.
     * Also handles plain base64 strings without the data URL prefix.
     */
    private byte[] decodeBase64Image(String dataUrl) {
        if (dataUrl == null || dataUrl.isEmpty()) {
            throw new IllegalArgumentException("Signature data is empty");
        }
        String base64 = dataUrl;
        // Strip data URL prefix if present
        if (dataUrl.contains(",")) {
            base64 = dataUrl.substring(dataUrl.indexOf(",") + 1);
        }
        return Base64.getDecoder().decode(base64);
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private Paragraph sectionTitle(String text) {
        Paragraph p = new Paragraph(text, HEADER_FONT);
        p.setSpacingAfter(6);
        return p;
    }

    private PdfPTable infoTable(String[][] rows) {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 2, 1, 2});
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                PdfPCell cell = new PdfPCell(new Paragraph(row[i] != null ? row[i] : "N/A", i % 2 == 0 ? LABEL_FONT : VALUE_FONT));
                cell.setBorder(Rectangle.BOTTOM);
                cell.setBorderColor(BORDER_COLOR);
                cell.setPadding(4);
                cell.setBackgroundColor(i % 2 == 0 ? BRAND_BG : Color.WHITE);
                table.addCell(cell);
            }
        }
        return table;
    }

    private void depositRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Paragraph(label, LABEL_FONT));
        labelCell.setBorder(Rectangle.BOTTOM);
        labelCell.setBorderColor(BORDER_COLOR);
        labelCell.setPadding(4);
        labelCell.setBackgroundColor(BRAND_BG);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Paragraph(value != null ? value : "N/A", VALUE_FONT));
        valueCell.setBorder(Rectangle.BOTTOM);
        valueCell.setBorderColor(BORDER_COLOR);
        valueCell.setPadding(4);
        table.addCell(valueCell);
    }

    private void priceRow(PdfPTable table, String label, String value) {
        priceRow(table, label, value, false);
    }

    private void priceRow(PdfPTable table, String label, String value, boolean bold) {
        PdfPCell labelCell = new PdfPCell(new Paragraph(label, bold ? HEADER_FONT : VALUE_FONT));
        labelCell.setBorder(Rectangle.BOTTOM);
        labelCell.setBorderColor(BORDER_COLOR);
        labelCell.setPadding(4);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Paragraph(value, bold ? HEADER_FONT : VALUE_FONT));
        valueCell.setBorder(Rectangle.BOTTOM);
        valueCell.setBorderColor(BORDER_COLOR);
        valueCell.setPadding(4);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(valueCell);
    }

    private Paragraph rightAlign(Paragraph p) {
        p.setAlignment(Element.ALIGN_RIGHT);
        return p;
    }

    private Paragraph line() {
        Paragraph p = new Paragraph(" ");
        p.setLeading(1);
        return p;
    }

    private BigDecimal safe(BigDecimal val) {
        return val != null ? val : BigDecimal.ZERO;
    }

    private int safeInt(Integer val, int def) {
        return val != null ? val : def;
    }

    private String formatMAD(BigDecimal val) {
        return (val != null ? val : BigDecimal.ZERO).toPlainString() + " MAD";
    }
}
