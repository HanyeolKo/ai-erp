package com.aierp.googleworkspace;

import com.aierp.identity.api.GoogleAccess;
import com.aierp.project.api.ProjectAccess;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageImpl;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DriveServiceTest {
    @Test
    void staleGrantIsRejectedAgainInsideTheSaveBarrier() {
        var access = mock(GoogleAccess.class);
        var http = mock(GoogleHttpClient.class);
        var refs = mock(DriveReferenceRepository.class);
        var projects = mock(ProjectAccess.class);
        var user = UUID.randomUUID();
        var project = UUID.randomUUID();
        var credential = new GoogleAccess.Credential("access", 7);
        when(projects.role(project, user)).thenReturn("MEMBER");
        when(access.credential(user, GoogleAccess.Feature.DRIVE)).thenReturn(credential);
        when(access.isCurrent(user, 7)).thenReturn(true);
        when(access.isCurrentForCommit(user, 7)).thenReturn(false);
        when(refs.findByProjectIdAndFileId(project, "file_1")).thenReturn(Optional.empty());
        when(http.execute(eq("GET"), any(), eq("access"), isNull()))
                .thenReturn(new GoogleHttpClient.Response(200, "{\"id\":\"file_1\",\"name\":\"a\"}"));

        var service = new DriveService(access, http, refs, projects);
        assertThatThrownBy(() -> service.attach(project, user, "file_1"))
                .hasMessage("GOOGLE_REAUTH_REQUIRED");
        verify(refs, never()).saveAndFlush(any());
    }

    @Test
    void existingAttachmentReplaysWithoutAProviderCall() {
        var access = mock(GoogleAccess.class);
        var http = mock(GoogleHttpClient.class);
        var refs = mock(DriveReferenceRepository.class);
        var projects = mock(ProjectAccess.class);
        var user = UUID.randomUUID();
        var project = UUID.randomUUID();
        var existing = new DriveReferenceEntity();
        existing.id = UUID.randomUUID(); existing.projectId = project; existing.fileId = "file_1";
        existing.name = "a"; existing.mimeType = "text/plain"; existing.url = DriveService.safeUrl("file_1");
        existing.attachedBy = user;
        when(projects.role(project, user)).thenReturn("MEMBER");
        when(access.credential(user, GoogleAccess.Feature.DRIVE)).thenReturn(new GoogleAccess.Credential("access", 7));
        when(access.isCurrent(user, 7)).thenReturn(true);
        when(refs.findByProjectIdAndFileId(project, "file_1")).thenReturn(Optional.of(existing));

        new DriveService(access, http, refs, projects).attach(project, user, "file_1");
        verifyNoInteractions(http);
    }

    @Test
    void projectPageUsesOneBoundedOwnerProfileLookup() {
        var access = mock(GoogleAccess.class);
        var http = mock(GoogleHttpClient.class);
        var refs = mock(DriveReferenceRepository.class);
        var projects = mock(ProjectAccess.class);
        var profiles = mock(com.aierp.identity.api.IdentityProfiles.class);
        var user = UUID.randomUUID();
        var project = UUID.randomUUID();
        var first = reference(project, "file_1", UUID.randomUUID());
        var second = reference(project, "file_2", UUID.randomUUID());
        when(projects.role(project, user)).thenReturn("MEMBER");
        when(refs.findByProjectId(eq(project), any())).thenReturn(new PageImpl<>(List.of(first, second)));
        when(profiles.find(any())).thenReturn(Map.of());

        new DriveService(access, http, refs, projects, profiles).projectFiles(project, user, 0);

        verify(profiles, times(1)).find(argThat(ids -> ids.size() == 2));
    }

    private static DriveReferenceEntity reference(UUID project, String fileId, UUID owner) {
        var row = new DriveReferenceEntity();
        row.id = UUID.randomUUID(); row.projectId = project; row.fileId = fileId; row.name = fileId;
        row.mimeType = "text/plain"; row.url = DriveService.safeUrl(fileId); row.attachedBy = owner;
        row.attachedAt = java.time.Instant.now();
        return row;
    }
}
