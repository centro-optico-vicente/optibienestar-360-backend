package com.fenixcore.optibienestar360.modules.media;

import com.fenixcore.optibienestar360.modules.media.dto.MediaItemDto;
import com.fenixcore.optibienestar360.modules.media.service.MediaItemsService;
import com.fenixcore.optibienestar360.modules.terms.service.TermsAcceptanceService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import com.fenixcore.optibienestar360.security.RoleNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT of the generic gallery routes (hub ADR 0027): one controller, five routes.
 * Each route must resolve to the right owner and scope (admin vs portal); the
 * service is mocked — its rules are covered by {@code MediaItemsServiceTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class MediaControllerIT {

    private static final String A = "11111111-1111-1111-1111-111111111111";
    private static final String B = "22222222-2222-2222-2222-222222222222";

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private TermsAcceptanceService termsAcceptanceService;

    @MockitoBean
    private MediaItemsService mediaService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private RequestPostProcessor principal() {
        CustomUserDetails p = CustomUserDetails.fromJwt(UUID.randomUUID(), "jti", "es",
                List.of(new SimpleGrantedAuthority(RoleNames.ALLY)));
        return authentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    private static MediaItemDto dto() {
        return new MediaItemDto(UUID.randomUUID(), "https://img.example/a.jpg", "URL", null, 0, true, null, Instant.now());
    }

    @ParameterizedTest
    @CsvSource({
            "/v1/admin/allies/" + A + "/media,                          ALLY,          ADMIN,  ''",
            "/v1/admin/products/" + A + "/media,                        PRODUCT,       ADMIN,  ''",
            "/v1/admin/allies/" + B + "/products/" + A + "/media,       ALLY_PRODUCT,  ADMIN,  " + B,
            "/v1/me/allies/" + A + "/media,                             ALLY,          PORTAL, ''",
            "/v1/me/allies/products/" + A + "/media,                    ALLY_PRODUCT,  PORTAL, ''"
    })
    void eachRoute_resolvesItsOwnerAndScope(String url, String ownerKey, MediaScope scope, String parent) throws Exception {
        when(mediaService.list(any(), any())).thenReturn(List.of(dto()));

        mockMvc.perform(get(url).with(principal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].primary").value(true));

        ArgumentCaptor<MediaTarget> captor = ArgumentCaptor.forClass(MediaTarget.class);
        verify(mediaService).list(captor.capture(), any());
        MediaTarget target = captor.getValue();
        assertThat(target.owner().key()).isEqualTo(ownerKey);
        assertThat(target.scope()).isEqualTo(scope);
        assertThat(target.ownerUuid()).isEqualTo(UUID.fromString(A));
        assertThat(target.parentUuid()).isEqualTo(parent.isEmpty() ? null : UUID.fromString(parent));
    }

    @Test
    void anonymousCallers_are401() throws Exception {
        mockMvc.perform(get("/v1/admin/allies/" + A + "/media")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/me/allies/" + A + "/media")).andExpect(status().isUnauthorized());
    }

    @Test
    void aMalformedUuid_isANotFound_notAServerError() throws Exception {
        mockMvc.perform(get("/v1/admin/allies/not-a-uuid/media").with(principal()))
                .andExpect(status().isNotFound());
    }

    @Test
    void upload_url_update_reorder_delete_reachTheService() throws Exception {
        String base = "/v1/admin/products/" + A + "/media";
        UUID media = UUID.randomUUID();
        when(mediaService.upload(any(), any(), any(), eq("Frente"))).thenReturn(dto());
        when(mediaService.addExternal(any(), any(), eq("https://img.example/a.jpg"), any())).thenReturn(dto());
        when(mediaService.update(any(), any(), eq(media), any(), eq(true))).thenReturn(dto());
        when(mediaService.reorder(any(), any(), any())).thenReturn(List.of(dto()));

        mockMvc.perform(multipart(base)
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "a.png", "image/png", new byte[]{1}))
                        .param("caption", "Frente").with(principal()))
                .andExpect(status().isOk());
        mockMvc.perform(post(base + "/url").with(principal()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://img.example/a.jpg\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch(base + "/" + media).with(principal()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"primary\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(put(base + "/order").with(principal()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uuids\":[\"" + media + "\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete(base + "/" + media).with(principal()))
                .andExpect(status().isNoContent());
        verify(mediaService).delete(any(), any(), eq(media));
    }
}
