package com.carrental.legal.controller;

import com.carrental.legal.dto.LegalDocumentSummaryDto;
import com.carrental.legal.dto.LegalDocumentVersionDto;
import com.carrental.legal.entity.LegalDocumentType;
import com.carrental.legal.service.LegalDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public, unauthenticated reads of currently published legal documents —
 * needed on the signup page, the marketing site, and the footer before a
 * visitor ever logs in. Mounted under /api/public so SecurityConfig's
 * existing "/api/public/**" permitAll rule covers it.
 */
@RestController
@RequestMapping("/api/public/legal/documents")
@RequiredArgsConstructor
public class LegalDocumentController {

    private final LegalDocumentService legalDocumentService;

    @GetMapping
    public List<LegalDocumentSummaryDto> listCurrent(@RequestParam(required = false) String locale) {
        return legalDocumentService.listCurrentPublished(locale);
    }

    @GetMapping("/{type}")
    public LegalDocumentVersionDto getCurrent(
            @PathVariable LegalDocumentType type,
            @RequestParam(required = false) String locale) {
        return legalDocumentService.getPublished(type, locale);
    }

    @GetMapping("/{type}/versions/{versionNumber}")
    public LegalDocumentVersionDto getVersion(
            @PathVariable LegalDocumentType type,
            @PathVariable Integer versionNumber,
            @RequestParam(required = false) String locale) {
        return legalDocumentService.getVersion(type, locale, versionNumber);
    }
}
