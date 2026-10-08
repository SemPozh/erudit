package com.erudit.admin.dto;

import com.erudit.admin.model.AdminAuditRecord;
import java.util.List; public record AdminAuditPage(List<AdminAuditRecord> items,long total,int page,int size){}
