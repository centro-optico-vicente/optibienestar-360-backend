package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.promoter.dto.CollectionScoreDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterMemberContactDto;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterMemberContact.ContactType;
import com.fenixcore.optibienestar360.modules.promoter.service.PromoterCollectionService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT del contrato de cobranza delegada (v2 PDF 2.b, {@link PromoterContactsController}):
 * autorización (writes → {@code PROMOTER_CONTACT_OWN}; reads → {@code PROMOTER_VIEW_OWN})
 * y el 404 de propiedad. {@link PromoterCollectionService} se mockea; principal
 * {@link CustomUserDetails} real (los endpoints leen {@code actor.getUuid()}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PromoterContactsControllerIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private PromoterCollectionService collectionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private RequestPostProcessor principal(String... authorities) {
        CustomUserDetails p = CustomUserDetails.fromJwt(UUID.randomUUID(), "jti", "es",
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
        return authentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    private PromoterMemberContactDto contactDto(ContactType type) {
        return new PromoterMemberContactDto(UUID.randomUUID(), UUID.randomUUID(), type,
                "nota", null, null, Instant.parse("2026-07-25T12:00:00Z"));
    }

    // ─── reminder (write, PROMOTER_CONTACT_OWN) ──────────────────────────────

    @Test
    void reminder_anonymous_is401_andNeverTouchesService() throws Exception {
        mockMvc.perform(post("/v1/promoter/me/contacts/{m}/reminder", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        verify(collectionService, never()).registerReminder(any(), any(), any());
    }

    @Test
    void reminder_withoutPermission_is403() throws Exception {
        mockMvc.perform(post("/v1/promoter/me/contacts/{m}/reminder", UUID.randomUUID())
                        .with(principal("PROMOTER_VIEW_OWN"))   // read perm, not the write one
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"x\"}"))
                .andExpect(status().isForbidden());
        verify(collectionService, never()).registerReminder(any(), any(), any());
    }

    @Test
    void reminder_withPermission_is201() throws Exception {
        when(collectionService.registerReminder(any(UUID.class), any(UUID.class), any()))
                .thenReturn(contactDto(ContactType.REMINDER));

        mockMvc.perform(post("/v1/promoter/me/contacts/{m}/reminder", UUID.randomUUID())
                        .with(principal("PROMOTER_CONTACT_OWN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Llamado hoy\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("REMINDER"));
    }

    @Test
    void reminder_memberNotInPortfolio_is404() throws Exception {
        when(collectionService.registerReminder(any(), any(), any()))
                .thenThrow(new NoSuchElementException("promoter.member.not_in_portfolio"));

        mockMvc.perform(post("/v1/promoter/me/contacts/{m}/reminder", UUID.randomUUID())
                        .with(principal("PROMOTER_CONTACT_OWN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    // ─── payment-promise (write, PROMOTER_CONTACT_OWN) ───────────────────────

    @Test
    void paymentPromise_withPermission_is201() throws Exception {
        when(collectionService.registerPaymentPromise(any(), any(), any(), any(), any()))
                .thenReturn(contactDto(ContactType.PAYMENT_PROMISE));

        // Far-future date so @FutureOrPresent never depends on the CI clock.
        String body = "{\"promisedAmount\":25.00,\"promisedAtDate\":\"2099-12-31\",\"note\":\"Promete\"}";
        mockMvc.perform(post("/v1/promoter/me/contacts/{m}/payment-promise", UUID.randomUUID())
                        .with(principal("PROMOTER_CONTACT_OWN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("PAYMENT_PROMISE"));
    }

    // ─── reads (PROMOTER_VIEW_OWN) ───────────────────────────────────────────

    @Test
    void contacts_withReadPermission_is200() throws Exception {
        when(collectionService.listContacts(any(UUID.class), any(UUID.class)))
                .thenReturn(List.of(contactDto(ContactType.REMINDER)));

        mockMvc.perform(get("/v1/promoter/me/contacts").param("memberUuid", UUID.randomUUID().toString())
                        .with(principal("PROMOTER_VIEW_OWN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void collectionScore_withReadPermission_is200() throws Exception {
        when(collectionService.collectionScore(any(UUID.class)))
                .thenReturn(new CollectionScoreDto(UUID.randomUUID(), 4, 2, 1, 1, new BigDecimal("50.00")));

        mockMvc.perform(get("/v1/promoter/me/collection-score").with(principal("PROMOTER_VIEW_OWN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scorePct").value(50.00))
                .andExpect(jsonPath("$.upToDate").value(2));
    }

    @Test
    void collectionScore_withoutReadPermission_is403() throws Exception {
        mockMvc.perform(get("/v1/promoter/me/collection-score").with(principal("MEMBER_VIEW_ALL")))
                .andExpect(status().isForbidden());
        verify(collectionService, never()).collectionScore(any());
    }
}
