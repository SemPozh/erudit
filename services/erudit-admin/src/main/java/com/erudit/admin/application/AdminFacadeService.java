package com.erudit.admin.application;
import com.erudit.admin.application.port.AdminRemote;
import com.erudit.admin.application.AdminModels.*;
import com.erudit.admin.application.port.AdminAuditRepository;
import com.erudit.admin.application.AdminOperationRunner.Context;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
@RequiredArgsConstructor
public class AdminFacadeService {
    private final AdminRemote.Users users;
    private final AdminRemote.Content content;
    private final AdminRemote.Notifications notifications;
    private final AdminRemote.Payments payments;
    private final AdminOperationRunner operations;
    private final AdminAuditRepository audit;
    public Page<UserProfile> users(Context c,int page,int size,String query){
        page(page,size);
        return operations.execute(c,"USER_LIST","USER","*",()->{
            var result=Objects.requireNonNull(users.search(query,page,size,c.requestId()));
            return new Page<>(Objects.requireNonNull(result.data()),metadata(page,size,result.total()),Long.toString(result.total()));
        });
    }
    public void block(Context c,UUID id){operations.execute(c,"USER_BLOCKED","USER",id.toString(),()->{users.block(id,c.requestId());return true;});}
    public Page<ContentItem> queue(Context c,int page,int size){
        page(page,size);return operations.execute(c,"MODERATION_QUEUE","CONTENT","*",()->checked(content.queue(page,size,c.requestId())));
    }
    public ContentItem moderate(Context c,UUID id,String status){
        return operations.execute(c,"CONTENT_"+status,"CONTENT",id.toString(),()->{
            var result=Objects.requireNonNull(content.moderate(new AdminRemote.Moderation(id,status,null),c.actor(),c.requestId()));
            if(!id.equals(result.contentId())||!status.equals(result.status())||result.data()==null||!id.equals(result.data().id()))throw new IllegalStateException("Malformed moderation result");
            return result.data();
        });
    }
    public Page<ContentReport> reports(Context c,int page,int size){
        page(page,size);return operations.execute(c,"CONTENT_REPORTS","CONTENT_REPORT","*",()->checked(content.reports(page,size,c.requestId())));
    }
    public void resolve(Context c,UUID id,Resolution request){
        operations.execute(c,"REPORT_RESOLVED","CONTENT_REPORT",id.toString(),()->{content.resolve(id,request,c.actor(),c.requestId());return true;});
    }
    public long sendMany(Context c,SendMany request){
        var ids=request.userIds();
        if(ids==null||ids.isEmpty()||ids.size()>1000||ids.stream().anyMatch(Objects::isNull))throw AdminFailure.invalid("userIds must contain 1..1000 non-null IDs");
        return operations.execute(c,"NOTIFICATIONS_SENT","NOTIFICATION_BATCH",request.templateId().toString(),()->{
            var template=Objects.requireNonNull(notifications.template(request.templateId(),c.requestId()));
            if(!template.enabled())throw new AdminFailure(409,"TEMPLATE_DISABLED","Notification template is disabled","FAILED",Map.of());
            String title=render(template.titleTemplate(),request.parameters()),body=render(template.bodyTemplate(),request.parameters());
            if(title.length()>300||body.length()>2000)throw AdminFailure.invalid("Rendered template is too long");
            int sent=0;List<UUID> failed=new ArrayList<>();
            for(UUID id:new LinkedHashSet<>(ids)){
                try{
                    var user=users.get(id,c.requestId());
                    if(user==null||!id.equals(user.id())||!user.active())throw new IllegalStateException("Recipient is unavailable");
                    var accepted=notifications.enqueue(new AdminRemote.Enqueue(id,template.code(),title,body),c.requestId());
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
    public Subscription subscription(Context c,UUID user){return operations.execute(c,"SUBSCRIPTION_VIEW","USER",user.toString(),()->{
        var result=Objects.requireNonNull(payments.subscription(user,c.requestId()));
        if(result.id()==null)throw new IllegalStateException("Malformed subscription result");return result;
    });}
    public Page<Payment> payments(Context c,UUID user,int page,int size){
        page(page,size);return operations.execute(c,"PAYMENTS_VIEW","USER",user.toString(),()->checked(payments.payments(page,size,user,c.requestId())));
    }
    public AdminAuditRepository.Page audit(int page,int size,UUID actor,String action,OffsetDateTime from,OffsetDateTime to){
        page(page,size);if(from!=null&&to!=null&&from.isAfter(to))throw AdminFailure.invalid("Invalid audit interval");
        return audit.list(page,size,actor,action,from,to);
    }
    private static <T> Page<T> checked(Page<T> response){
        if(response==null||response.data()==null)throw new IllegalStateException("Malformed downstream page");
        return response;
    }
    private static void page(int page,int size){if(page<0||size<1||size>50)throw AdminFailure.invalid("Invalid page or size");}
    private static Pagination metadata(int page,int size,long total){return new Pagination(page,size,total,(int)((total+size-1)/size));}
}
