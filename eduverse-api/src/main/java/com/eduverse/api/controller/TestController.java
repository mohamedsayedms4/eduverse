package com.eduverse.api.controller;

import com.eduverse.tenant.context.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @GetMapping("/tenant")
    public Map<String, String> getTenantInfo() {
        return Map.of(
            "currentTenant", TenantContext.getCurrentTenant() != null ? TenantContext.getCurrentTenant() : "none",
            "message", "Tenant isolation check"
        );
    }
}
