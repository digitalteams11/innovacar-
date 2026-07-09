package com.carrental.service;

import com.carrental.entity.*;
import com.carrental.repository.ContractTemplateRepository;
import com.carrental.repository.ContractTermsRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractTemplatePdfService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ContractTemplateRepository templateRepository;
    private final ContractTermsRepository termsRepository;

    public Optional<byte[]> generateIfAvailable(Contract contract, Tenant tenant, Deposit deposit) {
        if (contract == null || tenant == null || tenant.getId() == null) {
            return Optional.empty();
        }
        ContractTemplate template = resolveTemplate(contract, tenant.getId());
        if (template == null || template.getFrontFilePath() == null || template.getFrontFilePath().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(generate(contract, tenant, deposit, template));
    }

    /**
     * Public wrapper used by PdfService to resolve the template without triggering
     * a circular dependency. Returns null when no template is configured.
     */
    public ContractTemplate resolveTemplateForPdf(Contract contract, Long tenantId) {
        return resolveTemplate(contract, tenantId);
    }

    /**
     * Resolve which template to use for a contract PDF:
     * 1. the template explicitly selected on the contract (if it belongs to the tenant and is active),
     * 2. otherwise the agency default active template,
     * 3. otherwise null (caller falls back to the system default PDF).
     */
    private ContractTemplate resolveTemplate(Contract contract, Long tenantId) {
        ContractTemplate selected = contract.getSelectedTemplate();
        if (selected != null
                && selected.getTenant() != null
                && tenantId.equals(selected.getTenant().getId())
                && Boolean.TRUE.equals(selected.getActive())) {
            return selected;
        }
        return templateRepository
                .findFirstByTenantIdAndDefaultTemplateTrueAndActiveTrueOrderByUpdatedAtDesc(tenantId)
                .orElse(null);
    }

    /**
     * Render a preview PDF for a template using representative sample contract data.
     * Throws IllegalArgumentException (→ HTTP 400) when the template has no front page yet.
     */
    public byte[] generatePreview(ContractTemplate template) {
        if (template == null) {
            throw new IllegalArgumentException("Contract template not found");
        }
        if (template.getFrontFilePath() == null || template.getFrontFilePath().isBlank()) {
            throw new IllegalArgumentException("Template front page is missing. Please upload it first.");
        }
        return generate(sampleContract(), template.getTenant(), null, template);
    }

    private Contract sampleContract() {
        LocalDate start = LocalDate.of(2026, 6, 16);
        Contract sample = new Contract();
        sample.setContractNumber("CTR-PREVIEW");
        sample.setStatus(ContractStatus.DRAFT);
        sample.setClientFullName("Client Example");
        sample.setClientFirstName("Client");
        sample.setClientLastName("Example");
        sample.setClientPhone("+212 600-000000");
        sample.setClientEmail("client@example.com");
        sample.setClientAddress("123 Avenue Mohammed V");
        sample.setClientCity("Casablanca");
        sample.setClientCountry("Maroc");
        sample.setClientNationality("Marocaine");
        sample.setClientCin("AB123456");
        sample.setClientDriverLicense("DL-998877");
        sample.setClientBirthDate(LocalDate.of(1990, 1, 1));
        sample.setVehicleBrand("Dacia");
        sample.setVehicleModel("Logan");
        sample.setVehicleCategory("Berline");
        sample.setVehicleRegistration("12345-A-6");
        sample.setFuelType("Diesel");
        sample.setMileageStart(45000);
        sample.setMileageEnd(45350);
        sample.setStartDate(start);
        sample.setEndDate(start.plusDays(3));
        sample.setPickupTime(LocalTime.of(9, 0));
        sample.setReturnTime(LocalTime.of(18, 0));
        sample.setPickupLocation("Casablanca");
        sample.setReturnLocation("Casablanca");
        sample.setRentalDays(3);
        sample.setDailyPrice(BigDecimal.valueOf(400));
        sample.setTotalPrice(BigDecimal.valueOf(1200));
        sample.setPaidAmount(BigDecimal.valueOf(600));
        sample.setRemainingAmount(BigDecimal.valueOf(600));
        sample.setDepositAmount(BigDecimal.valueOf(2000));
        sample.setPaymentMethod("cash");
        sample.setCreatedAt(start.atStartOfDay());
        return sample;
    }

    private byte[] generate(Contract contract, Tenant tenant, Deposit deposit, ContractTemplate template) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 0, 0, 0, 0);
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();

            addTemplatePage(document, writer, template.getFrontFilePath(), 1);
            drawFields(writer.getDirectContent(), contract, tenant, deposit, template, 1);

            boolean hasBack = template.getBackFilePath() != null && !template.getBackFilePath().isBlank();
            boolean hasSecondPageFields = template.getFields() != null && template.getFields().stream()
                    .anyMatch(field -> Integer.valueOf(2).equals(field.getPageNumber()));
            if (hasBack || hasSecondPageFields) {
                document.newPage();
                if (hasBack) {
                    addTemplatePage(document, writer, template.getBackFilePath(), 1);
                }
                drawFields(writer.getDirectContent(), contract, tenant, deposit, template, 2);
            } else {
                addDefaultTermsPage(document, tenant);
            }

            document.close();
            return out.toByteArray();
        } catch (Exception exception) {
            log.error("Unable to generate contract template PDF: contractId={}, templateId={}",
                    contract != null ? contract.getId() : null, template != null ? template.getId() : null, exception);
            throw new IllegalStateException("Unable to generate contract PDF from agency template", exception);
        }
    }

    private void addTemplatePage(Document document, PdfWriter writer, String filePath, int sourcePage) throws Exception {
        if (filePath == null || !Files.exists(Path.of(filePath))) {
            throw new IllegalStateException("Template file is missing");
        }
        String lower = filePath.toLowerCase(Locale.ROOT);
        PdfContentByte canvas = writer.getDirectContentUnder();
        if (lower.endsWith(".pdf")) {
            PdfReader reader = new PdfReader(filePath);
            PdfImportedPage page = writer.getImportedPage(reader, sourcePage);
            Rectangle sourceSize = reader.getPageSize(sourcePage);
            float scaleX = PageSize.A4.getWidth() / sourceSize.getWidth();
            float scaleY = PageSize.A4.getHeight() / sourceSize.getHeight();
            canvas.addTemplate(page, scaleX, 0, 0, scaleY, 0, 0);
            reader.close();
            return;
        }
        Image image = Image.getInstance(filePath);
        image.setAbsolutePosition(0, 0);
        image.scaleAbsolute(PageSize.A4.getWidth(), PageSize.A4.getHeight());
        canvas.addImage(image);
    }

    private void drawFields(PdfContentByte canvas, Contract contract, Tenant tenant, Deposit deposit,
                            ContractTemplate template, int pageNumber) throws Exception {
        List<ContractTemplateField> fields = template.getFields() == null ? List.of() : template.getFields();
        for (ContractTemplateField field : fields) {
            if (!Boolean.TRUE.equals(field.getEnabled()) || !Integer.valueOf(pageNumber).equals(field.getPageNumber())) {
                continue;
            }
            drawField(canvas, field, valueFor(field.getFieldKey(), contract, tenant, deposit, field.getDateFormat()));
        }
    }

    private void drawField(PdfContentByte canvas, ContractTemplateField field, String value) throws Exception {
        float pageWidth = PageSize.A4.getWidth();
        float pageHeight = PageSize.A4.getHeight();
        float x = percent(field.getXPercent()) * pageWidth;
        float topY = percent(field.getYPercent()) * pageHeight;
        float width = Math.max(8, percent(field.getWidthPercent()) * pageWidth);
        float height = Math.max(8, percent(field.getHeightPercent()) * pageHeight);
        float y = pageHeight - topY - height;

        if (field.getFieldKey() != null && field.getFieldKey().startsWith("signature.")) {
            drawSignature(canvas, value, x, y, width, height);
            return;
        }
        if ("contract.verificationQr".equals(field.getFieldKey())) {
            drawPseudoQr(canvas, value, x, y, Math.min(width, height));
            return;
        }

        Font font = new Font(Font.HELVETICA, field.getFontSize() != null ? field.getFontSize() : 10,
                "bold".equalsIgnoreCase(field.getFontWeight()) ? Font.BOLD : Font.NORMAL,
                color(field.getColor()));
        Phrase phrase = new Phrase(value == null ? "" : value, font);
        if (Boolean.TRUE.equals(field.getMultiline())) {
            ColumnText column = new ColumnText(canvas);
            column.setSimpleColumn(phrase, x, y, x + width, y + height, field.getFontSize() + 2, align(field.getTextAlign()));
            column.go();
        } else {
            ColumnText.showTextAligned(canvas, align(field.getTextAlign()), phrase, x, y + Math.max(2, height / 3), 0);
        }
    }

    private void drawSignature(PdfContentByte canvas, String dataUrl, float x, float y, float width, float height) {
        try {
            Image image = imageFromDataUrl(dataUrl);
            if (image == null) return;
            image.scaleToFit(width, height);
            image.setAbsolutePosition(x, y);
            canvas.addImage(image);
        } catch (Exception exception) {
            log.warn("Unable to draw signature on contract template", exception);
        }
    }

    private void drawPseudoQr(PdfContentByte canvas, String seed, float x, float y, float size) {
        int cells = 21;
        float cell = size / cells;
        int hash = (seed == null ? "" : seed).hashCode();
        canvas.setColorFill(Color.WHITE);
        canvas.rectangle(x, y, size, size);
        canvas.fill();
        canvas.setColorFill(Color.BLACK);
        for (int row = 0; row < cells; row++) {
            for (int col = 0; col < cells; col++) {
                boolean finder = finder(col, row, 0, 0) || finder(col, row, 14, 0) || finder(col, row, 0, 14);
                boolean dark = finder || (((col * 31 + row * 17 + hash) & 3) == 0);
                if (dark) {
                    canvas.rectangle(x + col * cell, y + (cells - 1 - row) * cell, cell, cell);
                }
            }
        }
        canvas.fill();
    }

    private boolean finder(int x, int y, int ox, int oy) {
        return x >= ox && x < ox + 7 && y >= oy && y < oy + 7
                && (x == ox || x == ox + 6 || y == oy || y == oy + 6 || (x >= ox + 2 && x <= ox + 4 && y >= oy + 2 && y <= oy + 4));
    }

    private void addDefaultTermsPage(Document document, Tenant tenant) throws Exception {
        String terms = termsRepository.findFirstByTenantIdAndDefaultTermsTrueOrderByUpdatedAtDesc(tenant.getId())
                .map(ContractTerms::getContent)
                .filter(value -> !value.isBlank())
                .orElseGet(() -> tenant.getTermsAndConditions() != null && !tenant.getTermsAndConditions().isBlank()
                        ? tenant.getTermsAndConditions()
                        : ContractTemplateService.defaultTerms());
        document.newPage();
        document.setMargins(36, 36, 36, 36);
        document.add(new Paragraph("CONDITIONS GENERALES", new Font(Font.HELVETICA, 14, Font.BOLD)));
        document.add(new Paragraph("Please review your legal terms before using them officially.",
                new Font(Font.HELVETICA, 8, Font.ITALIC, Color.GRAY)));
        document.add(new Paragraph(" "));
        document.add(new Paragraph(terms, new Font(Font.HELVETICA, 9, Font.NORMAL)));
    }

    private String valueFor(String key, Contract c, Tenant tenant, Deposit deposit, String dateFormat) {
        if (key == null) return "";
        AdditionalDriver driver = c.getAdditionalDrivers() != null && !c.getAdditionalDrivers().isEmpty()
                ? c.getAdditionalDrivers().get(0) : null;
        return switch (key) {
            case "client.fullName" -> value(c.getClientFullName(), c.getClientName(), join(c.getClientFirstName(), c.getClientLastName()));
            case "client.firstName" -> value(c.getClientFirstName());
            case "client.lastName" -> value(c.getClientLastName());
            case "client.address" -> join(c.getClientAddress(), c.getClientCity(), c.getClientCountry());
            case "client.birthDate" -> date(c.getClientBirthDate(), dateFormat);
            case "client.nationality" -> value(c.getClientNationality());
            case "client.cin" -> value(c.getClientCin());
            case "client.passport" -> value(c.getClientPassportNumber());
            case "client.drivingLicenseNumber" -> value(c.getClientDriverLicense());
            case "client.drivingLicenseIssuedAt" -> date(c.getClientDriverLicenseIssue(), dateFormat);
            case "client.phone" -> value(c.getClientPhone());
            case "client.email" -> value(c.getClientEmail());
            case "additionalDriver.fullName" -> driver != null ? value(driver.getFullName()) : "";
            case "additionalDriver.address" -> driver != null ? value(driver.getAddress()) : "";
            case "additionalDriver.cin" -> driver != null ? value(driver.getCin(), driver.getPassportNumber()) : "";
            case "additionalDriver.drivingLicenseNumber" -> driver != null ? value(driver.getDriverLicenseNumber()) : "";
            case "vehicle.brand" -> value(c.getVehicleBrand());
            case "vehicle.model" -> value(c.getVehicleModel());
            case "vehicle.type" -> value(c.getVehicleCategory(), c.getContractType());
            case "vehicle.registrationNumber" -> value(c.getVehicleRegistration());
            case "vehicle.fuelType" -> value(c.getFuelType());
            case "vehicle.startMileage" -> integer(c.getMileageStart());
            case "vehicle.endMileage" -> integer(c.getMileageEnd());
            case "vehicle.fuelLevel" -> value(c.getFuelLevelStart(), c.getFuelLevelEnd());
            case "reservation.startDate" -> date(c.getStartDate(), dateFormat);
            case "reservation.startTime" -> time(c.getPickupTime());
            case "reservation.startLocation" -> value(c.getPickupLocation(), c.getPickupAgency());
            case "reservation.endDate" -> date(c.getEndDate(), dateFormat);
            case "reservation.endTime" -> time(c.getReturnTime());
            case "reservation.endLocation" -> value(c.getReturnLocation(), c.getReturnAgency());
            case "contract.number" -> value(c.getContractNumber());
            case "contract.createdAt" -> dateTime(c.getCreatedAt());
            case "contract.status" -> c.getStatus() != null ? c.getStatus().name() : "";
            case "payment.dailyPrice" -> money(c.getDailyPrice());
            case "payment.days" -> integer(c.getRentalDays());
            case "payment.totalAmount" -> money(c.getTotalPrice());
            case "payment.advancePaid" -> money(c.getPaidAmount());
            case "payment.remainingAmount" -> money(c.getRemainingAmount());
            case "payment.deposit" -> money(c.getDepositAmount() != null ? c.getDepositAmount() : deposit != null ? deposit.getAmount() : null);
            case "payment.extraFees" -> money(sum(c.getDeliveryFees(), c.getReturnFees(), c.getLateFees(), c.getCleaningFees(), c.getFuelCharges()));
            case "payment.paymentMethod" -> value(c.getPaymentMethod());
            case "payment.cash" -> check(c.getPaymentMethod(), "cash");
            case "payment.cheque" -> check(c.getPaymentMethod(), "cheque", "check");
            case "payment.card" -> check(c.getPaymentMethod(), "card", "carte");
            case "payment.bankTransfer" -> check(c.getPaymentMethod(), "transfer", "virement", "bank");
            case "documents.registrationCard", "documents.insurance", "documents.vignette", "documents.technicalInspection", "documents.authorization" -> "";
            case "signature.client" -> value(c.getClientSignature());
            case "signature.agency" -> value(c.getOwnerSignature(), tenant.getAgencySignature());
            case "signature.employee" -> value(c.getEmployeeSignature());
            case "contract.verificationQr" -> value(c.getPublicSigningUrl(), c.getQrToken(), c.getContractNumber());
            default -> "";
        };
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

    private float percent(BigDecimal value) {
        return value == null ? 0f : value.floatValue() / 100f;
    }

    private int align(String value) {
        if ("center".equalsIgnoreCase(value)) return Element.ALIGN_CENTER;
        if ("right".equalsIgnoreCase(value)) return Element.ALIGN_RIGHT;
        return Element.ALIGN_LEFT;
    }

    private Color color(String hex) {
        try {
            return Color.decode(hex == null || hex.isBlank() ? "#000000" : hex);
        } catch (NumberFormatException ignored) {
            return Color.BLACK;
        }
    }

    private String check(String paymentMethod, String... accepted) {
        String method = paymentMethod == null ? "" : paymentMethod.toLowerCase(Locale.ROOT);
        for (String item : accepted) {
            if (method.contains(item)) return "X";
        }
        return "";
    }

    private BigDecimal sum(BigDecimal... values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) total = total.add(value != null ? value : BigDecimal.ZERO);
        return total;
    }

    private String money(BigDecimal value) {
        return (value != null ? value : BigDecimal.ZERO).stripTrailingZeros().toPlainString() + " MAD";
    }

    private String integer(Integer value) {
        return value != null ? String.valueOf(value) : "";
    }

    private String date(LocalDate value, String pattern) {
        if (value == null) return "";
        if (pattern != null && !pattern.isBlank()) {
            return value.format(DateTimeFormatter.ofPattern(pattern));
        }
        return value.format(DATE);
    }

    private String time(LocalTime value) {
        return value != null ? value.format(TIME) : "";
    }

    private String dateTime(LocalDateTime value) {
        return value != null ? value.format(DATETIME) : "";
    }

    private String join(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                if (!builder.isEmpty()) builder.append(" ");
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
}
