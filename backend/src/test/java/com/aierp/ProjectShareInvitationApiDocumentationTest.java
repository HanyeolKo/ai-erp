package com.aierp;

import com.aierp.identity.api.ApplicationPrincipal;
import com.aierp.platform.web.*;
import com.aierp.project.ProjectRole;
import com.aierp.project.ProjectShareInvitationService;
import com.aierp.project.api.ProjectShareInvitationController;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static org.mockito.Mockito.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProjectShareInvitationController.class)
@AutoConfigureRestDocs @Import({SecurityConfiguration.class,ApiExceptionHandler.class})
class ProjectShareInvitationApiDocumentationTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProjectShareInvitationService service;
    final UUID project=UUID.fromString("10000000-0000-0000-0000-000000000001");
    final UUID user=UUID.fromString("20000000-0000-0000-0000-000000000002");
    final Instant expiry=Instant.parse("2026-09-14T10:00:00Z");
    final UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken(new ApplicationPrincipal(user,"user@example.test",true),null,List.of());

    @Test void documentsManagerShareLifecycle() throws Exception {
        var active=new ProjectShareInvitationService.ShareInvitationResponse(ProjectShareInvitationService.State.ACTIVE,"7K3M9F2D6R8TWX4C",expiry);
        when(service.current(project,(ApplicationPrincipal)auth.getPrincipal())).thenReturn(active);
        when(service.rotate(project,(ApplicationPrincipal)auth.getPrincipal())).thenReturn(active);
        mvc.perform(get("/api/v1/projects/{projectId}/share-invitation",project).with(authentication(auth)))
            .andExpect(status().isOk()).andDo(document("project-share-invitation-get",resource(ResourceSnippetParameters.builder().responseFields(fieldWithPath("state").description("Invitation state"),fieldWithPath("code").optional().description("Crockford invitation code"),fieldWithPath("expiresAt").optional().description("Expiry timestamp")).build())));
        mvc.perform(post("/api/v1/projects/{projectId}/share-invitation",project).with(authentication(auth)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andDo(document("project-share-invitation-rotate",resource(ResourceSnippetParameters.builder().responseFields(fieldWithPath("state").description("Invitation state"),fieldWithPath("code").description("Crockford invitation code"),fieldWithPath("expiresAt").description("Expiry timestamp")).build())));
        mvc.perform(delete("/api/v1/projects/{projectId}/share-invitation",project).with(authentication(auth)).with(csrf())).andExpect(status().isNoContent())
            .andDo(document("project-share-invitation-revoke",resource(ResourceSnippetParameters.builder().build())));
    }
    @Test void documentsPreviewAndJoin() throws Exception {
        var preview=new ProjectShareInvitationService.SharePreviewResponse(ProjectShareInvitationService.State.ACTIVE,project,"Planning","Project Manager",ProjectRole.MEMBER,expiry,false);
        var joined=new ProjectShareInvitationService.ProjectControllerResponse(project,UUID.randomUUID(),"Planning",ProjectRole.MEMBER);
        when(service.preview("7K3M9F2D6R8TWX4C",(ApplicationPrincipal)auth.getPrincipal())).thenReturn(preview);
        when(service.join("7K3M9F2D6R8TWX4C",(ApplicationPrincipal)auth.getPrincipal())).thenReturn(joined);
        mvc.perform(get("/api/v1/project-invitations/{code}","7K3M9F2D6R8TWX4C").with(authentication(auth))).andExpect(status().isOk())
            .andDo(document("project-share-invitation-preview",resource(ResourceSnippetParameters.builder().responseFields(fieldWithPath("state").description("Invitation state"),fieldWithPath("projectId").optional().description("Project ID"),fieldWithPath("projectName").optional().description("Project name"),fieldWithPath("inviterName").optional().description("Inviter display name"),fieldWithPath("role").optional().description("Role granted to a new member"),fieldWithPath("expiresAt").description("Expiry timestamp"),fieldWithPath("alreadyMember").description("Whether the account is already a member")).build())));
        mvc.perform(post("/api/v1/project-invitations/{code}/join","7K3M9F2D6R8TWX4C").with(authentication(auth)).with(csrf())).andExpect(status().isOk())
            .andDo(document("project-share-invitation-join",resource(ResourceSnippetParameters.builder().responseFields(fieldWithPath("id").description("Project ID"),fieldWithPath("groupId").description("Group ID"),fieldWithPath("name").description("Project name"),fieldWithPath("role").description("Current role")).build())));
    }
    @Test void rejectsUnauthenticatedAndMissingCsrfShareRequestsBeforeServiceCalls() throws Exception {
        mvc.perform(get("/api/v1/projects/{projectId}/share-invitation",project)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/projects/{projectId}/share-invitation",project).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/project-invitations/{code}/join","7K3M9F2D6R8TWX4C").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/projects/{projectId}/share-invitation",project).with(authentication(auth))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/projects/{projectId}/share-invitation",project).with(authentication(auth))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/project-invitations/{code}/join","7K3M9F2D6R8TWX4C").with(authentication(auth))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
