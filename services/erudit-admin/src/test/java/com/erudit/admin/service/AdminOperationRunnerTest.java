package com.erudit.admin.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class AdminOperationRunnerTest {
    @Test void validationAndLiteralTemplateValuesAreHandledBeforeDelivery(){
        assertThat(AdminFacadeService.render("Hello {{name}}",Map.of("name","$1\\value"))).isEqualTo("Hello $1\\value");
        assertThatThrownBy(()->AdminFacadeService.render("Hello {{name}}",Map.of())).isInstanceOf(AdminFailure.class);
    }
    @Test void auditIntentIsCommittedBeforeCallAndFailuresKeepTheirOutcome(){
        var writer=mock(AdminAuditWriter.class);var runner=new AdminOperationRunner(writer);UUID audit=UUID.randomUUID();
        var context=new AdminOperationRunner.Context(UUID.randomUUID(),"unit-90");
        when(writer.start(any(),anyString(),anyString(),anyString(),anyString())).thenReturn(audit);
        assertThatThrownBy(()->runner.execute(context,"ACTION","USER","id",()->{throw new AdminFailure(503,"PARTIAL_FAILURE","partial","PARTIAL",Map.of("sent",1));})).isInstanceOf(AdminFailure.class);
        var order=inOrder(writer);order.verify(writer).start(context.actor(),"unit-90","ACTION","USER","id");order.verify(writer).finish(audit,"PARTIAL",Map.of("sent",1));
    }
}
