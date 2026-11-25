package com.erudit.quiz;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

class CleanArchitectureTest {
    @Test void dependenciesPointInward() {
        var classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.erudit.quiz");
        noClasses().that().resideInAnyPackage("..domain..", "..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "java.sql..", "javax.sql..", "feign..", "io.github.resilience4j..",
                        "tools.jackson..", "..adapter..", "..config..", "..openapi..", "com.erudit.integration..",
                        "com.erudit.user.client..", "com.erudit.notificator..", "com.erudit.aiplatform..").check(classes);
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAPackage("..application..").check(classes);
        noClasses().that().resideInAPackage("..adapter.in..")
                .should().dependOnClassesThat().resideInAPackage("..adapter.out..").check(classes);
        noClasses().that().resideInAPackage("..adapter.out..")
                .should().dependOnClassesThat().resideInAPackage("..adapter.in..").check(classes);
        slices().matching("com.erudit.quiz.(*)..").should().beFreeOfCycles().check(classes);
    }
}
