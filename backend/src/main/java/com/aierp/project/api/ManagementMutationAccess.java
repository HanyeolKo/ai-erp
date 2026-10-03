package com.aierp.project.api;

import com.aierp.project.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Project-owned immutable port for actor-scoped receipts and management audit history. */
@Service
public class ManagementMutationAccess {
    private final ManagementMutationReceiptRepository receipts; private final ManagementAuditRepository audits;
    public ManagementMutationAccess(ManagementMutationReceiptRepository receipts,ManagementAuditRepository audits){this.receipts=receipts;this.audits=audits;}
    @Transactional public Optional<MutationReceipt> lock(UUID projectId,UUID actorId,UUID requestId){return receipts.lock(projectId,actorId,requestId).map(ManagementMutationAccess::view);}
    @Transactional(readOnly=true) public Optional<MutationReceipt> find(UUID projectId,UUID actorId,UUID requestId){return receipts.findByIdProjectIdAndIdActorIdAndIdRequestId(projectId,actorId,requestId).map(ManagementMutationAccess::view);}
    @Transactional public void saveReceipt(UUID projectId,UUID actorId,UUID requestId,String hash,String type,UUID resourceId,long version,String operation){var row=new ManagementMutationReceiptEntity(projectId,actorId,requestId);row.payloadHash=hash;row.resourceType=type;row.resourceId=resourceId;row.rowVersion=version;row.operation=operation;row.status="APPLIED";row.createdAt=Instant.now();receipts.saveAndFlush(row);}
    @Transactional public void audit(UUID projectId,UUID actorId,String type,UUID resourceId,long version,String operation,Map<String,Object> before,Map<String,Object> after){var a=new ManagementAuditEntity();a.id=UUID.randomUUID();a.projectId=projectId;a.actorId=actorId;a.occurredAt=Instant.now();a.resourceType=type;a.resourceId=resourceId;a.rowVersion=version;a.operation=operation;a.beforeValues=mutableMap(before);a.afterValues=mutableMap(after);audits.save(a);}
    @Transactional(readOnly=true) public AuditPage history(UUID projectId,String type,UUID resourceId,int page,int limit){var result=audits.findByProjectIdAndResourceTypeAndResourceIdOrderByOccurredAtDescIdDesc(projectId,type,resourceId,PageRequest.of(page,limit));return new AuditPage(result.getContent().stream().map(a->new AuditEntry(a.id,a.actorId,a.occurredAt,a.resourceType,a.resourceId,a.rowVersion,a.operation,a.beforeValues,a.afterValues)).toList(),result.hasNext());}
    private static MutationReceipt view(ManagementMutationReceiptEntity r){return new MutationReceipt(r.payloadHash,r.resourceType,r.resourceId,r.rowVersion,r.status);}
    public record MutationReceipt(String payloadHash,String resourceType,UUID resourceId,long rowVersion,String status){}
    public record AuditEntry(UUID id,UUID actorId,Instant occurredAt,String resourceType,UUID resourceId,long rowVersion,String operation,Map<String,Object> before,Map<String,Object> after){ public AuditEntry { before=immutableMap(before); after=immutableMap(after); } }
    public record AuditPage(List<AuditEntry> entries,boolean hasNext){public AuditPage{entries=List.copyOf(entries);}}
    private static Map<String,Object> immutableMap(Map<String,Object> input) { if (input==null) return null; var copy=new LinkedHashMap<String,Object>(); input.forEach((k,v)->copy.put(k,immutableValue(v))); return Collections.unmodifiableMap(copy); }
    private static Object immutableValue(Object value) { if(value instanceof Map<?,?> map){var copy=new LinkedHashMap<String,Object>();map.forEach((k,v)->copy.put(String.valueOf(k),immutableValue(v)));return Collections.unmodifiableMap(copy);} if(value instanceof Collection<?> c)return c.stream().map(ManagementMutationAccess::immutableValue).toList(); return value; }
    private static Map<String,Object> mutableMap(Map<String,Object> input) { if(input==null)return null;var copy=new LinkedHashMap<String,Object>();input.forEach((k,v)->copy.put(k,mutableValue(v)));return copy; }
    private static Object mutableValue(Object value) { if(value instanceof Map<?,?> map){var copy=new LinkedHashMap<String,Object>();map.forEach((k,v)->copy.put(String.valueOf(k),mutableValue(v)));return copy;} if(value instanceof Collection<?> c)return c.stream().map(ManagementMutationAccess::mutableValue).collect(java.util.stream.Collectors.toCollection(ArrayList::new));return value; }
}
