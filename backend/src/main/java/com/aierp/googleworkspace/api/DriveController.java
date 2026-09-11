package com.aierp.googleworkspace.api;
import com.aierp.googleworkspace.DriveService;
import com.aierp.identity.api.ApplicationPrincipal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
@RestController @RequestMapping("/api/v1")
public class DriveController {
 private final DriveService service; public DriveController(DriveService service){this.service=service;}
 private static UUID user(Authentication a){if(a.getPrincipal() instanceof ApplicationPrincipal p)return p.userId();throw new org.springframework.security.access.AccessDeniedException("APPLICATION_PRINCIPAL_REQUIRED");}
 @GetMapping("/google/drive/files") public DriveService.FilesResponse files(Authentication a,@RequestParam(defaultValue="") String query,@RequestParam(required=false) String pageToken){return service.files(user(a),query,pageToken);}
 @GetMapping("/projects/{projectId}/files") public DriveService.ProjectFilesResponse projectFiles(@PathVariable UUID projectId,Authentication a,@RequestParam(defaultValue="0") int page){return service.projectFiles(projectId,user(a),page);}
 @PostMapping("/projects/{projectId}/files") public DriveService.ProjectFile attach(@PathVariable UUID projectId,@RequestBody FileRequest request,Authentication a){return service.attach(projectId,user(a),request.fileId());}
 @DeleteMapping("/projects/{projectId}/files/{referenceId}") public ResponseEntity<Void> remove(@PathVariable UUID projectId,@PathVariable UUID referenceId,Authentication a){service.remove(projectId,user(a),referenceId);return ResponseEntity.noContent().build();}
 public record FileRequest(String fileId){}
}
