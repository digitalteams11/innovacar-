package com.carrental.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiKnowledgeServiceTest {

    private final AiKnowledgeService service = new AiKnowledgeService();

    @Test
    void nullLanguage_defaultsToEnglishInstruction() {
        String instruction = service.buildSystemInstruction("OWNER", "Acme", "Dashboard", "/dashboard", null);
        assertThat(instruction).contains("User preferred language: English.");
    }

    @Test
    void frLanguage_instructsFrenchResponse() {
        String instruction = service.buildSystemInstruction("OWNER", "Acme", "Dashboard", "/dashboard", "fr");
        assertThat(instruction).contains("User preferred language: French.");
    }

    @Test
    void arLanguage_instructsArabicResponseAndMentionsDarija() {
        String instruction = service.buildSystemInstruction("OWNER", "Acme", "Dashboard", "/dashboard", "ar");
        assertThat(instruction).contains("User preferred language: Arabic.");
        assertThat(instruction).contains("Darija");
    }

    @Test
    void fourArgOverload_stillWorks_defaultsToEnglish() {
        String instruction = service.buildSystemInstruction("OWNER", "Acme", "Dashboard", "/dashboard");
        assertThat(instruction).contains("User preferred language: English.");
    }
}
