package com.aierp;

import com.aierp.schedule.*;
import com.aierp.project.*;
import com.aierp.project.api.ProjectAccess;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import static org.assertj.core.api.Assertions.*;

/** Executes the read queries locally; PostgreSQL migration/concurrency still belongs to integrationTest. */
@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true","spring.jpa.properties.hibernate.auto_quote_keyword=true"})
@Import(ProjectAccess.class)
class ReadQueryDatabaseTest {
    @Autowired EntityManager em;
    @Autowired ScheduleRepository schedules;
    @Autowired ScheduleParticipantRepository participants;
    @Autowired ScheduleAcknowledgementRepository acks;
    @Autowired ScheduleChangeRepository changes;
    @Autowired ProjectAccess access;
    final UUID project=UUID.randomUUID(), user=UUID.randomUUID();
    final Instant start=Instant.parse("2026-09-07T10:00:00Z");
    @Test void databaseAppliesWindowPageOrderingAndCurrentAckFiltering() {
        var before=schedule(start.minusSeconds(7200),ScheduleEntity.Status.CONFIRMED);
        var current=schedule(start,ScheduleEntity.Status.CONFIRMED);
        var later=schedule(start.plusSeconds(7200),ScheduleEntity.Status.CONFIRMED);
        schedule(start,ScheduleEntity.Status.CANCELLED);
        participant(before.id,user);participant(current.id,user);participant(later.id,user);
        ack(before.id,user,1);ack(current.id,user,0); // old ACK must not hide the current revision.
        em.flush();em.clear();
        var page=PageRequest.of(0,1,Sort.by("startsAt","id"));
        assertThat(schedules.findUpcoming(project,start.minusSeconds(1),start.plusSeconds(10800),page)).extracting(s->s.id).containsExactly(current.id);
        assertThat(schedules.findPendingForUser(project,user,page)).extracting(s->s.id).containsExactly(current.id);
        assertThat(schedules.findWindow(project,start.minusSeconds(1),start.plusSeconds(10800),PageRequest.of(1,1,Sort.by("startsAt","id")))).hasSize(1);
        assertThat(schedules.countByProjectId(project)).isEqualTo(4);
        assertThat(schedules.existsByIdAndProjectId(current.id,project)).isTrue();
        assertThat(schedules.existsByIdAndProjectId(current.id,UUID.randomUUID())).isFalse();
        assertThat(acks.findCurrentByScheduleIds(List.of(before.id,current.id))).extracting(a->a.scheduleId).containsExactly(before.id);
        assertThat(participants.findByScheduleIdIn(List.of(current.id,later.id))).hasSize(2);
        var pending=participants.pendingCounts(project,PageRequest.of(0,200));
        assertThat(pending).hasSize(1);assertThat(pending.getFirst().getUserId()).isEqualTo(user);assertThat(pending.getFirst().getPendingCount()).isEqualTo(2);
    }
    @Test void projectOwnedEligibilityExcludesViewerAndUnknownUserInOneBatch() {
        UUID viewer=UUID.randomUUID(), unknown=UUID.randomUUID();
        em.persist(new ProjectEntity(project,UUID.randomUUID(),"Project"));
        membership(user,ProjectRole.MEMBER);membership(viewer,ProjectRole.VIEWER);em.flush();em.clear();
        assertThat(access.eligibleAcknowledgers(project,List.of(user,viewer,unknown))).containsExactly(user);
    }
    @Test void historyReadsOnlyRequestedMostRecentRows() {
        var schedule=schedule(start,ScheduleEntity.Status.DRAFT);
        for(int i=0;i<6;i++) {
            var change=new ScheduleChangeEntity();change.id=UUID.randomUUID();change.scheduleId=schedule.id;change.businessRevision=i;
            change.changeType="SCHEDULE_CHANGED";change.changedBy=user;change.createdAt=start.plusSeconds(i);em.persist(change);
        }
        em.flush();em.clear();
        assertThat(changes.findByScheduleId(schedule.id,PageRequest.of(0,2,Sort.by(Sort.Direction.DESC,"createdAt","id")))).extracting(c->c.businessRevision).containsExactly(5L,4L);
    }
    private ScheduleEntity schedule(Instant time,ScheduleEntity.Status status) {
        var s=new ScheduleEntity();s.id=UUID.randomUUID();s.projectId=project;s.createdBy=user;s.title="Planning";s.startsAt=time;
        s.endsAt=time.plusSeconds(3600);s.status=status;s.businessRevision=1;s.updatedAt=start;em.persist(s);return s;
    }
    private void participant(UUID schedule,UUID member) {
        var p=new ScheduleParticipantEntity();p.id=UUID.randomUUID();p.scheduleId=schedule;p.memberUserAccountId=member;em.persist(p);
    }
    private void ack(UUID schedule,UUID member,long revision) {
        var a=new ScheduleAcknowledgementEntity();a.scheduleId=schedule;a.userAccountId=member;a.businessRevision=revision;a.acknowledgedAt=start;em.persist(a);
    }
    private void membership(UUID member,ProjectRole role) {
        var m=new ProjectMemberEntity();m.projectId=project;m.userAccountId=member;m.role=role;em.persist(m);
    }
}
