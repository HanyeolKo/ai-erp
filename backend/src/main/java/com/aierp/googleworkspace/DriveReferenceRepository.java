package com.aierp.googleworkspace;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DriveReferenceRepository extends JpaRepository<DriveReferenceEntity,UUID> {
 Page<DriveReferenceEntity> findByProjectId(UUID projectId, Pageable page);
 Optional<DriveReferenceEntity> findByProjectIdAndFileId(UUID projectId,String fileId);
}
