package com.aierp.googleworkspace.api;
import com.aierp.googleworkspace.GmailService;
import com.aierp.identity.api.ApplicationPrincipal;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
@RestController @RequestMapping("/api/v1/google/mail")
public class GmailController {
 private final GmailService service;public GmailController(GmailService service){this.service=service;}private static UUID user(Authentication a){if(a.getPrincipal() instanceof ApplicationPrincipal p)return p.userId();throw new org.springframework.security.access.AccessDeniedException("APPLICATION_PRINCIPAL_REQUIRED");}
 @GetMapping("/messages") public GmailService.MessagesResponse messages(Authentication a,@RequestParam(defaultValue="INBOX")String folder,@RequestParam(defaultValue="")String query,@RequestParam(required=false)String pageToken){return service.messages(user(a),folder,query,pageToken);}
 @GetMapping("/messages/{id}") public GmailService.MessageDetail message(@PathVariable String id,Authentication a){return service.message(user(a),id);}
 @PostMapping("/send") public GmailService.SendReceipt send(@RequestBody GmailService.SendRequest r,Authentication a){return service.send(user(a),r);}
 @GetMapping("/sends/{requestId}") public GmailService.SendReceipt receipt(@PathVariable UUID requestId,Authentication a){return service.receipt(user(a),requestId);}
}
