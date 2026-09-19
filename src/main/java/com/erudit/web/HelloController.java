package com.erudit.web;

import com.erudit.openapi.api.DiagnosticsApi;
import com.erudit.openapi.model.HelloResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController implements DiagnosticsApi {

    @Override
    public ResponseEntity<HelloResponse> hello() {
        return ResponseEntity.ok(new HelloResponse("Hello, World!"));
    }
}
