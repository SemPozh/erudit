package com.erudit.payment.controller;
import com.erudit.payment.openapi.model.ErrorResponse;
import com.erudit.payment.openapi.model.ApiErrorDetailResponse;
import com.erudit.payment.service.PaymentException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class PaymentExceptionHandler {
    @ExceptionHandler(PaymentException.class)
    ResponseEntity<ErrorResponse> handle(PaymentException e) {
        return ResponseEntity.status(e.status()).body(new ErrorResponse(new ApiErrorDetailResponse("PAYMENT_" + e.status(), e.getMessage())));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(new ApiErrorDetailResponse("VALIDATION_ERROR", "Invalid payment request")));
    }
}
