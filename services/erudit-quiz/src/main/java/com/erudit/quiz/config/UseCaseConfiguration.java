package com.erudit.quiz.config;

import com.erudit.quiz.application.*;
import com.erudit.quiz.application.port.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.TransactionProxyFactoryBean;
import java.util.Properties;

@Configuration(proxyBeanMethods = false)
public class UseCaseConfiguration {
    @Bean AssessmentService assessmentService(AssessmentRepository assessments, PlatformTransactionManager transactions) {
        return transactional(new AssessmentService(assessments), transactions, "start", "submit");
    }
    @Bean QuizService quizService(QuizRepository quizzes, QuizGeneration generation, PlatformTransactionManager transactions) {
        return transactional(new QuizService(quizzes, generation), transactions, "create", "start", "hint", "submit");
    }
    @Bean CompetitionService competitionService(CompetitionRepository competitions, CompetitionParticipants participants, PlatformTransactionManager transactions) {
        return transactional(new CompetitionService(competitions, participants), transactions, "create", "invite", "join");
    }
    private static <T> T transactional(T target, PlatformTransactionManager transactions, String... writes) {
        Properties attributes = new Properties();
        attributes.setProperty("*", "PROPAGATION_REQUIRED,readOnly");
        for (String method : writes) attributes.setProperty(method, "PROPAGATION_REQUIRED");
        TransactionProxyFactoryBean proxy = new TransactionProxyFactoryBean();
        proxy.setTarget(target);
        proxy.setProxyTargetClass(true);
        proxy.setTransactionManager(transactions);
        proxy.setTransactionAttributes(attributes);
        proxy.afterPropertiesSet();
        return (T) proxy.getObject();
    }
}
