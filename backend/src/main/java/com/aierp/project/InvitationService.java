package com.aierp.project;

import com.aierp.identity.api.ApplicationPrincipal;
import com.aierp.group.api.GroupAccess;
import com.aierp.platform.events.EventJournal;
import com.aierp.platform.web.*;
import com.aierp.project.api.InvitationController.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

@Service @Transactional(readOnly=true)
public class InvitationService {
    private final ProjectInvitationRepository invitations;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final GroupAccess groups;
    private final EventJournal events;
    public InvitationService(ProjectInvitationRepository invitations,ProjectRepository projects,ProjectMemberRepository members,GroupAccess groups,EventJournal events) {
        this.invitations=invitations;this.projects=projects;this.members=members;this.groups=groups;this.events=events;
    }
    @Transactional public InvitationResponse invite(UUID groupId,InviteRequest input,ApplicationPrincipal user) {
        if(input.projectId()==null) throw new ValidationFailure("projectId","Project ID required");
        // All invitation creation/acceptance serializes on this existing project row.
        var project=projects.lockById(input.projectId()).orElseThrow(NoSuchElementException::new);
        if(!project.groupId.equals(groupId)) throw new AccessDeniedException("GROUP_PROJECT_MISMATCH");
        if(members.findByProjectIdAndUserAccountId(project.id,user.userId()).filter(m->m.role==ProjectRole.MANAGER).isEmpty())
            throw new AccessDeniedException("MANAGER_REQUIRED");
        var email=Checks.email(input.email());var now=Instant.now();
        for(var previous:invitations.findByProjectIdAndEmailAndStatus(project.id,email,ProjectInvitationEntity.Status.PENDING)) {
            if(previous.expiresAt.isAfter(now)) throw new IllegalStateException("INVITATION_PENDING");
            previous.status=ProjectInvitationEntity.Status.EXPIRED;previous.resolvedAt=now;
        }
        invitations.flush();
        var invitation=new ProjectInvitationEntity();invitation.id=UUID.randomUUID();invitation.projectId=project.id;
        invitation.email=email;invitation.token=UUID.randomUUID().toString()+UUID.randomUUID().toString();
        invitation.status=ProjectInvitationEntity.Status.PENDING;invitation.expiresAt=now.plus(Duration.ofDays(7));invitation.invitedBy=user.userId();
        invitations.saveAndFlush(invitation);
        events.record("INVITATION_CREATED",invitation.id,project.id,user.userId(),List.of(user.userId()),0);
        return response(invitation);
    }
    public InvitationResponse detail(String token,ApplicationPrincipal user) {
        var invitation=invitations.findByToken(token).orElseThrow(NoSuchElementException::new);
        emailMatches(invitation,user);return response(invitation);
    }
    @Transactional public InvitationResponse resolve(String token,ApplicationPrincipal user,boolean accept) {
        // Read a scalar first: loading an entity before waiting on the project lock would cache stale PENDING state.
        var projectId=invitations.findProjectIdByToken(token).orElseThrow(NoSuchElementException::new);
        projects.lockById(projectId).orElseThrow(NoSuchElementException::new);
        var invitation=invitations.lockByToken(token).orElseThrow(NoSuchElementException::new);
        emailMatches(invitation,user);
        if(invitation.status!=ProjectInvitationEntity.Status.PENDING || !invitation.expiresAt.isAfter(Instant.now())) throw new IllegalStateException("INVITATION_CONFLICT");
        if(accept && members.findByProjectIdAndUserAccountId(invitation.projectId,user.userId()).isEmpty()) {
            var member=new ProjectMemberEntity();member.projectId=invitation.projectId;member.userAccountId=user.userId();member.role=ProjectRole.MEMBER;members.save(member);
        }
        invitation.status=accept?ProjectInvitationEntity.Status.ACCEPTED:ProjectInvitationEntity.Status.REJECTED;invitation.resolvedAt=Instant.now();
        invitations.saveAndFlush(invitation);
        events.record(accept?"INVITATION_ACCEPTED":"INVITATION_REJECTED",invitation.id,invitation.projectId,user.userId(),List.of(user.userId()),0);
        return response(invitation);
    }
    private void emailMatches(ProjectInvitationEntity invitation,ApplicationPrincipal user) {
        if(!user.emailVerified() || !invitation.email.equals(Checks.email(user.email()))) throw new AccessDeniedException("VERIFIED_EMAIL_REQUIRED");
    }
    private InvitationResponse response(ProjectInvitationEntity i) {
        var status=i.status==ProjectInvitationEntity.Status.PENDING && !i.expiresAt.isAfter(Instant.now())?ProjectInvitationEntity.Status.EXPIRED:i.status;
        return new InvitationResponse(i.id,i.projectId,i.email,i.token,status,i.expiresAt);
    }
}
