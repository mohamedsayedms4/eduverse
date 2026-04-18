package com.eduverse.api.controller;

import com.eduverse.tenant.model.Tenant;
import com.eduverse.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/register")
@RequiredArgsConstructor
public class RegistrationController {

    private final TenantService tenantService;

    @PostMapping
    public Tenant register(@RequestParam String tenantId, @RequestParam String name) {
        return tenantService.registerTenant(tenantId, name);
    }
}
