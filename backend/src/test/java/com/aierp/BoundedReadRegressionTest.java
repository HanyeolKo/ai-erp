package com.aierp;

import com.aierp.platform.events.EventJournal;
import com.aierp.schedule.*;
import com.aierp.project.api.ProjectAccess;
import com.aierp.project.api.ProjectController;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.time.Instant;
import com.aierp.platform.web.ValidationFailure;
import com.aierp.project.*;
import com.aierp.identity.api.IdentityProfiles;
import com.aierp.calendarintegration.*;
import com.aierp.schedule.api.ScheduleLookup;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BoundedReadRegressionTest {
    final UUID project=UUID.randomUUID(), user=UUID.randomUUID();
    final ScheduleRepository schedules=mock(ScheduleRepository.class);
    final ScheduleParticipantRepository participants=mock(ScheduleParticipantRepository.class);
    final ScheduleAcknowledgementRepository acks=mock(ScheduleAcknowledgementRepository.class);
    final ScheduleChangeRepository changes=mock(ScheduleChangeRepository.class);
    final ProjectAccess access=mock(ProjectAccess.class);
    final com.aierp.identity.api.IdentityProfiles profiles=mock(com.aierp.identity.api.IdentityProfiles.class);
    final ScheduleService service=new ScheduleService(schedules,participants,acks,changes,access,mock(EventJournal.class),profiles);
    @Test void scheduleListPushesItsBoundIntoTheRepository() {
        var repository = mock(ScheduleRepository.class);
        var service = new ScheduleService(repository, mock(ScheduleParticipantRepository.class),
            mock(ScheduleAcknowledgementRepository.class), mock(ScheduleChangeRepository.class),
            mock(ProjectAccess.class), mock(EventJournal.class), mock(com.aierp.identity.api.IdentityProfiles.class));
        service.list(UUID.randomUUID(), UUID.randomUUID());
        assertThat(mockingDetails(repository).getInvocations()).anySatisfy(invocation ->
            assertThat(Arrays.asList(invocation.getArguments())).anyMatch(argument ->
                argument instanceof Pageable pageable && pageable.getPageSize() >= 1 && pageable.getPageSize() <= 200));
    }
    @Test void listUsesOneBatchPerChildTypeAndNeverReadsPerScheduleHistory() {
        var one=schedule(UUID.randomUUID());var two=schedule(UUID.randomUUID());
        var from=Instant.parse("2026-09-01T00:00:00Z");var to=from.plusSeconds(86400);
        var bounds=PageRequest.of(2,20,Sort.by("startsAt","id"));
        when(schedules.findWindow(project,from,to,bounds)).thenReturn(List.of(one,two));
        var p=new ScheduleParticipantEntity();p.scheduleId=one.id;p.memberUserAccountId=user;
        var a=new ScheduleAcknowledgementEntity();a.scheduleId=one.id;a.userAccountId=user;a.businessRevision=1;
        when(participants.findByScheduleIdIn(List.of(one.id,two.id))).thenReturn(List.of(p));
        when(acks.findCurrentByScheduleIds(List.of(one.id,two.id))).thenReturn(List.of(a));
        var result=service.list(project,user,from,to,2,20);
        assertThat(result).hasSize(2);assertThat(result.getFirst().participants().getFirst().acknowledged()).isTrue();
        assertThat(result).allSatisfy(s->assertThat(s.changes()).isEmpty());
        verify(schedules).findWindow(project,from,to,bounds);
        verify(participants).findByScheduleIdIn(List.of(one.id,two.id));verifyNoMoreInteractions(participants);
        verify(acks).findCurrentByScheduleIds(List.of(one.id,two.id));verifyNoMoreInteractions(acks);
        verifyNoInteractions(changes);
        verifyNoInteractions(profiles);
    }
    @Test void invalidBoundsNeverReachDatabase() {
        assertThatThrownBy(()->service.list(project,user,null,null,0,201)).isInstanceOf(ValidationFailure.class);
        assertThatThrownBy(()->service.list(project,user,null,null,-1,100)).isInstanceOf(ValidationFailure.class);
        assertThatThrownBy(()->service.list(project,user,Instant.now(),Instant.EPOCH,0,100)).isInstanceOf(ValidationFailure.class);
        verifyNoInteractions(schedules);
    }
    @Test void detailHistoryHasAnExplicitDatabaseLimitAndChronologicalRecentSlice() {
        var s=schedule(UUID.randomUUID());when(schedules.findByIdAndProjectId(s.id,project)).thenReturn(Optional.of(s));
        var newer=new ScheduleChangeEntity();newer.changeType="SCHEDULE_CONFIRMED";newer.businessRevision=1;
        var older=new ScheduleChangeEntity();older.changeType="SCHEDULE_CREATED";older.businessRevision=0;
        var page=PageRequest.of(0,2,Sort.by(Sort.Direction.DESC,"createdAt","id"));
        when(changes.findByScheduleId(s.id,page)).thenReturn(List.of(newer,older));
        assertThat(service.detail(project,s.id,user,2).changes()).extracting(c->c.type()).containsExactly("SCHEDULE_CREATED","SCHEDULE_CONFIRMED");
        verify(changes).findByScheduleId(s.id,page);verifyNoMoreInteractions(changes);
    }
    @Test void detailEnrichesOnlyStoredInternalParticipantsWithOneBoundedProfileRead() {
        var s=schedule(UUID.randomUUID());
        var internal=new ScheduleParticipantEntity();internal.scheduleId=s.id;internal.memberUserAccountId=user;
        var external=new ScheduleParticipantEntity();external.scheduleId=s.id;external.externalEmail="guest@example.test";
        when(schedules.findByIdAndProjectId(s.id,project)).thenReturn(Optional.of(s));
        when(participants.findByScheduleId(s.id)).thenReturn(List.of(internal,external));
        when(profiles.find(List.of(user))).thenReturn(Map.of(user,new com.aierp.identity.api.IdentityProfiles.PublicProfile(user,"Ada","ada@example.test")));
        var result=service.detail(project,s.id,user);
        assertThat(result.participants()).hasSize(2);
        assertThat(result.participants().get(0).displayName()).isEqualTo("Ada");
        assertThat(result.participants().get(0).email()).isEqualTo("ada@example.test");
        assertThat(result.participants().get(1).displayName()).isNull();
        assertThat(result.participants().get(1).email()).isNull();
        verify(profiles).find(List.of(user));
    }
    @Test void detailAuthorizesAndFindsScheduleBeforeAnyProfileRead() {
        var missing=UUID.randomUUID();
        when(access.role(project,user)).thenThrow(new org.springframework.security.access.AccessDeniedException("PROJECT_ACCESS_DENIED"));
        assertThatThrownBy(()->service.detail(project,missing,user)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(profiles);
        org.mockito.Mockito.reset(access);
        when(schedules.findByIdAndProjectId(missing,project)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.detail(project,missing,user)).isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(profiles);
    }
    @Test void detailReadsExactlyTwoHundredStoredIdsOnceAndLeavesMissingProfilesNull() {
        var s=schedule(UUID.randomUUID());
        var stored=java.util.stream.IntStream.range(0,200).mapToObj(i -> {
            var p=new ScheduleParticipantEntity();p.scheduleId=s.id;p.memberUserAccountId=UUID.nameUUIDFromBytes(("participant-"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8));return p;
        }).toList();
        var ids=stored.stream().map(p->p.memberUserAccountId).toList();
        when(schedules.findByIdAndProjectId(s.id,project)).thenReturn(Optional.of(s));
        when(participants.findByScheduleId(s.id)).thenReturn(stored);
        when(profiles.find(ids)).thenReturn(Map.of(
            ids.get(100),new com.aierp.identity.api.IdentityProfiles.PublicProfile(ids.get(100),"Participant 101","p101@example.test"),
            ids.get(199),new com.aierp.identity.api.IdentityProfiles.PublicProfile(ids.get(199),"Participant 200","p200@example.test")));
        var result=service.detail(project,s.id,user);
        assertThat(result.participants()).hasSize(200);
        assertThat(result.participants().get(100).displayName()).isEqualTo("Participant 101");
        assertThat(result.participants().get(199).email()).isEqualTo("p200@example.test");
        assertThat(result.participants().get(0).displayName()).isNull();
        verify(profiles).find(ids);
    }
    @Test void dashboardExcludesViewersFromPendingCountAndNeverOffersViewerActions() {
        UUID viewer=UUID.randomUUID();
        var activeCount=pending(user,3);var viewerCount=pending(viewer,9);
        when(access.role(project,viewer)).thenReturn("VIEWER");
        when(participants.pendingCounts(project,PageRequest.of(0,200))).thenReturn(List.of(activeCount,viewerCount));
        when(access.eligibleAcknowledgers(project,List.of(user,viewer))).thenReturn(Set.of(user));
        when(schedules.countByProjectId(project)).thenReturn(900L);
        var result=service.dashboard(project,viewer);
        assertThat(result.pendingAcknowledgementCount()).isEqualTo(3);assertThat(result.scheduleCount()).isEqualTo(900);
        assertThat(result.actionQueue()).isEmpty();
        verify(schedules,never()).findPendingForUser(any(),any(),any());
        verify(access).eligibleAcknowledgers(project,List.of(user,viewer));
        verifyNoInteractions(changes,acks,profiles);
    }
    @Test void dashboardPendingEligibilityIsProcessedInBoundedBatches() {
        var counts=java.util.stream.IntStream.range(0,200).mapToObj(i->pending(UUID.randomUUID(),1)).toList();
        when(participants.pendingCounts(project,PageRequest.of(0,200))).thenReturn(counts);
        var last=pending(user,2);
        when(participants.pendingCounts(project,PageRequest.of(1,200))).thenReturn(List.of(last));
        when(access.eligibleAcknowledgers(eq(project),any())).thenAnswer(i->new HashSet<>((Collection<UUID>)i.getArgument(1)));
        assertThat(service.dashboard(project,user).pendingAcknowledgementCount()).isEqualTo(202);
        verify(access,times(2)).eligibleAcknowledgers(eq(project),argThat(ids->ids.size()<=200));
        verify(participants).pendingCounts(project,PageRequest.of(1,200));
    }
    @Test void calendarProjectionRequiresTheScheduleToExistInThisProject() {
        var calendars=mock(ProjectCalendarRepository.class);var projections=mock(CalendarProjectionRepository.class);
        var calendar=new CalendarService(mock(CalendarConnectionRepository.class),calendars,projections,new DisabledCalendarAdapter(),access,new ScheduleLookup(schedules));
        UUID unknown=UUID.randomUUID();
        assertThatThrownBy(()->calendar.projection(project,unknown,user)).isInstanceOf(NoSuchElementException.class);
        verify(schedules).existsByIdAndProjectId(unknown,project);verifyNoInteractions(calendars,projections);
        when(schedules.existsByIdAndProjectId(unknown,project)).thenReturn(true);
        assertThat(calendar.projection(project,unknown,user).status()).isEqualTo("NOT_CONNECTED");
    }
    @Test void calendarRiskUsesDatabaseCountRatherThanMaterializingProjections() {
        var calendars=mock(ProjectCalendarRepository.class);var projections=mock(CalendarProjectionRepository.class);
        var c=new ProjectCalendarEntity();c.id=UUID.randomUUID();
        when(calendars.findByProjectId(project)).thenReturn(Optional.of(c));
        when(projections.countByProjectCalendarIdAndStatusNot(c.id,"SYNCED")).thenReturn(10000L);
        assertThat(new com.aierp.calendarintegration.api.CalendarDashboard(calendars,projections).riskCount(project)).isEqualTo(10000);
        verify(projections).countByProjectCalendarIdAndStatusNot(c.id,"SYNCED");verifyNoMoreInteractions(projections);
    }
    @Test void projectListLoadsOneBoundedMembershipPageAndOneProjectBatch() {
        var memberships=mock(ProjectMemberRepository.class);var projects=mock(ProjectRepository.class);
        var m=new ProjectMemberEntity();m.projectId=project;m.userAccountId=user;m.role=ProjectRole.MANAGER;
        var bounds=PageRequest.of(0,100,Sort.by("projectId"));
        when(memberships.findByUserAccountId(user,bounds)).thenReturn(List.of(m));
        when(projects.findAllById(List.of(project))).thenReturn(List.of(new ProjectEntity(project,UUID.randomUUID(),"Planning")));
        var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(new com.aierp.identity.api.ApplicationPrincipal(user,"a@example.test",true),null,List.of());
        assertThat(new ProjectController(projects,memberships,mock(com.aierp.group.api.GroupAccess.class),mock(ProjectCreationRequestRepository.class),mock(IdentityProfiles.class)).list(auth,null,null)).hasSize(1);
        verify(memberships).findByUserAccountId(user,bounds);verifyNoMoreInteractions(memberships);
        verify(projects).findAllById(List.of(project));verifyNoMoreInteractions(projects);
    }
    private ScheduleEntity schedule(UUID id) {
        var s=new ScheduleEntity();s.id=id;s.projectId=project;s.businessRevision=1;return s;
    }
    private ScheduleParticipantRepository.PendingCount pending(UUID id,long count) {
        var p=mock(ScheduleParticipantRepository.PendingCount.class);when(p.getUserId()).thenReturn(id);when(p.getPendingCount()).thenReturn(count);return p;
    }
}
