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
        String host = httpRequest.getHeader("Host");
        String tenantId = extractTenantId(host);

        try {
            TenantContext.setCurrentTenant(tenantId);
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String extractTenantId(String host) {
        if (host == null || host.isEmpty()) {
            return DEFAULT_TENANT;
        }

        // Host example: teacher1.eduverse.com
        String[] parts = host.split("\\.");
        if (parts.length > 2) {
            // Return the first part as tenant (subdomain)
            return parts[0];
        }
        
        return DEFAULT_TENANT;
    }
}
