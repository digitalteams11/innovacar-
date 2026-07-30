package com.carrental.service.reporting;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Disk-backed report PDF storage, mirroring {@code PdfService}'s contract-PDF storage convention. */
@Slf4j
@Service
public class ReportPdfStorage {

    private static final String STORAGE_DIR = "pdfs/reports";

    public record StoredFile(String fileStorageKey, long fileSizeBytes, String checksum) {}

    public StoredFile save(Long tenantId, Long reportId, byte[] pdfBytes) {
        try {
            Path dir = Paths.get(STORAGE_DIR);
            if (!Files.exists(dir)) Files.createDirectories(dir);
            String fileName = sanitizeFileName("tenant_" + tenantId + "_report_" + reportId + ".pdf");
            Path filePath = dir.resolve(fileName);
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                fos.write(pdfBytes);
            }
            return new StoredFile(fileName, pdfBytes.length, sha256(pdfBytes));
        } catch (IOException e) {
            throw new IllegalStateException("Report PDF save failed", e);
        }
    }

    public byte[] read(String fileStorageKey) {
        try {
            Path filePath = Paths.get(STORAGE_DIR).resolve(sanitizeFileName(fileStorageKey));
            return Files.exists(filePath) ? Files.readAllBytes(filePath) : null;
        } catch (IOException e) {
            log.error("Failed to read report PDF file={}", fileStorageKey, e);
            return null;
        }
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
