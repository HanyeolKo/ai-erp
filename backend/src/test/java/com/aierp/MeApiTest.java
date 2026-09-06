package com.aierp;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import com.aierp.platform.web.SecurityConfiguration;
import com.aierp.identity.api.ApplicationPrincipal;
import com.aierp.identity.api.CurrentUserController;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
import java.util.UUID;

@WebMvcTest(CurrentUserController.class)
@AutoConfigureRestDocs
@Import(SecurityConfiguration.class)
class MeApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returns_the_authenticated_principal_without_a_production_header_fallback() throws Exception {
        var principal = new ApplicationPrincipal(UUID.fromString("00000000-0000-0000-0000-000000000042"), "user@example.test", true);
        mockMvc.perform(get("/api/v1/me").with(authentication(new UsernamePasswordAuthenticationToken(principal, "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("00000000-0000-0000-0000-000000000042"))
                .andDo(document("current-user", resource(ResourceSnippetParameters.builder()
                        .tag("Identity")
                        .description("Returns the authenticated principal.")
                        .responseFields(
                                fieldWithPath("id").description("Stable internal UUID"),
                                fieldWithPath("authorities").description("Granted Spring Security authorities"))
                        .build())));
    }

    @Test
    void returns_a_problem_document_for_an_unauthenticated_api_request() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
