package com.fenixcore.optibienestar360.core.audit;

import com.fenixcore.optibienestar360.core.audit.dto.LoginAuditLogDto;
import com.fenixcore.optibienestar360.core.audit.entity.LoginAuditLog;
import com.fenixcore.optibienestar360.core.audit.repository.LoginAuditLogRepository;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end check of the {@code login_audit_log} writer/reader (V61, spec
 * 16-audit.md §Login) — {@link LoginAuditService} directly, rather than the
 * full {@code AuthService.login()} HTTP flow (which would need a persisted
 * User+Person+Role+password hash fixture); the pieces exercised here are
 * exactly what {@code AuthService} calls at each step. Same real-Postgres,
 * skip-if-unreachable pattern as {@code DataChangeAuditIT}/{@code ReportAuditIT}.
 */
@SpringBootTest
@ActiveProfiles("dev")
class LoginAuditIT {

	@Autowired
	private LoginAuditService loginAuditService;

	@Autowired
	private LoginAuditLogRepository loginAuditLogRepository;

	@Autowired
	private LoginAuditQueryService loginAuditQueryService;

	@Autowired
	private LoginSessionSweepJob loginSessionSweepJob;

	@BeforeAll
	static void assumePostgresReachable() {
		String host = System.getenv().getOrDefault("DATABASE_HOST", "localhost");
		int port = Integer.parseInt(System.getenv().getOrDefault("DATABASE_PORT", "5432"));
		boolean reachable;
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(host, port), 1000);
			reachable = true;
		} catch (IOException ex) {
			reachable = false;
		}
		Assumptions.assumeTrue(reachable, "No Postgres reachable at " + host + ":" + port + " — skipping login audit validation");
	}

	@Test
	void recordFailurePersistsRowWithNoSession() {
		String email = "test-marker-" + System.nanoTime() + "@example.test";
		try {
			loginAuditService.recordFailure(email, null, LoginAuditResult.FAILED_CREDENTIALS, "bad_password",
					"127.0.0.1", "JUnit", "localhost");

			Optional<LoginAuditLog> saved = loginAuditLogRepository.findAll().stream()
					.filter(l -> email.equals(l.getAttemptedEmail())).findFirst();
			assertTrue(saved.isPresent());
			assertFalse(saved.get().isValid());
			assertEquals(LoginAuditResult.FAILED_CREDENTIALS, saved.get().getResult());
		} finally {
			cleanUp(email);
		}
	}

	@Test
	void startSessionThenCloseInvalidatesTheCachedCheck() {
		String email = "test-marker-" + System.nanoTime() + "@example.test";
		try {
			Optional<UUID> sessionId = loginAuditService.startSession(
					null, email, List.of("ADMINISTRADOR"), "es", "127.0.0.1", "JUnit", "localhost", 30);
			assertTrue(sessionId.isPresent(), "expected a sid — login_audit_enabled defaults to true");
			assertTrue(loginAuditService.isSessionValid(sessionId.get()));

			loginAuditService.attachJti(sessionId.get(), "jti-" + System.nanoTime());

			loginAuditService.closeSession(sessionId.get(), "user_logout");
			assertFalse(loginAuditService.isSessionValid(sessionId.get()));

			LoginAuditLog reloaded = loginAuditLogRepository.findByUuid(sessionId.get()).orElseThrow();
			assertEquals(LoginSessionStatus.LOGGED_OUT, reloaded.getSessionStatus());
			assertEquals("user_logout", reloaded.getLogoutReason());
		} finally {
			cleanUp(email);
		}
	}

	@Test
	void isSessionValidRejectsUnknownSidButAllowsNoSidAtAll() {
		// An unknown sid IS a legitimate rejection (bogus/tampered token) — only
		// unresolvable errors (DB/cache down) fail open, not a plain missing row.
		assertFalse(loginAuditService.isSessionValid(UUID.randomUUID()));
		assertTrue(loginAuditService.isSessionValid(null), "no sid claim at all must fail open (nothing to check)");
	}

	@Test
	void expireStaleSessionsSweepsPastSessions() {
		String email = "test-marker-" + System.nanoTime() + "@example.test";
		try {
			UUID sessionId = loginAuditService.startSession(
					null, email, List.of(), "es", "127.0.0.1", "JUnit", "localhost", 30).orElseThrow();

			LoginAuditLog entry = loginAuditLogRepository.findByUuid(sessionId).orElseThrow();
			entry.setSessionExpiresAt(Instant.now().minusSeconds(60));
			loginAuditLogRepository.save(entry);

			loginSessionSweepJob.sweepExpiredSessions();

			LoginAuditLog reloaded = loginAuditLogRepository.findByUuid(sessionId).orElseThrow();
			assertFalse(reloaded.isValid());
			assertEquals(LoginSessionStatus.EXPIRED, reloaded.getSessionStatus());
		} finally {
			cleanUp(email);
		}
	}

	@Test
	void queryServiceFiltersByResultAndEmail() {
		String email = "test-marker-" + System.nanoTime() + "@example.test";
		try {
			loginAuditService.recordFailure(email, null, LoginAuditResult.FAILED_LOCKED, "account_locked",
					"127.0.0.1", "JUnit", "localhost");

			Page<LoginAuditLogDto> matching = loginAuditQueryService.list(
					PageRequest.of(0, 20), null, email, null, LoginAuditResult.FAILED_LOCKED, null, null, null);
			assertTrue(matching.getContent().stream().anyMatch(dto -> email.equals(dto.attemptedEmail())));

			Page<LoginAuditLogDto> mismatched = loginAuditQueryService.list(
					PageRequest.of(0, 20), null, email, null, LoginAuditResult.SUCCESS, null, null, null);
			assertTrue(mismatched.getContent().stream().noneMatch(dto -> email.equals(dto.attemptedEmail())));
		} finally {
			cleanUp(email);
		}
	}

	private void cleanUp(String email) {
		loginAuditLogRepository.findAll().stream()
				.filter(l -> email.equals(l.getAttemptedEmail()))
				.forEach(loginAuditLogRepository::delete);
	}

}
