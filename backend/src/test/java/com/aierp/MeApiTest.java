package com.aierp;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aierp.platform.web.SecurityConfiguration;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@AutoConfigureRestDocs
@Import(SecurityConfiguration.class)
class MeApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returns_the_authenticated_principal_without_a_production_header_fallback() throws Exception {
        mockMvc.perform(get("/api/v1/me").with(user("user-42").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user-42"))
                .andDo(document("current-user", resource(ResourceSnippetParameters.builder()
                        .tag("Identity")
                        .description("Returns the authenticated principal.")
                        .build())));
    }
}
