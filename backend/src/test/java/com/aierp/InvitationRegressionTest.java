package com.aierp;
import com.aierp.project.*;
import com.aierp.project.api.InvitationController;
import com.aierp.identity.api.ApplicationPrincipal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class InvitationRegressionTest {
    final UUID project=UUID.randomUUID(), user=UUID.randomUUID(), group=UUID.randomUUID();
    final ProjectInvitationRepository invitations=mock(ProjectInvitationRepository.class);
    final ProjectMemberRepository members=mock(ProjectMemberRepository.class);
    final ProjectInvitationEntity invitation=new ProjectInvitationEntity();
    InvitationController controller;
    final UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken(new ApplicationPrincipal(user,"member@example.test",true),null,List.of());
    @BeforeEach void setup() {
        invitation.id=UUID.randomUUID();invitation.projectId=project;invitation.token="token";invitation.email="member@example.test";invitation.status=ProjectInvitationEntity.Status.PENDING;invitation.expiresAt=Instant.now().plusSeconds(3600);
        when(invitations.findByToken("token")).thenReturn(Optional.of(invitation));
        when(invitations.findProjectIdByToken("token")).thenReturn(Optional.of(project));
        var member=new ProjectMemberEntity();member.projectId=project;member.userAccountId=user;member.role=ProjectRole.MANAGER;
        when(members.findByProjectIdAndUserAccountId(project,user)).thenReturn(Optional.of(member));
        when(invitations.lockByToken("token")).thenReturn(Optional.of(invitation));
        var projects=mock(ProjectRepository.class);
        when(projects.lockById(project)).thenReturn(Optional.of(new ProjectEntity(project,UUID.randomUUID(),"Project")));
        controller=new InvitationController(new InvitationService(invitations,projects,members,mock(com.aierp.group.api.GroupAccess.class),mock(com.aierp.platform.events.EventJournal.class)));
    }
    @Test void acceptingExistingManagerNeverOverwritesMembership() {
        controller.accept("token",auth);
        verify(members,never()).save(any());
    }
    @Test void suppliedGroupMustOwnTheProject() {
        assertThatThrownBy(()->controller.invite(group,new InvitationController.InviteRequest(project,"person@example.test"),auth)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
}
