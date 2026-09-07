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
    final ScheduleService service=new ScheduleService(schedules,participants,acks,changes,access,events,mock(com.aierp.identity.api.IdentityProfiles.class));
    ScheduleEntity schedule;
    @BeforeEach void setup() {
        schedule=new ScheduleEntity();schedule.id=id;schedule.projectId=project;schedule.createdBy=user;schedule.title="Planning";
        schedule.startsAt=Instant.parse("2026-09-06T10:00:00Z");schedule.endsAt=schedule.startsAt.plusSeconds(3600);schedule.status=ScheduleEntity.Status.CONFIRMED;schedule.businessRevision=2;schedule.rowVersion=5;
        when(schedules.findByIdAndProjectId(id,project)).thenReturn(Optional.of(schedule));
        when(schedules.lockByIdAndProjectId(id,project)).thenReturn(Optional.of(schedule));
        when(schedules.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
        var participant=new ScheduleParticipantEntity();participant.id=UUID.randomUUID();participant.scheduleId=id;participant.memberUserAccountId=user;
        when(participants.findByScheduleId(id)).thenReturn(List.of(participant));
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
}
