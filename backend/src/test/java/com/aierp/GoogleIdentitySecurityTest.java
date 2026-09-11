package com.aierp;

import com.aierp.identity.*;
import com.aierp.identity.api.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoogleIdentitySecurityTest {
    @ParameterizedTest
    @CsvSource({"false,true,true", "true,false,true", "true,true,false"})
    void existingGrantCannotBypassWorkspaceConfiguration(boolean workspaceEnabled,boolean oidcEnabled,boolean vaultConfigured) {
        var env=new MockEnvironment().withProperty("APP_GOOGLE_WORKSPACE_ENABLED",Boolean.toString(workspaceEnabled)).withProperty("APP_OIDC_ENABLED",Boolean.toString(oidcEnabled)).withProperty("GOOGLE_CLIENT_ID","client").withProperty("GOOGLE_CLIENT_SECRET","secret");
        var vault=mock(GoogleTokenVault.class); when(vault.configured()).thenReturn(vaultConfigured);
        var repository=mock(GoogleAuthorizationRepository.class); var service=new GoogleAuthorizationService(repository,vault,env,mock(org.springframework.transaction.PlatformTransactionManager.class),mock(GoogleTokenRefreshTransport.class),null);
        assertThat(service.configured()).isFalse();
        assertThatThrownBy(()->service.credential(UUID.randomUUID(),GoogleAccess.Feature.DRIVE)).isInstanceOf(GoogleAccess.GoogleAccessException.class).hasMessage("GOOGLE_NOT_CONNECTED");
        verifyNoInteractions(repository);
    }

    @Test void credentialFailsClosedWhenWorkspaceConfigurationIsDisabled() {
        var env=new MockEnvironment().withProperty("APP_GOOGLE_WORKSPACE_ENABLED","false").withProperty("APP_OIDC_ENABLED","true").withProperty("GOOGLE_CLIENT_ID","client").withProperty("GOOGLE_CLIENT_SECRET","secret");
        var vault=mock(GoogleTokenVault.class);when(vault.configured()).thenReturn(true);
        var repository=mock(GoogleAuthorizationRepository.class);var service=new GoogleAuthorizationService(repository,vault,env,mock(org.springframework.transaction.PlatformTransactionManager.class),mock(GoogleTokenRefreshTransport.class),null);
        assertThat(service.configured()).isFalse();
        assertThatThrownBy(()->service.credential(UUID.randomUUID(),GoogleAccess.Feature.DRIVE)).isInstanceOf(GoogleAccess.GoogleAccessException.class).hasMessage("GOOGLE_NOT_CONNECTED");
        verifyNoInteractions(repository);
    }

    @Test void calendarStatusDoesNotAdvertiseSingleScopeAsConnected() {
        var repository=mock(GoogleAuthorizationRepository.class);var vault=mock(GoogleTokenVault.class);var user=UUID.randomUUID();var grant=new GoogleAuthorizationEntity();grant.userAccountId=user;grant.accessTokenCiphertext="cipher";grant.status="CONNECTED";grant.grantedScopes="https://www.googleapis.com/auth/calendar.events";
        when(repository.findByUserAccountId(user)).thenReturn(Optional.of(grant));
        var service=new GoogleAuthorizationService(repository,vault);
        assertThat(service.status(user).status(GoogleAccess.Feature.CALENDAR)).isEqualTo(GoogleAccess.Status.PERMISSION_REQUIRED);
    }

    @Test void temporaryGoogleFailureExposesCategoryWithoutPermissionStatus() {
        var failure=new GoogleAccess.GoogleAccessException("REFRESH_UNAVAILABLE");
        assertThat(failure.category()).isEqualTo(com.aierp.platform.web.ExternalServiceFailure.Category.TEMPORARY);
        assertThat(failure.status()).isNull();
    }

    @Test void incrementalGrantDroppingAnyCompleteCapabilityPreservesPriorVault() {
        var priorScopes=Map.of(
            GoogleAccess.Feature.DRIVE,"https://www.googleapis.com/auth/drive.metadata.readonly",
            GoogleAccess.Feature.GMAIL,"https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/gmail.send",
            GoogleAccess.Feature.CALENDAR,"https://www.googleapis.com/auth/calendar.calendarlist.readonly https://www.googleapis.com/auth/calendar.events");
        for (var prior : GoogleAccess.Feature.values()) {
            var repository=mock(GoogleAuthorizationRepository.class); var vault=mock(GoogleTokenVault.class); var user=UUID.randomUUID();
            var current=new GoogleAuthorizationEntity(); current.userAccountId=user; current.subject="subject"; current.accountEmail="person@example.test";
            current.accessTokenCiphertext="old-access"; current.refreshTokenCiphertext="old-refresh"; current.grantedScopes=priorScopes.get(prior); current.status="CONNECTED"; current.generation=9;
            when(repository.lockByUserAccountId(user)).thenReturn(Optional.of(current));
            var service=new GoogleAuthorizationService(repository,vault);
            var newScopes=prior==GoogleAccess.Feature.DRIVE
                ? Set.of("https://www.googleapis.com/auth/gmail.readonly","https://www.googleapis.com/auth/gmail.send")
                : Set.of("https://www.googleapis.com/auth/drive.metadata.readonly");

            assertThatThrownBy(()->service.connect(user,"subject","person@example.test","new-access","new-refresh",newScopes,java.time.Instant.now(),9))
                .isInstanceOf(GoogleAccess.GoogleAccessException.class).hasMessage("GOOGLE_SCOPE_REGRESSION");
            assertThat(current.accessTokenCiphertext).isEqualTo("old-access");
            assertThat(current.refreshTokenCiphertext).isEqualTo("old-refresh");
            assertThat(current.grantedScopes).isEqualTo(priorScopes.get(prior));
            assertThat(current.generation).isEqualTo(9);
            verify(vault,never()).encrypt(anyString(),any(),anyString());
            verify(repository,never()).saveAndFlush(any());
        }
    }
}
