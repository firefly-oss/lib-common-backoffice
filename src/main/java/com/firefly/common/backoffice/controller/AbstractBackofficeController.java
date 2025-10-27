/*
 * Copyright 2025 Firefly Software Solutions Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.firefly.common.backoffice.controller;

import com.firefly.common.backoffice.resolver.BackofficeContextResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * <h1>Abstract Base Controller for Backoffice Administrative Endpoints</h1>
 * 
 * <p>This base class is for controllers that operate on <strong>administrative resources</strong>
 * without requiring customer impersonation. Perfect for system administration, reports,
 * or backoffice operations that don't access specific customer data.</p>
 * 
 * <h2>When to Use</h2>
 * <p>Extend this class when building REST endpoints for:</p>
 * <ul>
 *   <li><strong>System Reports:</strong> Analytics, dashboards, statistics</li>
 *   <li><strong>Configuration Management:</strong> Tenant settings, feature flags</li>
 *   <li><strong>User Management:</strong> Creating/managing backoffice users</li>
 *   <li><strong>Bulk Operations:</strong> Batch processing, data imports</li>
 * </ul>
 * 
 * <h2>Architecture</h2>
 * <p>This controller automatically extracts:</p>
 * <ul>
 *   <li><strong>Backoffice User ID:</strong> From Istio-injected <code>X-User-Id</code> header</li>
 *   <li><strong>Backoffice Roles:</strong> Admin, support, analyst roles</li>
 *   <li><strong>No Customer Impersonation:</strong> Operations don't access customer data</li>
 * </ul>
 * 
 * <h2>Quick Example</h2>
 * <pre>
 * {@code
 * @RestController
 * @RequestMapping("/backoffice/api/v1/reports")
 * public class ReportsController extends AbstractBackofficeController {
 *     
 *     @Autowired
 *     private ReportsService reportsService;
 *     
 *     @GetMapping("/daily-summary")
 *     @BackofficeSecure(roles = "analyst")
 *     public Mono<DailySummaryResponse> getDailySummary(ServerWebExchange exchange) {
 *         
 *         UUID backofficeUserId = extractBackofficeUserId(exchange);
 *         logAdminOperation(backofficeUserId, "getDailySummary");
 *         
 *         return reportsService.generateDailySummary();
 *     }
 * }
 * }
 * </pre>
 * 
 * <h2>What You Get</h2>
 * <ul>
 *   <li><strong>Backoffice User Extraction:</strong> {@link #extractBackofficeUserId(ServerWebExchange)}</li>
 *   <li><strong>No Customer Context:</strong> Administrative operations only</li>
 *   <li><strong>Audit Logging:</strong> {@link #logAdminOperation(UUID, String)}</li>
 * </ul>
 * 
 * @author Firefly Development Team
 * @since 1.0.0
 * @see AbstractBackofficeResourceController For customer-specific endpoints (with impersonation)
 */
@Slf4j
public abstract class AbstractBackofficeController {
    
    @Autowired
    private BackofficeContextResolver contextResolver;
    
    /**
     * Extracts the backoffice user ID from the request.
     * 
     * <p>The backoffice user ID is injected by Istio in the <code>X-User-Id</code> header
     * after JWT validation. This represents the authenticated admin/support user.</p>
     * 
     * @param exchange the server web exchange
     * @return the backoffice user UUID
     */
    protected Mono<UUID> extractBackofficeUserId(ServerWebExchange exchange) {
        return contextResolver.resolveBackofficeUserId(exchange)
                .doOnNext(userId -> log.debug("Extracted backoffice user ID: {}", userId))
                .doOnError(error -> log.error("Failed to extract backoffice user ID", error));
    }
    
    /**
     * Logs an administrative operation performed by a backoffice user.
     * 
     * <p>This creates an audit trail for all backoffice operations.</p>
     * 
     * @param backofficeUserId the backoffice user performing the operation
     * @param operation description of the operation (e.g., "getDailySummary", "updateConfig")
     */
    protected void logAdminOperation(UUID backofficeUserId, String operation) {
        log.info("[Backoffice Admin] User: {}, Operation: {}", backofficeUserId, operation);
    }
    
    /**
     * Convenience method to log an operation with the exchange.
     * 
     * @param exchange the server web exchange
     * @param operation description of the operation
     */
    protected void logOperation(ServerWebExchange exchange, String operation) {
        extractBackofficeUserId(exchange)
                .subscribe(userId -> logAdminOperation(userId, operation));
    }
}
