package com.aierp.project;

import com.aierp.identity.api.*;
import com.aierp.platform.events.EventJournal;
import com.aierp.platform.web.ValidationFailure;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectShareInvitationService {
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private final ProjectShareInvitationRepository invitations;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final EventJournal events;
    private final IdentityProfiles profiles;

    public ProjectShareInvitationService(ProjectShareInvitationRepository invitations, ProjectRepository projects,
            ProjectMemberRepository members, EventJournal events, IdentityProfiles profiles) {
        this.invitations=invitations; this.projects=projects; this.members=members; this.events=events; this.profiles=profiles;
    }
    private static String normalize(String input) {
        if (input == null || input.length() > 64) throw new ValidationFailure("code", "Invitation code is invalid");
        if (input.chars().anyMatch(c -> c > 0x7f || !(c == '-' || c == ' ' || Character.isLetterOrDigit(c))))
            throw new ValidationFailure("code", "Invitation code is invalid");
        var normalized=input.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
        if (normalized.length()!=16 || normalized.chars().anyMatch(c -> "0123456789ABCDEFGHJKMNPQRSTVWXYZ".indexOf(c)<0))
            throw new ValidationFailure("code", "Invitation code is invalid");
        return normalized;
    }
    private static String code() { var b=new StringBuilder(16); for(int i=0;i<16;i++) b.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]); return b.toString(); }
    private static State state(ProjectShareInvitationEntity i, Instant now) {
        if (i == null) return State.NOT_CREATED;
        if (i.revokedAt != null) return State.REVOKED;
        if (!i.expiresAt.isAfter(now)) return State.EXPIRED;
        return State.ACTIVE;
    }
    @Transactional
    public ShareInvitationResponse current(UUID projectId, ApplicationPrincipal user) {
        var project=projects.lockById(projectId).orElseThrow(NoSuchElementException::new);
        requireManager(project.id,user);
        var i=invitations.findForUpdateByProjectId(projectId).orElse(null);
        var s=state(i,Instant.now());
        return new ShareInvitationResponse(s,s==State.NOT_CREATED?null:i.code,s==State.NOT_CREATED?null:i.expiresAt);
    }
    @Transactional
    public ShareInvitationResponse rotate(UUID projectId, ApplicationPrincipal user) {
        var project=projects.lockById(projectId).orElseThrow(NoSuchElementException::new);
        requireManager(project.id,user);
        var i=invitations.findForUpdateByProjectId(projectId).orElseGet(() -> { var n=new ProjectShareInvitationEntity(); n.projectId=projectId; return n; });
        i.code=uniqueCode(); i.invitedBy=user.userId(); i.createdAt=Instant.now(); i.expiresAt=i.createdAt.plus(Duration.ofDays(7)); i.revokedAt=null;
        invitations.save(i);
        return new ShareInvitationResponse(State.ACTIVE,i.code,i.expiresAt);
    }
    @Transactional
    public void revoke(UUID projectId, ApplicationPrincipal user) {
        var project=projects.lockById(projectId).orElseThrow(NoSuchElementException::new);
        requireManager(project.id,user);
        var i=invitations.findForUpdateByProjectId(projectId).orElse(null);
        if (i != null && i.revokedAt == null) { i.revokedAt=Instant.now(); invitations.save(i); }
    }
    @Transactional(readOnly=true)
    public SharePreviewResponse preview(String rawCode, ApplicationPrincipal user) {
        verified(user); var code=normalize(rawCode);
        var i=invitations.findByCode(code).orElseThrow(NoSuchElementException::new);
        if (state(i,Instant.now()) != State.ACTIVE) return new SharePreviewResponse(state(i,Instant.now()),null,null,null,null,i.expiresAt,false);
        var project=projects.findById(i.projectId).orElseThrow(NoSuchElementException::new);
        var already=members.findByProjectIdAndUserAccountId(project.id,user.userId()).isPresent();
        var inviter=profile(i.invitedBy);
        var inviterName=inviter == null || inviter.displayName()==null || inviter.displayName().isBlank() || inviter.displayName().contains("@") ? "프로젝트 관리자" : inviter.displayName();
        return new SharePreviewResponse(State.ACTIVE,project.id,project.name,inviterName,ProjectRole.MEMBER,i.expiresAt,already);
    }
    @Transactional
    public ProjectControllerResponse join(String rawCode, ApplicationPrincipal user) {
        verified(user); var code=normalize(rawCode);
        var projectId=invitations.findProjectIdByCode(code).orElseThrow(NoSuchElementException::new);
        var project=projects.lockById(projectId).orElseThrow(NoSuchElementException::new);
        var i=invitations.findForUpdateByCode(code).orElseThrow(NoSuchElementException::new);
        if (!code.equals(i.code) || state(i,Instant.now()) != State.ACTIVE) throw new IllegalStateException("SHARE_INVITATION_CONFLICT");
        var current=members.findByProjectIdAndUserAccountId(project.id,user.userId());
        if (current.isEmpty()) {
            var member=new ProjectMemberEntity(); member.projectId=project.id; member.userAccountId=user.userId(); member.role=ProjectRole.MEMBER; members.save(member);
            events.record("INVITATION_ACCEPTED",project.id,project.id,user.userId(),List.of(user.userId()),0);
            return new ProjectControllerResponse(project.id,project.groupId,project.name,ProjectRole.MEMBER);
        }
        return new ProjectControllerResponse(project.id,project.groupId,project.name,current.get().role);
    }
    private String uniqueCode() { for (int n=0;n<8;n++) { var c=code(); if (invitations.findByCode(c).isEmpty()) return c; } throw new IllegalStateException("INVITATION_CODE_GENERATION_FAILED"); }
    private void requireManager(UUID projectId, ApplicationPrincipal user) { if (members.findByProjectIdAndUserAccountId(projectId,user.userId()).filter(m -> m.role==ProjectRole.MANAGER).isEmpty()) throw new AccessDeniedException("MANAGER_REQUIRED"); }
    private static void verified(ApplicationPrincipal user) { if (!user.emailVerified()) throw new AccessDeniedException("VERIFIED_EMAIL_REQUIRED"); }
    private IdentityProfiles.PublicProfile profile(UUID id) { return profiles.find(Set.of(id)).get(id); }
    public enum State { NOT_CREATED, ACTIVE, EXPIRED, REVOKED }
    public record ShareInvitationResponse(State state,String code,Instant expiresAt) { }
    public record SharePreviewResponse(State state,UUID projectId,String projectName,String inviterName,ProjectRole role,Instant expiresAt,boolean alreadyMember) { }
    public record ProjectControllerResponse(UUID id,UUID groupId,String name,ProjectRole role) { }
}
