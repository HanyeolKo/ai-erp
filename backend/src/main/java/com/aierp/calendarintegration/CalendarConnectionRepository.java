package com.aierp.calendarintegration;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CalendarConnectionRepository extends JpaRepository<CalendarConnectionEntity,UUID> {Optional<CalendarConnectionEntity> findByUserAccountId(UUID userAccountId);}
