package com.aierp.identity;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class GoogleTokenRefreshTransportClient {
    private static final Duration DEADLINE=Duration.ofSeconds(10);
    private static final int MAX_BODY=1_000_000;
    private static final ExecutorService BODY_READERS=Executors.newCachedThreadPool(r -> {
        var thread=new Thread(r,"google-refresh-body");
        thread.setDaemon(true);
        return thread;
    });
    private final Environment env;
    private final HttpClient client;
    private final URI endpoint;
    @org.springframework.beans.factory.annotation.Autowired
    public GoogleTokenRefreshTransportClient(Environment env){
        this(env,HttpClient.newBuilder().connectTimeout(DEADLINE).followRedirects(HttpClient.Redirect.NEVER).build(),URI.create("https://oauth2.googleapis.com/token"));
    }
    GoogleTokenRefreshTransportClient(Environment env,HttpClient client,URI endpoint){
        this.env=Objects.requireNonNull(env);
        this.client=Objects.requireNonNull(client);
        this.endpoint=Objects.requireNonNull(endpoint);
    }
    public GoogleTokenRefreshTransport.Result refresh(String refreshToken){
        long deadline=System.nanoTime()+DEADLINE.toNanos();
        HttpResponse<java.io.InputStream> response=null;
        try {
            String form="client_id="+enc(env.getProperty("GOOGLE_CLIENT_ID",""))+"&client_secret="+enc(env.getProperty("GOOGLE_CLIENT_SECRET",""))+"&refresh_token="+enc(refreshToken)+"&grant_type=refresh_token";
            var req=HttpRequest.newBuilder(endpoint).timeout(DEADLINE).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build();
            var responseFuture=client.sendAsync(req,HttpResponse.BodyHandlers.ofInputStream());
            try {
                response=responseFuture.get(remaining(deadline),TimeUnit.NANOSECONDS);
            } catch (TimeoutException timeout) {
                responseFuture.cancel(true);
                return new GoogleTokenRefreshTransport.Result(null,null,null,0);
            }
            String body=readBody(response.body(),deadline);
            if(response.statusCode()!=200)return new GoogleTokenRefreshTransport.Result(null,null,null,response.statusCode());
            var json=new tools.jackson.databind.json.JsonMapper().readTree(body);String token=json.path("access_token").asText(null);if(token==null||token.isBlank())return new GoogleTokenRefreshTransport.Result(null,null,null,502);String rotated=json.path("refresh_token").asText(null);long seconds=json.path("expires_in").asLong(3600);return new GoogleTokenRefreshTransport.Result(token,rotated,Instant.now().plusSeconds(Math.min(seconds,86400)),200);
        } catch(BodyTooLarge tooLarge){return new GoogleTokenRefreshTransport.Result(null,null,null,502);}
        catch(Exception e){return new GoogleTokenRefreshTransport.Result(null,null,null,0);}
        finally { if(response!=null) try { response.body().close(); } catch(Exception ignored) {} }
    }
    private static String readBody(java.io.InputStream input,long deadline) throws Exception {
        var read=BODY_READERS.submit(() -> {
            try(var in=input;var out=new java.io.ByteArrayOutputStream()) {
                byte[] buffer=new byte[4096]; int total=0,n;
                while((n=in.read(buffer))!=-1){ total+=n; if(total>MAX_BODY) throw new BodyTooLarge(); out.write(buffer,0,n); }
                return out.toString(StandardCharsets.UTF_8);
            }
        });
        long remainingNanos=deadline-System.nanoTime();
        if (remainingNanos<=0) { read.cancel(true); try { input.close(); } catch(Exception ignored) {} throw new TimeoutException("GOOGLE_HTTP_DEADLINE"); }
        try { return read.get(remainingNanos,TimeUnit.NANOSECONDS); }
        catch (ExecutionException failure) {
            if (failure.getCause() instanceof BodyTooLarge tooLarge) throw tooLarge;
            throw failure;
        }
        catch (TimeoutException timeout) { read.cancel(true); try { input.close(); } catch(Exception ignored) {} throw timeout; }
    }
    private static long remaining(long deadline) { long value=deadline-System.nanoTime(); if(value<=0) throw new IllegalStateException("GOOGLE_HTTP_DEADLINE"); return value; }
    private static final class BodyTooLarge extends RuntimeException {}
    private static String enc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
}
