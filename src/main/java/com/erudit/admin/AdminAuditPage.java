package com.erudit.admin;
import java.util.List; public record AdminAuditPage(List<AdminAuditRecord> items,long total,int page,int size){}
