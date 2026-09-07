package com.aierp.identity;

import java.net.URI;

/** Identity owned refresh transport; provider I/O is outside the grant transaction. */
public interface GoogleTokenRefreshTransport {
    Result refresh(String refreshToken);
    record Result(String accessToken,String refreshToken,java.time.Instant expiresAt,int status) {}
    @org.springframework.stereotype.Component
    final class Default implements GoogleTokenRefreshTransport {
        private final com.aierp.identity.GoogleTokenRefreshTransportClient client;
        public Default(com.aierp.identity.GoogleTokenRefreshTransportClient client){this.client=client;}
        public Result refresh(String token){return client.refresh(token);}
    }
}
