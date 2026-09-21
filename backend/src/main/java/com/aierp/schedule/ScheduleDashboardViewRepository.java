package com.aierp.schedule;
import java.util.*; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param;
public interface ScheduleDashboardViewRepository extends JpaRepository<ScheduleDashboardViewEntity,UUID> {
    Optional<ScheduleDashboardViewEntity> findByProjectId(UUID projectId);
    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query(value="insert into schedule.schedule_dashboard_view(project_id,dashboard_view_id,builtin_view,row_version) values (:project,:viewId,:builtin,1)",nativeQuery=true)
    int insertInitial(@Param("project") UUID project,@Param("viewId") UUID viewId,@Param("builtin") String builtin);
}
