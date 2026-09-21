package com.aierp.schedule;
import java.util.*; import org.springframework.data.jpa.repository.*;
public interface ScheduleDashboardViewRepository extends JpaRepository<ScheduleDashboardViewEntity,UUID> { Optional<ScheduleDashboardViewEntity> findByProjectId(UUID projectId); }
