package com.carrental.service;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Probes an SMTP host in the stages the Super Admin diagnostics page actually
 * needs to distinguish: {@code configuration -> dns -> tcp -> tls -> authentication}.
 * A single "testConnection() threw something" try/catch (the previous
 * implementation) cannot tell a hosting-network problem (Railway can't reach
 * Zoho at all) apart from a credentials problem (Railway reached Zoho fine,
 * but the App Password is wrong) — which matters because they need entirely
 * different fixes, and misreporting one as the other sends whoever's
 * debugging down the wrong path entirely.
 *
 * <p>DNS and TCP are checked with raw {@link InetAddress}/{@link Socket}
 * calls under a strict timeout <em>before</em> any SMTP/TLS/auth attempt, so
 * "authentication failed" can never be reported when the server was never
 * actually reached — the exact mistake the previous string-matching-only
 * classifier could make (a "connection refused" message containing neither
 * an auth nor a connection keyword fell through to a generic error).
 *
 * <p>Never throws — every stage failure is captured into the result record.
 * Never logs the password.
 */
@Slf4j
@Service
public class SmtpDiagnosticsService {

    private static final String STAGE_OK = "OK";
    private static final String STAGE_FAILED = "FAILED";
    private static final String STAGE_NOT_TESTED = "NOT_TESTED";

    // Each candidate is probed concurrently (see probeAllWithBudget), so these
    // bound a single probe's own worst case, not the endpoint's total runtime.
    // Kept low enough that DNS+TCP+SMTP together stay well under the 10-15s
    // response budget diagnoseSmtp() enforces across all candidates at once.
    private static final int DNS_TIMEOUT_SECONDS = 3;
    private static final int TCP_CONNECT_TIMEOUT_MS = 5000;
    private static final int SMTP_TIMEOUT_MS = 6000;

    public record SmtpProbeResult(
            boolean success,
            String host,
            int port,
            String usernameUsed,
            String fromEmailUsed,
            String configuration,
            String dns,
            String tcp,
            String tls,
            String authentication,
            String send,
            String errorCode,
            String message) {

        static SmtpProbeResult configurationMissing(String host, int port, String message) {
            return new SmtpProbeResult(false, host, port, null, null,
                    STAGE_FAILED, STAGE_NOT_TESTED, STAGE_NOT_TESTED, STAGE_NOT_TESTED, STAGE_NOT_TESTED, STAGE_NOT_TESTED,
                    "SMTP_CONFIGURATION_MISSING", message);
        }
    }

    public record ProbeCandidate(String host, int port, boolean ssl) {}

    // Separate from dnsExecutor: this pool runs whole probe() calls (DNS+TCP+SMTP),
    // dnsExecutor is only ever used internally by resolveDns(). Bounded and daemon
    // for the same reason as dnsExecutor — a stuck probe leaks at most one thread
    // from a fixed-size pool, never grows unbounded across repeated Diagnose clicks.
    private static final int PROBE_EXECUTOR_POOL_SIZE = 6;
    private final ExecutorService probeExecutor = Executors.newFixedThreadPool(PROBE_EXECUTOR_POOL_SIZE, runnable -> {
        Thread thread = new Thread(runnable, "smtp-probe");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Probes every candidate concurrently and waits at most {@code budgetMs} in
     * total — the previous sequential loop (5 candidates × up to ~24s worst
     * case each) could take 45-100+s, far past the frontend's request timeout,
     * so a fully healthy backend still reported as "API server unavailable".
     * Any candidate not finished when the budget expires is reported as a
     * timed-out result instead of blocking the response further; the probe's
     * own thread is left to finish against its internal per-stage timeouts
     * (see class-level note on dnsExecutor for why it can't be killed outright).
     */
    public List<SmtpProbeResult> probeAllWithBudget(List<ProbeCandidate> candidates,
                                                      String username, String fromEmail, String password,
                                                      long budgetMs) {
        List<CompletableFuture<SmtpProbeResult>> futures = new ArrayList<>(candidates.size());
        for (ProbeCandidate candidate : candidates) {
            futures.add(CompletableFuture.supplyAsync(
                    () -> probe(candidate.host(), candidate.port(), username, fromEmail, password, candidate.ssl()),
                    probeExecutor));
        }
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(budgetMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | InterruptedException | java.util.concurrent.ExecutionException e) {
            // At least one candidate didn't finish in time — fall through and
            // report whatever's done; unfinished ones become timeout results below.
        }

        List<SmtpProbeResult> results = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            ProbeCandidate candidate = candidates.get(i);
            CompletableFuture<SmtpProbeResult> future = futures.get(i);
            if (future.isDone() && !future.isCompletedExceptionally()) {
                results.add(future.join());
            } else {
                future.cancel(true);
                results.add(new SmtpProbeResult(false, candidate.host(), candidate.port(), username, fromEmail,
                        STAGE_OK, STAGE_NOT_TESTED, STAGE_NOT_TESTED, STAGE_NOT_TESTED, STAGE_NOT_TESTED, STAGE_NOT_TESTED,
                        "SMTP_DIAGNOSTIC_TIMEOUT",
                        "Diagnostics exceeded the " + (budgetMs / 1000) + "s time budget for " + candidate.host()
                                + ":" + candidate.port() + "."));
            }
        }
        return results;
    }

    /**
     * Probes connectivity + authentication for one host/port. Does not send a
     * real message — the "send" stage is always {@code NOT_TESTED} here; use
     * {@link SmtpMailService} for an actual send.
     */
    public SmtpProbeResult probe(String host, int port, String username, String fromEmail, String password, boolean ssl) {
        if (host == null || host.isBlank() || username == null || username.isBlank() || password == null || password.isBlank()) {
            return SmtpProbeResult.configurationMissing(host, port,
                    "SMTP host, username, or password is missing — cannot run diagnostics.");
        }

        // ── Stage 1: DNS ─────────────────────────────────────────────────────
        String dnsStage = resolveDns(host);
        if (!STAGE_OK.equals(dnsStage)) {
            return new SmtpProbeResult(false, host, port, username, fromEmail,
                    STAGE_OK, dnsStage, STAGE_NOT_TESTED, STAGE_NOT_TESTED, STAGE_NOT_TESTED, STAGE_NOT_TESTED,
                    "SMTP_DNS_FAILED",
                    "DNS could not resolve " + host + ". Verify the host name is correct.");
        }

        // ── Stage 2: TCP ─────────────────────────────────────────────────────
        TcpProbe tcpProbe = connectTcp(host, port);
        if (!STAGE_OK.equals(tcpProbe.stage)) {
            return new SmtpProbeResult(false, host, port, username, fromEmail,
                    STAGE_OK, STAGE_OK, tcpProbe.stage, STAGE_NOT_TESTED, STAGE_NOT_TESTED, STAGE_NOT_TESTED,
                    tcpProbe.errorCode, tcpProbe.message);
        }

        // ── Stage 3 + 4: SMTP greeting / STARTTLS-SSL / AUTH ────────────────
        // Jakarta Mail's testConnection() runs the greeting, TLS negotiation, and
        // AUTH in one call — we only reach it after DNS+TCP already succeeded
        // above, so any exception here is genuinely a protocol/TLS/auth-level
        // failure, never mistaken for a network-reachability problem.
        try {
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(host);
            sender.setPort(port);
            sender.setUsername(username);
            sender.setPassword(password);
            Properties props = new Properties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");
            if (ssl) {
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.starttls.enable", "false");
            } else {
                props.put("mail.smtp.ssl.enable", "false");
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
            }
            // Trust only the host actually being probed — never a wildcard.
            props.put("mail.smtp.ssl.trust", host);
            props.put("mail.smtp.connectiontimeout", String.valueOf(SMTP_TIMEOUT_MS));
            props.put("mail.smtp.timeout", String.valueOf(SMTP_TIMEOUT_MS));
            props.put("mail.smtp.writetimeout", String.valueOf(SMTP_TIMEOUT_MS));
            sender.setJavaMailProperties(props);

            sender.testConnection();

            return new SmtpProbeResult(true, host, port, username, fromEmail,
                    STAGE_OK, STAGE_OK, STAGE_OK, STAGE_OK, STAGE_OK, STAGE_NOT_TESTED,
                    null, "Connected and authenticated successfully.");
        } catch (Exception ex) {
            // JavaMailSenderImpl.testConnection() wraps every Jakarta Mail failure in
            // Spring's unchecked org.springframework.mail.MailException hierarchy — the
            // real AuthenticationFailedException/SSLException/MessagingException is the
            // *cause*, not the thrown type itself. This defensive catch(Exception) (not
            // just MailException) also guarantees this method truly never throws,
            // regardless of what Jakarta Mail or the JDK network stack does.
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            log.warn("[SMTP_DIAG] host={} port={} username={} stage=POST_TCP result=FAILED exceptionClass={}",
                    host, port, username, cause.getClass().getName());

            if (cause instanceof AuthenticationFailedException) {
                return new SmtpProbeResult(false, host, port, username, fromEmail,
                        STAGE_OK, STAGE_OK, STAGE_OK, STAGE_OK, STAGE_FAILED, STAGE_NOT_TESTED,
                        "SMTP_AUTHENTICATION_FAILED",
                        "Server reachable and TLS negotiated, but authentication was rejected for " + username
                                + ". Verify the full email address and App Password (Zoho requires an App Password, "
                                + "not the account login password, when 2FA is enabled).");
            }
            if (cause instanceof SSLException) {
                String code = ssl ? "SMTP_SSL_HANDSHAKE_FAILED" : "SMTP_STARTTLS_FAILED";
                return new SmtpProbeResult(false, host, port, username, fromEmail,
                        STAGE_OK, STAGE_OK, STAGE_OK, STAGE_FAILED, STAGE_NOT_TESTED, STAGE_NOT_TESTED,
                        code,
                        ssl ? "The server was reached, but the SSL handshake failed."
                            : "The server was reached, but STARTTLS negotiation failed.");
            }
            if (cause instanceof MessagingException) {
                String msg = cause.getMessage() != null ? cause.getMessage().toLowerCase() : "";
                if (msg.contains("sender address rejected") || msg.contains("553") || msg.contains("501")
                        || msg.contains("not allowed to send") || msg.contains("relay not permitted")) {
                    return new SmtpProbeResult(false, host, port, username, fromEmail,
                            STAGE_OK, STAGE_OK, STAGE_OK, STAGE_OK, STAGE_OK, STAGE_FAILED,
                            "SMTP_SENDER_REJECTED",
                            "Authenticated successfully, but the sender address " + fromEmail
                                    + " was rejected. It must match the authenticated mailbox or a verified alias.");
                }
                return new SmtpProbeResult(false, host, port, username, fromEmail,
                        STAGE_OK, STAGE_OK, STAGE_OK, STAGE_OK, STAGE_NOT_TESTED, STAGE_NOT_TESTED,
                        "SMTP_SEND_FAILED",
                        "Connected, but the SMTP session failed unexpectedly: " + cause.getClass().getSimpleName());
            }
            return new SmtpProbeResult(false, host, port, username, fromEmail,
                    STAGE_OK, STAGE_OK, STAGE_OK, STAGE_NOT_TESTED, STAGE_NOT_TESTED, STAGE_NOT_TESTED,
                    "SMTP_SEND_FAILED",
                    "Unexpected error after connecting: " + cause.getClass().getSimpleName());
        }
    }

    /**
     * Shared, bounded, and never torn down — {@link InetAddress#getAllByName}
     * is a blocking native call that does not respond to
     * {@link Thread#interrupt()}, so a DNS timeout below can never actually
     * kill the worker thread; it leaks until the underlying OS-level lookup
     * itself eventually gives up (which can take minutes on a blackholed
     * network, exactly the "TCP times out" case this service exists to
     * diagnose). Creating a brand-new {@code Executors.newSingleThreadExecutor()}
     * per probe call — the previous implementation — meant every single
     * "Diagnose SMTP" click leaked one more permanent thread (times up to 5
     * candidate hosts probed per click), each reserving native stack memory,
     * until the JVM could no longer create new threads at all
     * ({@code OutOfMemoryError: unable to create native thread}) and the
     * container was killed. A small fixed pool of daemon threads bounds the
     * damage to at most {@link #DNS_EXECUTOR_POOL_SIZE} permanently-stuck
     * threads total, no matter how many diagnostics ever run.
     */
    private static final int DNS_EXECUTOR_POOL_SIZE = 4;
    private final ExecutorService dnsExecutor = Executors.newFixedThreadPool(DNS_EXECUTOR_POOL_SIZE, runnable -> {
        Thread thread = new Thread(runnable, "smtp-dns-probe");
        thread.setDaemon(true);
        return thread;
    });

    private String resolveDns(String host) {
        Callable<Boolean> lookup = () -> {
            InetAddress.getAllByName(host);
            return true;
        };
        Future<Boolean> future = dnsExecutor.submit(lookup);
        try {
            future.get(DNS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return STAGE_OK;
        } catch (TimeoutException e) {
            // Does NOT kill the underlying thread (see class-level note) — the
            // pool is bounded specifically so this is an acceptable, capped cost
            // rather than an unbounded leak.
            future.cancel(true);
            return STAGE_FAILED;
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof UnknownHostException) return STAGE_FAILED;
            return STAGE_FAILED;
        }
    }

    private record TcpProbe(String stage, String errorCode, String message) {}

    private TcpProbe connectTcp(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), TCP_CONNECT_TIMEOUT_MS);
            return new TcpProbe(STAGE_OK, null, null);
        } catch (SocketTimeoutException ex) {
            return new TcpProbe(STAGE_FAILED, "SMTP_CONNECTION_TIMEOUT",
                    "Railway could not establish a TCP connection to " + host + ":" + port
                            + " within " + (TCP_CONNECT_TIMEOUT_MS / 1000) + "s. The port may be blocked by the hosting network.");
        } catch (ConnectException ex) {
            return new TcpProbe(STAGE_FAILED, "SMTP_CONNECTION_REFUSED",
                    host + ":" + port + " actively refused the connection. Verify the port is correct for this host.");
        } catch (UnknownHostException ex) {
            // Should already have been caught by the DNS stage, but handled defensively.
            return new TcpProbe(STAGE_FAILED, "SMTP_DNS_FAILED", "DNS could not resolve " + host + ".");
        } catch (Exception ex) {
            return new TcpProbe(STAGE_FAILED, "SMTP_CONNECTION_TIMEOUT",
                    "Could not connect to " + host + ":" + port + ": " + ex.getClass().getSimpleName());
        }
    }
}
