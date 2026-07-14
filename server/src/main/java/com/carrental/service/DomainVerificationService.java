package com.carrental.service;

import com.carrental.entity.WhiteLabelSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Hashtable;
import java.util.Locale;

/**
 * Performs real DNS lookups (TXT + CNAME) to verify that an agency actually controls the
 * custom domain it configured. No network call here is faked: a lookup either finds a
 * matching record or it doesn't, and the resulting status/error is exactly what happened.
 */
@Service
@Slf4j
public class DomainVerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Value("${app.domain.base-domain}")
    private String baseDomain;

    @Value("${app.domain.cname-target}")
    private String cnameTarget;

    public String generateVerificationToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        StringBuilder hex = new StringBuilder("innovacar-verify-");
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    public String getBaseDomain() {
        return baseDomain;
    }

    public String getCnameTarget() {
        return cnameTarget;
    }

    public record DnsInstructions(String txtRecordName, String txtRecordValue,
                                   String cnameRecordName, String cnameRecordValue) {
    }

    public DnsInstructions buildDnsInstructions(WhiteLabelSettings settings) {
        String domain = settings.getCustomDomain();
        return new DnsInstructions(
                "_innovacar-verify." + domain,
                settings.getVerificationToken(),
                domain,
                cnameTarget
        );
    }

    public record VerificationResult(boolean success, String status, String error) {
    }

    /**
     * Looks up the real TXT record on {@code _innovacar-verify.<domain>} and the real CNAME
     * record on {@code <domain>} and checks them against what was issued for this tenant.
     * Both records must match for the domain to be considered DNS_VERIFIED.
     */
    public VerificationResult verify(WhiteLabelSettings settings) {
        String domain = settings.getCustomDomain();
        if (domain == null || domain.isBlank()) {
            return new VerificationResult(false, "FAILED", "No custom domain configured");
        }
        if (settings.getVerificationToken() == null) {
            return new VerificationResult(false, "FAILED", "No verification token was issued for this domain");
        }

        try {
            boolean txtOk = lookupTxt("_innovacar-verify." + domain, settings.getVerificationToken());
            if (!txtOk) {
                return new VerificationResult(false, "FAILED",
                        "TXT record _innovacar-verify." + domain + " not found or does not match the expected value");
            }
            boolean cnameOk = lookupCname(domain, cnameTarget);
            if (!cnameOk) {
                return new VerificationResult(false, "FAILED",
                        "CNAME record for " + domain + " not found or does not point to " + cnameTarget);
            }
            return new VerificationResult(true, "DNS_VERIFIED", null);
        } catch (NamingException e) {
            log.warn("DNS verification failed for domain {}: {}", domain, e.getMessage());
            return new VerificationResult(false, "FAILED", "DNS lookup failed: " + e.getMessage());
        }
    }

    private boolean lookupTxt(String name, String expectedValue) throws NamingException {
        Attributes attrs = dnsLookup(name, "TXT");
        Attribute txt = attrs.get("TXT");
        if (txt == null) return false;
        for (int i = 0; i < txt.size(); i++) {
            Object value = txt.get(i);
            if (value == null) continue;
            String v = value.toString().replaceAll("^\"|\"$", "").trim();
            if (v.equalsIgnoreCase(expectedValue)) return true;
        }
        return false;
    }

    private boolean lookupCname(String name, String expectedTarget) throws NamingException {
        Attributes attrs = dnsLookup(name, "CNAME");
        Attribute cname = attrs.get("CNAME");
        if (cname == null) return false;
        for (int i = 0; i < cname.size(); i++) {
            Object value = cname.get(i);
            if (value == null) continue;
            String v = value.toString().trim();
            if (v.endsWith(".")) v = v.substring(0, v.length() - 1);
            if (v.equalsIgnoreCase(expectedTarget)) return true;
        }
        return false;
    }

    private Attributes dnsLookup(String name, String recordType) throws NamingException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", "3000");
        env.put("com.sun.jndi.dns.timeout.retries", "1");
        DirContext ctx = new InitialDirContext(env);
        try {
            return ctx.getAttributes(name, new String[]{recordType});
        } finally {
            ctx.close();
        }
    }

    public String slugify(String input) {
        if (input == null) return null;
        return input.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-").replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
    }

    public boolean isValidSubdomainSlug(String slug) {
        return slug != null && slug.matches("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$");
    }

    public LocalDateTime now() {
        return LocalDateTime.now();
    }
}
