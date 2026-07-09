package com.carrental.service;

import com.carrental.entity.AdditionalDriver;
import com.carrental.entity.Contract;
import com.carrental.entity.ContractTemplate;
import com.carrental.entity.Deposit;
import com.carrental.entity.Tenant;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfService {
    private final ContractTemplatePdfService contractTemplatePdfService;

    private static final Color INK = new Color(19, 43, 76);
    private static final Color BLUE = new Color(23, 76, 135);
    private static final Color LIGHT_BLUE = new Color(235, 243, 252);
    private static final Color BORDER = new Color(83, 103, 130);
    private static final Color PALE = new Color(248, 250, 252);

    private static final Font TITLE = new Font(Font.HELVETICA, 15, Font.BOLD, INK);
    private static final Font SECTION = new Font(Font.HELVETICA, 8.8f, Font.BOLD, Color.WHITE);
    private static final Font LABEL = new Font(Font.HELVETICA, 7.2f, Font.BOLD, INK);
    private static final Font VALUE = new Font(Font.HELVETICA, 7.2f, Font.NORMAL, Color.BLACK);
    private static final Font SMALL = new Font(Font.HELVETICA, 6.5f, Font.NORMAL, Color.BLACK);
    private static final Font SMALL_BOLD = new Font(Font.HELVETICA, 6.5f, Font.BOLD, INK);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String PDF_STORAGE_DIR = "pdfs/contracts";

    public byte[] generateContractPdf(Contract contract, Tenant tenant, Deposit deposit) {
        return generateContractPdf(contract, tenant, deposit, true);
    }

    public byte[] generateContractPdf(Contract contract, Tenant tenant, Deposit deposit, boolean useAgencyTemplate) {
        logAgencyDataDebug(contract, tenant);
        if (useAgencyTemplate) {
            // 1. Try agency-uploaded template overlay (only works when frontFilePath is set)
            java.util.Optional<byte[]> templatePdf = contractTemplatePdfService.generateIfAvailable(contract, tenant, deposit);
            if (templatePdf.isPresent()) {
                Long tplId = contract != null && contract.getSelectedTemplate() != null ? contract.getSelectedTemplate().getId() : null;
                String tplName = contract != null && contract.getSelectedTemplate() != null ? contract.getSelectedTemplate().getName() : "tenant_default";
                log.info("[CONTRACT_PDF_GENERATE] contractId={} templateId={} templateName={} output=AGENCY_TEMPLATE success=true",
                        contract != null ? contract.getId() : null, tplId, tplName);
                return templatePdf.get();
            }

            // 2. Overlay returned empty (system template or no uploaded file).
            //    Resolve the template record to get its code and route to the right programmatic layout.
            if (contract != null && tenant != null) {
                ContractTemplate resolved = contractTemplatePdfService.resolveTemplateForPdf(contract, tenant.getId());
                if (resolved != null) {
                    String code = resolved.getTemplateCode() != null ? resolved.getTemplateCode() : "";
                    log.info("[CONTRACT_PDF_TEMPLATE] contractId={} contractNumber={} selectedTemplateId={} selectedTemplateName={} templateCode={} fallbackUsed=false",
                            contract.getId(), contract.getContractNumber(), resolved.getId(), resolved.getName(), code);
                    return generateByTemplateCode(contract, tenant, deposit, code);
                }
            }
        }
        // 3. No template at all — use classic Moroccan layout.
        log.info("[CONTRACT_PDF_GENERATE] contractId={} templateId=null templateName=SYSTEM_PDF output=SYSTEM_DEFAULT success=true",
                contract != null ? contract.getId() : null);
        return generateClassicLayout(contract, tenant, deposit);
    }

    /** Routes to the correct programmatic layout based on the system template code. */
    private byte[] generateByTemplateCode(Contract contract, Tenant tenant, Deposit deposit, String code) {
        if (code == null) return generateClassicLayout(contract, tenant, deposit);
        return switch (code.toLowerCase()) {
            case "modern-a4" -> generateModernLayout(contract, tenant, deposit);
            case "compact-one-page" -> generateCompactLayout(contract, tenant, deposit);
            case "conditions-page", "detailed-agency", "vehicle-inspection" -> generateDetailedLayout(contract, tenant, deposit);
            case "premium-luxury", "enterprise-custom" -> generatePremiumLayout(contract, tenant, deposit);
            default -> generateClassicLayout(contract, tenant, deposit); // classic-moroccan + unknown codes
        };
    }

    // ── LAYOUT: Classic Moroccan ──────────────────────────────────────────────

    private byte[] generateClassicLayout(Contract contract, Tenant tenant, Deposit deposit) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 24, 24, 22, 22);
            PdfWriter.getInstance(document, baos);
            document.open();

            addHeader(document, contract, tenant);
            addFuelAndMeta(document, contract);

            PdfPTable main = new PdfPTable(2);
            main.setWidthPercentage(100);
            main.setWidths(new float[]{1, 1});
            main.addCell(column(leftColumn(contract)));
            main.addCell(column(rightColumn(contract)));
            document.add(main);

            addPayment(document, contract, deposit);
            addDocuments(document, contract);
            addLegalNote(document, tenant, contract);
            addSignatures(document, contract, tenant);
            addVerification(document, contract);
            addTermsPage(document, contract, tenant);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Classic layout PDF generation failed for contract {}", contract != null ? contract.getContractNumber() : null, e);
            throw new IllegalStateException("Unable to generate contract PDF", e);
        }
    }

    // ── LAYOUT: Modern A4 ────────────────────────────────────────────────────

    private byte[] generateModernLayout(Contract contract, Tenant tenant, Deposit deposit) {
        Color SLATE_900 = new Color(15, 23, 42);
        Color SLATE_50  = new Color(248, 250, 252);
        Color SLATE_200 = new Color(226, 232, 240);
        Font MOD_HDR   = new Font(Font.HELVETICA, 14, Font.BOLD, Color.WHITE);
        Font MOD_SEC   = new Font(Font.HELVETICA, 8f, Font.BOLD, Color.WHITE);
        Font MOD_LBL   = new Font(Font.HELVETICA, 6.8f, Font.BOLD, SLATE_900);
        Font MOD_VAL   = new Font(Font.HELVETICA, 7f, Font.NORMAL, Color.BLACK);
        Font MOD_SM    = new Font(Font.HELVETICA, 6f, Font.NORMAL, new Color(71, 85, 105));
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 28, 28, 24, 24);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            // ── Header: dark agency bar + contract meta ──
            PdfPTable hdr = new PdfPTable(2);
            hdr.setWidthPercentage(100);
            hdr.setWidths(new float[]{2f, 1.2f});

            PdfPCell agCell = new PdfPCell();
            agCell.setBorder(Rectangle.NO_BORDER);
            agCell.setBackgroundColor(SLATE_900);
            agCell.setPadding(10);
            Image logo = agencyAssetImage(effectiveLogo(contract, tenant), "logo");
            if (logo != null) { logo.scaleToFit(44, 34); agCell.addElement(logo); }
            agCell.addElement(new Paragraph(value(tenant != null ? tenant.getName() : null, "Agence de Location"), MOD_HDR));
            agCell.addElement(new Paragraph(join(tenant != null ? tenant.getAddress() : null, tenant != null ? tenant.getCity() : null), MOD_SM));
            agCell.addElement(new Paragraph("Tel: " + value(tenant != null ? tenant.getPhone() : null, ""), MOD_SM));
            hdr.addCell(agCell);

            PdfPCell ctCell = new PdfPCell();
            ctCell.setBorder(Rectangle.NO_BORDER);
            ctCell.setBackgroundColor(SLATE_50);
            ctCell.setPadding(10);
            ctCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            ctCell.addElement(new Paragraph("CONTRAT DE LOCATION", new Font(Font.HELVETICA, 8, Font.BOLD, SLATE_900)));
            ctCell.addElement(new Paragraph("N°: " + value(contract != null ? contract.getContractNumber() : null, ""), new Font(Font.HELVETICA, 8.5f, Font.BOLD, SLATE_900)));
            ctCell.addElement(new Paragraph("Date: " + date(contract != null ? contract.getStartDate() : null), MOD_SM));
            hdr.addCell(ctCell);
            doc.add(hdr);

            // ── Period strip ──
            PdfPTable strip = new PdfPTable(4);
            strip.setWidthPercentage(100);
            strip.setSpacingBefore(6);
            addInfoBox(strip, "DEPART", date(valueDate(contract != null ? contract.getPickupDate() : null, contract != null ? contract.getStartDate() : null)) + " " + time(contract != null ? contract.getPickupTime() : null), SLATE_50, SLATE_200, MOD_LBL, MOD_VAL);
            addInfoBox(strip, "RETOUR",  date(valueDate(contract != null ? contract.getReturnDate() : null, contract != null ? contract.getEndDate() : null))  + " " + time(contract != null ? contract.getReturnTime() : null),  SLATE_50, SLATE_200, MOD_LBL, MOD_VAL);
            addInfoBox(strip, "VEHICULE", value(contract != null ? contract.getVehicleBrand() : null, "") + " " + value(contract != null ? contract.getVehicleModel() : null, ""), SLATE_50, SLATE_200, MOD_LBL, MOD_VAL);
            addInfoBox(strip, "IMMAT.",   value(contract != null ? contract.getVehicleRegistration() : null, ""), SLATE_50, SLATE_200, MOD_LBL, MOD_VAL);
            doc.add(strip);

            // ── Client / Vehicle ──
            PdfPTable body = new PdfPTable(2);
            body.setWidthPercentage(100);
            body.setWidths(new float[]{1, 1});
            body.setSpacingBefore(5);
            body.addCell(column(modernSection("LOCATAIRE", new String[][]{
                {"Nom complet",   value(contract != null ? contract.getClientFullName() : null, "")},
                {"CIN/Passeport", value(contract != null ? contract.getClientCin() : null, contract != null ? contract.getClientPassportNumber() : null)},
                {"Permis n°",     value(contract != null ? contract.getClientDriverLicense() : null, "")},
                {"Telephone",     value(contract != null ? contract.getClientPhone() : null, "")},
                {"Nationalite",   value(contract != null ? contract.getClientNationality() : null, "")},
                {"Adresse",       join(contract != null ? contract.getClientAddress() : null, contract != null ? contract.getClientCity() : null)},
            }, SLATE_900, SLATE_50, MOD_SEC, MOD_LBL, MOD_VAL)));
            body.addCell(column(modernSection("VEHICULE", new String[][]{
                {"Marque/Modele",  value(contract != null ? contract.getVehicleBrand() : null, "") + " " + value(contract != null ? contract.getVehicleModel() : null, "")},
                {"Immatriculation",value(contract != null ? contract.getVehicleRegistration() : null, "")},
                {"Carburant",      value(contract != null ? contract.getFuelType() : null, contract != null ? contract.getFuelLevelStart() : null)},
                {"Km depart",      integer(contract != null ? contract.getMileageStart() : null)},
                {"Km retour",      integer(contract != null ? contract.getMileageEnd() : null)},
                {"Niveau carb.",   value(contract != null ? contract.getFuelLevelStart() : null, "")},
            }, SLATE_900, SLATE_50, MOD_SEC, MOD_LBL, MOD_VAL)));
            doc.add(body);

            // ── Payment ──
            PdfPTable pay = new PdfPTable(4);
            pay.setWidthPercentage(100);
            pay.setSpacingBefore(5);
            PdfPCell payHdr = new PdfPCell(new Phrase("REGLEMENT", MOD_SEC));
            payHdr.setColspan(4); payHdr.setBackgroundColor(SLATE_900);
            payHdr.setPadding(4); payHdr.setBorder(Rectangle.NO_BORDER);
            payHdr.setHorizontalAlignment(Element.ALIGN_CENTER);
            pay.addCell(payHdr);
            paymentField(pay, "Prix/jour",  money(contract != null ? contract.getDailyPrice() : null));
            paymentField(pay, "Nb jours",   integer(contract != null ? contract.getRentalDays() : null));
            paymentField(pay, "Total",       money(valueMoney(contract != null ? contract.getTotalPrice() : null, contract != null ? rentalTotal(contract) : null)));
            paymentField(pay, "Avance",      money(contract != null ? contract.getPaidAmount() : null));
            paymentField(pay, "Reste",       money(contract != null ? contract.getRemainingAmount() : null));
            paymentField(pay, "Caution",     depositMoney(valueMoney(contract != null ? contract.getDepositAmount() : null, deposit != null ? deposit.getAmount() : null)));
            paymentField(pay, "Mode paiement", value(contract != null ? contract.getPaymentMethod() : null, ""));
            paymentField(pay, "Frais supp.", money(sum(contract != null ? contract.getDeliveryFees() : null, contract != null ? contract.getReturnFees() : null)));
            doc.add(pay);

            // ── Docs ──
            PdfPTable docsT = new PdfPTable(1);
            docsT.setWidthPercentage(100);
            docsT.setSpacingBefore(4);
            PdfPCell docHdr = new PdfPCell(new Phrase("DOCUMENTS", MOD_SEC));
            docHdr.setBackgroundColor(SLATE_900); docHdr.setPadding(4); docHdr.setBorder(Rectangle.NO_BORDER);
            docsT.addCell(docHdr);
            PdfPCell docBody = new PdfPCell(new Phrase("[ ] Carte grise  [ ] Assurance  [ ] Vignette  [ ] Visite technique  [ ] Autorisation", MOD_VAL));
            docBody.setPadding(5); docBody.setBackgroundColor(SLATE_50); docBody.setBorder(Rectangle.NO_BORDER);
            docsT.addCell(docBody);
            doc.add(docsT);

            // ── Signatures ──
            PdfPTable sigs = new PdfPTable(2);
            sigs.setWidthPercentage(100);
            sigs.setSpacingBefore(5);
            sigs.addCell(signatureCell("Signature du Client", contract != null ? contract.getClientSignature() : null, contract != null ? contract.getClientSignedAt() : null));
            sigs.addCell(signatureCell("Signature Agence", value(contract != null ? contract.getOwnerSignature() : null, tenant != null ? tenant.getAgencySignature() : null), contract != null ? contract.getOwnerSignedAt() : null));
            doc.add(sigs);

            // Stamp if available (snapshot-aware)
            String modStampUrl = effectiveStamp(contract, tenant);
            Image modStampImg = agencyAssetImage(modStampUrl, "stamp");
            if (modStampImg != null) {
                PdfPTable modStampT = new PdfPTable(2);
                modStampT.setWidthPercentage(100);
                modStampT.setWidths(new float[]{1, 1});
                modStampT.addCell(cell("", Rectangle.NO_BORDER, 2, Element.ALIGN_LEFT, Color.WHITE));
                PdfPCell modStampC = cell("", Rectangle.BOX, 3, Element.ALIGN_CENTER, Color.WHITE);
                modStampImg.scaleToFit(68, 68);
                modStampImg.setAlignment(Element.ALIGN_CENTER);
                Paragraph modStampLbl = new Paragraph("Cachet / Tampon", SMALL_BOLD);
                modStampLbl.setAlignment(Element.ALIGN_CENTER);
                modStampC.addElement(modStampLbl);
                modStampC.addElement(modStampImg);
                modStampT.addCell(modStampC);
                doc.add(modStampT);
            }

            // ── QR ──
            PdfPTable verif = new PdfPTable(2);
            verif.setWidthPercentage(100);
            verif.setSpacingBefore(4);
            verif.addCell(pseudoQrCell(value(contract != null ? contract.getQrToken() : null, contract != null ? contract.getContractNumber() : null)));
            verif.addCell(cell("QR verification | " + value(contract != null ? contract.getContractNumber() : null, "") + " | " + value(contract != null ? contract.getPublicSigningUrl() : null, contract != null ? contract.getQrToken() : null, ""), Rectangle.BOX, 5, Element.ALIGN_LEFT, Color.WHITE));
            doc.add(verif);
            addTermsPage(doc, contract, tenant);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Modern A4 PDF failed for contract {}, falling back to classic", contract != null ? contract.getContractNumber() : null, e);
            return generateClassicLayout(contract, tenant, deposit);
        }
    }

    // ── LAYOUT: Compact One Page ──────────────────────────────────────────────

    private byte[] generateCompactLayout(Contract contract, Tenant tenant, Deposit deposit) {
        Font COMPACT_TITLE   = new Font(Font.HELVETICA, 10, Font.BOLD, INK);
        Font COMPACT_SECTION = new Font(Font.HELVETICA, 7f, Font.BOLD, Color.WHITE);
        Font COMPACT_LABEL   = new Font(Font.HELVETICA, 6f, Font.BOLD, INK);
        Font COMPACT_VALUE   = new Font(Font.HELVETICA, 6f, Font.NORMAL, Color.BLACK);
        Font COMPACT_SMALL   = new Font(Font.HELVETICA, 5.5f, Font.NORMAL, Color.BLACK);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 16, 16, 14, 14);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            // ── Compact header ──
            PdfPTable hdr = new PdfPTable(2);
            hdr.setWidthPercentage(100);
            hdr.setWidths(new float[]{2.2f, 1});
            PdfPCell agCell = cell("", Rectangle.BOX, 3, Element.ALIGN_LEFT, LIGHT_BLUE);
            agCell.addElement(new Paragraph(value(tenant != null ? tenant.getName() : null, "Agence"), COMPACT_TITLE));
            agCell.addElement(new Paragraph(join(tenant != null ? tenant.getAddress() : null, tenant != null ? tenant.getCity() : null), COMPACT_SMALL));
            agCell.addElement(new Paragraph("Tel: " + value(tenant != null ? tenant.getPhone() : null, ""), COMPACT_SMALL));
            hdr.addCell(agCell);
            PdfPCell numCell = cell("CONTRAT N°: " + value(contract != null ? contract.getContractNumber() : null, ""), Rectangle.BOX, 3, Element.ALIGN_CENTER, Color.WHITE, COMPACT_LABEL);
            numCell.addElement(new Paragraph("Date: " + date(contract != null ? contract.getStartDate() : null), COMPACT_SMALL));
            hdr.addCell(numCell);
            doc.add(hdr);

            // ── Title ──
            PdfPTable ttl = new PdfPTable(1);
            ttl.setWidthPercentage(100);
            ttl.addCell(cell("CONTRAT DE LOCATION DE VOITURE - COMPACT", Rectangle.BOX, 3, Element.ALIGN_CENTER, BLUE, COMPACT_SECTION));
            doc.add(ttl);

            // ── Fuel ──
            PdfPTable fuel = new PdfPTable(2);
            fuel.setWidthPercentage(100);
            fuel.setWidths(new float[]{2, 1});
            fuel.addCell(cell("Carburant: 0[ ] 1/4[ ] 1/2[ ] 3/4[ ] 1[ ]  Niveau: " + value(contract != null ? contract.getFuelLevelStart() : null, ""), Rectangle.BOX, 2.5f, Element.ALIGN_LEFT, Color.WHITE, COMPACT_SMALL));
            fuel.addCell(cell("Edition: " + date(contract != null && contract.getCreatedAt() != null ? contract.getCreatedAt().toLocalDate() : LocalDate.now()), Rectangle.BOX, 2.5f, Element.ALIGN_RIGHT, Color.WHITE, COMPACT_SMALL));
            doc.add(fuel);

            // ── Client + Vehicle compact ──
            PdfPTable body = new PdfPTable(2);
            body.setWidthPercentage(100);
            body.setWidths(new float[]{1, 1});
            PdfPTable leftT = new PdfPTable(1); leftT.setWidthPercentage(100);
            leftT.addCell(cell("CLIENT", Rectangle.BOX, 2.5f, Element.ALIGN_CENTER, BLUE, COMPACT_SECTION));
            compactField(leftT, "Nom", value(contract != null ? contract.getClientFullName() : null, ""), COMPACT_LABEL, COMPACT_VALUE);
            compactField(leftT, "CIN", value(contract != null ? contract.getClientCin() : null, contract != null ? contract.getClientPassportNumber() : null), COMPACT_LABEL, COMPACT_VALUE);
            compactField(leftT, "Permis", value(contract != null ? contract.getClientDriverLicense() : null, ""), COMPACT_LABEL, COMPACT_VALUE);
            compactField(leftT, "Tel", value(contract != null ? contract.getClientPhone() : null, ""), COMPACT_LABEL, COMPACT_VALUE);
            compactField(leftT, "Adresse", join(contract != null ? contract.getClientAddress() : null, contract != null ? contract.getClientCity() : null), COMPACT_LABEL, COMPACT_VALUE);
            body.addCell(column(leftT));

            PdfPTable rightT = new PdfPTable(1); rightT.setWidthPercentage(100);
            rightT.addCell(cell("VOITURE", Rectangle.BOX, 2.5f, Element.ALIGN_CENTER, BLUE, COMPACT_SECTION));
            compactField(rightT, "Marque",  value(contract != null ? contract.getVehicleBrand() : null, "") + " " + value(contract != null ? contract.getVehicleModel() : null, ""), COMPACT_LABEL, COMPACT_VALUE);
            compactField(rightT, "Immat.",  value(contract != null ? contract.getVehicleRegistration() : null, ""), COMPACT_LABEL, COMPACT_VALUE);
            compactField(rightT, "Km dep.", integer(contract != null ? contract.getMileageStart() : null), COMPACT_LABEL, COMPACT_VALUE);
            compactField(rightT, "Depart",  date(valueDate(contract != null ? contract.getPickupDate() : null, contract != null ? contract.getStartDate() : null)), COMPACT_LABEL, COMPACT_VALUE);
            compactField(rightT, "Retour",  date(valueDate(contract != null ? contract.getReturnDate() : null, contract != null ? contract.getEndDate() : null)), COMPACT_LABEL, COMPACT_VALUE);
            body.addCell(column(rightT));
            doc.add(body);

            // ── Compact payment ──
            PdfPTable pay = new PdfPTable(4);
            pay.setWidthPercentage(100);
            PdfPCell cmpPayHdr = cell("PAIEMENT", Rectangle.BOX, 2.5f, Element.ALIGN_CENTER, BLUE, COMPACT_SECTION);
            cmpPayHdr.setColspan(4);
            pay.addCell(cmpPayHdr);
            compactPayField(pay, "Prix/j",  money(contract != null ? contract.getDailyPrice() : null), COMPACT_LABEL, COMPACT_VALUE);
            compactPayField(pay, "Jours",   integer(contract != null ? contract.getRentalDays() : null), COMPACT_LABEL, COMPACT_VALUE);
            compactPayField(pay, "Total",   money(valueMoney(contract != null ? contract.getTotalPrice() : null, contract != null ? rentalTotal(contract) : null)), COMPACT_LABEL, COMPACT_VALUE);
            compactPayField(pay, "Avance",  money(contract != null ? contract.getPaidAmount() : null), COMPACT_LABEL, COMPACT_VALUE);
            compactPayField(pay, "Reste",   money(contract != null ? contract.getRemainingAmount() : null), COMPACT_LABEL, COMPACT_VALUE);
            compactPayField(pay, "Caution", depositMoney(valueMoney(contract != null ? contract.getDepositAmount() : null, deposit != null ? deposit.getAmount() : null)), COMPACT_LABEL, COMPACT_VALUE);
            compactPayField(pay, "Mode",    value(contract != null ? contract.getPaymentMethod() : null, ""), COMPACT_LABEL, COMPACT_VALUE);
            compactPayField(pay, "Nb j.",   integer(contract != null ? contract.getRentalDays() : null), COMPACT_LABEL, COMPACT_VALUE);
            doc.add(pay);

            // ── Documents ──
            PdfPTable docsT = new PdfPTable(1);
            docsT.setWidthPercentage(100);
            docsT.addCell(cell("DOCUMENTS: [ ] Carte grise  [ ] Assurance  [ ] Vignette  [ ] Visite tech.", Rectangle.BOX, 2.5f, Element.ALIGN_LEFT, Color.WHITE, COMPACT_SMALL));
            doc.add(docsT);

            // ── Compact signatures ──
            PdfPTable sigs = new PdfPTable(3);
            sigs.setWidthPercentage(100);
            sigs.setWidths(new float[]{1, 1, 0.6f});
            sigs.addCell(signatureCell("Signature Client", contract != null ? contract.getClientSignature() : null, contract != null ? contract.getClientSignedAt() : null));
            sigs.addCell(signatureCell("Signature Agence", value(contract != null ? contract.getOwnerSignature() : null, tenant != null ? tenant.getAgencySignature() : null), contract != null ? contract.getOwnerSignedAt() : null));
            sigs.addCell(pseudoQrCell(value(contract != null ? contract.getQrToken() : null, contract != null ? contract.getContractNumber() : null)));
            doc.add(sigs);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Compact PDF failed for contract {}, falling back to classic", contract != null ? contract.getContractNumber() : null, e);
            return generateClassicLayout(contract, tenant, deposit);
        }
    }

    // ── LAYOUT: Detailed / Conditions Page ───────────────────────────────────

    private byte[] generateDetailedLayout(Contract contract, Tenant tenant, Deposit deposit) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 24, 24, 22, 22);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            // Page 1 — same as classic
            addHeader(doc, contract, tenant);
            addFuelAndMeta(doc, contract);
            PdfPTable main = new PdfPTable(2);
            main.setWidthPercentage(100);
            main.setWidths(new float[]{1, 1});
            main.addCell(column(leftColumn(contract)));
            main.addCell(column(rightColumn(contract)));
            doc.add(main);
            addPayment(doc, contract, deposit);
            addDocuments(doc, contract);
            addSignatures(doc, contract, tenant);
            addVerification(doc, contract);
            addTermsPage(doc, contract, tenant);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Detailed PDF failed for contract {}, falling back to classic", contract != null ? contract.getContractNumber() : null, e);
            return generateClassicLayout(contract, tenant, deposit);
        }
    }

    // ── LAYOUT: Premium Luxury ────────────────────────────────────────────────

    private byte[] generatePremiumLayout(Contract contract, Tenant tenant, Deposit deposit) {
        Color DARK    = new Color(15, 23, 42);
        Color GOLD    = new Color(180, 83, 9);
        Color GOLD_LT = new Color(254, 243, 199);
        Color DARK_LT = new Color(30, 41, 59);
        Font PREM_HDR  = new Font(Font.HELVETICA, 15, Font.BOLD, Color.WHITE);
        Font PREM_SUB  = new Font(Font.HELVETICA, 7f, Font.NORMAL, new Color(148, 163, 184));
        Font PREM_SEC  = new Font(Font.HELVETICA, 8f, Font.BOLD, Color.WHITE);
        Font PREM_LBL  = new Font(Font.HELVETICA, 7f, Font.BOLD, DARK);
        Font PREM_VAL  = new Font(Font.HELVETICA, 7.2f, Font.NORMAL, Color.BLACK);
        Font PREM_SM   = new Font(Font.HELVETICA, 6.5f, Font.NORMAL, DARK);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 26, 26, 22, 22);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            // ── Premium dark header ──
            PdfPTable hdr = new PdfPTable(2);
            hdr.setWidthPercentage(100);
            hdr.setWidths(new float[]{2f, 1.1f});

            PdfPCell leftHdr = new PdfPCell();
            leftHdr.setBorder(Rectangle.NO_BORDER);
            leftHdr.setBackgroundColor(DARK);
            leftHdr.setPadding(12);
            Image logo = agencyAssetImage(effectiveLogo(contract, tenant), "logo");
            if (logo != null) { logo.scaleToFit(50, 38); leftHdr.addElement(logo); }
            leftHdr.addElement(new Paragraph(value(tenant != null ? tenant.getName() : null, "Agence Premium"), PREM_HDR));
            leftHdr.addElement(new Paragraph(join(tenant != null ? tenant.getAddress() : null, tenant != null ? tenant.getCity() : null), PREM_SUB));
            leftHdr.addElement(new Paragraph("Tel: " + value(tenant != null ? tenant.getPhone() : null, "") + "  |  " + value(tenant != null ? tenant.getEmail() : null, ""), PREM_SUB));
            hdr.addCell(leftHdr);

            PdfPCell rightHdr = new PdfPCell();
            rightHdr.setBorder(Rectangle.NO_BORDER);
            rightHdr.setBackgroundColor(GOLD);
            rightHdr.setPadding(12);
            rightHdr.setHorizontalAlignment(Element.ALIGN_RIGHT);
            rightHdr.addElement(new Paragraph("CONTRAT", new Font(Font.HELVETICA, 11, Font.BOLD, Color.WHITE)));
            rightHdr.addElement(new Paragraph("DE LOCATION", new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE)));
            rightHdr.addElement(new Paragraph("N°: " + value(contract != null ? contract.getContractNumber() : null, ""), new Font(Font.HELVETICA, 8, Font.NORMAL, GOLD_LT)));
            rightHdr.addElement(new Paragraph("Date: " + date(contract != null ? contract.getStartDate() : null), new Font(Font.HELVETICA, 7, Font.NORMAL, GOLD_LT)));
            hdr.addCell(rightHdr);
            doc.add(hdr);

            // ── Gold accent strip ──
            PdfPTable strip = new PdfPTable(4);
            strip.setWidthPercentage(100);
            strip.setSpacingBefore(4);
            addInfoBox(strip, "DEPART", date(valueDate(contract != null ? contract.getPickupDate() : null, contract != null ? contract.getStartDate() : null)), GOLD_LT, GOLD, PREM_LBL, PREM_VAL);
            addInfoBox(strip, "RETOUR",  date(valueDate(contract != null ? contract.getReturnDate() : null, contract != null ? contract.getEndDate() : null)),  GOLD_LT, GOLD, PREM_LBL, PREM_VAL);
            addInfoBox(strip, "JOURS",   integer(contract != null ? contract.getRentalDays() : null),            GOLD_LT, GOLD, PREM_LBL, PREM_VAL);
            addInfoBox(strip, "TOTAL",   money(valueMoney(contract != null ? contract.getTotalPrice() : null, contract != null ? rentalTotal(contract) : null)), GOLD_LT, GOLD, PREM_LBL, PREM_VAL);
            doc.add(strip);

            // ── Client / Vehicle ──
            PdfPTable body = new PdfPTable(2);
            body.setWidthPercentage(100);
            body.setWidths(new float[]{1, 1});
            body.setSpacingBefore(5);
            body.addCell(column(modernSection("LOCATAIRE", new String[][]{
                {"Nom complet",   value(contract != null ? contract.getClientFullName() : null, "")},
                {"CIN/Passeport", value(contract != null ? contract.getClientCin() : null, contract != null ? contract.getClientPassportNumber() : null)},
                {"Permis n°",     value(contract != null ? contract.getClientDriverLicense() : null, "")},
                {"Telephone",     value(contract != null ? contract.getClientPhone() : null, "")},
                {"Nationalite",   value(contract != null ? contract.getClientNationality() : null, "")},
                {"Adresse",       join(contract != null ? contract.getClientAddress() : null, contract != null ? contract.getClientCity() : null)},
            }, DARK_LT, GOLD_LT, PREM_SEC, PREM_LBL, PREM_VAL)));
            body.addCell(column(modernSection("VEHICULE", new String[][]{
                {"Marque/Modele",  value(contract != null ? contract.getVehicleBrand() : null, "") + " " + value(contract != null ? contract.getVehicleModel() : null, "")},
                {"Immatriculation",value(contract != null ? contract.getVehicleRegistration() : null, "")},
                {"Carburant",      value(contract != null ? contract.getFuelType() : null, "")},
                {"Km depart",      integer(contract != null ? contract.getMileageStart() : null)},
                {"Km retour",      integer(contract != null ? contract.getMileageEnd() : null)},
                {"Lieu depart",    value(contract != null ? contract.getPickupLocation() : null, "")},
            }, DARK_LT, GOLD_LT, PREM_SEC, PREM_LBL, PREM_VAL)));
            doc.add(body);

            // ── Payment ──
            PdfPTable pay = new PdfPTable(4);
            pay.setWidthPercentage(100);
            pay.setSpacingBefore(5);
            PdfPCell payH = new PdfPCell(new Phrase("REGLEMENT", PREM_SEC));
            payH.setColspan(4); payH.setBackgroundColor(GOLD); payH.setPadding(4); payH.setBorder(Rectangle.NO_BORDER);
            payH.setHorizontalAlignment(Element.ALIGN_CENTER);
            pay.addCell(payH);
            paymentField(pay, "Prix/jour", money(contract != null ? contract.getDailyPrice() : null));
            paymentField(pay, "Nb jours",  integer(contract != null ? contract.getRentalDays() : null));
            paymentField(pay, "Total",     money(valueMoney(contract != null ? contract.getTotalPrice() : null, contract != null ? rentalTotal(contract) : null)));
            paymentField(pay, "Avance",    money(contract != null ? contract.getPaidAmount() : null));
            paymentField(pay, "Reste",     money(contract != null ? contract.getRemainingAmount() : null));
            paymentField(pay, "Caution",   depositMoney(valueMoney(contract != null ? contract.getDepositAmount() : null, deposit != null ? deposit.getAmount() : null)));
            paymentField(pay, "Mode",      value(contract != null ? contract.getPaymentMethod() : null, ""));
            paymentField(pay, "Frais supp.", money(sum(contract != null ? contract.getDeliveryFees() : null, contract != null ? contract.getReturnFees() : null)));
            doc.add(pay);

            // ── Documents ──
            PdfPTable docsT = new PdfPTable(1);
            docsT.setWidthPercentage(100);
            docsT.setSpacingBefore(4);
            PdfPCell docH = new PdfPCell(new Phrase("DOCUMENTS", PREM_SEC));
            docH.setBackgroundColor(DARK_LT); docH.setPadding(4); docH.setBorder(Rectangle.NO_BORDER);
            docsT.addCell(docH);
            PdfPCell docBody = new PdfPCell(new Phrase("[ ] Carte grise  [ ] Assurance  [ ] Vignette  [ ] Visite technique  [ ] Autorisation", PREM_SM));
            docBody.setPadding(5); docBody.setBackgroundColor(GOLD_LT); docBody.setBorder(Rectangle.NO_BORDER);
            docsT.addCell(docBody);
            doc.add(docsT);

            // ── Signatures ──
            PdfPTable sigs = new PdfPTable(2);
            sigs.setWidthPercentage(100);
            sigs.setSpacingBefore(5);
            sigs.addCell(signatureCell("Signature du Client", contract != null ? contract.getClientSignature() : null, contract != null ? contract.getClientSignedAt() : null));
            sigs.addCell(signatureCell("Signature Agence / Direction", value(contract != null ? contract.getOwnerSignature() : null, tenant != null ? tenant.getAgencySignature() : null), contract != null ? contract.getOwnerSignedAt() : null));
            doc.add(sigs);

            // Stamp if available (snapshot-aware)
            String premStampUrl = effectiveStamp(contract, tenant);
            Image premStampImg = agencyAssetImage(premStampUrl, "stamp");
            if (premStampImg != null) {
                PdfPTable premStampT = new PdfPTable(2);
                premStampT.setWidthPercentage(100);
                premStampT.setWidths(new float[]{1, 1});
                premStampT.addCell(cell("", Rectangle.NO_BORDER, 2, Element.ALIGN_LEFT, Color.WHITE));
                PdfPCell premStampC = cell("", Rectangle.BOX, 3, Element.ALIGN_CENTER, Color.WHITE);
                premStampImg.scaleToFit(68, 68);
                premStampImg.setAlignment(Element.ALIGN_CENTER);
                Paragraph premStampLbl = new Paragraph("Cachet / Tampon", SMALL_BOLD);
                premStampLbl.setAlignment(Element.ALIGN_CENTER);
                premStampC.addElement(premStampLbl);
                premStampC.addElement(premStampImg);
                premStampT.addCell(premStampC);
                doc.add(premStampT);
            }

            // ── QR ──
            PdfPTable verif = new PdfPTable(2);
            verif.setWidthPercentage(100);
            verif.setSpacingBefore(4);
            verif.addCell(pseudoQrCell(value(contract != null ? contract.getQrToken() : null, contract != null ? contract.getContractNumber() : null)));
            verif.addCell(cell("Contrat premium verifie | " + value(contract != null ? contract.getContractNumber() : null, "") + " | " + value(contract != null ? contract.getPublicSigningUrl() : null, contract != null ? contract.getQrToken() : null, ""), Rectangle.BOX, 5, Element.ALIGN_LEFT, Color.WHITE));
            doc.add(verif);
            addTermsPage(doc, contract, tenant);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Premium PDF failed for contract {}, falling back to classic", contract != null ? contract.getContractNumber() : null, e);
            return generateClassicLayout(contract, tenant, deposit);
        }
    }

    private void addHeader(Document document, Contract contract, Tenant tenant) throws Exception {
        PdfPTable header = new PdfPTable(3);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{1.1f, 2.6f, 1.2f});

        PdfPCell logo = cell("", Rectangle.BOX, 4, Element.ALIGN_CENTER, LIGHT_BLUE);
        Image logoImage = agencyAssetImage(effectiveLogo(contract, tenant), "logo");
        if (logoImage != null) {
            logoImage.scaleToFit(55, 42);
            logo.addElement(logoImage);
        } else {
            Paragraph mark = new Paragraph(initials(value(tenant != null ? tenant.getName() : null, "RC")), TITLE);
            mark.setAlignment(Element.ALIGN_CENTER);
            logo.addElement(mark);
        }
        header.addCell(logo);

        PdfPCell agency = cell("", Rectangle.BOX, 5, Element.ALIGN_LEFT, Color.WHITE);
        agency.addElement(new Paragraph(value(tenant != null ? tenant.getName() : null, "Agence de location"), TITLE));
        agency.addElement(new Paragraph(value(tenant != null ? tenant.getAddress() : null, "") + " " + value(tenant != null ? tenant.getCity() : null, ""), SMALL));
        agency.addElement(new Paragraph("Tel: " + value(tenant != null ? tenant.getPhone() : null, "__________")
                + "   Email: " + value(tenant != null ? tenant.getEmail() : null, "__________"), SMALL));
        agency.addElement(new Paragraph("RC / ICE: " + value(tenant != null ? tenant.getTaxId() : null, "________________"), SMALL));
        header.addCell(agency);

        PdfPCell car = cell("", Rectangle.BOX, 4, Element.ALIGN_CENTER, Color.WHITE);
        car.addElement(new Paragraph("SCHEMA VEHICULE", SMALL_BOLD));
        car.addElement(new Paragraph("      ______", SMALL));
        car.addElement(new Paragraph("  ___/|_||_\\`.__", SMALL));
        car.addElement(new Paragraph(" (   _    _ _  \\", SMALL));
        car.addElement(new Paragraph(" =`-(_)--(_)-' ", SMALL));
        header.addCell(car);
        document.add(header);

        PdfPTable title = new PdfPTable(2);
        title.setWidthPercentage(100);
        title.setWidths(new float[]{2.1f, 1});
        PdfPCell titleCell = cell("CONTRAT DE LOCATION DE VOITURE", Rectangle.BOX, 6, Element.ALIGN_CENTER, LIGHT_BLUE);
        titleCell.setPhrase(new Phrase("CONTRAT DE LOCATION DE VOITURE", TITLE));
        title.addCell(titleCell);
        title.addCell(cell("CONTRAT N°: " + value(contract.getContractNumber(), "__________"), Rectangle.BOX, 6, Element.ALIGN_CENTER, Color.WHITE));
        document.add(title);
    }

    private void addFuelAndMeta(Document document, Contract contract) throws Exception {
        PdfPTable meta = new PdfPTable(2);
        meta.setWidthPercentage(100);
        meta.setWidths(new float[]{2, 1});
        meta.addCell(cell("CARBURANT:  0   [ ]   1/4   [ ]   1/2   [ ]   3/4   [ ]   1   [ ]        Niveau depart: "
                + value(contract.getFuelLevelStart(), "______"), Rectangle.BOX, 4, Element.ALIGN_LEFT, Color.WHITE));
        meta.addCell(cell("Date edition: " + date(contract.getCreatedAt() != null ? contract.getCreatedAt().toLocalDate() : LocalDate.now()),
                Rectangle.BOX, 4, Element.ALIGN_RIGHT, Color.WHITE));
        document.add(meta);
    }

    private PdfPTable leftColumn(Contract c) {
        PdfPTable table = oneColumn();
        section(table, "CLIENT");
        field(table, "Nom", value(c.getClientLastName(), splitName(c.getClientFullName(), false)));
        field(table, "Prenom", value(c.getClientFirstName(), splitName(c.getClientFullName(), true)));
        field(table, "Adresse", join(c.getClientAddress(), c.getClientCity(), c.getClientCountry()));
        field(table, "Date de naissance", date(c.getClientBirthDate()));
        field(table, "Nationalite", c.getClientNationality());
        field(table, "CIN / Passeport", value(c.getClientCin(), c.getClientPassportNumber()));
        field(table, "Delivre le", "");
        field(table, "N° de permis", c.getClientDriverLicense());
        field(table, "Delivre le", date(c.getClientDriverLicenseIssue()));
        field(table, "Telephone", c.getClientPhone());

        section(table, "AUTRE CONDUCTEUR");
        AdditionalDriver d = c.getAdditionalDrivers() != null && !c.getAdditionalDrivers().isEmpty() ? c.getAdditionalDrivers().get(0) : null;
        field(table, "Nom", d != null ? d.getFullName() : "");
        field(table, "Prenom", "");
        field(table, "Adresse", d != null ? d.getAddress() : "");
        field(table, "CIN", d != null ? value(d.getCin(), d.getPassportNumber()) : "");
        field(table, "Delivree le", "");
        field(table, "N° de permis", d != null ? d.getDriverLicenseNumber() : "");
        field(table, "Delivre le", "");
        return table;
    }

    private PdfPTable rightColumn(Contract c) {
        PdfPTable table = oneColumn();
        section(table, "VOITURE");
        field(table, "Marque", c.getVehicleBrand());
        field(table, "Modele / Type", c.getVehicleModel());
        field(table, "Matricule", c.getVehicleRegistration());
        field(table, "Carburant", value(c.getFuelType(), c.getFuelLevelStart()));
        field(table, "Kilometrage depart", integer(c.getMileageStart()));
        field(table, "Kilometrage retour", integer(c.getMileageEnd()));

        section(table, "DEPART");
        field(table, "Date", date(valueDate(c.getPickupDate(), c.getStartDate())));
        field(table, "Heure", time(c.getPickupTime()));
        field(table, "Lieu", value(c.getPickupLocation(), c.getPickupAgency()));

        section(table, "RETOUR");
        field(table, "Date", date(valueDate(c.getReturnDate(), c.getEndDate())));
        field(table, "Heure", time(c.getReturnTime()));
        field(table, "Lieu", value(c.getReturnLocation(), c.getReturnAgency()));

        section(table, "PROLONGATION");
        field(table, "Du", "");
        field(table, "Au", "");
        field(table, "Lieu de depart", "");
        field(table, "Lieu de retour", "");
        field(table, "Frais livraison / reprise", money(sum(c.getDeliveryFees(), c.getReturnFees())));

        section(table, "CHANGEMENT DE VEHICULE");
        field(table, "Marque", "");
        field(table, "Type", "");
        field(table, "Matricule", "");
        field(table, "Carburant", "");
        field(table, "Date / Heure / Lieu", "");
        return table;
    }

    private void addPayment(Document document, Contract c, Deposit deposit) throws Exception {
        PdfPTable methods = new PdfPTable(1);
        methods.setWidthPercentage(100);
        section(methods, "MODE DE REGLEMENT");
        methods.addCell(cell(paymentBox(c.getPaymentMethod()), Rectangle.BOX, 4, Element.ALIGN_LEFT, Color.WHITE));
        document.add(methods);

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.25f, 1, 1.25f, 1});
        paymentField(table, "Prix journalier", money(c.getDailyPrice()));
        paymentField(table, "Nombre de jours", integer(c.getRentalDays()));
        paymentField(table, "Total location", money(valueMoney(c.getTotalPrice(), rentalTotal(c))));
        paymentField(table, "Avance payee", money(c.getPaidAmount()));
        paymentField(table, "Reste a payer", money(c.getRemainingAmount()));
        paymentField(table, "Depot garantie / Caution", depositMoney(valueMoney(c.getDepositAmount(), deposit != null ? deposit.getAmount() : null)));
        paymentField(table, "Frais supplementaires", money(sum(c.getDeliveryFees(), c.getReturnFees(), c.getLateFees(), c.getCleaningFees(), c.getFuelCharges())));
        paymentField(table, "Total final", money(valueMoney(c.getTotalPrice(), rentalTotal(c))));
        document.add(table);
    }

    private void addDocuments(Document document, Contract c) throws Exception {
        PdfPTable table = oneColumn();
        section(table, "DOCUMENTS DE BORD");
        table.addCell(cell("[ ] Carte grise     [ ] Assurance     [ ] Vignette     [ ] Visite technique     [ ] Autorisation de circulation",
                Rectangle.BOX, 4, Element.ALIGN_LEFT, Color.WHITE));
        String inspection = c.getVehicleCondition() != null ? "Inspection vehicule: Oui" : "Inspection vehicule: Non renseignee";
        table.addCell(cell(inspection, Rectangle.BOX, 4, Element.ALIGN_LEFT, Color.WHITE));
        document.add(table);
    }

    private void addLegalNote(Document document, Tenant tenant, Contract contract) throws Exception {
        PdfPTable table = oneColumn();
        section(table, "CONDITIONS GENERALES");
        String termsText = effectiveTerms(contract, tenant);
        table.addCell(cell(termsText, Rectangle.BOX, 5, Element.ALIGN_LEFT, PALE));
        document.add(table);
    }

    /**
     * Renders a dedicated "CONDITIONS GENERALES DE LOCATION" page using the agency's
     * own terms (branding snapshot → live tenant settings) or, if none is configured,
     * a numbered default set of French rental terms. Ends with a footer showing the
     * contract number so the page is identifiable if printed separately.
     */
    private void addTermsPage(Document doc, Contract contract, Tenant tenant) throws Exception {
        doc.newPage();
        Font condTitle = new Font(Font.HELVETICA, 12, Font.BOLD, INK);
        Font condText  = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, Color.BLACK);
        Font condFoot  = new Font(Font.HELVETICA, 6.5f, Font.ITALIC, new Color(100, 100, 100));

        PdfPTable condHdr = new PdfPTable(1);
        condHdr.setWidthPercentage(100);
        condHdr.addCell(cell("CONDITIONS GENERALES DE LOCATION", Rectangle.BOX, 6, Element.ALIGN_CENTER, LIGHT_BLUE, condTitle));
        doc.add(condHdr);

        String customTerms = effectiveTerms(contract, tenant);
        boolean hasCustomTerms = (contract != null && contract.getBrandingTermsSnapshot() != null && !contract.getBrandingTermsSnapshot().isBlank())
                || (tenant != null && tenant.getTermsAndConditions() != null && !tenant.getTermsAndConditions().isBlank());

        if (hasCustomTerms) {
            // Agency has set their own T&C — render line by line
            String[] lines = customTerms.split("\\r?\\n");
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                PdfPTable lineTable = new PdfPTable(1);
                lineTable.setWidthPercentage(100);
                lineTable.setSpacingBefore(3);
                lineTable.addCell(cell(line.trim(), Rectangle.NO_BORDER, 2, Element.ALIGN_LEFT, Color.WHITE, condText));
                doc.add(lineTable);
            }
        } else {
            // Default numbered French rental terms
            String[] defaults = {
                "Le client est responsable du vehicule pendant toute la duree de la location.",
                "Le vehicule doit etre restitue a la date, l'heure et au lieu convenus.",
                "Le niveau de carburant doit etre restitue tel que convenu dans le contrat.",
                "Toute amende, peage ou penalite survenue durant la location est a la charge du client.",
                "Tout dommage non declare lors de la prise en charge peut etre facture au client.",
                "La caution / depot de garantie est remboursable apres restitution du vehicule, en l'absence de dommage ou de frais impayes.",
                "Tout retard de restitution peut entrainer des frais supplementaires.",
                "Le contrat devient valide apres signature de l'agence et du client."
            };
            int n = 1;
            for (String art : defaults) {
                PdfPTable artTable = new PdfPTable(1);
                artTable.setWidthPercentage(100);
                artTable.setSpacingBefore(4);
                artTable.addCell(cell(n + ". " + art, Rectangle.NO_BORDER, 3, Element.ALIGN_LEFT, Color.WHITE, condText));
                doc.add(artTable);
                n++;
            }
        }

        PdfPTable footer = new PdfPTable(1);
        footer.setWidthPercentage(100);
        footer.setSpacingBefore(16);
        footer.addCell(cell("Contrat N°: " + value(contract != null ? contract.getContractNumber() : null, ""),
                Rectangle.TOP, 4, Element.ALIGN_CENTER, Color.WHITE, condFoot));
        doc.add(footer);
    }

    private void addSignatures(Document document, Contract c, Tenant tenant) throws Exception {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 1});
        table.addCell(signatureCell("Signature du Client", c.getClientSignature(), c.getClientSignedAt()));
        table.addCell(signatureCell("Signature de la Direction / Agence",
                value(c.getOwnerSignature(), tenant != null ? tenant.getAgencySignature() : null), c.getOwnerSignedAt()));
        document.add(table);

        // Render agency stamp if available (snapshot-aware)
        String stampUrl = effectiveStamp(c, tenant);
        Image stampImg = agencyAssetImage(stampUrl, "stamp");
        if (stampImg != null) {
            PdfPTable stampTable = new PdfPTable(2);
            stampTable.setWidthPercentage(100);
            stampTable.setWidths(new float[]{1, 1});
            stampTable.addCell(cell("", Rectangle.NO_BORDER, 2, Element.ALIGN_LEFT, Color.WHITE));
            PdfPCell stampCell = cell("", Rectangle.BOX, 3, Element.ALIGN_CENTER, Color.WHITE);
            stampImg.scaleToFit(68, 68);
            stampImg.setAlignment(Element.ALIGN_CENTER);
            Paragraph stampLabel = new Paragraph("Cachet / Tampon", SMALL_BOLD);
            stampLabel.setAlignment(Element.ALIGN_CENTER);
            stampCell.addElement(stampLabel);
            stampCell.addElement(stampImg);
            stampTable.addCell(stampCell);
            document.add(stampTable);
        }
    }

    /**
     * Logs exactly what agency data this PDF is about to render, so a stale/wrong
     * branding report can be diagnosed from logs alone — proves whether the PDF
     * pulled real tenant data or fell back to initials/defaults. Never logs raw
     * asset bytes, the terms text itself, or signature/stamp image data.
     */
    private void logAgencyDataDebug(Contract contract, Tenant tenant) {
        String logoSource = effectiveLogo(contract, tenant);
        String stampSource = effectiveStamp(contract, tenant);
        String terms = effectiveTerms(contract, tenant);
        boolean usingFallbackInitials = logoSource == null || logoSource.isBlank();
        log.info("[PDF_AGENCY_DATA_DEBUG] contractId={} agencyId={} agencyName={} agencyAddressPresent={} " +
                "agencyPhonePresent={} agencyEmail={} agencyRcIcePresent={} logoPresent={} logoSource={} " +
                "termsPresent={} termsLength={} signaturePresent={} stampPresent={} usingFallbackInitials={} templateFile={}",
                contract != null ? contract.getId() : null,
                tenant != null ? tenant.getId() : null,
                tenant != null ? tenant.getName() : null,
                tenant != null && tenant.getAddress() != null && !tenant.getAddress().isBlank(),
                tenant != null && tenant.getPhone() != null && !tenant.getPhone().isBlank(),
                tenant != null ? tenant.getEmail() : null,
                tenant != null && tenant.getTaxId() != null && !tenant.getTaxId().isBlank(),
                logoSource != null && !logoSource.isBlank(),
                logoSource == null || logoSource.isBlank() ? "none"
                        : logoSource.startsWith("data:") ? "base64"
                        : logoSource.startsWith("http") ? "url" : "local-file",
                terms != null && !terms.isBlank(),
                terms != null ? terms.length() : 0,
                contract != null && contract.getClientSignature() != null && !contract.getClientSignature().isBlank(),
                stampSource != null && !stampSource.isBlank(),
                usingFallbackInitials,
                contract != null && contract.getSelectedTemplate() != null
                        ? contract.getSelectedTemplate().getName() : "SYSTEM_DEFAULT");
    }

    /** Returns the agency logo to use: snapshot value if set, otherwise live tenant value. */
    private String effectiveLogo(Contract contract, Tenant tenant) {
        if (contract != null && contract.getBrandingLogoUrl() != null && !contract.getBrandingLogoUrl().isBlank())
            return contract.getBrandingLogoUrl();
        return tenant != null ? tenant.getLogoUrl() : null;
    }

    /** Returns the agency stamp to use: snapshot value if set, otherwise live tenant value. */
    private String effectiveStamp(Contract contract, Tenant tenant) {
        if (contract != null && contract.getBrandingStampUrl() != null && !contract.getBrandingStampUrl().isBlank())
            return contract.getBrandingStampUrl();
        return tenant != null ? tenant.getAgencyStampUrl() : null;
    }

    /** Returns the terms text to embed in the PDF. Prefers snapshot → live T&C → hardcoded fallback. */
    private String effectiveTerms(Contract contract, Tenant tenant) {
        if (contract != null && contract.getBrandingTermsSnapshot() != null && !contract.getBrandingTermsSnapshot().isBlank())
            return contract.getBrandingTermsSnapshot();
        if (tenant != null && tenant.getTermsAndConditions() != null && !tenant.getTermsAndConditions().isBlank())
            return tenant.getTermsAndConditions();
        return "NB: Ce contrat ne vaut en aucun cas comme facture.\n"
                + "J'ai lu et accepte les conditions stipulees au verso de ce contrat. "
                + "Le client est seul responsable des violations de la loi sur la circulation routiere.";
    }

    private void addVerification(Document document, Contract c) throws Exception {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 3});
        table.addCell(pseudoQrCell(value(c.getQrToken(), c.getPublicSigningUrl(), c.getContractNumber())));
        table.addCell(cell("QR de verification du contrat\n"
                + "Contrat: " + value(c.getContractNumber(), "") + "\n"
                + "URL / Token: " + value(c.getPublicSigningUrl(), c.getQrToken(), "Non genere") + "\n"
                + "Document genere depuis les donnees officielles de l'agence.",
                Rectangle.BOX, 5, Element.ALIGN_LEFT, Color.WHITE));
        document.add(table);
    }

    public String saveContractPdf(Contract contract, byte[] pdfBytes) {
        try {
            Path dir = Paths.get(PDF_STORAGE_DIR);
            if (!Files.exists(dir)) Files.createDirectories(dir);
            String fileName = sanitizeFileName(contract.getContractNumber() + "_" + contract.getId() + ".pdf");
            Path filePath = dir.resolve(fileName);
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                fos.write(pdfBytes);
            }
            return "/api/contracts/" + contract.getId() + "/pdf-file";
        } catch (IOException e) {
            throw new IllegalStateException("PDF save failed", e);
        }
    }

    public byte[] getContractPdfFile(Long contractId, String contractNumber) {
        try {
            Path filePath = Paths.get(PDF_STORAGE_DIR).resolve(sanitizeFileName(contractNumber + "_" + contractId + ".pdf"));
            return Files.exists(filePath) ? Files.readAllBytes(filePath) : null;
        } catch (IOException e) {
            log.error("Failed to read PDF for contract [id={}]", contractId, e);
            return null;
        }
    }

    private PdfPTable oneColumn() {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        return table;
    }

    private PdfPCell column(PdfPTable inner) {
        PdfPCell cell = new PdfPCell(inner);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(2);
        return cell;
    }

    private void section(PdfPTable table, String title) {
        table.addCell(cell(title, Rectangle.BOX, 4, Element.ALIGN_CENTER, BLUE, SECTION));
    }

    private void field(PdfPTable table, String label, String value) {
        PdfPTable row = new PdfPTable(2);
        row.setWidthPercentage(100);
        try {
            row.setWidths(new float[]{0.85f, 1.55f});
        } catch (Exception ignored) {
        }
        row.addCell(cell(label, Rectangle.BOX, 3, Element.ALIGN_LEFT, LIGHT_BLUE, LABEL));
        row.addCell(cell(value(value, ""), Rectangle.BOX, 3, Element.ALIGN_LEFT, Color.WHITE, VALUE));
        table.addCell(new PdfPCell(row));
    }

    private void paymentField(PdfPTable table, String label, String value) {
        table.addCell(cell(label, Rectangle.BOX, 4, Element.ALIGN_LEFT, LIGHT_BLUE, LABEL));
        table.addCell(cell(value(value, ""), Rectangle.BOX, 4, Element.ALIGN_RIGHT, Color.WHITE, VALUE));
    }

    private PdfPCell signatureCell(String title, String signature, LocalDateTime signedAt) {
        PdfPCell cell = cell("", Rectangle.BOX, 5, Element.ALIGN_CENTER, Color.WHITE);
        cell.setFixedHeight(82);
        Paragraph p = new Paragraph(title, SMALL_BOLD);
        p.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(p);
        Image image = imageFromDataUrl(signature);
        if (image != null) {
            image.scaleToFit(135, 38);
            image.setAlignment(Element.ALIGN_CENTER);
            cell.addElement(image);
        } else {
            cell.addElement(new Paragraph("\n\n______________________________", SMALL));
        }
        Paragraph date = new Paragraph(signedAt != null ? "Signe le: " + signedAt.format(DATETIME) : "Date: ____ / ____ / ______", SMALL);
        date.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(date);
        return cell;
    }

    private PdfPCell pseudoQrCell(String seed) {
        PdfPCell outer = cell("", Rectangle.BOX, 4, Element.ALIGN_CENTER, Color.WHITE);
        PdfPTable qr = new PdfPTable(21);
        qr.setWidthPercentage(72);
        int hash = seed.hashCode();
        for (int y = 0; y < 21; y++) {
            for (int x = 0; x < 21; x++) {
                boolean finder = finder(x, y, 0, 0) || finder(x, y, 14, 0) || finder(x, y, 0, 14);
                boolean dark = finder || (((x * 31 + y * 17 + hash) & 3) == 0);
                PdfPCell dot = new PdfPCell();
                dot.setFixedHeight(2.6f);
                dot.setBorder(Rectangle.NO_BORDER);
                dot.setBackgroundColor(dark ? Color.BLACK : Color.WHITE);
                qr.addCell(dot);
            }
        }
        outer.addElement(qr);
        return outer;
    }

    private boolean finder(int x, int y, int ox, int oy) {
        return x >= ox && x < ox + 7 && y >= oy && y < oy + 7
                && (x == ox || x == ox + 6 || y == oy || y == oy + 6 || (x >= ox + 2 && x <= ox + 4 && y >= oy + 2 && y <= oy + 4));
    }

    private PdfPCell cell(String text, int border, float padding, int align, Color bg) {
        return cell(text, border, padding, align, bg, VALUE);
    }

    private PdfPCell cell(String text, int border, float padding, int align, Color bg, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(value(text, ""), font));
        cell.setBorder(border);
        cell.setBorderColor(BORDER);
        cell.setPadding(padding);
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBackgroundColor(bg);
        return cell;
    }

    private Image imageFromDataUrl(String dataUrl) {
        try {
            if (dataUrl == null || dataUrl.isBlank() || !dataUrl.contains(",")) return null;
            byte[] imgBytes = Base64.getDecoder().decode(dataUrl.substring(dataUrl.indexOf(",") + 1));
            return Image.getInstance(imgBytes);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Resolves an agency asset (logo, stamp) regardless of how it was stored —
     * base64 data URL (signature-pad / upload-as-base64 style), a plain
     * http(s) URL, or a local file path — and never lets a bad/unreachable
     * source crash PDF generation. Returns null (never throws) on failure.
     */
    private Image agencyAssetImage(String source, String assetLabel) {
        if (source == null || source.isBlank()) return null;
        try {
            byte[] bytes;
            String resolvedFrom;
            if (source.startsWith("data:") && source.contains(",")) {
                bytes = Base64.getDecoder().decode(source.substring(source.indexOf(",") + 1));
                resolvedFrom = "base64";
            } else if (source.startsWith("http://") || source.startsWith("https://")) {
                java.net.URL url = new java.net.URL(source);
                java.net.URLConnection connection = url.openConnection();
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(4000);
                try (var in = connection.getInputStream()) {
                    bytes = in.readAllBytes();
                }
                resolvedFrom = "url";
            } else {
                Path localPath = Paths.get(source);
                if (Files.exists(localPath)) {
                    bytes = Files.readAllBytes(localPath);
                    resolvedFrom = "local-file";
                } else {
                    // Last resort: raw base64 without a data: prefix.
                    bytes = Base64.getDecoder().decode(source);
                    resolvedFrom = "raw-base64";
                }
            }
            Image image = Image.getInstance(bytes);
            log.debug("[AGENCY_ASSET_LOADED] asset={} source={}", assetLabel, resolvedFrom);
            return image;
        } catch (Exception e) {
            log.warn("AGENCY_LOGO_LOAD_FAILED asset={} reason={}", assetLabel, e.getMessage());
            return null;
        }
    }

    private String paymentBox(String method) {
        String normalized = value(method, "").toUpperCase();
        return mark(normalized.contains("CASH") || normalized.contains("ESPECE"), "Espece")
                + "   " + mark(normalized.contains("CHEQUE"), "Cheque")
                + "   " + mark(normalized.contains("CARD") || normalized.contains("CARTE"), "Carte bancaire")
                + "   " + mark(normalized.contains("BANK") || normalized.contains("VIREMENT"), "Virement")
                + "   " + mark(normalized.isBlank() || normalized.contains("OTHER"), "Autres");
    }

    private String mark(boolean checked, String label) {
        return (checked ? "[X] " : "[ ] ") + label;
    }

    private String splitName(String fullName, boolean first) {
        if (fullName == null || fullName.isBlank()) return "";
        String[] parts = fullName.trim().split("\\s+", 2);
        return first ? parts[0] : parts.length > 1 ? parts[1] : "";
    }

    private LocalDate valueDate(LocalDate preferred, LocalDate fallback) {
        return preferred != null ? preferred : fallback;
    }

    private BigDecimal valueMoney(BigDecimal preferred, BigDecimal fallback) {
        return preferred != null ? preferred : fallback;
    }

    private BigDecimal rentalTotal(Contract c) {
        return safe(c.getDailyPrice()).multiply(BigDecimal.valueOf(c.getRentalDays() != null ? c.getRentalDays() : 0));
    }

    private BigDecimal sum(BigDecimal... values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) total = total.add(safe(value));
        return total;
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String money(BigDecimal value) {
        return safe(value).stripTrailingZeros().toPlainString() + " MAD";
    }

    /** Deposit/caution is not rental revenue; show a clear "not required" label instead of "0 MAD". */
    private String depositMoney(BigDecimal value) {
        return safe(value).compareTo(BigDecimal.ZERO) == 0 ? "Non exigee" : money(value);
    }

    private String integer(Integer value) {
        return value != null ? String.valueOf(value) : "";
    }

    private String date(LocalDate value) {
        return value != null ? value.format(DATE) : "";
    }

    private String time(LocalTime value) {
        return value != null ? value.format(TIME) : "";
    }

    private String join(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                if (!builder.isEmpty()) builder.append(", ");
                builder.append(value.trim());
            }
        }
        return builder.toString();
    }

    private String value(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    private String initials(String value) {
        String[] words = value(value, "RC").split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isBlank()) result.append(Character.toUpperCase(word.charAt(0)));
            if (result.length() == 2) break;
        }
        return result.isEmpty() ? "RC" : result.toString();
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    // ── Shared helpers for new layouts ────────────────────────────────────────

    /** Builds a labelled info box cell used in the modern/premium period strips. */
    private void addInfoBox(PdfPTable table, String label, String value, Color bg, Color borderColor, Font labelFont, Font valueFont) {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.BOX);
        c.setBorderColor(borderColor);
        c.setPadding(5);
        c.setBackgroundColor(bg);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        Paragraph lbl = new Paragraph(label, labelFont);
        lbl.setAlignment(Element.ALIGN_CENTER);
        Paragraph val = new Paragraph(value(value, ""), valueFont);
        val.setAlignment(Element.ALIGN_CENTER);
        c.addElement(lbl);
        c.addElement(val);
        table.addCell(c);
    }

    /** Builds a one-column section table for modern/premium layouts.
     *  rows is a String[N][2] of {label, value} pairs. */
    private PdfPTable modernSection(String title, String[][] rows, Color headerBg, Color rowBg, Font headerFont, Font labelFont, Font valueFont) {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        PdfPCell hdr = new PdfPCell(new Phrase(title, headerFont));
        hdr.setBackgroundColor(headerBg);
        hdr.setPadding(4);
        hdr.setBorder(Rectangle.NO_BORDER);
        hdr.setHorizontalAlignment(Element.ALIGN_CENTER);
        t.addCell(hdr);
        for (String[] row : rows) {
            PdfPTable rowT = new PdfPTable(2);
            rowT.setWidthPercentage(100);
            try { rowT.setWidths(new float[]{0.9f, 1.5f}); } catch (Exception ignored) {}
            PdfPCell lblCell = new PdfPCell(new Phrase(row[0], labelFont));
            lblCell.setPadding(3); lblCell.setBorder(Rectangle.BOX); lblCell.setBackgroundColor(rowBg);
            PdfPCell valCell = new PdfPCell(new Phrase(value(row[1], ""), valueFont));
            valCell.setPadding(3); valCell.setBorder(Rectangle.BOX); valCell.setBackgroundColor(Color.WHITE);
            rowT.addCell(lblCell);
            rowT.addCell(valCell);
            PdfPCell wrapper = new PdfPCell(rowT);
            wrapper.setBorder(Rectangle.NO_BORDER);
            wrapper.setPadding(0);
            t.addCell(wrapper);
        }
        return t;
    }

    /** Key-value field for compact layout (very small fonts, tiny padding). */
    private void compactField(PdfPTable table, String label, String val, Font labelFont, Font valueFont) {
        PdfPTable row = new PdfPTable(2);
        row.setWidthPercentage(100);
        try { row.setWidths(new float[]{0.75f, 1.5f}); } catch (Exception ignored) {}
        PdfPCell lCell = new PdfPCell(new Phrase(label, labelFont));
        lCell.setPadding(2); lCell.setBorder(Rectangle.BOX); lCell.setBackgroundColor(LIGHT_BLUE);
        PdfPCell vCell = new PdfPCell(new Phrase(value(val, ""), valueFont));
        vCell.setPadding(2); vCell.setBorder(Rectangle.BOX); vCell.setBackgroundColor(Color.WHITE);
        row.addCell(lCell);
        row.addCell(vCell);
        PdfPCell wrapper = new PdfPCell(row);
        wrapper.setBorder(Rectangle.NO_BORDER);
        wrapper.setPadding(0);
        table.addCell(wrapper);
    }

    /** Two-cell payment row (label + value) for compact layout. */
    private void compactPayField(PdfPTable table, String label, String val, Font labelFont, Font valueFont) {
        PdfPCell lc = new PdfPCell(new Phrase(label, labelFont));
        lc.setPadding(2.5f); lc.setBorder(Rectangle.BOX); lc.setBackgroundColor(LIGHT_BLUE);
        PdfPCell vc = new PdfPCell(new Phrase(value(val, ""), valueFont));
        vc.setPadding(2.5f); vc.setBorder(Rectangle.BOX); vc.setHorizontalAlignment(Element.ALIGN_RIGHT); vc.setBackgroundColor(Color.WHITE);
        table.addCell(lc);
        table.addCell(vc);
    }
}
