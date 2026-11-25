package com.erudit.admin.application;
import com.erudit.admin.application.port.AuditLog;
import com.erudit.admin.application.port.RemoteFailure;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
public class AdminOperationRunner {
    private final AuditLog audit;
    public AdminOperationRunner(AuditLog audit){this.audit=audit;}
    public <T>T execute(Context context,String action,String targetType,String targetId,Supplier<T> call){
        // Commit the intent before network I/O; remote failure must not roll this local audit back.
        UUID id=audit.start(context.actor(),context.requestId(),action,targetType,targetId);
        try {
            T result=call.get();audit.finish(id,"SUCCESS",result instanceof Number count?Map.of("sent",count.longValue()):Map.of());return result;
        } catch(AdminFailure failure){audit.finish(id,failure.outcome(),failure.details());throw failure;}
        catch(RuntimeException failure){
            int status=503;
            for(Throwable cause=failure;cause!=null;cause=cause.getCause())if(cause instanceof RemoteFailure remote){
                if(remote.status()>=400 && remote.status()<500)status=remote.status();break;
            }
            String outcome=status<500?"FAILED":"UNKNOWN";
            audit.finish(id,outcome,Map.of("httpStatus",status));
            throw new AdminFailure(status,status<500?"REMOTE_REJECTED":"DEPENDENCY_UNAVAILABLE",
                    status<500?"Downstream service rejected the request":"Downstream result could not be confirmed",outcome,Map.of("auditId",id.toString()));
        }
    }
    public record Context(UUID actor,String requestId){}
}
