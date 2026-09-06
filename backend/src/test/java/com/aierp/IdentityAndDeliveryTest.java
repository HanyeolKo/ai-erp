package com.aierp;
import com.aierp.identity.*;
import com.aierp.identity.api.*;
import com.aierp.platform.events.*;
import com.aierp.platform.web.OidcSettings;
import com.aierp.calendarintegration.*;
import com.aierp.project.api.ProjectAccess;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class IdentityAndDeliveryTest {
    @Test void googleSubjectRetainsPersistedUuidAndUpdatesVerifiedProfile() {
        var accounts=mock(UserAccountRepository.class);var identities=mock(GoogleIdentityRepository.class);
        var account=new UserAccountEntity();account.id=UUID.randomUUID();account.email="old@example.test";
        var identity=new GoogleIdentityEntity();identity.id=UUID.randomUUID();identity.subject="google-subject";identity.userAccountId=account.id;
        when(identities.findBySubject("google-subject")).thenReturn(Optional.of(identity));when(accounts.findById(account.id)).thenReturn(Optional.of(account));
        var principal=new IdentityProvisioning(accounts,identities).provision("google-subject"," Updated@Example.Test ",true,"Updated Name");
        assertThat(principal.userId()).isEqualTo(account.id);assertThat(principal.email()).isEqualTo("updated@example.test");
        assertThat(account.displayName).isEqualTo("Updated Name");assertThat(account.emailVerifiedAt).isNotNull();
    }
    @Test void unverifiedGoogleAccountCannotProvision() {
        assertThatThrownBy(()->new IdentityProvisioning(mock(UserAccountRepository.class),mock(GoogleIdentityRepository.class)).provision("sub","a@example.test",false,"Name"))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void loginRequiresFlagAndBothNonblankCredentials() {
        var env=new MockEnvironment();assertThat(OidcSettings.enabled(env)).isFalse();
        env.setProperty("APP_OIDC_ENABLED","true");env.setProperty("GOOGLE_CLIENT_ID","client");env.setProperty("GOOGLE_CLIENT_SECRET"," ");assertThat(OidcSettings.enabled(env)).isFalse();
        env.setProperty("GOOGLE_CLIENT_SECRET","test-only-secret");assertThat(OidcSettings.enabled(env)).isTrue();
        env.setProperty("APP_OIDC_ENABLED","false");assertThat(OidcSettings.enabled(env)).isFalse();
    }
    @Test void externalFailuresClassifyWithoutEscapingToScheduleCaller() {
        var projections=mock(CalendarProjectionRepository.class);var calendars=mock(ProjectCalendarRepository.class);var adapter=mock(CalendarAdapter.class);
        UUID project=UUID.randomUUID(),schedule=UUID.randomUUID(),user=UUID.randomUUID();
        var calendar=new ProjectCalendarEntity();calendar.id=UUID.randomUUID();calendar.projectId=project;
        var projection=new CalendarProjectionEntity();projection.id=UUID.randomUUID();projection.scheduleId=schedule;projection.projectCalendarId=calendar.id;projection.businessRevision=3;
        when(calendars.findByProjectId(project)).thenReturn(Optional.of(calendar));when(projections.findByScheduleIdAndProjectCalendarId(schedule,calendar.id)).thenReturn(Optional.of(projection));when(projections.lockById(projection.id)).thenReturn(Optional.of(projection));when(adapter.configured()).thenReturn(true);
        var service=new CalendarService(mock(CalendarConnectionRepository.class),calendars,projections,adapter,mock(ProjectAccess.class));
        when(adapter.deliver(projection)).thenThrow(new RuntimeException("injected transport failure"));
        assertThat(service.retry(project,schedule,user).retryClassification()).isEqualTo("TRANSIENT");
        doThrow(new CalendarAdapter.ReauthorizationRequired()).when(adapter).deliver(projection);
        assertThat(service.retry(project,schedule,user).status()).isEqualTo("REAUTH_REQUIRED");
        doThrow(new CalendarAdapter.PermanentFailure()).when(adapter).deliver(projection);
        assertThat(service.retry(project,schedule,user).retryClassification()).isEqualTo("PERMANENT");
        assertThat(projection.businessRevision).isEqualTo(3);
    }
    @Test void outboxMarksPublishedOnlyAfterSuccessfulConsumersAndSkipsReplay() {
        var publications=mock(EventPublicationRepository.class);var publication=new EventPublication();publication.id=UUID.randomUUID();
        publication.payload=new DomainEvent(publication.id,"SCHEDULE_CONFIRMED",UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),List.of(),1);
        when(publications.lockById(publication.id)).thenReturn(Optional.of(publication));
        var delivered=new ArrayList<DomainEvent>();EventConsumer consumer=delivered::add;
        var relay=new EventRelay(publications,List.of(consumer));relay.deliver(publication.id);relay.deliver(publication.id);
        assertThat(delivered).containsExactly(publication.payload);assertThat(publication.publishedAt).isNotNull();
    }
    @Test void outboxFailureRemainsPendingAndDoesNotEscapeAfterCommitListener() {
        var publications=mock(EventPublicationRepository.class);var relay=mock(EventRelay.class);
        var event=new DomainEvent(UUID.randomUUID(),"SCHEDULE_CONFIRMED",UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),List.of(),1);
        doThrow(new IllegalStateException("injected subscriber failure")).when(relay).deliver(event.publicationId());
        assertThatCode(()->new OutboxWorker(relay,publications).committed(event)).doesNotThrowAnyException();
    }
}
