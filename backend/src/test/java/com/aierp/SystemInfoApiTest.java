package com.aierp;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aierp.platform.system.SystemInfoController;
import com.aierp.platform.web.SecurityConfiguration;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.userdetails.UserDetailsService;

@WebMvcTest(SystemInfoController.class)
@AutoConfigureRestDocs
@Import(SecurityConfiguration.class)
class SystemInfoApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserDetailsService userDetailsService;

    @Test
    void does_not_provision_a_default_or_dummy_user() {
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("foundation-test"))
                .isInstanceOf(org.springframework.security.core.userdetails.UsernameNotFoundException.class);
    }

    @Test
    void returns_only_the_foundation_system_information_and_documents_it() throws Exception {
        mockMvc.perform(get("/api/v1/system/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("AI ERP"))
                .andExpect(jsonPath("$.phase").value("foundation"))
                .andDo(document("system-info", resource(ResourceSnippetParameters.builder()
                        .tag("System")
                        .description("AI ERP 초기 기반 상태를 확인합니다.")
                        .responseFields(
                                fieldWithPath("name").description("서비스 이름"),
                                fieldWithPath("phase").description("현재 단계"))
                        .build())));
    }

    @Test
    void blocks_unapproved_application_paths() throws Exception {
        mockMvc.perform(get("/api/v1/identity/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    void blocks_state_changes_without_disabling_csrf() throws Exception {
        mockMvc.perform(post("/api/v1/system/info"))
                .andExpect(status().isForbidden());
    }
}
