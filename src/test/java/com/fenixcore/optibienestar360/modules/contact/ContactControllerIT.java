package com.fenixcore.optibienestar360.modules.contact;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT of the public contact form endpoint ({@link ContactController}):
 * {@code POST /v1/public/contact-messages} is anonymous ({@code /v1/public/**}
 * is permitAll in SecurityConfig). {@link ContactService} is mocked.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class ContactControllerIT {

    private static final String BODY = """
            {"name":"Ana Pérez","email":"ana@example.com","phone":"04141234567",
             "subject":"Información","message":"Quiero conocer los planes."}
            """;

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private ContactService contactService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void submit_anonymous_is201() throws Exception {
        mockMvc.perform(post("/v1/public/contact-messages")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
        verify(contactService, times(1)).submit(any());
    }

    @Test
    void submit_invalidBody_isRejectedWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/v1/public/contact-messages")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().is4xxClientError());
        verify(contactService, times(0)).submit(any());
    }
}
