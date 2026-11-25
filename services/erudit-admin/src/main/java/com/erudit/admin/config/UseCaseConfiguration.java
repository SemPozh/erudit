package com.erudit.admin.config;

import com.erudit.admin.application.*;
import com.erudit.admin.application.port.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class UseCaseConfiguration {
    @Bean AdminOperationRunner adminOperationRunner(AuditLog audit) { return new AdminOperationRunner(audit); }
    @Bean AdminFacadeService adminFacadeService(AdminRemote.Users users, AdminRemote.Content content,
            AdminRemote.Notifications notifications, AdminRemote.Payments payments,
            AdminOperationRunner operations, AdminAuditRepository audit) {
        return new AdminFacadeService(users, content, notifications, payments, operations, audit);
    }
}
