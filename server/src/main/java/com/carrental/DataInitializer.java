package com.carrental;

import com.carrental.entity.*;
import com.carrental.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final SubscriptionPlanRepository planRepository;
    private final FeatureDefinitionRepository featureRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final PaymentGatewayConfigRepository gatewayConfigRepository;
    private final AffiliateRuleRepository affiliateRuleRepository;
    private final PlatformSettingsRepository platformSettingsRepository;
    private final KnowledgeArticleRepository knowledgeArticleRepository;
    private final LegalDocumentRepository legalDocumentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.bootstrap.super-admin.enabled:false}")
    private boolean bootstrapSuperAdminEnabled;

    @Value("${app.bootstrap.super-admin.email:superadmin@innovax.tech}")
    private String bootstrapSuperAdminEmail;

    @Value("${app.bootstrap.super-admin.password:}")
    private String bootstrapSuperAdminPassword;

    @Value("${app.bootstrap.demo.enabled:false}")
    private boolean bootstrapDemoEnabled;

    @Value("${app.bootstrap.demo.admin-email:admin@test.com}")
    private String bootstrapDemoAdminEmail;

    @Value("${app.bootstrap.demo.admin-password:}")
    private String bootstrapDemoAdminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        long startNanos = System.nanoTime();
        log.info("[STARTUP_STEP_BEGIN] DataInitializer");
        try {
            runInternal();
            log.info("[STARTUP_STEP_OK] DataInitializer durationMs={}",
                    (System.nanoTime() - startNanos) / 1_000_000);
        } catch (RuntimeException e) {
            // Deliberately NOT rethrown: this is a CommandLineRunner, which Spring Boot
            // invokes AFTER Tomcat is already up and "Started LocationCarApplication" has
            // already been logged (confirmed empirically — Tomcat/Started log both precede
            // this runner in the startup log). If this exception were allowed to propagate,
            // SpringApplication.run()'s failure handling closes the whole ApplicationContext
            // (stopping the just-started Tomcat) and the JVM exits — turning any transient
            // or edge-case seeding failure into a full app crash and Railway healthcheck
            // failure / restart loop, even though seed data is optional, non-critical state.
            log.error("[STARTUP_STEP_FAILED] DataInitializer exceptionClass={} message={} — "
                            + "continuing startup; seed/repair data may be incomplete until next successful run",
                    e.getClass().getName(), e.getMessage());
        }
    }

    private void runInternal() {
        // 1. Seed platform settings
        if (platformSettingsRepository.count() == 0) {
            platformSettingsRepository.save(PlatformSettings.builder()
                    .platformName("Innovax Technologies")
                    .primaryColor("#0b1437")
                    .defaultLanguage("en")
                    .supportedLanguages("en,fr,ar")
                    .defaultCurrency("MAD")
                    .maintenanceMode(false)
                    .apiRateLimit(1000)
                    .sessionTimeoutMinutes(120)
                    .maxLoginAttempts(5)
                    .lockoutDurationMinutes(30)
                    .require2fa(false)
                    .build());
            log.info("Platform settings initialized");
        }

        // 2. Seed subscription plans
        if (planRepository.count() == 0) {
            planRepository.save(SubscriptionPlan.builder()
                    .name("Trial").code("trial")
                    .monthlyPrice(BigDecimal.ZERO).yearlyPrice(BigDecimal.ZERO)
                    .description("2-month free trial with full access to core features.")
                    .maxVehicles(10).maxEmployees(5).maxGpsDevices(5).maxReservations(100)
                    .storageLimitMb(1024).apiAccess(false).whiteLabel(false).prioritySupport(false)
                    .isActive(true).displayOrder(1).build());

            planRepository.save(SubscriptionPlan.builder()
                    .name("Basic").code("basic")
                    .monthlyPrice(new BigDecimal("299.00")).yearlyPrice(new BigDecimal("2990.00"))
                    .description("Perfect for small agencies getting started.")
                    .maxVehicles(25).maxEmployees(10).maxGpsDevices(10).maxReservations(500)
                    .storageLimitMb(5120).apiAccess(false).whiteLabel(false).prioritySupport(false)
                    .isActive(true).displayOrder(2).build());

            planRepository.save(SubscriptionPlan.builder()
                    .name("Standard").code("standard")
                    .monthlyPrice(new BigDecimal("599.00")).yearlyPrice(new BigDecimal("5990.00"))
                    .description("Ideal for growing agencies with advanced needs.")
                    .maxVehicles(75).maxEmployees(25).maxGpsDevices(30).maxReservations(2000)
                    .storageLimitMb(20480).apiAccess(true).whiteLabel(false).prioritySupport(true)
                    .isActive(true).displayOrder(3).build());

            planRepository.save(SubscriptionPlan.builder()
                    .name("Premium").code("premium")
                    .monthlyPrice(new BigDecimal("999.00")).yearlyPrice(new BigDecimal("9990.00"))
                    .description("For established agencies requiring premium features.")
                    .maxVehicles(200).maxEmployees(60).maxGpsDevices(100).maxReservations(10000)
                    .storageLimitMb(102400).apiAccess(true).whiteLabel(true).prioritySupport(true)
                    .isActive(true).displayOrder(4).build());

            planRepository.save(SubscriptionPlan.builder()
                    .name("Enterprise").code("enterprise")
                    .monthlyPrice(new BigDecimal("2499.00")).yearlyPrice(new BigDecimal("24990.00"))
                    .description("Unlimited scale for enterprise operations.")
                    .maxVehicles(9999).maxEmployees(9999).maxGpsDevices(9999).maxReservations(99999)
                    .storageLimitMb(1048576).apiAccess(true).whiteLabel(true).prioritySupport(true)
                    .isActive(true).displayOrder(5).build());

            log.info("Subscription plans seeded");
        }

        seedFeaturesAndPlanAccess();
        seedPromotionsAndBilling();
        int repairedSubscriptions = tenantRepository.repairPaidPlansMarkedAsTrial();
        if (repairedSubscriptions > 0) {
            log.warn("Repaired {} paid subscription record(s) incorrectly marked as TRIAL", repairedSubscriptions);
        }
        repairUsersRoleCheckConstraint();

        // 3. ALWAYS ensure system tenant exists
        Tenant systemTenant = tenantRepository.findByEmail("system@innovax.tech")
                .orElseGet(() -> {
                    Tenant t = tenantRepository.save(Tenant.builder()
                            .name("Innovax Technologies")
                            .email("system@innovax.tech")
                            .subscriptionActive(true)
                            .subscriptionEndDate(LocalDate.now().plusYears(100))
                            .status("ACTIVE")
                            .planName("Enterprise")
                            .address("Casablanca, Morocco")
                            .phone("+212 522 123 456")
                            .taxId("ICE001234567000")
                            .city("Casablanca")
                            .country("Morocco")
                            .maxVehicles(9999)
                            .maxEmployees(9999)
                            .maxGpsDevices(9999)
                            .maxReservations(99999)
                            .storageLimitMb(1048576)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build());
                    log.info("System tenant created for SUPER_ADMIN");
                    return t;
                });

        // 4. Ensure predictable development accounts, even when other rows already exist.
        if (bootstrapDemoEnabled) {
            ensureDevelopmentAccounts(systemTenant);
        } else {
            // Optional secure bootstrap for the first SUPER_ADMIN user in production.
            // existsByRole (a single indexed EXISTS query), not findAll().stream() —
            // this ran unconditionally on every boot and previously loaded every user
            // row in the entire database into memory just to check one boolean.
            boolean superAdminExists = userRepository.existsByRole(Role.SUPER_ADMIN);

            if (!superAdminExists) {
                if (bootstrapSuperAdminEnabled && hasText(bootstrapSuperAdminPassword)) {
                    userRepository.save(User.builder()
                            .email(bootstrapSuperAdminEmail)
                            .password(passwordEncoder.encode(bootstrapSuperAdminPassword))
                            .role(Role.SUPER_ADMIN)
                            .tenant(systemTenant)
                            .build());
                    log.info("SUPER_ADMIN bootstrap account created for email={}", bootstrapSuperAdminEmail);
                } else {
                    log.warn("No SUPER_ADMIN exists. Set APP_BOOTSTRAP_SUPER_ADMIN_ENABLED=true and "
                            + "APP_BOOTSTRAP_SUPER_ADMIN_PASSWORD to create the initial admin account.");
                }
            }
        }
    }

    private void ensureDevelopmentAccounts(Tenant systemTenant) {
        if (!hasText(bootstrapDemoAdminPassword)) {
            throw new IllegalStateException(
                    "Demo bootstrap requires APP_BOOTSTRAP_DEMO_ADMIN_PASSWORD.");
        }
        Tenant demoTenant = tenantRepository.findByEmail("contact@premium-rentals.com")
                .orElseGet(() -> tenantRepository.save(Tenant.builder()
                        .name("Premium Rentals")
                        .email("contact@premium-rentals.com")
                        .subscriptionActive(true)
                        .subscriptionEndDate(LocalDate.now().plusYears(1))
                        .status("ACTIVE")
                        .planName("Standard")
                        .city("Marrakech")
                        .country("Morocco")
                        .maxVehicles(75)
                        .maxEmployees(25)
                        .maxGpsDevices(30)
                        .maxReservations(2000)
                        .storageLimitMb(20480)
                        .build()));

        ensureDevelopmentUser("superadmin@test.com", Role.SUPER_ADMIN, systemTenant);
        ensureDevelopmentUser(bootstrapDemoAdminEmail, Role.ADMIN, demoTenant);
        ensureDevelopmentUser("owner@test.com", Role.AGENCY_OWNER, demoTenant);
        ensureDevelopmentUser("employee@test.com", Role.EMPLOYEE, demoTenant);
        log.info("Development accounts are ready.");
    }

    private void ensureDevelopmentUser(String email, Role role, Tenant tenant) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        var existing = userRepository.findFirstByEmailIgnoreCaseOrderByIdAsc(normalizedEmail);
        if (existing.isPresent()) {
            User user = existing.get();
            boolean changed = false;
            if (!passwordEncoder.matches(bootstrapDemoAdminPassword, user.getPassword())) {
                user.setPassword(passwordEncoder.encode(bootstrapDemoAdminPassword));
                changed = true;
            }
            if (user.getRole() != role) {
                user.setRole(role);
                changed = true;
            }
            if (user.getTenant() == null || !user.getTenant().getId().equals(tenant.getId())) {
                user.setTenant(tenant);
                changed = true;
            }
            if (!Boolean.TRUE.equals(user.getAccountEnabled())) {
                user.setAccountEnabled(true);
                changed = true;
            }
            if (!Boolean.TRUE.equals(user.getEmailVerified())) {
                user.setEmailVerified(true);
                changed = true;
            }
            if (user.getFailedLoginAttempts() == null || user.getFailedLoginAttempts() != 0) {
                user.setFailedLoginAttempts(0);
                changed = true;
            }
            if (user.getLockedUntil() != null) {
                user.setLockedUntil(null);
                changed = true;
            }
            // SUPER_ADMIN dev users must have no sub-role (null = unrestricted/full access).
            // Prevents accidental Data Reset Center lock-out if the staff-role system
            // assigns a non-SUPER_OWNER sub-role to these bootstrap accounts.
            if (role == Role.SUPER_ADMIN && user.getSuperAdminRole() != null) {
                user.setSuperAdminRole(null);
                changed = true;
            }
            if (changed) {
                userRepository.save(user);
                log.info("Development user repaired: {}", normalizedEmail);
            } else {
                log.info("Development user ready: {}", normalizedEmail);
            }
            return;
        }
        userRepository.save(User.builder()
                .email(normalizedEmail)
                .password(passwordEncoder.encode(bootstrapDemoAdminPassword))
                .role(role)
                .tenant(tenant)
                .accountEnabled(true)
                .emailVerified(true)
                .failedLoginAttempts(0)
                .build());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void repairUsersRoleCheckConstraint() {
        try {
            jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
                String databaseName = connection.getMetaData().getDatabaseProductName();
                if (databaseName == null || !databaseName.toLowerCase().contains("postgresql")) {
                    return null;
                }
                String allowedRoles = Arrays.stream(Role.values())
                        .map(role -> "'" + role.name() + "'")
                        .collect(Collectors.joining(", "));
                try (Statement statement = connection.createStatement()) {
                    // ALTER TABLE takes an ACCESS EXCLUSIVE lock on `users`. This runs
                    // unconditionally on every boot, so without a lock_timeout a single
                    // stale/zombie connection still holding a lock on this table (e.g.
                    // left over from a prior instance that was SIGKILLed by a container
                    // OOM-kill instead of shutting down cleanly) would hang this
                    // CommandLineRunner — and therefore the entire startup sequence —
                    // indefinitely. Fail fast instead so DataInitializer still completes
                    // and the app still becomes ready; the outer catch below just logs it.
                    statement.execute("SET LOCAL lock_timeout = '3s'");
                    statement.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check");
                    statement.execute("ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN (" + allowedRoles + "))");
                }
                log.info("users_role_check constraint synchronized with application roles");
                return null;
            });
        } catch (Exception ex) {
            log.warn("Could not repair users_role_check constraint automatically: {}", ex.getMessage());
        }
    }

    private void seedFeaturesAndPlanAccess() {
        String[][] definitions = {
                {"VEHICLE_MANAGEMENT", "Vehicle Management", "Manage fleet vehicles and availability.", "Maintain a reliable live fleet inventory.", "Core"},
                {"CLIENT_MANAGEMENT", "Client Management", "Manage rental customer records.", "Keep customer history and balances connected.", "Core"},
                {"RESERVATION_MANAGEMENT", "Reservation Management", "Create and manage reservations.", "Coordinate vehicle availability and bookings.", "Core"},
                {"CONTRACT_MANAGEMENT", "Contract Management", "Generate and manage rental contracts.", "Keep contracts linked to reservations and payments.", "Core"},
                {"DIGITAL_SIGNATURE", "Digital Signature", "Send contracts for electronic signature.", "Sign contracts from mobile and desktop devices.", "Contracts"},
                {"QR_SIGNATURE", "QR Signature", "Sign contracts using secure QR links.", "Complete signatures quickly on customer devices.", "Contracts"},
                {"ONLINE_BOOKING", "Online Booking", "Accept customer bookings online.", "Generate reservations without manual entry.", "Growth"},
                {"CLIENT_PORTAL", "Client Portal", "Give customers access to contracts and invoices.", "Reduce support work with self-service access.", "Growth"},
                {"GPS_TRACKING", "GPS Tracking", "Track connected rental vehicles.", "See live fleet positions and status.", "GPS"},
                {"ADVANCED_GPS", "Advanced GPS", "Use advanced GPS alerts and trip analytics.", "Monitor geofences, trips, and operational risk.", "GPS"},
                {"API_ACCESS", "API Access", "Integrate external systems through APIs.", "Connect accounting, booking, and partner systems.", "Platform"},
                {"CUSTOM_DOMAIN", "Custom Domain", "Use an agency-owned domain.", "Deliver a branded customer experience.", "Branding"},
                {"WHITE_LABEL", "White Label", "Remove platform branding.", "Operate the customer experience under your own brand.", "Branding"},
                {"REPORTS_BASIC", "Basic Reports", "Access standard operational reports.", "Review core rental activity and revenue.", "Analytics"},
                {"REPORTS_ADVANCED", "Advanced Reports", "Access advanced analytics and trends.", "Understand growth, profitability, and utilization.", "Analytics"},
                {"MULTI_EMPLOYEE", "Employee Management", "Manage multiple employee accounts.", "Delegate work with controlled staff access.", "Operations"},
                {"CUSTOM_BRANDING", "Custom Branding", "Configure agency colors and logo.", "Present a consistent agency identity.", "Branding"},
                {"EMAIL_AUTOMATION", "Email Automation", "Automate customer email communication.", "Send confirmations, reminders, and follow-ups.", "Automation"},
                {"SMS_AUTOMATION", "SMS Automation", "Automate SMS communication.", "Reach customers with time-sensitive updates.", "Automation"},
                {"WHATSAPP_NOTIFICATIONS", "WhatsApp Notifications", "Send automated WhatsApp notifications.", "Communicate through a preferred customer channel.", "Automation"},
                {"EXPORT_PDF", "PDF Export", "Export contracts and reports as PDF.", "Create portable professional documents.", "Exports"},
                {"EXPORT_EXCEL", "Excel Export", "Export operational data to Excel.", "Analyze and share structured business data.", "Exports"},
                {"PAYMENTS", "Payment Tracking", "Track rental business payments and revenue.", "View payments, deposits, and financial summaries.", "Finance"},
                {"PAYMENT_STATS", "Payment Statistics", "Access payment statistics and revenue charts.", "Analyse monthly revenue and payment breakdowns.", "Finance"},
                {"INVOICE_GENERATION", "Invoice Generation", "Generate customer invoices.", "Keep billing connected to contracts and payments.", "Billing"},
                {"PAYMENT_GATEWAY", "Payment Gateway", "Collect online customer payments.", "Accept and reconcile digital payments.", "Billing"},
                {"CONTRACT_TEMPLATES", "Contract Templates", "Use ready professional contract templates.", "Choose polished contract layouts from the template gallery.", "Contracts"},
                {"CUSTOM_CONTRACT_TEMPLATES", "Custom Contract Templates", "Upload scanned agency contract paper.", "Generate PDFs that match the agency's original paper contract.", "Contracts"},
                {"CONTRACT_TEMPLATE_MAPPING", "Contract Template Mapping", "Map website data onto contract templates.", "Place client, vehicle, pricing, and signature fields precisely.", "Contracts"},
                {"CONTRACT_CONDITIONS_PAGE", "Contract Conditions Page", "Use uploaded or agency-specific conditions pages.", "Attach legal terms and conditions to generated contracts.", "Contracts"},
                {"PREMIUM_CONTRACT_TEMPLATES", "Premium Contract Templates", "Unlock premium and luxury contract layouts.", "Use richer contract designs for premium rental workflows.", "Contracts"},
                {"ENTERPRISE_CONTRACT_TEMPLATES", "Enterprise Contract Templates", "Unlock enterprise contract templates.", "Use advanced templates for large multi-branch agencies.", "Contracts"},
                {"SUPPORT_PRIORITY", "Priority Support", "Receive priority support service.", "Reduce operational downtime with faster assistance.", "Support"}
        };

        for (String[] definition : definitions) {
            if (!featureRepository.existsByCode(definition[0])) {
                featureRepository.save(FeatureDefinition.builder()
                        .code(definition[0])
                        .name(definition[1])
                        .description(definition[2])
                        .benefits(definition[3])
                        .category(definition[4])
                        .active(true)
                        .build());
            }
        }

        Set<String> trial = setOf(
                "VEHICLE_MANAGEMENT", "CLIENT_MANAGEMENT", "RESERVATION_MANAGEMENT",
                "CONTRACT_MANAGEMENT", "EXPORT_PDF", "REPORTS_BASIC", "CONTRACT_TEMPLATES"
        );
        Set<String> basic = union(trial, setOf("GPS_TRACKING", "INVOICE_GENERATION", "MULTI_EMPLOYEE", "CONTRACT_CONDITIONS_PAGE", "PAYMENTS"));
        Set<String> standard = union(basic, setOf(
                "DIGITAL_SIGNATURE", "QR_SIGNATURE", "CLIENT_PORTAL", "REPORTS_ADVANCED",
                "CUSTOM_BRANDING", "EMAIL_AUTOMATION", "EXPORT_EXCEL",
                "CUSTOM_CONTRACT_TEMPLATES", "CONTRACT_TEMPLATE_MAPPING", "PAYMENT_STATS"
        ));
        Set<String> premium = Arrays.stream(definitions).map(row -> row[0])
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        premium.remove("ENTERPRISE_CONTRACT_TEMPLATES");
        premium.add("PREMIUM_CONTRACT_TEMPLATES");
        Set<String> enterprise = Arrays.stream(definitions).map(row -> row[0])
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        assignPlanFeatures("trial", trial, definitions);
        assignPlanFeatures("basic", basic, definitions);
        assignPlanFeatures("standard", standard, definitions);
        assignPlanFeatures("premium", premium, definitions);
        assignPlanFeatures("enterprise", enterprise, definitions);
    }

    private void assignPlanFeatures(String planCode, Set<String> enabledFeatures, String[][] definitions) {
        SubscriptionPlan plan = planRepository.findByCode(planCode).orElse(null);
        if (plan == null) return;
        for (String[] definition : definitions) {
            PlanFeature planFeature = planFeatureRepository.findByPlanIdAndFeatureCode(plan.getId(), definition[0])
                    .orElseGet(() -> PlanFeature.builder().plan(plan).featureCode(definition[0]).build());
            planFeature.setEnabled(enabledFeatures.contains(definition[0]));
            planFeatureRepository.save(planFeature);
        }
    }

    private void seedPromotionsAndBilling() {
        savePromo("WELCOME20", "PERCENTAGE", new BigDecimal("20"), "trial,basic,standard,premium");
        savePromo("RENTCAR50", "PERCENTAGE", new BigDecimal("50"), "basic,standard,premium");
        savePromo("INNOVAX2026", "FIXED", new BigDecimal("200"), "standard,premium");
        saveBenefitPromo("2MONTHSFREE", "Two Months Free", "FREE_MONTHS", 2, null, null,
                "basic,standard,premium");
        saveBenefitPromo("FREEGPS", "Free GPS Module", "FREE_FEATURE", 1, "GPS_TRACKING", null,
                "trial,basic,standard");
        saveBenefitPromo("FREESIGN", "Free Online Signature", "FREE_FEATURE", 1, "DIGITAL_SIGNATURE", null,
                "trial,basic");
        saveBenefitPromo("PREMIUM30", "Premium For 30 Days", "PLAN_TRIAL", 1, null, "premium",
                "trial,basic,standard");

        for (String provider : new String[]{"CMI_MOROCCO", "STRIPE", "PAYPAL", "WHOP"}) {
            if (gatewayConfigRepository.findByProvider(provider).isEmpty()) {
                gatewayConfigRepository.save(PaymentGatewayConfig.builder()
                        .provider(provider)
                        .enabled(false)
                        .mode("TEST")
                        .build());
            }
        }

        if (affiliateRuleRepository.count() == 0) {
            affiliateRuleRepository.save(AffiliateRule.builder()
                    .name("Default Agency Referral")
                    .rewardType("FREE_MONTH")
                    .freeMonths(1)
                    .commissionPercent(new BigDecimal("10"))
                    .active(true)
                    .build());
        }

        if (knowledgeArticleRepository.count() == 0) {
            knowledgeArticleRepository.save(KnowledgeArticle.builder()
                    .slug("first-reservation")
                    .title("Create your first reservation")
                    .category("Getting Started")
                    .summary("Connect a client and an available vehicle with conflict-safe rental dates.")
                    .content("Open Reservations, choose a client and an available vehicle, then set pickup and return dates and times. RentCar checks availability before saving and keeps the vehicle, contract, invoice and payment workflow connected.")
                    .published(true)
                    .build());
            knowledgeArticleRepository.save(KnowledgeArticle.builder()
                    .slug("contract-signing")
                    .title("Send a contract for digital signature")
                    .category("Contracts")
                    .summary("Generate a secure public signing link and QR code for the customer.")
                    .content("Generate the contract from a reservation, add the agency signature, then create the signing link. The customer can review and sign without an account. The final PDF is regenerated after both signatures are present.")
                    .published(true)
                    .build());
            knowledgeArticleRepository.save(KnowledgeArticle.builder()
                    .slug("record-payment")
                    .title("Record full or partial payments")
                    .category("Payments")
                    .summary("Keep balances and related statuses synchronized from one transaction.")
                    .content("Record the payment from Payments or the related contract. RentCar recalculates the paid and remaining amounts, then synchronizes the invoice, reservation, contract, dashboard and reports.")
                    .published(true)
                    .build());
        }

        if (legalDocumentRepository.count() == 0) {
            legalDocumentRepository.save(LegalDocument.builder()
                    .code("TERMS_OF_SERVICE")
                    .title("Terms of Service")
                    .version("2026.1")
                    .content("These terms govern authorized business use of the RentCar platform. Agencies remain responsible for the accuracy of rental, client, vehicle and payment information entered into their workspace.")
                    .active(true)
                    .build());
            legalDocumentRepository.save(LegalDocument.builder()
                    .code("PRIVACY_POLICY")
                    .title("Privacy Policy")
                    .version("2026.1")
                    .content("RentCar processes account and operational data to provide the service, secure tenant workspaces, maintain audit history and support legal obligations. Tenant data remains logically isolated by agency.")
                    .active(true)
                    .build());
            legalDocumentRepository.save(LegalDocument.builder()
                    .code("SUBSCRIPTION_POLICY")
                    .title("Subscription Policy")
                    .version("2026.1")
                    .content("Plan features and limits are controlled by the active subscription. Expired agencies retain read-only access to existing records and must renew before creating or changing operational data.")
                    .active(true)
                    .build());
        }
    }

    private void savePromo(String code, String type, BigDecimal value, String plans) {
        if (promoCodeRepository.findByCode(code).isPresent()) return;
        promoCodeRepository.save(PromoCode.builder()
                .code(code)
                .discountType(type)
                .discountValue(value)
                .maxUses(1000)
                .usedCount(0)
                .validFrom(LocalDate.now())
                .validTo(LocalDate.now().plusYears(1))
                .applicablePlans(plans)
                .isActive(true)
                .build());
    }

    private void saveBenefitPromo(
            String code,
            String name,
            String promotionType,
            Integer freeMonths,
            String freeFeatureCode,
            String trialPlanCode,
            String plans) {
        if (promoCodeRepository.findByCode(code).isPresent()) return;
        promoCodeRepository.save(PromoCode.builder()
                .code(code)
                .promotionName(name)
                .promotionType(promotionType)
                .discountType("FIXED")
                .discountValue(BigDecimal.ZERO)
                .freeMonths(freeMonths)
                .freeFeatureCode(freeFeatureCode)
                .trialPlanCode(trialPlanCode)
                .maxUses(1000)
                .usedCount(0)
                .validFrom(LocalDate.now())
                .validTo(LocalDate.now().plusYears(1))
                .applicablePlans(plans)
                .isActive(true)
                .build());
    }

    private Set<String> setOf(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }

    @SafeVarargs
    private final Set<String> union(Set<String>... sets) {
        Set<String> result = new LinkedHashSet<>();
        for (Set<String> set : sets) result.addAll(set);
        return result;
    }
}
