package com.fenixcore.optibienestar360.modules.notification;

import com.fenixcore.optibienestar360.modules.notification.dto.UserNotificationDto;
import com.fenixcore.optibienestar360.modules.notification.entity.UserNotification.Category;
import com.fenixcore.optibienestar360.modules.notification.entity.UserNotification.Severity;
import com.fenixcore.optibienestar360.modules.notification.service.UserNotificationService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT of {@link MyNotificationsController} (V259) — session-only inbox, no
 * catalog permission, every call scoped to the JWT subject. Service mocked;
 * the SQL scoping runs in {@code UserNotificationInboxIT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class MyNotificationsControllerIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private UserNotificationService service;

    private MockMvc mockMvc;
    private final UUID caller = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /** An authenticated session without any permission: the inbox needs none. */
    private RequestPostProcessor session() {
        CustomUserDetails p = CustomUserDetails.fromJwt(caller, "jti", "es", List.of());
        return authentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    @Test
    void anonymous_is401() throws Exception {
        mockMvc.perform(get("/v1/me/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/me/notifications/unread-count")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/v1/me/notifications/read-all")).andExpect(status().isUnauthorized());
    }

    @Test
    void list_returnsTheCallersPage_withTheContractFields() throws Exception {
        UUID uuid = UUID.randomUUID();
        UserNotificationDto dto = new UserNotificationDto(uuid, "REGISTRATION_APPROVED", Category.ACCOUNT,
                Severity.SUCCESS, Map.of("kind", "ALLY", "name", "Óptica Sol"), "¡Ya eres parte de OptiBienestar!",
                "Tu solicitud fue aprobada", "/allies", Instant.parse("2026-11-01T09:00:00Z"), null, false);
        when(service.listMine(eq(caller), eq(true), eq("ACCOUNT"), any()))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/v1/me/notifications")
                        .param("unread", "true").param("category", "ACCOUNT")
                        .param("page", "0").param("size", "20")
                        .with(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].uuid").value(uuid.toString()))
                .andExpect(jsonPath("$.content[0].type").value("REGISTRATION_APPROVED"))
                .andExpect(jsonPath("$.content[0].category").value("ACCOUNT"))
                .andExpect(jsonPath("$.content[0].severity").value("SUCCESS"))
                .andExpect(jsonPath("$.content[0].params.kind").value("ALLY"))
                .andExpect(jsonPath("$.content[0].params.name").value("Óptica Sol"))
                .andExpect(jsonPath("$.content[0].title").value("¡Ya eres parte de OptiBienestar!"))
                .andExpect(jsonPath("$.content[0].body").value("Tu solicitud fue aprobada"))
                .andExpect(jsonPath("$.content[0].actionUrl").value("/allies"))
                .andExpect(jsonPath("$.content[0].createdAt").exists())
                .andExpect(jsonPath("$.content[0].createdAt_Display").exists())
                .andExpect(jsonPath("$.content[0].read").value(false));
    }

    @Test
    void list_withoutUnreadFlag_asksForAllRows() throws Exception {
        when(service.listMine(eq(caller), eq(false), isNull(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/v1/me/notifications").with(session())).andExpect(status().isOk());

        verify(service).listMine(eq(caller), eq(false), isNull(), any());
    }

    @Test
    void unreadCount_returnsTheCount() throws Exception {
        when(service.unreadCount(caller)).thenReturn(4L);

        mockMvc.perform(get("/v1/me/notifications/unread-count").with(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(4));
    }

    @Test
    void markRead_is204_forTheCaller() throws Exception {
        UUID uuid = UUID.randomUUID();

        mockMvc.perform(post("/v1/me/notifications/{uuid}/read", uuid).with(session()))
                .andExpect(status().isNoContent());

        verify(service).markRead(caller, uuid);
    }

    @Test
    void markRead_notTheCallers_is404() throws Exception {
        UUID uuid = UUID.randomUUID();
        doThrow(new NoSuchElementException("user_notification.not_found")).when(service).markRead(caller, uuid);

        mockMvc.perform(post("/v1/me/notifications/{uuid}/read", uuid).with(session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void readAll_is204() throws Exception {
        mockMvc.perform(post("/v1/me/notifications/read-all").with(session()))
                .andExpect(status().isNoContent());

        verify(service).markAllRead(caller);
    }
}
