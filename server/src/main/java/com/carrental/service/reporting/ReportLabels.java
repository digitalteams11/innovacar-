package com.carrental.service.reporting;

import java.util.Locale;
import java.util.Map;

/** Small label lookup for the ~40 strings that appear on a report PDF — EN/FR/AR. Not a full i18n framework, deliberately. */
public final class ReportLabels {

    private ReportLabels() {}

    private static final Map<String, Map<String, String>> LABELS = Map.ofEntries(
            entry("title.monthly", "Rapport mensuel", "Monthly Report", "تقرير شهري"),
            entry("title.yearly", "Rapport annuel", "Annual Report", "تقرير سنوي"),
            entry("cover.generated", "Genere le", "Generated on", "تاريخ الإنشاء"),
            entry("cover.period", "Periode", "Period", "الفترة"),
            entry("section.executive_summary", "RESUME EXECUTIF", "EXECUTIVE SUMMARY", "الملخص التنفيذي"),
            entry("section.ai_label", "Analyse automatisee", "Automated analysis", "تحليل آلي"),
            entry("section.financial_overview", "APERCU FINANCIER", "FINANCIAL OVERVIEW", "نظرة عامة مالية"),
            entry("section.profit_loss", "PROFIT / PERTE", "PROFIT / LOSS", "الربح / الخسارة"),
            entry("section.revenue_expense", "REVENUS ET DEPENSES", "REVENUE AND EXPENSES", "الإيرادات والمصروفات"),
            entry("section.reservations_contracts", "RESERVATIONS ET CONTRATS", "RESERVATIONS AND CONTRACTS", "الحجوزات والعقود"),
            entry("section.fleet", "ETAT DE LA FLOTTE", "FLEET STATUS", "حالة الأسطول"),
            entry("section.top_vehicles", "VEHICULES LES PLUS PERFORMANTS", "TOP-PERFORMING VEHICLES", "المركبات الأفضل أداء"),
            entry("section.low_vehicles", "VEHICULES A SURVEILLER", "VEHICLES NEEDING ATTENTION", "مركبات تحتاج إلى انتباه"),
            entry("section.maintenance", "APERCU MAINTENANCE", "MAINTENANCE OVERVIEW", "نظرة عامة على الصيانة"),
            entry("section.outstanding", "SOLDES IMPAYES", "OUTSTANDING BALANCES", "الأرصدة المستحقة"),
            entry("section.comparison", "COMPARAISON DE PERIODE", "PERIOD COMPARISON", "مقارنة الفترة"),
            entry("section.recommendations", "RECOMMANDATIONS", "RECOMMENDATIONS", "التوصيات"),
            entry("field.gross_revenue", "Revenu brut", "Gross revenue", "الإيراد الإجمالي"),
            entry("field.net_revenue", "Revenu net", "Net revenue", "الإيراد الصافي"),
            entry("field.expenses", "Depenses", "Expenses", "المصروفات"),
            entry("field.profit", "Profit", "Profit", "الربح"),
            entry("field.loss", "Perte", "Loss", "الخسارة"),
            entry("field.outstanding", "Solde impaye", "Outstanding balance", "الرصيد المستحق"),
            entry("field.overdue", "Impayes en retard", "Overdue payments", "المدفوعات المتأخرة"),
            entry("field.refunds", "Remboursements", "Refunds", "المبالغ المستردة"),
            entry("field.avg_revenue_rental", "Revenu moyen / location", "Avg revenue per rental", "متوسط الإيراد لكل إيجار"),
            entry("field.total_reservations", "Reservations totales", "Total reservations", "إجمالي الحجوزات"),
            entry("field.confirmed", "Confirmees", "Confirmed", "مؤكدة"),
            entry("field.cancelled", "Annulees", "Cancelled", "ملغاة"),
            entry("field.active_contracts", "Contrats actifs", "Active contracts", "العقود النشطة"),
            entry("field.completed_contracts", "Contrats termines", "Completed contracts", "العقود المكتملة"),
            entry("field.avg_duration", "Duree moyenne (jours)", "Avg duration (days)", "متوسط المدة (أيام)"),
            entry("field.occupancy", "Taux d'occupation", "Occupancy rate", "معدل الإشغال"),
            entry("field.total_vehicles", "Vehicules totaux", "Total vehicles", "إجمالي المركبات"),
            entry("field.available", "Disponibles", "Available", "متاحة"),
            entry("field.rented", "Loues", "Rented", "مؤجرة"),
            entry("field.reserved", "Reserves", "Reserved", "محجوزة"),
            entry("field.maintenance", "En maintenance", "In maintenance", "قيد الصيانة"),
            entry("field.inactive", "Inactifs", "Inactive", "غير نشطة"),
            entry("field.utilization", "Taux d'utilisation", "Utilization rate", "معدل الاستخدام"),
            entry("field.revenue_per_vehicle", "Revenu / vehicule", "Revenue / vehicle", "الإيراد لكل مركبة"),
            entry("field.maintenance_cost_per_vehicle", "Cout maintenance / vehicule", "Maintenance cost / vehicle", "تكلفة الصيانة لكل مركبة"),
            entry("field.maintenance_orders", "Ordres de maintenance", "Maintenance orders", "أوامر الصيانة"),
            entry("field.maintenance_completed", "Terminees", "Completed", "منتهية"),
            entry("field.maintenance_active", "En cours", "Active", "قيد التنفيذ"),
            entry("field.maintenance_total_cost", "Cout total", "Total cost", "التكلفة الإجمالية"),
            entry("field.highest_cost", "Intervention la plus couteuse", "Highest-cost order", "أعلى تكلفة صيانة"),
            entry("field.repeated_maintenance", "Vehicules en maintenance repetee", "Vehicles with repeated maintenance", "مركبات ذات صيانة متكررة"),
            entry("field.upcoming", "Maintenances a venir", "Upcoming scheduled", "الصيانة القادمة"),
            entry("field.new_clients", "Nouveaux clients", "New clients", "عملاء جدد"),
            entry("field.returning_clients", "Clients fideles", "Returning clients", "عملاء عائدون"),
            entry("field.clients_overdue", "Clients avec solde impaye", "Clients with overdue balance", "عملاء لديهم رصيد مستحق"),
            entry("field.change_vs_previous", "vs periode precedente", "vs previous period", "مقارنة بالفترة السابقة"),
            entry("footer.confidential", "Confidentiel - usage interne uniquement", "Confidential - internal use only", "سري - للاستخدام الداخلي فقط"),
            entry("footer.report_id", "Rapport N", "Report ID", "رقم التقرير"),
            entry("footer.page", "Page", "Page", "صفحة"),
            entry("button.viewReport", "Voir le rapport", "View report", "عرض التقرير")
    );

    public static String get(String key, String language) {
        Map<String, String> row = LABELS.get(key);
        if (row == null) return key;
        String lang = normalize(language);
        return row.getOrDefault(lang, row.get("fr"));
    }

    private static String normalize(String language) {
        if (language == null) return "fr";
        String lower = language.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "en", "ar" -> lower;
            default -> "fr";
        };
    }

    private static Map.Entry<String, Map<String, String>> entry(String key, String fr, String en, String ar) {
        return Map.entry(key, Map.of("fr", fr, "en", en, "ar", ar));
    }
}
