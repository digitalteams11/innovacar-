package com.carrental.service;

import com.carrental.entity.AdditionalDriver;
import com.carrental.entity.Contract;
import com.carrental.entity.Deposit;
import com.carrental.entity.Tenant;
import com.lowagie.text.Chunk;
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

    private final QrCodeService qrCodeService;

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
        logAgencyDataDebug(contract, tenant);
        log.info("[CONTRACT_PDF_GENERATE] contractId={} output=SYSTEM_DEFAULT success=true",
                contract != null ? contract.getId() : null);
        return generateClassicLayout(contract, tenant, deposit);
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
            addAcceptanceStatement(document);
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

        section(table, "DEPART");
        field(table, "Date", date(valueDate(c.getPickupDate(), c.getStartDate())));
        field(table, "Heure", time(c.getPickupTime()));
        field(table, "Lieu", value(c.getPickupLocation(), c.getPickupAgency()));

        section(table, "RETOUR");
        field(table, "Date", date(valueDate(c.getReturnDate(), c.getEndDate())));
        field(table, "Heure", time(c.getReturnTime()));
        field(table, "Lieu", value(c.getReturnLocation(), c.getReturnAgency()));
        BigDecimal deliveryAndCollection = sum(c.getDeliveryFees(), c.getReturnFees());
        if (deliveryAndCollection != null && deliveryAndCollection.signum() > 0) {
            field(table, "Frais livraison / reprise", money(deliveryAndCollection));
        }

        // PROLONGATION and CHANGEMENT DE VEHICULE are intentionally omitted here:
        // there is no extension/replacement data model on Contract yet, so these
        // sections would always render as empty placeholder rows on every single
        // contract — exactly what a real agency contract must never do. Add them
        // back once real extension/replacement fields exist to render from.
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

    /** Short acceptance line on page 1, right before the signatures — the full terms live on page 2, not here. */
    private void addAcceptanceStatement(Document document) throws Exception {
        PdfPTable table = oneColumn();
        table.setSpacingBefore(3);
        Font ackFont = new Font(Font.HELVETICA, 6.8f, Font.ITALIC, Color.BLACK);
        table.addCell(cell("En signant ce contrat, le client reconnait avoir pris connaissance de l'etat du vehicule, "
                + "des montants dus, de la caution et des conditions generales de location figurant en page suivante.",
                Rectangle.BOX, 4, Element.ALIGN_LEFT, PALE, ackFont));
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
        Font condTitle = new Font(Font.HELVETICA, 11, Font.BOLD, INK);
        Font condSecTitle = new Font(Font.HELVETICA, 7f, Font.BOLD, INK);
        Font condText  = new Font(Font.HELVETICA, 6.6f, Font.NORMAL, Color.BLACK);
        Font condFoot  = new Font(Font.HELVETICA, 6.5f, Font.ITALIC, new Color(100, 100, 100));

        PdfPTable condHdr = new PdfPTable(1);
        condHdr.setWidthPercentage(100);
        condHdr.addCell(cell("CONDITIONS GENERALES DE LOCATION", Rectangle.BOX, 5, Element.ALIGN_CENTER, LIGHT_BLUE, condTitle));
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
                lineTable.setSpacingBefore(2);
                lineTable.addCell(cell(line.trim(), Rectangle.NO_BORDER, 2, Element.ALIGN_LEFT, Color.WHITE, condText));
                doc.add(lineTable);
            }
        } else {
            // Concise default terms — one short numbered paragraph per rule, tight spacing to fit one A4 page.
            String[][] defaults = {
                {"Conducteur autorise", "Le vehicule ne peut etre conduit que par le locataire et les conducteurs inscrits au contrat, titulaires d'un permis de conduire valide."},
                {"Utilisation du vehicule", "Le locataire s'engage a utiliser le vehicule avec prudence, a respecter le Code de la route et a ne pas sous-louer le vehicule, participer a une competition, transporter des produits interdits ou quitter le Maroc sans autorisation ecrite du loueur."},
                {"Etat du vehicule", "Le locataire reconnait recevoir le vehicule dans l'etat decrit au depart. Toute anomalie doit etre signalee avant la remise des cles. Le vehicule doit etre restitue dans le meme etat, sous reserve de l'usure normale."},
                {"Duree et restitution", "Le vehicule doit etre restitue a la date, a l'heure et au lieu prevus. Toute prolongation doit etre autorisee par le loueur. Tout retard peut entrainer des frais supplementaires."},
                {"Paiement et caution", "Le locataire doit regler les montants indiques au contrat. La caution peut etre utilisee pour couvrir les dommages, le carburant manquant, les retards, les amendes, la perte des cles ou les frais de nettoyage exceptionnel."},
                {"Carburant et kilometrage", "Le vehicule doit etre rendu avec le niveau de carburant convenu. Tout carburant manquant ou depassement du kilometrage autorise peut etre facture."},
                {"Accident, panne ou vol", "Le locataire doit informer immediatement le loueur, contacter les autorites lorsque necessaire, remplir un constat et ne faire aucune reparation sans autorisation prealable, sauf urgence de securite."},
                {"Assurance", "Le vehicule beneficie de l'assurance prevue au contrat. Les franchises et dommages non couverts restent a la charge du locataire dans les limites prevues par le contrat et la legislation applicable."},
                {"Infractions", "Les amendes, infractions routieres, peages et frais administratifs lies a la periode de location restent a la charge du locataire."},
                {"Donnees et GPS", "Les donnees personnelles et les informations de geolocalisation eventuelles sont utilisees uniquement pour la gestion du contrat, la securite du vehicule, les incidents et les obligations legales."},
                {"Acceptation", "La signature du contrat confirme que le locataire a lu et accepte le contrat, l'etat du vehicule, les montants, la caution et les presentes conditions."},
                {"Droit applicable", "Le contrat est soumis au droit marocain. En cas de litige, les parties rechercheront d'abord une solution amiable avant de saisir la juridiction competente."}
            };
            int n = 1;
            for (String[] art : defaults) {
                Paragraph p = new Paragraph();
                p.setSpacingBefore(3f);
                p.setLeading(7.6f);
                p.add(new Chunk(n + ". " + art[0] + " — ", condSecTitle));
                p.add(new Chunk(art[1], condText));
                doc.add(p);
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
        log.debug("[PDF_AGENCY_DATA_DEBUG] contractId={} agencyId={} agencyName={} agencyAddressPresent={} " +
                "agencyPhonePresent={} agencyEmail={} agencyRcIcePresent={} logoPresent={} logoSource={} " +
                "termsPresent={} termsLength={} signaturePresent={} stampPresent={} usingFallbackInitials={}",
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
                usingFallbackInitials);
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
        table.addCell(qrCell(c.getPublicSigningUrl(), value(c.getQrToken(), c.getPublicSigningUrl(), c.getContractNumber())));
        table.addCell(cell("QR de verification du contrat\n"
                + "Contrat: " + value(c.getContractNumber(), "") + "\n"
                + "URL / Token: " + value(c.getPublicSigningUrl(), c.getQrToken(), "Non genere") + "\n"
                + "Document genere depuis les donnees officielles de l'agence.",
                Rectangle.BOX, 5, Element.ALIGN_LEFT, Color.WHITE));
        document.add(table);
    }

    /** Real scannable QR when a public signing URL exists; falls back to the pseudo-pattern otherwise. */
    private PdfPCell qrCell(String signingUrl, String pseudoSeed) {
        if (signingUrl != null && !signingUrl.isBlank()) {
            byte[] png = qrCodeService.generatePng(signingUrl, 240);
            if (png != null) {
                try {
                    Image qrImage = Image.getInstance(png);
                    PdfPCell outer = cell("", Rectangle.BOX, 4, Element.ALIGN_CENTER, Color.WHITE);
                    qrImage.scaleToFit(90, 90);
                    qrImage.setAlignment(Element.ALIGN_CENTER);
                    outer.addElement(qrImage);
                    return outer;
                } catch (Exception ignored) {
                    // fall through to pseudo cell
                }
            }
        }
        return pseudoQrCell(pseudoSeed);
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
                // Uploaded assets are stored as web-root-relative paths (e.g. "/uploads/agency/5/logo.png"),
                // the same convention served by WebMvcFeatureConfig's "/uploads/**" resource handler, which
                // maps them onto Path.of("uploads") relative to the app's working directory. Paths.get()
                // on a string starting with "/" would instead resolve from the filesystem/drive root, so
                // strip the leading slash to land on the same app-relative file the browser actually loads.
                Path localPath = source.startsWith("/uploads/")
                        ? Path.of(source.substring(1))
                        : Paths.get(source);
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
