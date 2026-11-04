package com.erudit.admin.controller;
import com.erudit.admin.openapi.model.ApiErrorDetailResponse;
import com.erudit.admin.openapi.model.ErrorResponse;
import com.erudit.admin.service.AdminFailure;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice @Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminExceptionHandler {
    @ExceptionHandler(AdminFailure.class)
    ResponseEntity<ErrorResponse> failure(AdminFailure failure,HttpServletRequest request){
        return ResponseEntity.status(failure.status()).body(new ErrorResponse(new ApiErrorDetailResponse(failure.code(),failure.getMessage()))
                .requestId(request.getHeader("X-Request-ID")).details(failure.details()));
    }
}
