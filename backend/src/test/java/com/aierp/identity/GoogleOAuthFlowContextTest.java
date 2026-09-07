package com.aierp.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aierp.identity.api.*;
import jakarta.servlet.http.HttpSession;
import java.lang.reflect.Method;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.mock.web.*;

class GoogleOAuthFlowContextTest {
    @Test
    void resolverBindsOverlappingConnectAttemptsToIndependentSingleUseStates() {
        var registration=ClientRegistration.withRegistrationId("google").clientId("id").clientSecret("secret")
            .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}").authorizationUri("https://accounts.google.test/auth")
            .tokenUri("https://oauth2.googleapis.test/token").userInfoUri("https://openidconnect.googleapis.test/userinfo")
            .userNameAttributeName("sub").clientName("Google").build();
        var resolver=new OidcConfiguration().googleAuthorizationRequestResolver(new InMemoryClientRegistrationRepository(registration));
        var request=new MockHttpServletRequest(); request.setServletPath("/oauth2/authorization/google"); request.setRequestURI("/oauth2/authorization/google");
        var session=request.getSession();
        session.setAttribute("google.connect.pending",new LinkedHashMap<>(Map.of(
            "intentA", new LinkedHashMap<>(Map.of("feature","DRIVE","user",UUID.randomUUID().toString(),"subject","subA","generation","1","created",Long.toString(System.currentTimeMillis()),"email","a@example.test"))
        )));
        session.setAttribute("google.connect.activeIntent","intentA");
        OAuth2AuthorizationRequest first=resolver.resolve(request);
        var pending=(Map<String,Map<String,String>>)session.getAttribute("google.connect.pending");
        pending.put("intentB",new LinkedHashMap<>(Map.of("feature","GMAIL","user",UUID.randomUUID().toString(),"subject","subB","generation","2","created",Long.toString(System.currentTimeMillis()),"email","b@example.test")));
        session.setAttribute("google.connect.pending",pending); session.setAttribute("google.connect.activeIntent","intentB");
        OAuth2AuthorizationRequest second=resolver.resolve(request);

        assertNotEquals(first.getState(),second.getState());
        assertEquals("CONNECT", consume(session,first.getState()).get("kind"));
        assertNull(consume(session,first.getState()),"replay must consume nothing");
        assertEquals("GMAIL", consume(session,second.getState()).get("feature"),"old callback cannot delete newer context");
        SecurityContextHolder.clearContext();
        session.removeAttribute("google.connect.activeIntent");
        OAuth2AuthorizationRequest login=resolver.resolve(request);
        assertEquals("LOGIN",consume(session,login.getState()).get("kind"),"only an unauthenticated resolver flow may provision a normal login");
    }

    @Test
    void expiredAndReplayedConnectCallbacksRestoreOriginalErpPrincipal() throws Exception {
        var configuration=new OidcConfiguration();
        var grants=mock(GoogleAuthorizationService.class);
        var provider=mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        var handler=configuration.googleSuccess(mock(IdentityProvisioning.class),grants,mock(GoogleIdentityRepository.class),provider);
        var oidc=mock(OidcUser.class); when(oidc.getSubject()).thenReturn("different-subject");
        var authentication=mock(Authentication.class); when(authentication.getPrincipal()).thenReturn(oidc);
        var request=new MockHttpServletRequest(); request.setParameter("state","expired-state");
        var session=request.getSession(); session.setAttribute("google.connect.restoreUser",UUID.randomUUID().toString()); session.setAttribute("google.connect.restoreEmail","owner@example.test");
        UUID owner=UUID.fromString((String)session.getAttribute("google.connect.restoreUser"));
        session.setAttribute("google.oauth.flows",new LinkedHashMap<>(Map.of("expired-state",new LinkedHashMap<>(Map.of("kind","CONNECT","feature","DRIVE","user",owner.toString(),"subject","expected","generation","0","created",Long.toString(System.currentTimeMillis()-700_000),"email","owner@example.test")))));

        handler.onAuthenticationSuccess(request,new MockHttpServletResponse(),authentication);
        assertEquals(owner,((ApplicationPrincipal)SecurityContextHolder.getContext().getAuthentication().getPrincipal()).userId());

        request.setParameter("state","expired-state");
        handler.onAuthenticationSuccess(request,new MockHttpServletResponse(),authentication);
        assertEquals(owner,((ApplicationPrincipal)SecurityContextHolder.getContext().getAuthentication().getPrincipal()).userId(),"replayed callback must not leave an OIDC principal");
        SecurityContextHolder.clearContext();
    }

    @Test
    void staleFailureConsumesOnlyItsStateAndLeavesNewerConnectContext() throws Exception {
        var configuration=new OidcConfiguration();
        var handler=configuration.googleFailure();
        var request=new MockHttpServletRequest(); request.setParameter("state","state-a");
        var response=new MockHttpServletResponse(); var session=request.getSession();
        UUID owner=UUID.randomUUID(); session.setAttribute("google.connect.restoreUser",owner.toString()); session.setAttribute("google.connect.restoreEmail","owner@example.test");
        putFlow(session,"state-a",new LinkedHashMap<>(Map.of("kind","CONNECT","feature","DRIVE","user",owner.toString(),"subject","a","generation","0","created",Long.toString(System.currentTimeMillis()),"email","owner@example.test")));
        putFlow(session,"state-b",new LinkedHashMap<>(Map.of("kind","CONNECT","feature","GMAIL","user",owner.toString(),"subject","b","generation","0","created",Long.toString(System.currentTimeMillis()),"email","owner@example.test")));

        handler.onAuthenticationFailure(request,response,new OAuth2AuthenticationException(new OAuth2Error("access_denied"),"provider failure"));

        assertEquals(owner,((ApplicationPrincipal)SecurityContextHolder.getContext().getAuthentication().getPrincipal()).userId());
        assertNull(consume(session,"state-a"));
        assertEquals("GMAIL",consume(session,"state-b").get("feature"));
        SecurityContextHolder.clearContext();
    }

    @Test
    void unknownLoginCallbackClearsSavedSessionAuthentication() throws Exception {
        SecurityContextHolder.clearContext();
        var configuration=new OidcConfiguration();
        var provider=mock(ObjectProvider.class); when(provider.getIfAvailable()).thenReturn(null);
        var handler=configuration.googleSuccess(mock(IdentityProvisioning.class),mock(GoogleAuthorizationService.class),mock(GoogleIdentityRepository.class),provider);
        var oidc=mock(OidcUser.class); var authentication=mock(Authentication.class); when(authentication.getPrincipal()).thenReturn(oidc);
        var request=new MockHttpServletRequest(); request.setParameter("state","unknown-state"); var response=new MockHttpServletResponse();
        var session=request.getSession(); saveSessionAuthentication(request,response,authentication);

        handler.onAuthenticationSuccess(request,response,authentication);

        assertSessionContextIsEmpty(request,response,session);
        SecurityContextHolder.clearContext();
    }

    @Test
    void expiredLoginCallbackClearsSavedSessionAuthentication() throws Exception {
        SecurityContextHolder.clearContext();
        var configuration=new OidcConfiguration();
        var provider=mock(ObjectProvider.class); when(provider.getIfAvailable()).thenReturn(null);
        var handler=configuration.googleSuccess(mock(IdentityProvisioning.class),mock(GoogleAuthorizationService.class),mock(GoogleIdentityRepository.class),provider);
        var oidc=mock(OidcUser.class); var authentication=mock(Authentication.class); when(authentication.getPrincipal()).thenReturn(oidc);
        var request=new MockHttpServletRequest(); request.setParameter("state","expired-login"); var response=new MockHttpServletResponse();
        var session=request.getSession(); saveSessionAuthentication(request,response,authentication);
        putFlow(session,"expired-login",new LinkedHashMap<>(Map.of("kind","LOGIN","created",Long.toString(System.currentTimeMillis()-700_000))));

        handler.onAuthenticationSuccess(request,response,authentication);

        assertSessionContextIsEmpty(request,response,session);
        SecurityContextHolder.clearContext();
    }

    private static void saveSessionAuthentication(MockHttpServletRequest request,MockHttpServletResponse response,Authentication authentication) {
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        new HttpSessionSecurityContextRepository().saveContext(context,request,response);
    }

    private static void assertSessionContextIsEmpty(MockHttpServletRequest request,MockHttpServletResponse response,HttpSession session) {
        assertNull(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
        var loaded=new HttpSessionSecurityContextRepository().loadContext(new HttpRequestResponseHolder(request,response));
        assertNull(loaded.getAuthentication());
    }

    @SuppressWarnings("unchecked")
    private static void putFlow(HttpSession session,String state,Map<String,String> flow) throws Exception {
        Method method=OidcConfiguration.class.getDeclaredMethod("putFlow",HttpSession.class,String.class,Map.class);
        method.setAccessible(true); method.invoke(null,session,state,flow);
    }
    @SuppressWarnings("unchecked")
    private static Map<String,String> consume(HttpSession session,String state) {
        try {
            Method method=OidcConfiguration.class.getDeclaredMethod("consumeFlow",HttpSession.class,String.class);
            method.setAccessible(true); return (Map<String,String>)method.invoke(null,session,state);
        } catch (Exception e) { throw new AssertionError(e); }
    }
}
