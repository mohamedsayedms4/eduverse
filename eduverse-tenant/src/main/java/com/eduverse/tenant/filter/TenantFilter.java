package com.eduverse.tenant.filter;

import com.eduverse.tenant.context.TenantContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantFilter implements Filter {

    private static final String DEFAULT_TENANT = "public";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String tenantId = resolveTenantId(httpRequest);

        try {
            TenantContext.setCurrentTenant(tenantId);
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String resolveTenantId(HttpServletRequest request) {
        // 1. Check Header (High priority for API calls)
        String tenantId = request.getHeader("X-TenantID");
        if (tenantId != null && !tenantId.isEmpty()) {
            return tenantId;
        }

        // 2. Check Query Parameter (Good for simple testing)
        tenantId = request.getParameter("tenant");
        if (tenantId != null && !tenantId.isEmpty()) {
            return tenantId;
        }

        // 3. Check Subdomain (Standard for Production)
        String host = request.getHeader("Host");
        if (host != null && !host.isEmpty()) {
            String[] parts = host.split("\\.");
            if (parts.length > 2) {
                return parts[0];
            }
        }
        
        return DEFAULT_TENANT;
    }
}
