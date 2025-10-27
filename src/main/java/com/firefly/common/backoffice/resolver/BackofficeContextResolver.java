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

package com.firefly.common.backoffice.resolver;

import com.firefly.common.backoffice.context.BackofficeContext;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Interface for resolving backoffice context with customer impersonation from incoming requests.
 * Implementations are responsible for extracting and enriching context information including:
 * <ul>
 *   <li>backofficeUserId - The actual backoffice/admin user making the request</li>
 *   <li>impersonatedPartyId - The customer (party) being accessed/impersonated</li>
 *   <li>contractId, productId - Business context identifiers</li>
 *   <li>roles and permissions - For both backoffice user and impersonated party</li>
 * </ul>
 * 
 * <p>This is the main entry point for backoffice context resolution with impersonation support.</p>
 * 
 * <h2>Expected Headers (Injected by Istio for Backoffice Routes)</h2>
 * <ul>
 *   <li><code>X-User-Id</code> - Backoffice user UUID (required) - Authenticated backoffice user</li>
 *   <li><code>X-Impersonate-Party-Id</code> - Customer party UUID (required) - Customer being accessed</li>
 *   <li><code>X-Tenant-Id</code> - Tenant UUID (optional) - Can be resolved from party</li>
 *   <li><code>X-Impersonation-Reason</code> - Reason for impersonation (optional, for audit)</li>
 * </ul>
 * 
 * @author Firefly Development Team
 * @since 1.0.0
 */
public interface BackofficeContextResolver {
    
    /**
     * Resolves the complete backoffice context from the request.
     * This method extracts all IDs automatically (backoffice user, impersonated party, tenant, contract, product).
     * 
     * @param exchange the server web exchange
     * @return Mono of resolved BackofficeContext
     */
    Mono<BackofficeContext> resolveContext(ServerWebExchange exchange);
    
    /**
     * Resolves the backoffice context with explicit contractId and productId.
     * This is the method controllers should use to pass IDs extracted from {@code @PathVariable}.
     * 
     * <p>Backoffice user and impersonated party IDs are extracted from Istio headers 
     * (X-User-Id, X-Impersonate-Party-Id), but contract and product IDs are provided 
     * explicitly by the controller.</p>
     * 
     * @param exchange the server web exchange
     * @param contractId the contract ID from {@code @PathVariable} (nullable)
     * @param productId the product ID from {@code @PathVariable} (nullable)
     * @return Mono of resolved BackofficeContext
     */
    Mono<BackofficeContext> resolveContext(ServerWebExchange exchange, UUID contractId, UUID productId);
    
    /**
     * Resolves the backoffice user ID from the request.
     * This should extract the authenticated backoffice/admin user identifier.
     * 
     * <p>Expected header: X-User-Id (injected by Istio for backoffice routes)</p>
     * 
     * @param exchange the server web exchange
     * @return Mono of backoffice user UUID
     */
    Mono<UUID> resolveBackofficeUserId(ServerWebExchange exchange);
    
    /**
     * Resolves the impersonated party ID from the request.
     * This is the customer (party) whose data is being accessed or modified.
     * 
     * <p>Expected header: X-Impersonate-Party-Id (required for backoffice operations on customer data)</p>
     * 
     * @param exchange the server web exchange
     * @return Mono of impersonated party UUID
     */
    Mono<UUID> resolveImpersonatedPartyId(ServerWebExchange exchange);
    
    /**
     * Resolves the contract ID from the request.
     * This may come from path parameters, query parameters, or headers.
     * 
     * @param exchange the server web exchange
     * @return Mono of contract UUID (may be empty)
     */
    Mono<UUID> resolveContractId(ServerWebExchange exchange);
    
    /**
     * Resolves the product ID from the request.
     * This may come from path parameters, query parameters, or headers.
     * 
     * @param exchange the server web exchange
     * @return Mono of product UUID (may be empty)
     */
    Mono<UUID> resolveProductId(ServerWebExchange exchange);
    
    /**
     * Resolves the tenant ID from the request.
     * This typically comes from the impersonated party's tenant association.
     * 
     * @param exchange the server web exchange
     * @return Mono of tenant UUID
     */
    Mono<UUID> resolveTenantId(ServerWebExchange exchange);
    
    /**
     * Resolves the impersonation reason from the request (for audit trail).
     * This may come from headers or request attributes.
     * 
     * @param exchange the server web exchange
     * @return Mono of impersonation reason (may be empty)
     */
    Mono<String> resolveImpersonationReason(ServerWebExchange exchange);
    
    /**
     * Validates that the backoffice user has permission to impersonate the given party.
     * This should check with the Security Center or permission service.
     * 
     * @param backofficeUserId the backoffice user requesting impersonation
     * @param impersonatedPartyId the party being impersonated
     * @param exchange the server web exchange
     * @return Mono of boolean indicating if impersonation is authorized
     */
    Mono<Boolean> validateImpersonationPermission(UUID backofficeUserId, 
                                                   UUID impersonatedPartyId, 
                                                   ServerWebExchange exchange);
    
    /**
     * Checks if this resolver supports the given request.
     * Allows for multiple resolver implementations with different strategies.
     * 
     * @param exchange the server web exchange
     * @return true if this resolver can handle the request
     */
    default boolean supports(ServerWebExchange exchange) {
        return true;
    }
    
    /**
     * Priority of this resolver (higher values take precedence).
     * Used when multiple resolvers support the same request.
     * 
     * @return priority value
     */
    default int getPriority() {
        return 0;
    }
}
