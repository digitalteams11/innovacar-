package com.carrental;

import com.carrental.entity.*;
import com.carrental.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

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
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
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

        // 4. ALWAYS ensure SUPER_ADMIN user exists
        boolean superAdminExists = userRepository.findAll().stream()
                .anyMatch(u -> "superadmin@innovax.tech".equals(u.getEmail()) && u.getRole() == Role.SUPER_ADMIN);

        if (!superAdminExists) {
            userRepository.save(User.builder()
                    .email("superadmin@innovax.tech")
                    .password(passwordEncoder.encode("superadmin123"))
                    .role(Role.SUPER_ADMIN)
                    .tenant(systemTenant)
                    .build());
            log.info("SUPER_ADMIN seeded: email=superadmin@innovax.tech, password=superadmin123");
        } else {
            log.info("SUPER_ADMIN already exists");
        }

        // 5. Seed default demo tenant + admin (only if no other real tenants exist)
        if (tenantRepository.count() <= 1) {
            Tenant demoTenant = tenantRepository.save(Tenant.builder()
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
                    .build());

            userRepository.save(User.builder()
                    .email("admin@test.com")
                    .password(passwordEncoder.encode("admin123"))
                    .role(Role.ADMIN)
                    .tenant(demoTenant)
                    .build());

            log.info("Default demo data seeded: email=admin@test.com, password=admin123");
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
                {"MULTI_BRANCH", "Multi Branch", "Operate multiple agency locations.", "Coordinate inventory and teams across branches.", "Operations"},
                {"CUSTOM_BRANDING", "Custom Branding", "Configure agency colors and logo.", "Present a consistent agency identity.", "Branding"},
                {"EMAIL_AUTOMATION", "Email Automation", "Automate customer email communication.", "Send confirmations, reminders, and follow-ups.", "Automation"},
                {"SMS_AUTOMATION", "SMS Automation", "Automate SMS communication.", "Reach customers with time-sensitive updates.", "Automation"},
                {"WHATSAPP_NOTIFICATIONS", "WhatsApp Notifications", "Send automated WhatsApp notifications.", "Communicate through a preferred customer channel.", "Automation"},
                {"EXPORT_PDF", "PDF Export", "Export contracts and reports as PDF.", "Create portable professional documents.", "Exports"},
                {"EXPORT_EXCEL", "Excel Export", "Export operational data to Excel.", "Analyze and share structured business data.", "Exports"},
                {"INVOICE_GENERATION", "Invoice Generation", "Generate customer invoices.", "Keep billing connected to contracts and payments.", "Billing"},
                {"PAYMENT_GATEWAY", "Payment Gateway", "Collect online customer payments.", "Accept and reconcile digital payments.", "Billing"},
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
                "CONTRACT_MANAGEMENT", "EXPORT_PDF", "REPORTS_BASIC"
        );
        Set<String> basic = union(trial, setOf("GPS_TRACKING", "INVOICE_GENERATION", "MULTI_EMPLOYEE"));
        Set<String> standard = union(basic, setOf(
                "DIGITAL_SIGNATURE", "QR_SIGNATURE", "CLIENT_PORTAL", "REPORTS_ADVANCED",
                "CUSTOM_BRANDING", "EMAIL_AUTOMATION", "EXPORT_EXCEL"
        ));
        Set<String> premium = Arrays.stream(definitions).map(row -> row[0])
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        assignPlanFeatures("trial", trial, definitions);
        assignPlanFeatures("basic", basic, definitions);
        assignPlanFeatures("standard", standard, definitions);
        assignPlanFeatures("premium", premium, definitions);
        assignPlanFeatures("enterprise", premium, definitions);
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
