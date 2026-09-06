package com.aierp.calendarintegration;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProjectCalendarRepository extends JpaRepository<ProjectCalendarEntity,UUID> {Optional<ProjectCalendarEntity> findByProjectId(UUID projectId);}
