package com.aierp.schedule;
import jakarta.persistence.*; import java.util.UUID;
@Entity @Table(schema="schedule", name="schedule_dashboard_view")
public class ScheduleDashboardViewEntity {
    @Id public UUID projectId; public UUID dashboardViewId; public String builtinView="builtin-cards"; @Version public long rowVersion;
    public ScheduleDashboardViewEntity() {}
}
