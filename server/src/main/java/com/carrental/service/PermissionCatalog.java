package com.carrental.service;

import com.carrental.entity.PermissionRiskLevel;

import java.util.List;

import static com.carrental.entity.PermissionRiskLevel.*;

/**
 * The single backend source of truth for every permission code in the system
 * (spec: "Backend is the source of truth", "Do not define permissions only in
 * the frontend"). {@link PermissionSyncService} reconciles this list against
 * the {@code permission_definitions} table on every startup: new entries here
 * are inserted automatically (a brand-new page/feature's permission appears
 * in the Role Permissions UI with zero manual DB work), metadata on existing
 * rows is refreshed, and any DB row whose code has been removed from this
 * list is marked inactive (never deleted — existing role/user grants for it
 * are preserved but stop granting access, per PermissionSyncService/
 * RolePermissionService#hasResolvedPermission).
 *
 * <p>To add a new page/feature's permission: add one {@link Entry} here. No
 * other file needs to change for it to show up in the catalog API, the role
 * editor UI, and enforcement — see {@code RolePermissionController#catalog()}.
 *
 * <p>Legacy/alias codes (the original {@code VIEW_VEHICLES}-style names this
 * codebase shipped with before the {@code VEHICLE_VIEW}-style modern names)
 * are also registered here, marked {@code deprecated=true} with
 * {@code aliasOf} pointing at the modern code — they stay active (existing
 * role assignments made under the old name must keep working) but are
 * filtered out of the new role-editor UI, which only ever shows/edits the
 * modern set.
 */
public final class PermissionCatalog {

    public record Entry(
            String code, String module, String resource, String action,
            PermissionRiskLevel riskLevel, List<String> dependencies,
            String labelKey, String descriptionKey, int sortOrder,
            boolean deprecated, String aliasOf) {

        static Entry of(String code, String module, String resource, String action,
                         PermissionRiskLevel risk, List<String> deps, int sortOrder) {
            // Keyed on resource+action, NOT module+action — a module can contain more than
            // one resource with the same action name (e.g. FLEET has both VEHICLE and, via
            // MAINTENANCE, MAINTENANCE; PAYMENTS has both PAYMENT and INVOICE), and
            // module+action alone would collide (PAYMENT_VIEW and INVOICE_VIEW would both
            // resolve to "permissions.payments.view"). resource+action is unique per code.
            String labelKey = "permissions." + resource.toLowerCase() + "." + action.toLowerCase();
            // Derived straight from the code itself (already globally unique) rather than
            // resource+action, so this can never collide the way a resource/action-derived
            // key could for two codes that happen to share both.
            String descriptionKey = "permissions.descriptions." + camelFromCode(code);
            return new Entry(code, module, resource, action, risk, deps, labelKey, descriptionKey, sortOrder, false, null);
        }

        static Entry alias(String legacyCode, String aliasOf, String module) {
            return new Entry(legacyCode, module, null, null, NORMAL, List.of(),
                    null, null, 9999, true, aliasOf);
        }

        /** VEHICLE_STATUS_UPDATE -> vehicleStatusUpdate */
        private static String camelFromCode(String code) {
            StringBuilder sb = new StringBuilder();
            for (String part : code.toLowerCase().split("_")) {
                if (part.isEmpty()) continue;
                sb.append(sb.isEmpty() ? part : Character.toUpperCase(part.charAt(0)) + part.substring(1));
            }
            return sb.toString();
        }
    }

    private static int n = 0;
    private static int next() { return (n += 10); }

    public static final List<Entry> ENTRIES = List.of(
            Entry.of("DASHBOARD_VIEW", "DASHBOARD", "DASHBOARD", "VIEW", NORMAL, List.of(), next()),

            Entry.of("VEHICLE_VIEW", "FLEET", "VEHICLE", "VIEW", NORMAL, List.of(), next()),
            Entry.of("VEHICLE_CREATE", "FLEET", "VEHICLE", "CREATE", NORMAL, List.of("VEHICLE_VIEW"), next()),
            Entry.of("VEHICLE_UPDATE", "FLEET", "VEHICLE", "UPDATE", ELEVATED, List.of("VEHICLE_VIEW"), next()),
            Entry.of("VEHICLE_DELETE", "FLEET", "VEHICLE", "DELETE", DANGEROUS, List.of("VEHICLE_VIEW"), next()),
            Entry.of("VEHICLE_ARCHIVE", "FLEET", "VEHICLE", "ARCHIVE", ELEVATED, List.of("VEHICLE_VIEW"), next()),
            Entry.of("VEHICLE_STATUS_UPDATE", "FLEET", "VEHICLE", "STATUS_UPDATE", NORMAL, List.of("VEHICLE_VIEW"), next()),

            Entry.of("MAINTENANCE_VIEW", "MAINTENANCE", "MAINTENANCE", "VIEW", NORMAL, List.of(), next()),
            Entry.of("MAINTENANCE_CREATE", "MAINTENANCE", "MAINTENANCE", "CREATE", NORMAL, List.of("MAINTENANCE_VIEW"), next()),
            Entry.of("MAINTENANCE_UPDATE", "MAINTENANCE", "MAINTENANCE", "UPDATE", ELEVATED, List.of("MAINTENANCE_VIEW"), next()),
            Entry.of("MAINTENANCE_COMPLETE", "MAINTENANCE", "MAINTENANCE", "COMPLETE", NORMAL, List.of("MAINTENANCE_VIEW"), next()),
            Entry.of("VEHICLE_MAINTENANCE_MANAGE", "MAINTENANCE", "MAINTENANCE", "MANAGE", ELEVATED, List.of("VEHICLE_VIEW"), next()),
            Entry.of("MAINTENANCE_DELETE", "MAINTENANCE", "MAINTENANCE", "DELETE", DANGEROUS, List.of("MAINTENANCE_VIEW"), next()),

            Entry.of("CLIENT_VIEW", "CLIENTS", "CLIENT", "VIEW", NORMAL, List.of(), next()),
            Entry.of("CLIENT_CREATE", "CLIENTS", "CLIENT", "CREATE", NORMAL, List.of("CLIENT_VIEW"), next()),
            Entry.of("CLIENT_UPDATE", "CLIENTS", "CLIENT", "UPDATE", ELEVATED, List.of("CLIENT_VIEW"), next()),
            Entry.of("CLIENT_DELETE", "CLIENTS", "CLIENT", "DELETE", DANGEROUS, List.of("CLIENT_VIEW"), next()),

            Entry.of("RESERVATION_VIEW", "RESERVATIONS", "RESERVATION", "VIEW", NORMAL, List.of(), next()),
            Entry.of("RESERVATION_CREATE", "RESERVATIONS", "RESERVATION", "CREATE", NORMAL, List.of("RESERVATION_VIEW"), next()),
            Entry.of("RESERVATION_UPDATE", "RESERVATIONS", "RESERVATION", "UPDATE", ELEVATED, List.of("RESERVATION_VIEW"), next()),
            Entry.of("RESERVATION_CONFIRM", "RESERVATIONS", "RESERVATION", "CONFIRM", NORMAL, List.of("RESERVATION_VIEW"), next()),
            Entry.of("RESERVATION_CANCEL", "RESERVATIONS", "RESERVATION", "CANCEL", ELEVATED, List.of("RESERVATION_VIEW"), next()),
            Entry.of("RESERVATION_DELETE", "RESERVATIONS", "RESERVATION", "DELETE", DANGEROUS, List.of("RESERVATION_VIEW"), next()),

            Entry.of("CONTRACT_VIEW", "CONTRACTS", "CONTRACT", "VIEW", NORMAL, List.of(), next()),
            Entry.of("CONTRACT_CREATE", "CONTRACTS", "CONTRACT", "CREATE", NORMAL, List.of("CONTRACT_VIEW"), next()),
            Entry.of("CONTRACT_UPDATE", "CONTRACTS", "CONTRACT", "UPDATE", ELEVATED, List.of("CONTRACT_VIEW"), next()),
            Entry.of("CONTRACT_SIGN", "CONTRACTS", "CONTRACT", "SIGN", NORMAL, List.of("CONTRACT_VIEW"), next()),
            Entry.of("CONTRACT_SEND_EMAIL", "CONTRACTS", "CONTRACT", "SEND_EMAIL", NORMAL, List.of("CONTRACT_VIEW"), next()),
            Entry.of("CONTRACT_DOWNLOAD_PDF", "CONTRACTS", "CONTRACT", "DOWNLOAD_PDF", NORMAL, List.of("CONTRACT_VIEW"), next()),
            Entry.of("CONTRACT_RESTORE", "CONTRACTS", "CONTRACT", "RESTORE", ELEVATED, List.of("CONTRACT_VIEW"), next()),
            Entry.of("CONTRACT_DELETE", "CONTRACTS", "CONTRACT", "DELETE", DANGEROUS, List.of("CONTRACT_VIEW"), next()),
            Entry.of("CONTRACT_PURGE", "CONTRACTS", "CONTRACT", "PURGE", DANGEROUS, List.of("CONTRACT_VIEW"), next()),

            Entry.of("ADDITIONAL_DRIVER_VIEW", "CONTRACTS", "ADDITIONAL_DRIVER", "VIEW", NORMAL, List.of("CONTRACT_VIEW"), next()),
            Entry.of("ADDITIONAL_DRIVER_CREATE", "CONTRACTS", "ADDITIONAL_DRIVER", "CREATE", NORMAL, List.of("CONTRACT_VIEW"), next()),
            Entry.of("ADDITIONAL_DRIVER_UPDATE", "CONTRACTS", "ADDITIONAL_DRIVER", "UPDATE", ELEVATED, List.of("CONTRACT_VIEW"), next()),
            Entry.of("ADDITIONAL_DRIVER_DELETE", "CONTRACTS", "ADDITIONAL_DRIVER", "DELETE", DANGEROUS, List.of("CONTRACT_VIEW"), next()),
            Entry.of("ADDITIONAL_DRIVER_SIGNATURE_SEND", "CONTRACTS", "ADDITIONAL_DRIVER", "SIGNATURE_SEND", NORMAL, List.of("CONTRACT_VIEW"), next()),
            Entry.of("ADDITIONAL_DRIVER_SIGNATURE_REVOKE", "CONTRACTS", "ADDITIONAL_DRIVER", "SIGNATURE_REVOKE", ELEVATED, List.of("CONTRACT_VIEW"), next()),
            Entry.of("ADDITIONAL_DRIVER_SIGNATURE_VIEW", "CONTRACTS", "ADDITIONAL_DRIVER", "SIGNATURE_VIEW", NORMAL, List.of("CONTRACT_VIEW"), next()),

            Entry.of("PAYMENT_VIEW", "PAYMENTS", "PAYMENT", "VIEW", NORMAL, List.of(), next()),
            Entry.of("PAYMENT_CREATE", "PAYMENTS", "PAYMENT", "CREATE", NORMAL, List.of("PAYMENT_VIEW"), next()),
            Entry.of("PAYMENT_UPDATE", "PAYMENTS", "PAYMENT", "UPDATE", ELEVATED, List.of("PAYMENT_VIEW"), next()),
            Entry.of("PAYMENT_REFUND", "PAYMENTS", "PAYMENT", "REFUND", DANGEROUS, List.of("PAYMENT_VIEW"), next()),
            Entry.of("INVOICE_VIEW", "PAYMENTS", "INVOICE", "VIEW", NORMAL, List.of(), next()),
            Entry.of("INVOICE_EXPORT", "PAYMENTS", "INVOICE", "EXPORT", NORMAL, List.of("INVOICE_VIEW"), next()),
            Entry.of("INVOICE_PDF_DOWNLOAD", "PAYMENTS", "INVOICE", "PDF_DOWNLOAD", NORMAL, List.of("INVOICE_VIEW"), next()),
            Entry.of("INVOICE_PRINT", "PAYMENTS", "INVOICE", "PRINT", NORMAL, List.of("INVOICE_VIEW"), next()),
            Entry.of("INVOICE_EMAIL_SEND", "PAYMENTS", "INVOICE", "EMAIL_SEND", NORMAL, List.of("INVOICE_VIEW"), next()),

            Entry.of("REPORT_VIEW", "REPORTS", "REPORT", "VIEW", NORMAL, List.of(), next()),
            Entry.of("REPORT_GENERATE", "REPORTS", "REPORT", "GENERATE", NORMAL, List.of("REPORT_VIEW"), next()),
            Entry.of("REPORT_DOWNLOAD", "REPORTS", "REPORT", "DOWNLOAD", NORMAL, List.of("REPORT_VIEW"), next()),
            Entry.of("REPORT_SEND_EMAIL", "REPORTS", "REPORT", "SEND_EMAIL", NORMAL, List.of("REPORT_VIEW"), next()),
            Entry.of("REPORT_FINANCIAL", "REPORTS", "REPORT", "FINANCIAL", ELEVATED, List.of("REPORT_VIEW"), next()),
            Entry.of("REPORT_ADVANCED", "REPORTS", "REPORT", "ADVANCED", ELEVATED, List.of("REPORT_VIEW"), next()),

            Entry.of("GPS_VIEW", "GPS", "GPS", "VIEW", NORMAL, List.of(), next()),
            Entry.of("GPS_ALERTS_VIEW", "GPS", "GPS_ALERT", "VIEW", NORMAL, List.of("GPS_VIEW"), next()),
            Entry.of("GPS_ALERTS_MANAGE", "GPS", "GPS_ALERT", "MANAGE", ELEVATED, List.of("GPS_VIEW"), next()),
            Entry.of("GPS_SETTINGS", "GPS", "GPS", "MANAGE", ELEVATED, List.of("GPS_VIEW"), next()),

            Entry.of("EMPLOYEE_VIEW", "EMPLOYEES", "EMPLOYEE", "VIEW", NORMAL, List.of(), next()),
            Entry.of("EMPLOYEE_CREATE", "EMPLOYEES", "EMPLOYEE", "CREATE", ELEVATED, List.of("EMPLOYEE_VIEW"), next()),
            Entry.of("EMPLOYEE_UPDATE", "EMPLOYEES", "EMPLOYEE", "UPDATE", ELEVATED, List.of("EMPLOYEE_VIEW"), next()),
            Entry.of("EMPLOYEE_DELETE", "EMPLOYEES", "EMPLOYEE", "DELETE", DANGEROUS, List.of("EMPLOYEE_VIEW"), next()),
            Entry.of("EMPLOYEE_RESET_PASSWORD", "EMPLOYEES", "EMPLOYEE", "RESET_PASSWORD", DANGEROUS, List.of("EMPLOYEE_VIEW"), next()),

            Entry.of("AGENCY_SETTINGS_VIEW", "SETTINGS", "SETTINGS", "VIEW", NORMAL, List.of(), next()),
            Entry.of("AGENCY_SETTINGS_UPDATE", "SETTINGS", "SETTINGS", "UPDATE", ELEVATED, List.of("AGENCY_SETTINGS_VIEW"), next()),

            Entry.of("ROLE_ACCESS_MANAGE", "SECURITY", "ROLE", "MANAGE", DANGEROUS, List.of("EMPLOYEE_VIEW"), next()),
            Entry.of("SECURITY_VIEW", "SECURITY", "SECURITY", "VIEW", NORMAL, List.of(), next()),
            Entry.of("SECURITY_MANAGE", "SECURITY", "SECURITY", "MANAGE", DANGEROUS, List.of("SECURITY_VIEW"), next()),

            // ── Legacy aliases — deprecated, hidden from the new UI, still active ──
            Entry.alias("VIEW_VEHICLES", "VEHICLE_VIEW", "FLEET"),
            Entry.alias("CREATE_VEHICLE", "VEHICLE_CREATE", "FLEET"),
            Entry.alias("EDIT_VEHICLE", "VEHICLE_UPDATE", "FLEET"),
            Entry.alias("DELETE_VEHICLE", "VEHICLE_DELETE", "FLEET"),
            Entry.alias("VIEW_CLIENTS", "CLIENT_VIEW", "CLIENTS"),
            Entry.alias("CREATE_CLIENT", "CLIENT_CREATE", "CLIENTS"),
            Entry.alias("EDIT_CLIENT", "CLIENT_UPDATE", "CLIENTS"),
            Entry.alias("DELETE_CLIENT", "CLIENT_DELETE", "CLIENTS"),
            Entry.alias("VIEW_CLIENT_DOCUMENTS_FULL", "CLIENT_VIEW", "CLIENTS"),
            Entry.alias("VIEW_RESERVATIONS", "RESERVATION_VIEW", "RESERVATIONS"),
            Entry.alias("CREATE_RESERVATION", "RESERVATION_CREATE", "RESERVATIONS"),
            Entry.alias("EDIT_RESERVATION", "RESERVATION_UPDATE", "RESERVATIONS"),
            Entry.alias("CANCEL_RESERVATION", "RESERVATION_CANCEL", "RESERVATIONS"),
            Entry.alias("VIEW_CONTRACTS", "CONTRACT_VIEW", "CONTRACTS"),
            Entry.alias("CREATE_CONTRACT", "CONTRACT_CREATE", "CONTRACTS"),
            Entry.alias("EDIT_CONTRACT", "CONTRACT_UPDATE", "CONTRACTS"),
            Entry.alias("DELETE_CONTRACT", "CONTRACT_DELETE", "CONTRACTS"),
            Entry.alias("SIGN_CONTRACT", "CONTRACT_SIGN", "CONTRACTS"),
            Entry.alias("COMPLETE_CONTRACT", "CONTRACT_UPDATE", "CONTRACTS"),
            Entry.alias("CONTRACT_EXPORT_PDF", "CONTRACT_DOWNLOAD_PDF", "CONTRACTS"),
            Entry.alias("CONTRACT_QR_SIGNATURE", "CONTRACT_SIGN", "CONTRACTS"),
            Entry.alias("CONTRACT_INSPECTION_MEDIA", "CONTRACT_UPDATE", "CONTRACTS"),
            Entry.alias("VIEW_PAYMENTS", "PAYMENT_VIEW", "PAYMENTS"),
            Entry.alias("RECORD_PAYMENT", "PAYMENT_CREATE", "PAYMENTS"),
            Entry.alias("VIEW_DEPOSITS", "PAYMENT_VIEW", "PAYMENTS"),
            Entry.alias("MANAGE_DEPOSITS", "PAYMENT_UPDATE", "PAYMENTS"),
            Entry.alias("VIEW_INVOICES", "INVOICE_VIEW", "PAYMENTS"),
            Entry.alias("MANAGE_INVOICES", "INVOICE_EXPORT", "PAYMENTS"),
            Entry.alias("PAYMENT_STATS_VIEW", "REPORT_FINANCIAL", "PAYMENTS"),
            Entry.alias("VIEW_REPORTS", "REPORT_VIEW", "REPORTS"),
            Entry.alias("GPS_ACCESS", "GPS_VIEW", "GPS"),
            Entry.alias("MANAGE_GPS", "GPS_SETTINGS", "GPS"),
            Entry.alias("GPS_SETTINGS_VIEW", "GPS_VIEW", "GPS"),
            Entry.alias("GPS_SETTINGS_UPDATE", "GPS_SETTINGS", "GPS"),
            Entry.alias("GPS_CREDENTIALS_DELETE", "GPS_SETTINGS", "GPS"),
            Entry.alias("GPS_TEST_CONNECTION", "GPS_VIEW", "GPS"),
            Entry.alias("GPS_SYNC_DEVICES", "GPS_SETTINGS", "GPS"),
            Entry.alias("VIEW_MAINTENANCE", "MAINTENANCE_VIEW", "MAINTENANCE"),
            Entry.alias("MANAGE_MAINTENANCE", "VEHICLE_MAINTENANCE_MANAGE", "MAINTENANCE"),
            Entry.alias("MANAGE_EMPLOYEES", "EMPLOYEE_CREATE", "EMPLOYEES"),
            Entry.alias("MANAGE_SETTINGS", "AGENCY_SETTINGS_UPDATE", "SETTINGS")
    );

    /** Codes considered inherently dangerous — used by the sync's new-permission default policy (spec section 23). */
    public static boolean isDangerous(PermissionRiskLevel level) {
        return level == DANGEROUS;
    }

    private PermissionCatalog() {}
}
