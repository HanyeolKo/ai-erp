package com.aierp.project;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> { }
