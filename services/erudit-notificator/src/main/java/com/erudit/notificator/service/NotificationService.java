package com.erudit.notificator.service;

import com.erudit.notificator.model.NotificationRecord;
import com.erudit.notificator.model.Preference;
import com.erudit.notificator.model.Template;
import com.erudit.notificator.repository.NotificationRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Service
public class NotificationService {
    private final NotificationRepository repository;
    private final Scheduler jdbc;
    public NotificationService(NotificationRepository repository,@Value("${erudit.notifications.jdbc-threads:4}")int threads,
                               @Value("${erudit.notifications.jdbc-queue:256}")int queue){this.repository=repository;this.jdbc=Schedulers.newBoundedElastic(threads,queue,"notification-jdbc");}
    public Mono<Preference> preference(UUID user){return blocking(()->repository.preference(user));}
    public Mono<Preference> savePreference(Preference value){return blocking(()->repository.save(value));}
    public Mono<Page> list(UUID user,int page,int size){if(page<0||size<1||size>50)return Mono.error(new IllegalArgumentException("Invalid page or size"));return blocking(()->new Page(repository.list(user,page,size),repository.count(user),page,size));}
    public Mono<Long> unread(UUID user){return blocking(()->repository.unread(user));}
    public Mono<Void> read(UUID user,UUID id){return blocking(()->{if(!repository.read(user,id))throw new IllegalArgumentException("Notification not found");return 0;}).then();}
    public Mono<Void> readAll(UUID user){return blocking(()->{repository.readAll(user);return 0;}).then();}
    public Flux<Template> templates(){return blocking(repository::templates).flatMapMany(Flux::fromIterable);}
    public Mono<Template> saveTemplate(Template value){return blocking(()->repository.save(value));}
    public Mono<Template> template(UUID id){return blocking(()->repository.template(id).orElseThrow(()->new IllegalArgumentException("Template not found")));}
    public Mono<Template> updateTemplate(Template value){return blocking(()->repository.update(value));}
    public Mono<Void> deleteTemplate(UUID id){return blocking(()->{repository.deleteTemplate(id);return 0;}).then();}
    public Mono<NotificationRecord> enqueue(UUID user,String code,String title,String body){return blocking(()->{Template t=repository.template(code).orElseThrow(()->new IllegalArgumentException("Template not found"));return repository.insert(user,code,title==null?t.title():title,body==null?t.body():body);});}
    public Flux<NotificationRecord> enqueue(List<Enqueue> values){if(values==null||values.isEmpty()||values.size()>100)return Flux.error(new IllegalArgumentException("Batch size must be 1..100"));return Flux.fromIterable(values).concatMap(v->enqueue(v.user(),v.code(),v.title(),v.body()));}
    private <T>Mono<T>blocking(java.util.concurrent.Callable<T> action){return Mono.fromCallable(action).subscribeOn(jdbc);}
    public record Page(List<NotificationRecord> items,long total,int page,int size){}
    public record Enqueue(UUID user,String code,String title,String body){}
}
