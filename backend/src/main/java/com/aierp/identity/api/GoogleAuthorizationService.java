package com.aierp.identity.api;

import com.aierp.identity.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Identity-owned encrypted grant store and capability gate. */
@Service
public class GoogleAuthorizationService implements GoogleAccess {
    private final GoogleAuthorizationRepository grants;
    private final GoogleTokenVault vault;
    private final org.springframework.core.env.Environment environment;
    private final org.springframework.transaction.support.TransactionTemplate transactions;
    private final com.aierp.identity.GoogleTokenRefreshTransport refreshTransport;
    private final com.aierp.identity.UserAccountRepository accounts;
    public GoogleAuthorizationService(GoogleAuthorizationRepository grants, GoogleTokenVault vault) { this.grants=grants; this.vault=vault; this.environment=null; this.transactions=null; this.refreshTransport=null; this.accounts=null; }
    @org.springframework.beans.factory.annotation.Autowired
    public GoogleAuthorizationService(GoogleAuthorizationRepository grants, GoogleTokenVault vault, org.springframework.core.env.Environment environment, org.springframework.transaction.PlatformTransactionManager transactionManager, com.aierp.identity.GoogleTokenRefreshTransport refreshTransport, com.aierp.identity.UserAccountRepository accounts) { this.grants=grants; this.vault=vault; this.environment=environment; this.transactions=new org.springframework.transaction.support.TransactionTemplate(transactionManager); this.refreshTransport=refreshTransport; this.accounts=accounts; }

    @Override @Transactional(readOnly=true)
    public Connection status(UUID userId) {
        var grant=grants.findByUserAccountId(userId).orElse(null);
        var result=new EnumMap<Feature,Status>(Feature.class);
        for(var feature:Feature.values()) result.put(feature, status(grant,feature));
        return new Connection(grant==null?null:grant.accountEmail,result);
    }

    @Override
    public Credential credential(UUID userId, Feature feature) {
        if(!configured()) throw new GoogleAccess.GoogleAccessException(Status.NOT_CONNECTED);
        var grant=grants.findByUserAccountId(userId).orElseThrow(()->new GoogleAccess.GoogleAccessException(Status.NOT_CONNECTED));
        var status=status(grant,feature);
        if(status!=Status.CONNECTED) throw new GoogleAccess.GoogleAccessException(status);
        if(grant.expiresAt!=null && grant.expiresAt.isBefore(Instant.now())) {
            if(transactions==null||refreshTransport==null) throw new GoogleAccess.GoogleAccessException(Status.REAUTH_REQUIRED);
            var claim=beginRefresh(userId);
            com.aierp.identity.GoogleTokenRefreshTransport.Result result;
            try { result=refreshTransport.refresh(claim.refreshToken()); }
            catch(RuntimeException failure) { failRefresh(claim,false); throw new GoogleAccess.GoogleAccessException("REFRESH_UNAVAILABLE"); }
            if(result.status()==400||result.status()==401) { failRefresh(claim,true); throw new GoogleAccess.GoogleAccessException(Status.REAUTH_REQUIRED); }
            if(result.status()!=200||result.accessToken()==null) { failRefresh(claim,false); throw new GoogleAccess.GoogleAccessException("REFRESH_UNAVAILABLE"); }
            if(!completeRefresh(claim,result.accessToken(),result.refreshToken(),result.expiresAt())) { failRefresh(claim,false); throw new GoogleAccess.GoogleAccessException("REFRESH_STALE"); }
            return new Credential(result.accessToken(),claim.generation()+1);
        }
        return new Credential(vault.decrypt(grant.accessTokenCiphertext,userId,grant.subject),grant.generation);
    }

    @Override @Transactional(readOnly=true)
    public boolean isCurrent(UUID userId, long generation) {
        return grants.findByUserAccountId(userId).map(g->g.generation==generation && !"REAUTH_REQUIRED".equals(g.status)).orElse(false);
    }

    /**
     * A terminal provider result must be committed against the same grant
     * generation that supplied its credential. This method deliberately uses
     * the pessimistic grant query. When called inside a receipt transaction,
     * Spring joins that transaction and retains the row lock through commit;
     * disconnect/connect therefore cannot invalidate the check in between.
     */
    @Override
    public boolean isCurrentForCommit(UUID userId, long generation) {
        if (transactions == null) return isCurrent(userId, generation);
        return transactions.execute(status -> {
            // Keep the same account -> grant lock order used by connect and
            // disconnect. A disconnect that began first must invalidate the
            // credential before the receipt can commit.
            if (accounts != null && accounts.lockById(userId).isEmpty()) return false;
            return grants.lockByUserAccountId(userId)
                    .map(g -> g.generation == generation && !"REAUTH_REQUIRED".equals(g.status))
                    .orElse(false);
        });
    }

    @Transactional(readOnly=true)
    public long generation(UUID userId) { return grants.findByUserAccountId(userId).map(g -> g.generation).orElse(0L); }
    public boolean configured() { return vault.configured() && (environment==null || (Boolean.parseBoolean(environment.getProperty("APP_GOOGLE_WORKSPACE_ENABLED","false")) && com.aierp.platform.web.OidcSettings.enabled(environment))); }

    /** Claims a refresh in a short transaction; provider I/O must happen after this method returns. */
    public RefreshClaim beginRefresh(UUID userId) {
        if(transactions==null) throw new IllegalStateException("TRANSACTION_MANAGER_REQUIRED");
        return transactions.execute(status->{
            var grant=grants.lockByUserAccountId(userId).orElseThrow(()->new GoogleAccess.GoogleAccessException(Status.NOT_CONNECTED));
            if(grant.refreshTokenCiphertext==null) throw new GoogleAccess.GoogleAccessException(Status.REAUTH_REQUIRED);
            if(grant.refreshLeaseUntil!=null && grant.refreshLeaseUntil.isAfter(Instant.now())) throw new GoogleAccess.GoogleAccessException("REFRESH_IN_PROGRESS");
            var claim=UUID.randomUUID();grant.refreshClaimToken=claim;grant.refreshLeaseUntil=Instant.now().plusSeconds(15);grants.saveAndFlush(grant);
            return new RefreshClaim(userId,claim,grant.generation,vault.decrypt(grant.refreshTokenCiphertext,userId,grant.subject),grant.subject);
        });
    }

    /** Completes only the still-current refresh claim, preventing stale rotation overwrites. */
    public boolean completeRefresh(RefreshClaim claim,String accessToken,String refreshToken,Instant expiresAt) {
        if(transactions==null) throw new IllegalStateException("TRANSACTION_MANAGER_REQUIRED");
        return transactions.execute(status->{
            var grant=grants.lockByUserAccountId(claim.userId()).orElse(null);
            if(grant==null||!Objects.equals(grant.refreshClaimToken,claim.claimToken())||grant.generation!=claim.generation())return false;
            grant.accessTokenCiphertext=vault.encrypt(accessToken,claim.userId(),grant.subject);if(refreshToken!=null&&!refreshToken.isBlank())grant.refreshTokenCiphertext=vault.encrypt(refreshToken,claim.userId(),grant.subject);grant.expiresAt=expiresAt;grant.status="CONNECTED";grant.refreshClaimToken=null;grant.refreshLeaseUntil=null;grant.generation++;grant.updatedAt=Instant.now();grants.saveAndFlush(grant);return true;
        });
    }
    public void failRefresh(RefreshClaim claim,boolean reauth) {
        if(transactions==null)return;
        transactions.executeWithoutResult(tx->{var grant=grants.lockByUserAccountId(claim.userId()).orElse(null);if(grant!=null&&Objects.equals(grant.refreshClaimToken,claim.claimToken())&&grant.generation==claim.generation()){grant.refreshClaimToken=null;grant.refreshLeaseUntil=null;if(reauth)grant.status="REAUTH_REQUIRED";grant.updatedAt=Instant.now();grants.saveAndFlush(grant);}});
    }
    public record RefreshClaim(UUID userId,UUID claimToken,long generation,String refreshToken,String subject){
        @Override public String toString(){return "RefreshClaim[userId="+userId+", claimToken="+claimToken+", generation="+generation+", refreshToken=[REDACTED]]";}
    }

    @Transactional
    public void connect(UUID userId, String subject, String email, String accessToken, String refreshToken,
                        Collection<String> scopes, Instant expiresAt) {
        connect(userId, subject, email, accessToken, refreshToken, scopes, expiresAt, -1L);
    }

    @Transactional
    public void connect(UUID userId, String subject, String email, String accessToken, String refreshToken,
                        Collection<String> scopes, Instant expiresAt, long expectedGeneration) {
        if(userId==null || subject==null || subject.isBlank() || email==null || email.isBlank() || accessToken==null || accessToken.isBlank())
            throw new IllegalArgumentException("GOOGLE_GRANT_INVALID");
        if(accounts!=null) accounts.lockById(userId).orElseThrow(()->new IllegalArgumentException("ACCOUNT_NOT_FOUND"));
        var current=grants.lockByUserAccountId(userId).orElse(null);
        if(current!=null && expectedGeneration>=0 && current.generation!=expectedGeneration) throw new GoogleAccess.GoogleAccessException("CONNECT_STALE");
        if(current!=null && current.subject!=null && !Objects.equals(current.subject,subject)) throw new GoogleAccess.GoogleAccessException("ACCOUNT_MISMATCH");
        var scopeSet=new LinkedHashSet<String>(); if(scopes!=null) scopes.stream().filter(Objects::nonNull).map(String::trim).filter(s->!s.isBlank()).forEach(scopeSet::add);
        if (current != null && current.accessTokenCiphertext != null && "CONNECTED".equals(current.status)) {
            for (var feature : Feature.values()) {
                if (supports(feature, split(current.grantedScopes)) && !supports(feature, scopeSet)) {
                    // An incremental callback is valid only when it retains every
                    // capability already proven by the prior provider grant. Do
                    // not encrypt or persist any part of a regressing grant.
                    throw new GoogleAccess.GoogleAccessException("GOOGLE_SCOPE_REGRESSION", com.aierp.platform.web.ExternalServiceFailure.Category.PERMISSION_REQUIRED);
                }
            }
        }
        var now=Instant.now();
        if(current==null) { current=new GoogleAuthorizationEntity(); current.userAccountId=userId; current.createdAt=now; current.generation=0; }
        current.subject=subject; current.accountEmail=email.trim().toLowerCase(Locale.ROOT); current.accessTokenCiphertext=vault.encrypt(accessToken,userId,subject);
        if(refreshToken!=null && !refreshToken.isBlank()) current.refreshTokenCiphertext=vault.encrypt(refreshToken,userId,subject);
        current.grantedScopes=String.join(" ",scopeSet); current.expiresAt=expiresAt; current.status=(refreshToken==null&&current.refreshTokenCiphertext==null)?"REAUTH_REQUIRED":"CONNECTED"; current.generation++; current.updatedAt=now;
        grants.saveAndFlush(current);
    }

    @Transactional
    public void disconnect(UUID userId) {
        if(accounts!=null) accounts.lockById(userId).orElseThrow(()->new IllegalArgumentException("ACCOUNT_NOT_FOUND"));
        var g=grants.lockByUserAccountId(userId).orElseGet(()->{var n=new GoogleAuthorizationEntity();n.userAccountId=userId;n.generation=0;n.createdAt=Instant.now();return n;});
        g.accessTokenCiphertext=null;g.refreshTokenCiphertext=null;g.grantedScopes="";g.status="NOT_CONNECTED";g.generation++;g.updatedAt=Instant.now();grants.saveAndFlush(g);
    }

    private Status status(GoogleAuthorizationEntity g, Feature feature) {
        if(g==null || g.accessTokenCiphertext==null) return Status.NOT_CONNECTED;
        if("REAUTH_REQUIRED".equals(g.status)) return Status.REAUTH_REQUIRED;
        var scopes=split(g.grantedScopes);
        boolean ok=supports(feature,scopes);
        if(!ok) return Status.PERMISSION_REQUIRED;
        if(feature==Feature.GMAIL && !(hasAny(scopes,"https://www.googleapis.com/auth/gmail.readonly","https://www.googleapis.com/auth/gmail.modify","https://mail.google.com/") && hasAny(scopes,"https://www.googleapis.com/auth/gmail.send","https://www.googleapis.com/auth/gmail.modify","https://mail.google.com/"))) return Status.PERMISSION_REQUIRED;
        if(feature==Feature.CALENDAR && !(hasAny(scopes,"https://www.googleapis.com/auth/calendar.calendarlist.readonly","https://www.googleapis.com/auth/calendar") && hasAny(scopes,"https://www.googleapis.com/auth/calendar.events","https://www.googleapis.com/auth/calendar"))) return Status.PERMISSION_REQUIRED;
        return "REAUTH_REQUIRED".equals(g.status)?Status.REAUTH_REQUIRED:Status.CONNECTED;
    }
    private static boolean hasAny(Set<String> values,String... candidates) { for(String c:candidates) if(values.contains(c)) return true; return false; }
    private static boolean supports(Feature feature, Set<String> scopes) {
        return switch(feature) {
            case DRIVE -> hasAny(scopes,"https://www.googleapis.com/auth/drive.metadata.readonly","https://www.googleapis.com/auth/drive","https://www.googleapis.com/auth/drive.readonly");
            case GMAIL -> hasAny(scopes,"https://www.googleapis.com/auth/gmail.readonly","https://www.googleapis.com/auth/gmail.modify","https://mail.google.com/") && hasAny(scopes,"https://www.googleapis.com/auth/gmail.send","https://www.googleapis.com/auth/gmail.modify","https://mail.google.com/");
            case CALENDAR -> hasAny(scopes,"https://www.googleapis.com/auth/calendar.calendarlist.readonly","https://www.googleapis.com/auth/calendar") && hasAny(scopes,"https://www.googleapis.com/auth/calendar.events","https://www.googleapis.com/auth/calendar");
        };
    }
    private static Set<String> split(String scopes) { return scopes==null||scopes.isBlank()?Set.of():new LinkedHashSet<>(List.of(scopes.trim().split("\\s+"))); }

}
