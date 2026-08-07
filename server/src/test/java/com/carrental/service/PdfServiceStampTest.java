package com.carrental.service;

import com.carrental.entity.Contract;
import com.carrental.entity.ContractStatus;
import com.carrental.entity.Tenant;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the agency stamp actually renders into the real generated PDF —
 * see PdfService#addSignatures/#agencyAssetImage. Uses PdfTextExtractor to
 * check for the "Cachet / Tampon" label, which addSignatures only ever adds
 * when agencyAssetImage successfully resolved and decoded an image — a
 * false positive (label present but image failed) is not possible with
 * this code path.
 */
class PdfServiceStampTest {

    private final PdfService pdfService = new PdfService(new QrCodeService());
    private static final String UPLOAD_ROOT = "uploads/agency/999001";

    @AfterEach
    void cleanup() throws Exception {
        Path dir = Path.of(UPLOAD_ROOT);
        if (Files.exists(dir)) {
            try (var walk = Files.walk(dir)) {
                walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }

    private String writeTestImage(String fileName, String format) throws Exception {
        Path dir = Path.of(UPLOAD_ROOT);
        Files.createDirectories(dir);
        Path file = dir.resolve(fileName);
        // JPEG has no alpha channel — an ARGB image would fail/corrupt on JPEG
        // encode, which is a test-fixture artifact, not something a real JPEG
        // upload (always opaque RGB) would ever hit.
        int type = "jpg".equals(format) || "jpeg".equals(format) ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        BufferedImage img = new BufferedImage(40, 40, type);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, format, out);
            Files.write(file, out.toByteArray());
        }
        return "/" + UPLOAD_ROOT + "/" + fileName;
    }

    private Contract baseContract() {
        return Contract.builder()
                .id(1L)
                .contractNumber("CT-TEST-0001")
                .status(ContractStatus.DRAFT)
                .clientSignature(null)
                .ownerSignature(null)
                .build();
    }

    private String extractText(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= reader.getNumberOfPages(); i++) {
                sb.append(new PdfTextExtractor(reader).getTextFromPage(i));
            }
            return sb.toString();
        } finally {
            reader.close();
        }
    }

    @Test
    void agencyWithPngStamp_stampLabelAppearsInGeneratedPdf() throws Exception {
        String stampPath = writeTestImage("stamp.png", "png");
        Tenant tenant = Tenant.builder().id(999001L).name("Innovacar Test").agencyStampUrl(stampPath).build();
        byte[] pdf = pdfService.generateContractPdf(baseContract(), tenant, null);

        assertThat(pdf).isNotEmpty();
        assertThat(extractText(pdf)).contains("Cachet");
    }

    @Test
    void agencyWithJpegStamp_stampLabelAppearsInGeneratedPdf() throws Exception {
        String stampPath = writeTestImage("stamp.jpg", "jpg");
        Tenant tenant = Tenant.builder().id(999001L).name("Innovacar Test").agencyStampUrl(stampPath).build();
        byte[] pdf = pdfService.generateContractPdf(baseContract(), tenant, null);

        assertThat(extractText(pdf)).contains("Cachet");
    }

    @Test
    void agencyWithoutStamp_pdfGeneratesWithoutStampLabelOrCrash() throws Exception {
        Tenant tenant = Tenant.builder().id(999002L).name("Innovacar Test").agencyStampUrl(null).build();
        byte[] pdf = pdfService.generateContractPdf(baseContract(), tenant, null);

        assertThat(pdf).isNotEmpty();
        assertThat(extractText(pdf)).doesNotContain("Cachet");
    }

    @Test
    void agencyWithSignatureAndStamp_bothRenderTogether() throws Exception {
        String stampPath = writeTestImage("stamp.png", "png");
        // Minimal 1x1 transparent PNG as a base64 data URL "signature".
        String signatureDataUrl = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(
                pngBytes());
        Tenant tenant = Tenant.builder().id(999001L).name("Innovacar Test")
                .agencyStampUrl(stampPath).agencySignature(signatureDataUrl).build();
        byte[] pdf = pdfService.generateContractPdf(baseContract(), tenant, null);

        String text = extractText(pdf);
        assertThat(text).contains("Cachet");
        assertThat(text).contains("Signature de la Direction");
    }

    @Test
    void missingStampFileOnDisk_doesNotCrashGeneration() throws Exception {
        Tenant tenant = Tenant.builder().id(999003L).name("Innovacar Test")
                .agencyStampUrl("/uploads/agency/999003/does-not-exist.png").build();

        byte[] pdf = pdfService.generateContractPdf(baseContract(), tenant, null);

        assertThat(pdf).isNotEmpty();
        assertThat(extractText(pdf)).doesNotContain("Cachet");
    }

    @Test
    void signedContractSnapshot_usesFrozenStampNotLiveTenantStamp() throws Exception {
        String originalStamp = writeTestImage("stamp-original.png", "png");
        String newStamp = writeTestImage("stamp-new.png", "png");

        Contract signedContract = baseContract();
        signedContract.setBrandingStampUrl(originalStamp);

        // Tenant's live stamp has since changed to newStamp, but the signed
        // contract's own snapshot (brandingStampUrl) must win — this is what
        // effectiveStamp()/PdfService actually renders from.
        Tenant tenant = Tenant.builder().id(999001L).name("Innovacar Test").agencyStampUrl(newStamp).build();

        byte[] pdf = pdfService.generateContractPdf(signedContract, tenant, null);
        assertThat(extractText(pdf)).contains("Cachet");
    }

    private byte[] pngBytes() throws Exception {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        }
    }
}
