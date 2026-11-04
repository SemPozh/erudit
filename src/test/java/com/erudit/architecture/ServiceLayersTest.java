package com.erudit.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static org.assertj.core.api.Assertions.*;

class ServiceLayersTest {
    @Test void compiledClassesRespectLayersAndServiceOwnership() throws Exception {
        Path root=Path.of(System.getProperty("architecture.root"));
        var paths=new ArrayList<Path>();
        for(String group:List.of("services","platform","infrastructure")){
            try(var modules=Files.list(root.resolve(group))){
                for(var module:modules.filter(Files::isDirectory).toList()){
                    var classes=module.resolve("build/classes/java/main");
                    assertThat(classes).isDirectory();paths.add(classes);
                }
            }
        }
        var imported=new ClassFileImporter().importPaths(paths);
        assertThat(imported.size()).isGreaterThan(100);
        noClasses().that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAPackage("..repository..").check(imported);
        noClasses().that().resideInAnyPackage("..service..","..repository..")
                .should().dependOnClassesThat().resideInAPackage("..controller..").check(imported);
        classes().that().resideInAPackage("..repository..")
                .should().onlyBeAccessed().byAnyPackage("..service..","..repository..").check(imported);
        noClasses().should().dependOnClassesThat().resideInAnyPackage("org.springframework.security..","io.jsonwebtoken..").check(imported);
        var owners=List.of("admin","user","content","quiz","payment","notificator","eventreceiver","aiplatform");
        for(String owner:owners){
            var foreign=owners.stream().filter(name->!name.equals(owner)).map(name->"com.erudit."+name+"..").toArray(String[]::new);
            noClasses().that().resideInAPackage("com.erudit."+owner+"..")
                    .should().dependOnClassesThat().resideInAnyPackage(foreign).check(imported);
        }
        noClasses().that().resideInAnyPackage("com.erudit.integration..","com.erudit.observability..")
                .should().dependOnClassesThat().resideInAnyPackage(owners.stream().map(name->"com.erudit."+name+"..").toArray(String[]::new)).check(imported);
    }
}
