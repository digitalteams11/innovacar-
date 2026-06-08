package com.carrental.service;

import com.carrental.dto.dashboard.DashboardResponse;
import com.carrental.dto.payment.PaymentResponse;
import com.carrental.entity.*;
import com.carrental.repository.*;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service to aggregate dashboard metrics from real data.
 * Every counter is computed from the database — no static or fake values.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final VehicleRepository vehicleRepository;
    private final PaymentRepository paymentRepository;
    private final DepositRepository depositRepository;
    private final ReservationRepository reservationRepository;
    private final ClientRepository clientRepository;
    private final InvoiceRepository invoiceRepository;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboardMetrics() {
        Long tenantId = TenantContext.getCurrentTenantId();

        long totalVehicles = vehicleRepository.countByTenantId(tenantId);
        long rentedVehicles = vehicleRepository.countByTenantIdAndStatut(tenantId, VehicleStatus.RENTED);
        long totalReservations = reservationRepository.findAllByTenantId(tenantId).size();
        long totalClients = clientRepository.findAllByTenantId(tenantId).size();

        // Revenue from real rental payments
        BigDecimal totalRevenue = paymentRepository.sumCollectedRentalRevenueByTenantId(tenantId);
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;

        BigDecimal monthlyRevenue = calculateMonthlyRevenue(tenantId);

        // Pending payments
        BigDecimal pendingAmount = paymentRepository.sumPendingAmountByTenantId(tenantId);
        long pendingCount = paymentRepository.countPendingByTenantId(tenantId);

        // Invoice counts
        long paidInvoices = invoiceRepository.findAllByTenantId(tenantId).stream()
                .filter(i -> i.getStatus() == InvoiceStatus.PAID)
                .count();
        long overdueInvoices = invoiceRepository.findAllByTenantId(tenantId).stream()
                .filter(i -> i.getStatus() == InvoiceStatus.OVERDUE)
                .count();

        // Refunds
        BigDecimal refundAmount = paymentRepository.sumRefundsByTenantId(tenantId);
        long refundCount = paymentRepository.countRefundsByTenantId(tenantId);

        // Recent transactions
        List<PaymentResponse> recent = paymentRepository.findTop5ByTenantIdOrderByPaymentDateDesc(tenantId)
                .stream()
                .map(PaymentResponse::from)
                .toList();

        // Deposit stats
        BigDecimal totalDepositsHeld = depositRepository.sumActiveDepositsByTenantId(tenantId);
        Long pendingReturns = depositRepository.countPendingReturnsByTenantId(tenantId);
        BigDecimal returnedDeposits = depositRepository.sumReturnedDepositsByTenantId(tenantId);
        BigDecimal depositDeductions = depositRepository.sumTotalDeductionsByTenantId(tenantId);
        BigDecimal depositRevenue = depositRepository.sumDepositRevenueByTenantId(tenantId);

        return DashboardResponse.builder()
                .totalVehicles(totalVehicles)
                .rentedVehicles(rentedVehicles)
                .totalRevenue(totalRevenue)
                .monthlyRevenue(monthlyRevenue)
                .totalReservations(totalReservations)
                .totalClients(totalClients)
                .pendingPaymentsAmount(pendingAmount != null ? pendingAmount : BigDecimal.ZERO)
                .pendingPaymentsCount(pendingCount)
                .paidInvoices(paidInvoices)
                .overdueInvoices(overdueInvoices)
                .refundAmount(refundAmount != null ? refundAmount : BigDecimal.ZERO)
                .refundCount(refundCount)
                .recentTransactions(recent)
                .totalDepositsHeld(totalDepositsHeld != null ? totalDepositsHeld : BigDecimal.ZERO)
                .pendingReturns(pendingReturns != null ? pendingReturns : 0L)
                .returnedDeposits(returnedDeposits != null ? returnedDeposits : BigDecimal.ZERO)
                .depositDeductions(depositDeductions != null ? depositDeductions : BigDecimal.ZERO)
                .depositRevenue(depositRevenue != null ? depositRevenue : BigDecimal.ZERO)
                .build();
    }

    private BigDecimal calculateMonthlyRevenue(Long tenantId) {
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfMonth = startOfMonth.plusMonths(1);
        BigDecimal revenue = paymentRepository.sumCollectedRentalRevenueBetween(tenantId, startOfMonth, endOfMonth);
        return revenue != null ? revenue : BigDecimal.ZERO;
    }
}
