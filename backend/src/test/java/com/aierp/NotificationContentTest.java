package com.aierp;

import com.aierp.identity.api.ApplicationPrincipal;
import com.aierp.notification.*;
import com.aierp.notification.api.NotificationController;
import com.aierp.platform.events.*;
import com.aierp.project.api.ProjectNotificationAccess;
import com.aierp.schedule.api.ScheduleLookup;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationContentTest {
    @Test void eventSnapshotSurvivesJsonRoundTripAndLegacySevenFieldJson() throws Exception {
        var snapshot=new DomainEvent.NotificationSnapshot("Old project","Old schedule","Morgan",Instant.parse("2026-09-30T10:00:00Z"),"CONFIRMED",4,"Schedule details changed",
            List.of(new DomainEvent.ChangedField("title","Title","Before","After")));
        var event=new DomainEvent(UUID.randomUUID(),"SCHEDULE_CHANGED",UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),List.of(UUID.randomUUID()),4,snapshot);
        var mapper=new ObjectMapper();
        assertThat(mapper.readValue(mapper.writeValueAsBytes(event),DomainEvent.class).notificationSnapshot()).isEqualTo(snapshot);
        var legacy="{\"publicationId\":\""+event.publicationId()+"\",\"type\":\"SCHEDULE_CHANGED\",\"aggregateId\":\""+event.aggregateId()+"\",\"projectId\":\""+event.projectId()+"\",\"actorId\":\""+event.actorId()+"\",\"recipients\":[],\"businessRevision\":4}";
        assertThat(mapper.readValue(legacy,DomainEvent.class).notificationSnapshot()).isNull();
    }

    @Test void delayedConsumerPersistsCapturedFactsWithoutReconstructingMutableSchedule() {
        var notifications=mock(NotificationRepository.class);var deliveries=mock(NotificationDeliveryRepository.class);
        UUID recipient=UUID.randomUUID();var snapshot=new DomainEvent.NotificationSnapshot("Historic project","Historic title","Morgan",Instant.parse("2026-09-30T10:00:00Z"),"CONFIRMED",2,"Schedule confirmed",List.of());
        var event=new DomainEvent(UUID.randomUUID(),"SCHEDULE_CONFIRMED",UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),List.of(recipient),2,snapshot);
        new NotificationEventConsumer(notifications,deliveries).consume(event);
        var capture=org.mockito.ArgumentCaptor.forClass(NotificationEntity.class);verify(notifications).save(capture.capture());
        var notification=capture.getValue();var content=(Map<String,Object>)notification.payload.get("content");
        assertThat(content.get("projectName")).isEqualTo("Historic project");assertThat(content.get("scheduleTitle")).isEqualTo("Historic title");assertThat(content.get("actorDisplayName")).isEqualTo("Morgan");
        assertThat(content.get("occurredAt")).isEqualTo("2026-09-30T10:00:00Z");
        assertThat(content).doesNotContainKey("email");assertThat(notification.link).contains("/schedules/");
        var mapper=new ObjectMapper();var stored=(Map<String,Object>)mapper.readValue(mapper.writeValueAsBytes(notification.payload),Map.class);
        var persistedContent=(Map<String,Object>)stored.get("content");
        assertThat(persistedContent.get("occurredAt")).isEqualTo("2026-09-30T10:00:00Z");
        verify(deliveries).save(any());
    }

    @Test void revokedDeletedAndNullTypeRowsFailClosedAndLegacyStillHasCurrentLabels() {
        var notifications=mock(NotificationRepository.class);var projects=mock(ProjectNotificationAccess.class);var schedules=mock(ScheduleLookup.class);
        var controller=new NotificationController(notifications,projects,schedules);
        UUID user=UUID.randomUUID(),project=UUID.randomUUID(),schedule=UUID.randomUUID();
        var auth=new UsernamePasswordAuthenticationToken(new ApplicationPrincipal(user,"account@example.test",true),null,List.of());
        var row=new NotificationEntity();row.id=UUID.randomUUID();row.userAccountId=user;row.type="SCHEDULE_CANCELLED";row.link="/projects/"+project+"/schedules/"+schedule;
        row.createdAt=Instant.parse("2026-09-30T12:00:00Z");row.payload=Map.of("content",Map.of("projectName","Historic project","scheduleTitle","Historic title","actorDisplayName","Morgan","occurredAt","2026-09-30T10:00:00Z","scheduleStatus","CONFIRMED","businessRevision",2,"summary","Schedule cancelled","changedFields",List.of()));
        when(notifications.findByUserAccountId(eq(user),any(Pageable.class))).thenReturn(List.of(row));
        when(projects.visibleProject(project,user)).thenReturn(Optional.empty());
        var restricted=controller.list(auth,null,null).getFirst();
        assertThat(restricted.link()).isNull();assertThat(restricted.content().provenance()).isEqualTo("RESTRICTED");
        assertThat(restricted.content().projectName()).isNull();assertThat(restricted.content().actorDisplayName()).isNull();
        when(notifications.findByIdAndUserAccountId(row.id,user)).thenReturn(Optional.of(row));
        var restrictedRead=controller.read(row.id,auth);
        assertThat(restrictedRead.link()).isNull();assertThat(restrictedRead.content().projectName()).isNull();

        when(projects.visibleProject(project,user)).thenReturn(Optional.of(new ProjectNotificationAccess.ProjectView(project,"Current project")));
        when(schedules.notificationTarget(project,schedule)).thenReturn(Optional.empty());
        var deleted=controller.list(auth,null,null).getFirst();
        assertThat(deleted.link()).isNull();assertThat(deleted.content().projectName()).isEqualTo("Historic project");
        assertThat(deleted.content().scheduleStatus()).isEqualTo("CONFIRMED");assertThat(deleted.content().resourceAvailable()).isFalse();

        row.type=null;var malformed=controller.list(auth,null,null).getFirst();
        assertThat(malformed.type()).isEqualTo("UNKNOWN");assertThat(malformed.link()).isNull();

        row.type="SCHEDULE_CHANGED";row.payload=Map.of("content","malformed payload");
        when(schedules.notificationTarget(project,schedule)).thenReturn(Optional.of(new ScheduleLookup.NotificationTarget("Current title","CANCELLED")));
        var malformedPayload=controller.list(auth,null,null).getFirst();
        assertThat(malformedPayload.content().provenance()).isEqualTo("LEGACY");assertThat(malformedPayload.content().actorDisplayName()).isNull();
        row.type="FUTURE_UNKNOWN";row.link="/projects/"+project;
        row.payload=Map.of("content",Map.of("projectName","leaked old name","scheduleTitle","leaked old title","actorDisplayName","leaked actor","summary","leaked summary"));
        var unknown=controller.list(auth,null,null).getFirst();
        assertThat(unknown.type()).isEqualTo("FUTURE_UNKNOWN");assertThat(unknown.content().projectName()).isNull();
        assertThat(unknown.content().actorDisplayName()).isNull();assertThat(unknown.content().summary()).isEqualTo("Notification details unavailable");
    }

    @Test void legacyRowsUseExplicitCurrentFieldsAndReadRemainsOwnedAndIndependent() {
        var notifications=mock(NotificationRepository.class);var projects=mock(ProjectNotificationAccess.class);var schedules=mock(ScheduleLookup.class);
        var controller=new NotificationController(notifications,projects,schedules);
        UUID user=UUID.randomUUID(),project=UUID.randomUUID(),schedule=UUID.randomUUID();
        var auth=new UsernamePasswordAuthenticationToken(new ApplicationPrincipal(user,"account@example.test",true),null,List.of());
        var row=new NotificationEntity();row.id=UUID.randomUUID();row.userAccountId=user;row.type="SCHEDULE_CHANGED";row.link="/projects/"+project+"/schedules/"+schedule;row.createdAt=Instant.now();row.payload=Map.of("businessRevision",1);
        when(notifications.findByUserAccountId(eq(user),any(Pageable.class))).thenReturn(List.of(row));
        when(projects.visibleProject(project,user)).thenReturn(Optional.of(new ProjectNotificationAccess.ProjectView(project,"Current project")));
        when(schedules.notificationTarget(project,schedule)).thenReturn(Optional.of(new ScheduleLookup.NotificationTarget("Current title","CANCELLED")));
        var response=controller.list(auth,null,null).getFirst();
        assertThat(response.content().provenance()).isEqualTo("LEGACY");assertThat(response.content().projectName()).isNull();
        assertThat(response.content().currentProjectName()).isEqualTo("Current project");assertThat(response.content().currentScheduleStatus()).isEqualTo("CANCELLED");
        UUID other=UUID.randomUUID();clearInvocations(schedules);
        when(notifications.findByIdAndUserAccountId(row.id,other)).thenReturn(Optional.empty());
        assertThatThrownBy(()->controller.read(row.id,new UsernamePasswordAuthenticationToken(new ApplicationPrincipal(other,"other@example.test",true),null,List.of())))
            .isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(schedules);
    }
}
