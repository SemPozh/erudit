package com.erudit.admin.service;
import java.util.Map;
public class AdminFailure extends RuntimeException {
    private final int status;
    private final String code;
    private final String outcome;
    private final Map<String,Object> details;
    public AdminFailure(int status,String code,String message,String outcome,Map<String,Object> details){super(message);this.status=status;this.code=code;this.outcome=outcome;this.details=details;}
    public int status(){return status;} public String code(){return code;} public String outcome(){return outcome;} public Map<String,Object> details(){return details;}
    public static AdminFailure invalid(String message){return new AdminFailure(400,"VALIDATION_ERROR",message,"FAILED",Map.of());}
}
