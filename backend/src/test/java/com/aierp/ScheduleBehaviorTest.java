package com.aierp;
import com.aierp.schedule.*;
import com.aierp.schedule.api.ScheduleController.*;
import com.aierp.project.api.ProjectAccess;
import com.aierp.platform.events.EventJournal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ScheduleBehaviorTest {
    final UUID project=UUID.randomUUID(), id=UUID.randomUUID(), user=UUID.randomUUID();
    final ScheduleRepository schedules=mock(ScheduleRepository.class);
    final ScheduleParticipantRepository participants=mock(ScheduleParticipantRepository.class);
    final ScheduleAcknowledgementRepository acks=mock(ScheduleAcknowledgementRepository.class);
    final ScheduleChangeRepository changes=mock(ScheduleChangeRepository.class);
    final ProjectAccess access=mock(ProjectAccess.class);
    final EventJournal events=mock(EventJournal.class);
    final com.aierp.identity.api.IdentityProfiles profiles=mock(com.aierp.identity.api.IdentityProfiles.class);
    final ScheduleService service=new ScheduleService(schedules,participants,acks,changes,access,events,profiles);
    ScheduleEntity schedule;
    @BeforeEach void setup() {
        schedule=new ScheduleEntity();schedule.id=id;schedule.projectId=project;schedule.createdBy=user;schedule.title="Planning";
        schedule.startsAt=Instant.parse("2026-09-06T10:00:00Z");schedule.endsAt=schedule.startsAt.plusSeconds(3600);schedule.status=ScheduleEntity.Status.CONFIRMED;schedule.businessRevision=2;schedule.rowVersion=5;
        when(schedules.findByIdAndProjectId(id,project)).thenReturn(Optional.of(schedule));
        when(schedules.lockByIdAndProjectId(id,project)).thenReturn(Optional.of(schedule));
        when(schedules.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
        var participant=new ScheduleParticipantEntity();participant.id=UUID.randomUUID();participant.scheduleId=id;participant.memberUserAccountId=user;
        when(participants.findByScheduleId(id)).thenReturn(List.of(participant));
        when(access.projectName(project)).thenReturn("Planning project");
        when(profiles.find(Set.of(user))).thenReturn(Map.of(user,new com.aierp.identity.api.IdentityProfiles.PublicProfile(user,"Morgan Example","private@example.test")));
    }
    @Test void acknowledgementUsesBusinessRevisionWithoutScheduleRowVersion() {
        assertThat(service.acknowledge(project,id,new Acknowledge(2L),user).rowVersion()).isEqualTo(5);
        verify(schedules,never()).saveAndFlush(any());
    }
    @Test void staleBusinessRevisionCannotBeAcknowledged() {
        assertThatThrownBy(()->service.acknowledge(project,id,new Acknowledge(1L),user)).isInstanceOf(IllegalStateException.class);
    }
    @Test void viewerCannotAcknowledgeEvenWhenListedAsParticipant() {
        when(access.role(project,user)).thenReturn("VIEWER");
        assertThatThrownBy(()->service.acknowledge(project,id,new Acknowledge(2L),user))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void externalAttendeeCannotAcknowledge() {
        when(participants.findByScheduleId(id)).thenReturn(List.of());
        assertThatThrownBy(()->service.acknowledge(project,id,new Acknowledge(2L),user)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void oldAcknowledgementDoesNotCountForNewRevision() {
        assertThat(service.detail(project,id,user).participants().getFirst().acknowledged()).isFalse();
        verify(acks).findByScheduleIdAndBusinessRevision(id,2);
    }
    @Test void cancellationAdvancesRevisionOnceAndRejectsRepeat() {
        assertThat(service.cancel(project,id,new Revision(5L),user).businessRevision()).isEqualTo(3);
        assertThatThrownBy(()->service.cancel(project,id,new Revision(5L),user)).isInstanceOf(IllegalStateException.class);
    }
    @Test void staleRowVersionNeverChangesSchedule() {
        assertThatThrownBy(()->service.cancel(project,id,new Revision(4L),user)).isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
        assertThat(schedule.status).isEqualTo(ScheduleEntity.Status.CONFIRMED);
    }
    @Test void updateEventCapturesTruthfulTransactionTimeFieldValues() {
        var beforeStart=schedule.startsAt;var beforeEnd=schedule.endsAt;
        service.update(project,id,new Write("Renamed planning",beforeStart.plusSeconds(600),beforeEnd.plusSeconds(600),5L,null,List.of(user),List.of()),user);
        var capture=org.mockito.ArgumentCaptor.forClass(com.aierp.platform.events.DomainEvent.NotificationSnapshot.class);
        verify(events).record(eq("SCHEDULE_CHANGED"),eq(id),eq(project),eq(user),anyList(),eq(3L),capture.capture());
        var snapshot=capture.getValue();
        assertThat(snapshot.projectName()).isEqualTo("Planning project");assertThat(snapshot.scheduleTitle()).isEqualTo("Renamed planning");
        assertThat(snapshot.actorDisplayName()).isEqualTo("Morgan Example");assertThat(snapshot.scheduleStatus()).isEqualTo("CONFIRMED");
        assertThat(snapshot.changedFields()).extracting(com.aierp.platform.events.DomainEvent.ChangedField::field).containsExactly("title","startsAt","endsAt");
        assertThat(snapshot.changedFields().getFirst().before()).isEqualTo("Planning");assertThat(snapshot.changedFields().getFirst().after()).isEqualTo("Renamed planning");
        assertThat(snapshot.changedFields().get(1).before()).isEqualTo(beforeStart.toString());assertThat(snapshot.changedFields().get(1).after()).isEqualTo(beforeStart.plusSeconds(600).toString());
    }
    @Test void confirmAndCancelEventsCaptureActualStateTransitions() {
        schedule.status=ScheduleEntity.Status.DRAFT;schedule.businessRevision=0;
        service.confirm(project,id,new Revision(5L),user);
        var confirmed=org.mockito.ArgumentCaptor.forClass(com.aierp.platform.events.DomainEvent.NotificationSnapshot.class);
        verify(events).record(eq("SCHEDULE_CONFIRMED"),eq(id),eq(project),eq(user),anyList(),eq(1L),confirmed.capture());
        assertThat(confirmed.getValue().scheduleStatus()).isEqualTo("CONFIRMED");
        assertThat(confirmed.getValue().changedFields()).containsExactly(new com.aierp.platform.events.DomainEvent.ChangedField("status","Status","DRAFT","CONFIRMED"));

        reset(events);schedule.rowVersion=6;schedule.status=ScheduleEntity.Status.CONFIRMED;schedule.businessRevision=1;
        service.cancel(project,id,new Revision(6L),user);
        var cancelled=org.mockito.ArgumentCaptor.forClass(com.aierp.platform.events.DomainEvent.NotificationSnapshot.class);
        verify(events).record(eq("SCHEDULE_CANCELLED"),eq(id),eq(project),eq(user),anyList(),eq(2L),cancelled.capture());
        assertThat(cancelled.getValue().scheduleStatus()).isEqualTo("CANCELLED");
        assertThat(cancelled.getValue().changedFields()).containsExactly(new com.aierp.platform.events.DomainEvent.ChangedField("status","Status","CONFIRMED","CANCELLED"));
    }
}
