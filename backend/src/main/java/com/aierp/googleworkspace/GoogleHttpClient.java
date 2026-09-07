package com.aierp.googleworkspace;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Fixed-host Google transport. Tests inject this interface with a local synthetic server. */
public interface GoogleHttpClient {
    Response execute(String method, URI uri, String accessToken, String body);
    record Response(int status, String body) {}

    @Component
    final class Default implements GoogleHttpClient {
        private final HttpClient client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(10)).build();
        @Override public Response execute(String method, URI uri, String token, String body) {
            if(!"https".equals(uri.getScheme())) throw new GoogleServiceException("PROVIDER_UNAVAILABLE");
            if(!uri.getHost().equals("www.googleapis.com") && !uri.getHost().equals("gmail.googleapis.com")) throw new GoogleServiceException("PROVIDER_UNAVAILABLE");
            try {
                var builder=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).header("Authorization","Bearer "+token).header("Accept","application/json");
                if(body!=null) builder.header("Content-Type","application/json");
                var request=builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build();
                var result=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
                try (var input=result.body(); var out=new java.io.ByteArrayOutputStream()) {
                    byte[] buffer=new byte[8192]; int total=0; int read;
                    while((read=input.read(buffer))!=-1) {
                        total+=read;
                        if(total>2_000_000) throw new GoogleServiceException("PROVIDER_RESPONSE_TOO_LARGE");
                        out.write(buffer,0,read);
                    }
                    return new Response(result.statusCode(),out.toString(java.nio.charset.StandardCharsets.UTF_8));
                }
            } catch (GoogleServiceException e) { throw e; } catch(Exception e) { throw new GoogleServiceException("PROVIDER_UNAVAILABLE",e); }
        }
    }
    class GoogleServiceException extends RuntimeException { public GoogleServiceException(String c){super(c);} public GoogleServiceException(String c,Throwable t){super(c,t);} }
}
