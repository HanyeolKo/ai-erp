package com.aierp.googleworkspace;

import com.aierp.identity.api.*;
import com.aierp.project.api.ProjectAccess;
import java.net.URI;
import java.net.URLEncoder;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class DriveService {
 private static final JsonMapper JSON=new JsonMapper();
 private final GoogleAccess access; private final GoogleHttpClient http; private final DriveReferenceRepository refs; private final ProjectAccess projects; private final com.aierp.identity.api.IdentityProfiles profiles; private final TransactionTemplate transactions;
 public DriveService(GoogleAccess access,GoogleHttpClient http,DriveReferenceRepository refs,ProjectAccess projects){this(access,http,refs,projects,null,null,true);}
 public DriveService(GoogleAccess access,GoogleHttpClient http,DriveReferenceRepository refs,ProjectAccess projects,com.aierp.identity.api.IdentityProfiles profiles){this(access,http,refs,projects,profiles,null,true);}
 @Autowired public DriveService(GoogleAccess access,GoogleHttpClient http,DriveReferenceRepository refs,ProjectAccess projects,com.aierp.identity.api.IdentityProfiles profiles,org.springframework.transaction.PlatformTransactionManager transactionManager){this(access,http,refs,projects,profiles,transactionManager==null?null:new TransactionTemplate(transactionManager),true);}
 private DriveService(GoogleAccess access,GoogleHttpClient http,DriveReferenceRepository refs,ProjectAccess projects,com.aierp.identity.api.IdentityProfiles profiles,TransactionTemplate transactions,boolean ignored){this.access=access;this.http=http;this.refs=refs;this.projects=projects;this.profiles=profiles;this.transactions=transactions;}
 public FilesResponse files(UUID user,String query,String pageToken){
   var credential=access.credential(user,GoogleAccess.Feature.DRIVE); String q=query==null?"":query.trim(); if(q.length()>200) throw new IllegalArgumentException("QUERY_TOO_LONG");
   if(pageToken!=null && pageToken.length()>1000) throw new IllegalArgumentException("PAGE_TOKEN_INVALID");
   String escaped=q.replace("\\","\\\\").replace("'","\\'"); String driveQuery="trashed = false"+(escaped.isBlank()?"":" and name contains '"+escaped+"'");
   String fields="nextPageToken,files(id,name,mimeType,modifiedTime)"; String uri="https://www.googleapis.com/drive/v3/files?q="+enc(driveQuery)+"&orderBy=modifiedTime%20desc&pageSize=20&fields="+enc(fields)+(pageToken==null||pageToken.isBlank()?"":"&pageToken="+enc(pageToken));
   var response=http.execute("GET",URI.create(uri),credential.accessToken(),null); check(response);
   // The grant may have been disconnected while the provider request was in flight.
   // Never expose a response acquired with a stale generation.
   if(!access.isCurrent(user,credential.generation())) throw new GoogleAccess.GoogleAccessException(GoogleAccess.Status.REAUTH_REQUIRED);
   JsonNode root=parse(response.body()); var list=new ArrayList<FileItem>();
   int count=0;
   for(var n:root.path("files")) { if(count++ >= 20) break; String id=n.path("id").asText(); if(validId(id)) list.add(new FileItem(id,n.path("name").asText(""),n.path("mimeType").asText("application/octet-stream"),safeUrl(id),n.path("modifiedTime").asText(null)));}
   return new FilesResponse(List.copyOf(list),boundedToken(root.path("nextPageToken").asText(null)));
 }
 public ProjectFilesResponse projectFiles(UUID project,UUID user,int page){
   String role=projects.role(project,user);
   if(page<0 || page>1000) throw new IllegalArgumentException("PAGE_INVALID");
   var p=refs.findByProjectId(project,PageRequest.of(page,25));
   // Resolve all attachment owners in one bounded profile query. Calling IdentityProfiles
   // for each row turns a page into N+1 database reads and becomes visible on larger projects.
   var ownerIds=p.getContent().stream().map(r->r.attachedBy).filter(Objects::nonNull).distinct().toList();
   var profileMap=profiles==null?Map.<UUID,com.aierp.identity.api.IdentityProfiles.PublicProfile>of():profiles.find(ownerIds);
   return new ProjectFilesResponse(p.getContent().stream().map(r->toProject(r,user,role,profileMap)).toList(),p.hasNext());
 }
 public ProjectFile attach(UUID project,UUID user,String fileId){
   requireAttach(project,user);
   if(!validId(fileId)) throw new IllegalArgumentException("FILE_ID_INVALID");
   var c=access.credential(user,GoogleAccess.Feature.DRIVE);
   if(!access.isCurrent(user,c.generation())) throw new GoogleAccess.GoogleAccessException(GoogleAccess.Status.REAUTH_REQUIRED);
   var existing=refs.findByProjectIdAndFileId(project,fileId);
   if(existing.isPresent()) return toProject(existing.get(),user,projects.role(project,user));
   var response=http.execute("GET",URI.create("https://www.googleapis.com/drive/v3/files/"+enc(fileId)+"?fields=id,name,mimeType,modifiedTime,trashed"),c.accessToken(),null);
   check(response); JsonNode n=parse(response.body());
   if(!fileId.equals(n.path("id").asText()) || n.path("trashed").asBoolean(false)) throw new GoogleHttpClient.GoogleServiceException("PROVIDER_INVALID_RESPONSE");
   if(!access.isCurrent(user,c.generation())) throw new GoogleAccess.GoogleAccessException(GoogleAccess.Status.REAUTH_REQUIRED);
   try {
     return saveAttachment(project,user,fileId,n,c);
   } catch (org.springframework.dao.DataIntegrityViolationException duplicate) {
     // A retry after a lost response may race the unique key. Read the committed
     // winner and replay it; never issue a second provider read or create a second row.
     var winner=refs.findByProjectIdAndFileId(project,fileId);
     if(winner.isPresent()) return toProject(winner.get(),user,projects.role(project,user));
     throw duplicate;
   }
 }
 private ProjectFile saveAttachment(UUID project,UUID user,String fileId,JsonNode n,GoogleAccess.Credential credential){
   java.util.function.Supplier<ProjectFile> work=()->{
     // Keep this section short: provider I/O happens before the project lock.
     projects.lockProject(project);
     String role=projects.role(project,user);
     if("VIEWER".equals(role)) throw new org.springframework.security.access.AccessDeniedException("FILE_ATTACH_DENIED");
     if(!access.isCurrentForCommit(user,credential.generation())) throw new GoogleAccess.GoogleAccessException(GoogleAccess.Status.REAUTH_REQUIRED);
     var existing=refs.findByProjectIdAndFileId(project,fileId);
     if(existing.isPresent()) return toProject(existing.get(),user,role);
     var r=new DriveReferenceEntity();r.id=UUID.randomUUID();r.projectId=project;r.fileId=fileId;r.name=bounded(n.path("name").asText(""),500);r.mimeType=bounded(n.path("mimeType").asText("application/octet-stream"),255);r.url=safeUrl(fileId);r.attachedBy=user;r.attachedAt=Instant.now();return toProject(refs.saveAndFlush(r),user,role);
   };
   if(transactions==null) return work.get();
   return transactions.execute(status->work.get());
 }
 @Transactional public void remove(UUID project,UUID user,UUID reference){String role=projects.role(project,user);var r=refs.findById(reference).orElseThrow(()->new NoSuchElementException("REFERENCE_NOT_FOUND"));if(!r.projectId.equals(project)||!(r.attachedBy.equals(user)||role.equals("MANAGER")))throw new org.springframework.security.access.AccessDeniedException("FILE_REMOVE_DENIED");refs.delete(r);}
 private void requireAttach(UUID project,UUID user){if("VIEWER".equals(projects.role(project,user)))throw new org.springframework.security.access.AccessDeniedException("FILE_ATTACH_DENIED");}
 private ProjectFile toProject(DriveReferenceEntity r,UUID u,String role){
   var profileMap=profiles==null?Map.<UUID,com.aierp.identity.api.IdentityProfiles.PublicProfile>of():profiles.find(List.of(r.attachedBy));
   return toProject(r,u,role,profileMap);
 }
 private ProjectFile toProject(DriveReferenceEntity r,UUID u,String role,Map<UUID,com.aierp.identity.api.IdentityProfiles.PublicProfile> profileMap){
   String label="알 수 없는 사용자";var p=profileMap.get(r.attachedBy);
   if(p!=null) label=p.displayName()==null||p.displayName().isBlank()?p.email():p.displayName();
   return new ProjectFile(r.id,r.fileId,r.name,r.mimeType,r.url,label,r.attachedAt,r.attachedBy.equals(u)||"MANAGER".equals(role));
 }
 private static boolean validId(String id){return id!=null&&id.length()<=255&&id.matches("[A-Za-z0-9_-]+");}
 public static String safeUrl(String id){if(!validId(id))throw new IllegalArgumentException("FILE_ID_INVALID");return "https://drive.google.com/open?id="+id;}
 private static JsonNode parse(String s){try{return JSON.readTree(s);}catch(Exception e){throw new GoogleHttpClient.GoogleServiceException("PROVIDER_INVALID_RESPONSE",e);}}
 private static void check(GoogleHttpClient.Response r){GoogleHttpClient.requireSuccess(r);}
 private static String boundedToken(String t){return t==null||t.length()>1000?null:t;}
 private static String bounded(String s,int n){return s==null?"":s.substring(0,Math.min(n,s.length()));}
 private static String enc(String s){return URLEncoder.encode(s,java.nio.charset.StandardCharsets.UTF_8);}
 public record FileItem(String id,String name,String mimeType,String url,String modifiedTime){}
 public record FilesResponse(List<FileItem> files,String nextPageToken){}
 public record ProjectFile(UUID id,String fileId,String name,String mimeType,String url,String attachedBy,Instant attachedAt,boolean canRemove){}
 public record ProjectFilesResponse(List<ProjectFile> files,boolean hasNext){}
}
