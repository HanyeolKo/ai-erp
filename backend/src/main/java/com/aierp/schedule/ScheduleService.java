package com.aierp.schedule;

import com.aierp.project.api.ProjectAccess;
import com.aierp.platform.events.EventJournal;
import com.aierp.platform.web.*;
import com.aierp.schedule.api.ScheduleController.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.aierp.schedule.api.ScheduleDashboard;
import java.time.Duration;
import java.util.stream.Collectors;

@Service @Transactional(readOnly=true)
public class ScheduleService {
    private final ScheduleRepository schedules;
    private final ScheduleParticipantRepository participants;
    private final ScheduleAcknowledgementRepository acknowledgements;
    private final ScheduleChangeRepository changes;
    private final ProjectAccess access;
    private final EventJournal events;
    public ScheduleService(ScheduleRepository schedules, ScheduleParticipantRepository participants,
            ScheduleAcknowledgementRepository acknowledgements, ScheduleChangeRepository changes,
            ProjectAccess access, EventJournal events) {
        this.schedules=schedules;this.participants=participants;this.acknowledgements=acknowledgements;
        this.changes=changes;this.access=access;this.events=events;
    }
    public List<ScheduleResponse> list(UUID projectId,UUID user) {
        return list(projectId,user,null,null,null,null);
    }
    public List<ScheduleResponse> list(UUID projectId,UUID user,Instant from,Instant to,Integer page,Integer limit) {
        access.role(projectId,user);
        if(from!=null && to!=null && !from.isBefore(to)) throw new ValidationFailure("to","Must be after from");
        var bounds=ReadLimits.page(page,limit,Sort.by("startsAt","id"));
        return summaries(schedules.findWindow(projectId,from==null?Instant.parse("0001-01-01T00:00:00Z"):from,
            to==null?Instant.parse("9999-12-31T23:59:59Z"):to,bounds));
    }
    public ScheduleResponse detail(UUID projectId,UUID id,UUID user) {
        return detail(projectId,id,user,null);
    }
    public ScheduleResponse detail(UUID projectId,UUID id,UUID user,Integer historyLimit) {
        access.role(projectId,user); return response(find(projectId,id),ReadLimits.history(historyLimit));
    }
    public ScheduleDashboard.Summary dashboard(UUID projectId,UUID user) {
        var role=access.role(projectId,user);
        var page=PageRequest.of(0,ReadLimits.DEFAULT,Sort.by("startsAt","id"));
        var now=Instant.now();
        var upcoming=schedules.findUpcoming(projectId,now,now.plus(Duration.ofDays(14)),page);
        var actions="VIEWER".equals(role)?List.<ScheduleEntity>of():schedules.findPendingForUser(projectId,user,page);
        // Fetch participants and current ACKs once for the union of the two bounded dashboard lists.
        var combined=new LinkedHashMap<UUID,ScheduleEntity>();
        upcoming.forEach(s->combined.put(s.id,s));actions.forEach(s->combined.put(s.id,s));
        var responses=summaries(new ArrayList<>(combined.values())).stream().collect(Collectors.toMap(ScheduleResponse::id,s->s));
        long pending=0;
        for(int number=0;;number++) {
            var counts=participants.pendingCounts(projectId,PageRequest.of(number,ReadLimits.MAX));
            if(counts.isEmpty()) break;
            var eligible=access.eligibleAcknowledgers(projectId,counts.stream().map(ScheduleParticipantRepository.PendingCount::getUserId).toList());
            pending+=counts.stream().filter(c->eligible.contains(c.getUserId())).mapToLong(ScheduleParticipantRepository.PendingCount::getPendingCount).sum();
            if(counts.size()<ReadLimits.MAX) break;
        }
        return new ScheduleDashboard.Summary(schedules.countByProjectId(projectId),pending,
            upcoming.stream().map(s->responses.get(s.id)).toList(),actions.stream().map(s->responses.get(s.id)).toList());
    }
    @Transactional public ScheduleResponse create(UUID projectId,Write input,UUID user) {
        access.requireWriter(projectId,user,null); validate(input,false);
        var schedule=new ScheduleEntity();
        schedule.id=UUID.randomUUID();schedule.projectId=projectId;schedule.createdBy=user;
        schedule.status=ScheduleEntity.Status.DRAFT;schedule.businessRevision=0;
        apply(schedule,input);
        schedules.saveAndFlush(schedule);
        replaceParticipants(schedule,input);
        change(schedule,user,"SCHEDULE_CREATED");
        return response(schedule);
    }
    @Transactional public ScheduleResponse update(UUID projectId,UUID id,Write input,UUID user) {
        access.requireWriter(projectId,user,null);
        var schedule=find(projectId,id);access.requireWriter(projectId,user,schedule.createdBy);
        validate(input,true);check(schedule,input.rowVersion());editable(schedule);
        boolean changed=!Objects.equals(schedule.title,input.title().trim())
                || !Objects.equals(schedule.description,input.description())
                || !Objects.equals(schedule.startsAt,input.startsAt()) || !Objects.equals(schedule.endsAt,input.endsAt())
                || participantsChanged(schedule,input);
        if(!changed) return response(schedule);
        apply(schedule,input);
        replaceParticipants(schedule,input);
        if(schedule.status==ScheduleEntity.Status.CONFIRMED) schedule.businessRevision++;
        schedules.saveAndFlush(schedule); change(schedule,user,"SCHEDULE_CHANGED");
        return response(schedule);
    }
    @Transactional public ScheduleResponse confirm(UUID projectId,UUID id,Revision revision,UUID user) {
        var schedule=writable(projectId,id,revision,user);
        if(schedule.status!=ScheduleEntity.Status.DRAFT) throw new IllegalStateException("SCHEDULE_NOT_DRAFT");
        schedule.status=ScheduleEntity.Status.CONFIRMED;schedule.businessRevision=1;
        return changed(schedule,user,"SCHEDULE_CONFIRMED");
    }
    @Transactional public ScheduleResponse cancel(UUID projectId,UUID id,Revision revision,UUID user) {
        var schedule=writable(projectId,id,revision,user);editable(schedule);
        if(schedule.status==ScheduleEntity.Status.CONFIRMED) schedule.businessRevision++;
        schedule.status=ScheduleEntity.Status.CANCELLED;
        return changed(schedule,user,"SCHEDULE_CANCELLED");
    }
    @Transactional public ScheduleResponse acknowledge(UUID projectId,UUID id,Acknowledge input,UUID user) {
        if("VIEWER".equals(access.role(projectId,user))) throw new AccessDeniedException("VIEWER_READ_ONLY");
        var schedule=schedules.lockByIdAndProjectId(id,projectId).orElseThrow(NoSuchElementException::new);
        if(input.expectedBusinessRevision()==null || input.expectedBusinessRevision()!=schedule.businessRevision || schedule.businessRevision==0)
            throw new IllegalStateException("ACK_REVISION_CONFLICT");
        if(participants.findByScheduleId(id).stream().noneMatch(p->user.equals(p.memberUserAccountId)))
            throw new AccessDeniedException("MEMBER_PARTICIPANT_REQUIRED");
        var key=new ScheduleAcknowledgementEntity.Key(id,user,schedule.businessRevision);
        if(acknowledgements.existsById(key)) return response(schedule);
        var ack=new ScheduleAcknowledgementEntity();ack.scheduleId=id;ack.userAccountId=user;
        ack.businessRevision=schedule.businessRevision;ack.acknowledgedAt=Instant.now();
        acknowledgements.save(ack);
        change(schedule,user,"SCHEDULE_ACKNOWLEDGED");
        return response(schedule);
    }
    private ScheduleEntity writable(UUID projectId,UUID id,Revision input,UUID user) {
        access.requireWriter(projectId,user,null);
        var s=find(projectId,id);access.requireWriter(projectId,user,s.createdBy);check(s,input.rowVersion());return s;
    }
    private void editable(ScheduleEntity s) { if(s.status==ScheduleEntity.Status.CANCELLED) throw new IllegalStateException("SCHEDULE_CANCELLED"); }
    private void check(ScheduleEntity s,Long expected) {
        if(s.rowVersion!=Checks.version(expected)) throw new ObjectOptimisticLockingFailureException(ScheduleEntity.class,s.id);
    }
    private void validate(Write input,boolean update) {
        Checks.schedule(input.title(),input.startsAt(),input.endsAt());
        if(update) Checks.version(input.rowVersion());
        if(input.description()!=null && input.description().length()>10000) throw new ValidationFailure("description","Description too long");
    }
    private void apply(ScheduleEntity s,Write w) {
        s.title=w.title().trim();s.description=w.description();s.startsAt=w.startsAt();s.endsAt=w.endsAt();s.updatedAt=Instant.now();
    }
    private Set<UUID> memberSet(Write w) {
        var result=new LinkedHashSet<UUID>(w.memberParticipantIds()==null?List.of():w.memberParticipantIds());
        if(result.contains(null)) throw new ValidationFailure("memberParticipantIds","Null member ID");
        return result;
    }
    private Set<String> emailSet(Write w) {
        var result=new LinkedHashSet<String>();
        if(w.externalAttendeeEmails()!=null) w.externalAttendeeEmails().forEach(email->result.add(Checks.email(email)));
        return result;
    }
    private boolean participantsChanged(ScheduleEntity s,Write w) {
        if(w.memberParticipantIds()==null && w.externalAttendeeEmails()==null) return false;
        var existing=participants.findByScheduleId(s.id);
        var memberIds=existing.stream().map(p->p.memberUserAccountId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        var emails=existing.stream().map(p->p.externalEmail).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        return !memberIds.equals(memberSet(w)) || !emails.equals(emailSet(w));
    }
    private void replaceParticipants(ScheduleEntity s,Write w) {
        if(w.memberParticipantIds()==null && w.externalAttendeeEmails()==null) return;
        var memberIds=memberSet(w);var emails=emailSet(w);
        if(memberIds.size()+emails.size()>200) throw new ValidationFailure("participants","At most 200 participants");
        for(UUID id:memberIds) access.role(s.projectId,id);
        participants.deleteByScheduleId(s.id);participants.flush();
        for(UUID id:memberIds) {var p=new ScheduleParticipantEntity();p.id=UUID.randomUUID();p.scheduleId=s.id;p.memberUserAccountId=id;participants.save(p);}
        for(String email:emails) {var p=new ScheduleParticipantEntity();p.id=UUID.randomUUID();p.scheduleId=s.id;p.externalEmail=email;participants.save(p);}
    }
    private ScheduleResponse changed(ScheduleEntity s,UUID user,String type) {
        s.updatedAt=Instant.now();schedules.saveAndFlush(s);change(s,user,type);return response(s);
    }
    private void change(ScheduleEntity s,UUID user,String type) {
        var c=new ScheduleChangeEntity();c.id=UUID.randomUUID();c.scheduleId=s.id;c.businessRevision=s.businessRevision;c.changeType=type;c.changedBy=user;c.createdAt=Instant.now();changes.save(c);
        var recipients=participants.findByScheduleId(s.id).stream().map(p->p.memberUserAccountId).filter(Objects::nonNull).toList();
        events.record(type,s.id,s.projectId,user,recipients,s.businessRevision);
    }
    private ScheduleEntity find(UUID projectId,UUID id) {return schedules.findByIdAndProjectId(id,projectId).orElseThrow(NoSuchElementException::new);}
    private ScheduleResponse response(ScheduleEntity s) {
        return response(s,ReadLimits.HISTORY_DEFAULT);
    }
    private ScheduleResponse response(ScheduleEntity s,int historyLimit) {
        var ps=participants.findByScheduleId(s.id);
        var acks=acknowledgements.findByScheduleIdAndBusinessRevision(s.id,s.businessRevision).stream().map(a->a.userAccountId).toList();
        // Select the most recent N in SQL, then present that bounded slice chronologically.
        var history=changes.findByScheduleId(s.id,PageRequest.of(0,historyLimit,Sort.by(Sort.Direction.DESC,"createdAt","id"))).reversed();
        return new ScheduleResponse(s.id,s.projectId,s.title,s.status,s.rowVersion,s.businessRevision,s.startsAt,s.endsAt,s.description,s.createdBy,
            ps.stream().map(p->new Participant(p.memberUserAccountId,p.externalEmail, p.memberUserAccountId!=null && acks.contains(p.memberUserAccountId))).toList(),
            history.stream().map(c->new Change(c.businessRevision,c.changeType,c.changedBy,c.createdAt)).toList());
    }
    private List<ScheduleResponse> summaries(List<ScheduleEntity> selected) {
        if(selected.isEmpty()) return List.of();
        var ids=selected.stream().map(s->s.id).toList();
        var ps=participants.findByScheduleIdIn(ids).stream().collect(Collectors.groupingBy(p->p.scheduleId));
        var acks=acknowledgements.findCurrentByScheduleIds(ids).stream().collect(Collectors.groupingBy(a->a.scheduleId,
            Collectors.mapping(a->a.userAccountId,Collectors.toSet())));
        return selected.stream().map(s->new ScheduleResponse(s.id,s.projectId,s.title,s.status,s.rowVersion,s.businessRevision,
            s.startsAt,s.endsAt,s.description,s.createdBy,
            ps.getOrDefault(s.id,List.of()).stream().map(p->new Participant(p.memberUserAccountId,p.externalEmail,
                p.memberUserAccountId!=null && acks.getOrDefault(s.id,Set.of()).contains(p.memberUserAccountId))).toList(),List.of())).toList();
    }
}
