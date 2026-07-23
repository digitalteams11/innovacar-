package com.carrental.service.export;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Shared response-building helpers for PDF/CSV/XLSX export endpoints — one
 * canonical place for the Content-Disposition/Content-Type/cache-control
 * headers every export controller needs, so they can't drift out of sync. */
public final class ExportHttpUtil {

    private ExportHttpUtil() {}

    public static String filename(String entityLabel, String extension) {
        return "innovacar-" + entityLabel + "-" + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + "." + extension;
    }

    /** Content-Disposition with an RFC 5987 UTF-8 filename — safe even though our filenames are ASCII today. */
    public static ResponseEntity<byte[]> fileResponse(byte[] content, MediaType mediaType, String filename) {
        String encoded;
        try {
            encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            encoded = filename;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded);
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        return ResponseEntity.ok().headers(headers).body(content);
    }
}
