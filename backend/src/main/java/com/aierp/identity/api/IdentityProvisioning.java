package com.aierp.identity.api;
import com.aierp.identity.*;
import com.aierp.platform.web.Checks;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityProvisioning {
    private final UserAccountRepository accounts;
    private final GoogleIdentityRepository identities;
    public IdentityProvisioning(UserAccountRepository accounts,GoogleIdentityRepository identities) {this.accounts=accounts;this.identities=identities;}
    @Transactional public ApplicationPrincipal provision(String subject,String email,boolean verified,String displayName) {
        if(subject==null || subject.isBlank() || !verified) throw new org.springframework.security.access.AccessDeniedException("VERIFIED_GOOGLE_IDENTITY_REQUIRED");
        email=Checks.email(email);
        identities.lockSubject(subject);
        var identity=identities.findBySubject(subject).orElse(null);
        var account=identity==null?new UserAccountEntity():accounts.findById(identity.userAccountId).orElseThrow();
        Instant now=Instant.now();
        if(account.id==null) {account.id=UUID.randomUUID();account.createdAt=now;}
        account.email=email;account.emailVerifiedAt=now;account.updatedAt=now;
        account.displayName=displayName==null || displayName.isBlank()?email:displayName.substring(0,Math.min(200,displayName.length()));
        accounts.saveAndFlush(account);
        if(identity==null) {identity=new GoogleIdentityEntity();identity.id=UUID.randomUUID();identity.subject=subject;identity.userAccountId=account.id;identity.createdAt=now;}
        identity.email=email;identity.updatedAt=now;identities.saveAndFlush(identity);
        return new ApplicationPrincipal(account.id,account.email,true);
    }
}
