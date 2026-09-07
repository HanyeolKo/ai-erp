package com.aierp;

import com.aierp.group.ErpGroupEntity;
import com.aierp.group.api.GroupAccess;
import com.aierp.group.GroupMemberEntity;
import com.aierp.group.GroupMemberRepository;
import com.aierp.group.GroupRole;
import com.aierp.identity.api.ApplicationPrincipal;
import com.aierp.platform.web.ApiExceptionHandler;
import com.aierp.platform.web.SecurityConfiguration;
import com.aierp.project.api.ProjectController;
import com.aierp.project.ProjectMemberRepository;
import com.aierp.project.ProjectRepository;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@WebMvcTest(ProjectController.class)
@Import({SecurityConfiguration.class, ApiExceptionHandler.class, GroupAccess.class})
class ProjectCreationApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProjectRepository projects;
    @MockitoBean ProjectMemberRepository members;
    @MockitoBean GroupMemberRepository groupMembers;
    @MockitoBean com.aierp.group.ErpGroupRepository groups;
    final UUID user = UUID.fromString("20000000-0000-0000-0000-000000000002");
    final UUID ownerGroup = UUID.fromString("10000000-0000-0000-0000-000000000001");
    final UUID adminGroup = UUID.fromString("10000000-0000-0000-0000-000000000002");
    final UUID memberGroup = UUID.fromString("10000000-0000-0000-0000-000000000003");
    final UUID legacyGroup = UUID.fromString("10000000-0000-0000-0000-000000000004");
    final UUID noMembershipGroup = UUID.fromString("10000000-0000-0000-0000-000000000005");
    final String contentType = "application/json;charset=UTF-8";
    final String payload = "{\"groupId\":\"%s\",\"name\":\"%s\"}";
    final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(new ApplicationPrincipal(user,"member@example.test",true),null,List.of());

    @Test void creationOptionsReturnOwnGroupsWithStablePaginationAndReasons() throws Exception {
        when(groupMembers.findByUserAccountId(eq(user), eq(PageRequest.of(0,2,Sort.by("groupId"))))).thenReturn(List.of(
            member(ownerGroup,GroupRole.OWNER),
            member(memberGroup,GroupRole.MEMBER)
        ));
        when(groupMembers.findByUserAccountId(eq(user), eq(PageRequest.of(1,2,Sort.by("groupId"))))).thenReturn(List.of(
            member(adminGroup,GroupRole.ADMIN),
            member(legacyGroup,null)
        ));
        when(groupMembers.findByUserAccountId(eq(user), eq(PageRequest.of(2,2,Sort.by("groupId"))))).thenReturn(List.of());
        when(groups.findAllById(List.of(ownerGroup,memberGroup))).thenReturn(List.of(group(ownerGroup,"Owner"),group(memberGroup,"Member")));
        when(groups.findAllById(List.of(adminGroup,legacyGroup))).thenReturn(List.of(group(adminGroup,"Admin"),group(legacyGroup,"Legacy")));

        mvc.perform(get("/api/v1/projects/creation-options").with(authentication(auth)).queryParam("page","0").queryParam("limit","2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(ownerGroup.toString())).andExpect(jsonPath("$[0].name").value("Owner")).andExpect(jsonPath("$[0].canCreate").value(true)).andExpect(jsonPath("$[0].reason").isEmpty())
            .andExpect(jsonPath("$[1].id").value(memberGroup.toString())).andExpect(jsonPath("$[1].name").value("Member")).andExpect(jsonPath("$[1].canCreate").value(false)).andExpect(jsonPath("$[1].reason").value("GROUP_ROLE_REQUIRED"));

        mvc.perform(get("/api/v1/projects/creation-options").with(authentication(auth)).queryParam("page","1").queryParam("limit","2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(adminGroup.toString())).andExpect(jsonPath("$[0].name").value("Admin")).andExpect(jsonPath("$[0].canCreate").value(true)).andExpect(jsonPath("$[0].reason").isEmpty())
            .andExpect(jsonPath("$[1].id").value(legacyGroup.toString())).andExpect(jsonPath("$[1].name").value("Legacy")).andExpect(jsonPath("$[1].canCreate").value(false)).andExpect(jsonPath("$[1].reason").value("GROUP_ROLE_NOT_CONFIGURED"));

        mvc.perform(get("/api/v1/projects/creation-options").with(authentication(auth)).queryParam("page","2").queryParam("limit","2"))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void creationOptionsExposeAnEmptyArrayForAnAccountWithNoGroups() throws Exception {
        when(groupMembers.findByUserAccountId(eq(user), any())).thenReturn(List.of());
        mvc.perform(get("/api/v1/projects/creation-options").with(authentication(auth)))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void ownerAndAdminCanCreateProjectAndReceiveManagerMembership() throws Exception {
        allowGroupRole(ownerGroup,GroupRole.OWNER);
        allowGroupRole(adminGroup,GroupRole.ADMIN);
        when(projects.save(any())).thenAnswer(i -> i.getArgument(0));
        when(members.save(any())).thenAnswer(i -> i.getArgument(0));

        create(ownerGroup,"Owner project")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groupId").value(ownerGroup.toString()))
            .andExpect(jsonPath("$.name").value("Owner project"))
            .andExpect(jsonPath("$.role").value("MANAGER"));
        create(adminGroup,"Admin project")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groupId").value(adminGroup.toString()))
            .andExpect(jsonPath("$.name").value("Admin project"))
            .andExpect(jsonPath("$.role").value("MANAGER"));

        verify(projects,atLeast(2)).save(any());
        verify(members,atLeast(2)).save(any());
    }

    @Test void memberForeignOrLegacyGroupCannotCreateAndWritesDoNotHappen() throws Exception {
        when(groupMembers.findForProjectCreation(eq(memberGroup),eq(user))).thenReturn(Optional.of(member(memberGroup,GroupRole.MEMBER)));
        when(groupMembers.findForProjectCreation(eq(legacyGroup),eq(user))).thenReturn(Optional.of(member(legacyGroup,null)));
        when(groupMembers.findForProjectCreation(eq(noMembershipGroup),eq(user))).thenReturn(Optional.empty());
        when(groups.existsById(any())).thenReturn(true);

        create(memberGroup,"Member project").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        create(legacyGroup,"Legacy project").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        create(noMembershipGroup,"No-membership project").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(projects,members);
    }

    @Test void creationRejectsInvalidNameAndMissingGroupWithFieldErrors() throws Exception {
        allowGroupRole(ownerGroup,GroupRole.OWNER);

        mvc.perform(post("/api/v1/projects").with(authentication(auth)).with(csrf().asHeader())
                .contentType(contentType).content(payload.formatted(ownerGroup,"   ")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));

        mvc.perform(post("/api/v1/projects").with(authentication(auth)).with(csrf().asHeader())
                .contentType(contentType).content(payload.formatted(ownerGroup,"x".repeat(201))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));

        mvc.perform(post("/api/v1/projects").with(authentication(auth)).with(csrf().asHeader())
                .contentType(contentType).content("{\"name\":\"Project\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("groupId"));

        verifyNoInteractions(projects,members);
    }

    @Test void creationAcceptsTwoHundredCharactersAndTrimsOuterWhitespace() throws Exception {
        allowGroupRole(ownerGroup,GroupRole.OWNER);
        when(projects.save(any())).thenAnswer(i -> i.getArgument(0));
        when(members.save(any())).thenAnswer(i -> i.getArgument(0));
        var maxName="x".repeat(200);
        create(ownerGroup,"  "+maxName+"  ")
            .andExpect(status().isOk()).andExpect(jsonPath("$.name").value(maxName));
    }

    @Test void creationRequiresAuthenticationAndCsrf() throws Exception {
        mvc.perform(get("/api/v1/projects/creation-options")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/projects").with(csrf().asHeader()).contentType(contentType).content(payload.formatted(ownerGroup,"Project")))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/projects").with(authentication(auth)).contentType(contentType).content(payload.formatted(ownerGroup,"Project")))
            .andExpect(status().isForbidden());

        verifyNoInteractions(projects,members);
    }

    private org.springframework.test.web.servlet.ResultActions create(UUID groupId, String name) throws Exception {
        return mvc.perform(post("/api/v1/projects").with(authentication(auth)).with(csrf().asHeader()).contentType(contentType)
                .content(payload.formatted(groupId,name)));
    }
    private void allowGroupRole(UUID groupId, GroupRole role) {
        when(groupMembers.findForProjectCreation(eq(groupId), eq(user))).thenReturn(Optional.of(member(groupId, role)));
        when(groups.existsById(groupId)).thenReturn(true);
    }
    private static GroupMemberEntity member(UUID groupId,GroupRole role) {
        var m = new GroupMemberEntity(); m.groupId = groupId; m.userAccountId = UUID.fromString("20000000-0000-0000-0000-000000000002"); m.role = role; return m;
    }
    private static ErpGroupEntity group(UUID id,String name) {
        var g = new ErpGroupEntity(); g.id = id; g.name = name; return g;
    }
}
