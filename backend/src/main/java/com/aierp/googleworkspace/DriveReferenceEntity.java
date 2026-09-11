package com.aierp.googleworkspace;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(schema="google_workspace", name="drive_reference", uniqueConstraints=@UniqueConstraint(columnNames={"project_id","file_id"}))
public class DriveReferenceEntity {
 @Id public UUID id; public UUID projectId; public String fileId; public String name; public String mimeType; public String url; public UUID attachedBy; public Instant attachedAt;
}
