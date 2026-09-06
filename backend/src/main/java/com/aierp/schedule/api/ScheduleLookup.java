package com.aierp.schedule.api;

import com.aierp.schedule.ScheduleRepository;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Schedule-owned existence contract for other modules. Caller must authorize project access first. */
@Service
@Transactional(readOnly = true)
public class ScheduleLookup {
    private final ScheduleRepository schedules;
    public ScheduleLookup(ScheduleRepository schedules) { this.schedules = schedules; }
    public void requireInProject(UUID projectId, UUID scheduleId) {
        if (!schedules.existsByIdAndProjectId(scheduleId, projectId)) throw new NoSuchElementException();
    }
}
