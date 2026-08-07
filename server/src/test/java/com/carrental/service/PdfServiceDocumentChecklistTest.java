package com.carrental.service;

import com.carrental.entity.Contract;
import com.carrental.entity.ContractStatus;
import com.carrental.entity.Tenant;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the "DOCUMENTS DE BORD" checklist on the generated PDF actually
 * reflects the contract's document* snapshot fields — see
 * PdfService#addDocuments / ContractService#applyDocumentSnapshot. Extracts
 * real text from a real generated PDF (iText's PdfTextExtractor), same
 * approach as PdfServiceStampTest, rather than asserting on internal
 * PdfService state.
 */
class PdfServiceDocumentChecklistTest {

    private final PdfService pdfService = new PdfService(new QrCodeService());
    private final Tenant tenant = Tenant.builder().id(1L).name("Innovacar Test").build();

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
    void checkedDocumentsRenderWithXAndUncheckedWithBlankBox() throws Exception {
        Contract contract = Contract.builder()
                .id(1L).contractNumber("CT-TEST-DOCS-1").status(ContractStatus.DRAFT)
                .documentCarteGrise(true)
                .documentAssurance(true)
                .documentVignette(false)
                .documentVisiteTechnique(true)
                .documentAutorisationCirculation(false)
                .build();

        String text = extractText(pdfService.generateContractPdf(contract, tenant, null));

        assertThat(text).contains("[X] Carte grise");
        assertThat(text).contains("[X] Assurance");
        assertThat(text).contains("[ ] Vignette");
        assertThat(text).contains("[X] Visite technique");
        assertThat(text).contains("[ ] Autorisation de circulation");
    }

    @Test
    void nullSnapshotFieldsRenderAsUnchecked() throws Exception {
        // A contract created before this feature existed (or one with no
        // linked vehicle) — every document* field is null, not false, but
        // must still render as an unchecked box, never crash generation.
        Contract contract = Contract.builder()
                .id(2L).contractNumber("CT-TEST-DOCS-2").status(ContractStatus.DRAFT)
                .build();
        // Explicitly force the fields to null — @Builder.Default would
        // otherwise apply false, which this test wants to distinguish from.
        contract.setDocumentCarteGrise(null);
        contract.setDocumentAssurance(null);
        contract.setDocumentVignette(null);
        contract.setDocumentVisiteTechnique(null);
        contract.setDocumentAutorisationCirculation(null);

        String text = extractText(pdfService.generateContractPdf(contract, tenant, null));

        assertThat(text).contains("[ ] Carte grise");
        assertThat(text).contains("[ ] Assurance");
        assertThat(text).contains("[ ] Vignette");
        assertThat(text).contains("[ ] Visite technique");
        assertThat(text).contains("[ ] Autorisation de circulation");
    }
}
