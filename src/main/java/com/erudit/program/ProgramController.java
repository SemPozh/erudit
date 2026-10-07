package com.erudit.program;

import com.erudit.openapi.api.ProgramsApi;
import com.erudit.openapi.model.NextStepResponse;
import com.erudit.openapi.model.ProgramResponse;
import com.erudit.web.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProgramController implements ProgramsApi {
    private final ProgramService service;
    private final HttpServletRequest request;

    public ProgramController(ProgramService service, HttpServletRequest request) {
        this.service = service;
        this.request = request;
    }

    @Override
    public ResponseEntity<ProgramResponse> generateProgram() {
        return ResponseEntity.status(HttpStatus.CREATED).body(response(service.generate(currentUser())));
    }

    @Override
    public ResponseEntity<ProgramResponse> getMyProgram() {
        return ResponseEntity.ok(response(service.get(currentUser())));
    }

    @Override
    public ResponseEntity<ProgramResponse> getProgramProgress() {
        return ResponseEntity.ok(response(service.get(currentUser())));
    }

    @Override
    public ResponseEntity<NextStepResponse> getNextStep() {
        NextStep value = service.next(currentUser());
        var data = new com.erudit.openapi.model.NextStep()
                .contentId(value.contentId()).quizId(value.quizId()).reason(value.reason())
                .streakDays(value.streakDays()).dailyGoalProgress(value.dailyGoalProgress());
        return ResponseEntity.ok(new NextStepResponse(data));
    }

    private ProgramResponse response(LearningProgram program) {
        var modules = program.modules().stream().map(module -> {
            var lessons = module.lessons().stream().map(lesson -> new com.erudit.openapi.model.ProgramLesson(
                    lesson.id(), lesson.contentId(), lesson.quizId(),
                    com.erudit.openapi.model.ProgramLesson.StatusEnum.valueOf(lesson.status().name()),
                    lesson.status() == LessonStatus.COMPLETED ? 100.0 : 0.0)).toList();
            return new com.erudit.openapi.model.ProgramModule(module.id(), module.topic(), module.title(),
                    module.progressPercent(), lessons);
        }).toList();
        return new ProgramResponse(new com.erudit.openapi.model.Program(
                program.id(), modules, program.progressPercent()));
    }

    private String currentUser() {
        if (request.getUserPrincipal() == null) throw new UnauthorizedException("Authentication is required");
        return request.getUserPrincipal().getName();
    }
}
