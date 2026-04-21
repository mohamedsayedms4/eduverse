package com.eduverse.tenant.hibernate;

import com.eduverse.tenant.context.TenantContext;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SqlserverTenantInterceptor implements StatementInspector {

    @Override
    public String inspect(String sql) {
        String tenantId = TenantContext.getCurrentTenant();
        
        // Only transform if we have a real tenant and it's not the default public/dbo
        if (tenantId != null && !"public".equals(tenantId) && !"dbo".equals(tenantId)) {
            // Qualify the core tables using regex to handle word boundaries safely
            String transformedSql = sql.replaceAll("\\b(users|refresh_tokens|courses|lessons)\\b", "[" + tenantId + "].$1");
            
            if (!sql.equals(transformedSql)) {
                log.debug("Transformed SQL for tenant {}: {}", tenantId, transformedSql);
            }
            return transformedSql;
        }
        
        return sql;
    }
}
