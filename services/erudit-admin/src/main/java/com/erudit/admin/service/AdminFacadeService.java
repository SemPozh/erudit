package com.erudit.admin.service;
import com.erudit.admin.client.RemoteClients;
import com.erudit.admin.openapi.model.*;
import com.erudit.admin.repository.AdminAuditRepository;
import com.erudit.admin.service.AdminOperationRunner.Context;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
@Service
@RequiredArgsConstructor
public class AdminFacadeService {
    private final RemoteClients.Users users;
    private final RemoteClients.Content content;
    private final RemoteClients.Notifications notifications;
    private final RemoteClients.Payments payments;
    private final AdminOperationRunner operations;
    private final AdminAuditRepository audit;
    private final JsonMapper json=JsonMapper.builder().build();
    public ResponseEntity<AdminUserListResponse> users(Context c,int page,int size,String query){
        page(page,size);
        return operations.execute(c,"USER_LIST","USER","*",()->{
            var result=Objects.requireNonNull(users.search(query,page,size,c.requestId()));
            return ResponseEntity.ok().header("X-Total-Count",Long.toString(result.total()))
                    .body(new AdminUserListResponse(Objects.requireNonNull(result.data())).pagination(metadata(page,size,result.total())));
        });
    }
    public void block(Context c,UUID id){operations.execute(c,"USER_BLOCKED","USER",id.toString(),()->{users.block(id,c.requestId());return true;});}
    public ResponseEntity<ContentListResponse> queue(Context c,int page,int size){
        page(page,size);return operations.execute(c,"MODERATION_QUEUE","CONTENT","*",()->checked(content.queue(page,size,c.requestId())));
    }
    public ContentItemResponse moderate(Context c,UUID id,String status){
        return operations.execute(c,"CONTENT_"+status,"CONTENT",id.toString(),()->{
            var result=Objects.requireNonNull(content.moderate(new RemoteClients.Moderation(id,status,null),c.actor(),c.requestId()));
            if(!id.equals(result.contentId())||!status.equals(result.status())||result.data()==null||!id.equals(result.data().getId()))throw new IllegalStateException("Malformed moderation result");
            return new ContentItemResponse(result.data());
        });
    }
    public ResponseEntity<ContentReportListResponse> reports(Context c,int page,int size){
        page(page,size);return operations.execute(c,"CONTENT_REPORTS","CONTENT_REPORT","*",()->checked(content.reports(page,size,c.requestId())));
    }
    public void resolve(Context c,UUID id,ReportResolutionRequest request){
        operations.execute(c,"REPORT_RESOLVED","CONTENT_REPORT",id.toString(),()->{content.resolve(id,request,c.actor(),c.requestId());return true;});
    }
    public long sendMany(Context c,NotificationSendManyRequest request){
        var ids=request.getUserIds();
        if(ids==null||ids.isEmpty()||ids.size()>1000||ids.contains(null))throw AdminFailure.invalid("userIds must contain 1..1000 non-null IDs");
        return operations.execute(c,"NOTIFICATIONS_SENT","NOTIFICATION_BATCH",request.getTemplateId().toString(),()->{
            var template=Objects.requireNonNull(notifications.template(request.getTemplateId(),c.requestId()));
            if(!template.enabled())throw new AdminFailure(409,"TEMPLATE_DISABLED","Notification template is disabled","FAILED",Map.of());
            String title=render(template.titleTemplate(),request.getParameters()),body=render(template.bodyTemplate(),request.getParameters());
            if(title.length()>300||body.length()>2000)throw AdminFailure.invalid("Rendered template is too long");
            int sent=0;List<UUID> failed=new ArrayList<>();
            for(UUID id:new LinkedHashSet<>(ids)){
                try{
                    var user=users.get(id,c.requestId());
                    if(user==null||!id.equals(user.id())||!user.active())throw new IllegalStateException("Recipient is unavailable");
                    var accepted=notifications.enqueue(new RemoteClients.Enqueue(id,template.code(),title,body),c.requestId());
                    if(accepted==null||accepted.id()==null||!id.equals(accepted.userId()))throw new IllegalStateException("Queue result is unknown");
                    sent++;
                }catch(RuntimeException failure){failed.add(id);}
            }
            if(!failed.isEmpty())throw new AdminFailure(503,"PARTIAL_FAILURE","Some recipients could not be confirmed in the mock queue",
                    sent>0?"PARTIAL":"FAILED",Map.of("sent",sent,"failed",failed,"requested",new HashSet<>(ids).size()));
            return (long)sent;
        });
    }
    static String render(String text,Map<String,String> parameters){
        if(text==null)throw new IllegalStateException("Malformed notification template");
        var matcher=Pattern.compile("[{][{]([A-Za-z][A-Za-z0-9_.-]{0,49})[}][}]").matcher(text);
        String result=matcher.replaceAll(match->{String value=parameters==null?null:parameters.get(match.group(1));
            if(value==null)throw AdminFailure.invalid("Missing template parameter: "+match.group(1));return java.util.regex.Matcher.quoteReplacement(value);});
        if(result.contains("{{")||result.contains("}}"))throw AdminFailure.invalid("Malformed template parameter");return result;
    }
    public SubscriptionResponse subscription(Context c,UUID user){return operations.execute(c,"SUBSCRIPTION_VIEW","USER",user.toString(),()->{
        var result=Objects.requireNonNull(payments.subscription(user,c.requestId()));
        if(result.getData()==null||result.getData().getId()==null)throw new IllegalStateException("Malformed subscription result");return result;
    });}
    public ResponseEntity<PaymentListResponse> payments(Context c,UUID user,int page,int size){
        page(page,size);return operations.execute(c,"PAYMENTS_VIEW","USER",user.toString(),()->checked(payments.payments(page,size,user,c.requestId())));
    }
    public ResponseEntity<AuditListResponse> audit(int page,int size,UUID actor,String action,OffsetDateTime from,OffsetDateTime to){
        page(page,size);if(from!=null&&to!=null&&from.isAfter(to))throw AdminFailure.invalid("Invalid audit interval");
        var result=audit.list(page,size,actor,action,from,to);
        var data=result.items().stream().map(a->{
            Map<String,Object> details=new HashMap<>(json.readValue(a.metadata(),Map.class));details.put("status",a.status());details.put("requestId",a.requestId());
            return new AuditRecordDataResponse(a.id(),a.actorId(),a.action(),a.occurredAt()).targetType(a.targetType()).targetId(a.targetId()).metadata(details);
        }).toList();
        return ResponseEntity.ok().header("X-Total-Count",Long.toString(result.total())).body(new AuditListResponse(data,metadata(page,size,result.total())));
    }
    private static <T>ResponseEntity<T> checked(ResponseEntity<T> response){
        if(response==null||response.getBody()==null)throw new IllegalStateException("Empty downstream response");
        T body=response.getBody();
        if(body instanceof ContentListResponse page && page.getData()==null || body instanceof ContentReportListResponse reports && reports.getData()==null
                || body instanceof PaymentListResponse payments && payments.getData()==null)throw new IllegalStateException("Malformed downstream page");
        var result=ResponseEntity.ok();String total=response.getHeaders().getFirst("X-Total-Count");
        if(total!=null)result.header("X-Total-Count",total);return result.body(body);
    }
    private static void page(int page,int size){if(page<0||size<1||size>50)throw AdminFailure.invalid("Invalid page or size");}
    private static PageMetadataResponse metadata(int page,int size,long total){return new PageMetadataResponse(page,size,total,(int)((total+size-1)/size));}
}
