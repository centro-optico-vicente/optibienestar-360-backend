package com.fenixcore.optibienestar360.modules.notification;

import com.fenixcore.optibienestar360.modules.auth.repository.UserRoleRepository;
import com.fenixcore.optibienestar360.modules.notification.dto.UserNotificationCommand;
import com.fenixcore.optibienestar360.modules.notification.dto.UserNotificationDto;
import com.fenixcore.optibienestar360.modules.notification.entity.UserNotification.Category;
import com.fenixcore.optibienestar360.modules.notification.entity.UserNotification.Severity;
import com.fenixcore.optibienestar360.modules.notification.service.UserNotificationPublisher;
import com.fenixcore.optibienestar360.modules.notification.service.UserNotificationService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end check of the in-app inbox against real Postgres (V259): the
 * publisher's dedup and role fan-out queries, and the user-side filters —
 * own rows only, expired rows hidden, unread count, read and read-all. Uses
 * two seeded users and a unique {@code type} per run, deleted afterwards. Same
 * real-Postgres, skip-if-unreachable pattern as {@code LoginAuditIT}.
 */
@SpringBootTest
@ActiveProfiles("dev")
class UserNotificationInboxIT {

    @Autowired private UserNotificationPublisher publisher;
    @Autowired private UserNotificationService service;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private JdbcTemplate jdbc;

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
        Assumptions.assumeTrue(reachable,
                "No Postgres reachable at " + host + ":" + port + " — skipping user notification inbox validation");
    }

    @Test
    void inboxIsScopedToTheCaller_hidesExpiredRows_andTracksReadState() {
        List<Map<String, Object>> users = jdbc.queryForList(
                "SELECT users_id, uuid FROM users WHERE is_active ORDER BY users_id LIMIT 2");
        Assumptions.assumeTrue(users.size() == 2, "needs two seeded users");
        Long firstId = ((Number) users.get(0).get("users_id")).longValue();
        UUID first = (UUID) users.get(0).get("uuid");
        Long secondId = ((Number) users.get(1).get("users_id")).longValue();
        UUID second = (UUID) users.get(1).get("uuid");
        String type = "IT_INBOX_" + System.nanoTime();
        try {
            long firstUnreadBefore = service.unreadCount(first);

            // Dedup: the same key twice creates the rows once.
            assertThat(publisher.publish(command(type, Set.of(firstId, secondId), "k1", null))).isEqualTo(2);
            assertThat(publisher.publish(command(type, Set.of(firstId, secondId), "k1", null))).isZero();
            // An already expired notice for the first user.
            assertThat(publisher.publish(command(type, Set.of(firstId), "k2", Instant.now().minusSeconds(60))))
                    .isEqualTo(1);

            List<UserNotificationDto> firstRows = mine(first, false, type);
            assertThat(firstRows).hasSize(1);
            assertThat(firstRows.getFirst().read()).isFalse();
            assertThat(firstRows.getFirst().params()).containsEntry("kind", "ALLY");
            assertThat(service.unreadCount(first)).isEqualTo(firstUnreadBefore + 1);

            // Another user's row is a 404 for the caller.
            UUID secondsRow = mine(second, false, type).getFirst().uuid();
            assertThatThrownBy(() -> service.markRead(first, secondsRow)).isInstanceOf(NoSuchElementException.class);

            // Read (idempotent) → gone from the unread list and the count.
            service.markRead(first, firstRows.getFirst().uuid());
            service.markRead(first, firstRows.getFirst().uuid());
            assertThat(mine(first, true, type)).isEmpty();
            assertThat(mine(first, false, type).getFirst().read()).isTrue();
            assertThat(service.unreadCount(first)).isEqualTo(firstUnreadBefore);

            // Read-all only touches the caller's rows.
            assertThat(mine(second, true, type)).hasSize(1);
            service.markAllRead(second);
            assertThat(mine(second, true, type)).isEmpty();
        } finally {
            jdbc.update("DELETE FROM user_notifications WHERE type = ?", type);
        }
    }

    @Test
    void roleFanOutResolvesTheUsersHoldingTheRole() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT ur.user_id, r.name FROM user_roles ur
                JOIN roles r ON r.roles_id = ur.role_id
                JOIN users u ON u.users_id = ur.user_id
                WHERE ur.is_active AND r.is_active AND u.is_active AND u.status = 'ACTIVE'
                  AND (ur.expires_at IS NULL OR ur.expires_at > NOW())
                ORDER BY ur.user_id LIMIT 1""");
        Assumptions.assumeTrue(!rows.isEmpty(), "needs a seeded role assignment");
        Long userId = ((Number) rows.getFirst().get("user_id")).longValue();
        String role = (String) rows.getFirst().get("name");

        assertThat(userRoleRepository.findEffectiveUserIdsByRoleNames(List.of(role), Instant.now()))
                .contains(userId);
        // Exercises the permission join; the result depends on the seed.
        assertThat(userRoleRepository.findEffectiveUserIdsByPermissionNames(
                List.of("NOTIFICATION_VIEW_ALL"), Instant.now())).isNotNull();
    }

    private List<UserNotificationDto> mine(UUID user, boolean unreadOnly, String type) {
        return service.listMine(user, unreadOnly, "ACCOUNT", PageRequest.of(0, 100)).getContent().stream()
                .filter(n -> type.equals(n.type()))
                .toList();
    }

    private static UserNotificationCommand command(String type, Set<Long> recipients, String dedupKey,
                                                   Instant expiresAt) {
        return UserNotificationCommand.builder()
                .type(type)
                .category(Category.ACCOUNT)
                .severity(Severity.INFO)
                .params(Map.of("kind", "ALLY", "name", "IT"))
                .fallbackTitle("IT title")
                .fallbackBody("IT body")
                .actionUrl("/allies")
                .sourceModule("it")
                .dedupKey(dedupKey)
                .expiresAt(expiresAt)
                .recipientUserIds(recipients)
                .build();
    }
}
