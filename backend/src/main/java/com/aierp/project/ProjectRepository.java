package com.aierp.project;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
public interface ProjectRepository extends JpaRepository<ProjectEntity,UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProjectEntity p where p.id = :id")
    Optional<ProjectEntity> lockById(UUID id);
}
