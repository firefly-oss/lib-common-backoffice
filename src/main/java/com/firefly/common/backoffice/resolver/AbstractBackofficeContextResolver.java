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
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Abstract base implementation of BackofficeContextResolver.
 * Provides common functionality and template methods for backoffice context resolution with impersonation.
 * 
 * <p>Subclasses should implement the abstract methods to provide specific
 * resolution strategies for their use case.</p>
 * 
 * <p>This class handles:</p>
 * <ul>
 *   <li>Extraction of backoffice user ID from X-User-Id header</li>
 *   <li>Extraction of impersonated party ID from X-Impersonate-Party-Id header</li>
 *   <li>Validation of impersonation permissions</li>
 *   <li>Enrichment with roles and permissions for both users</li>
 *   <li>Audit trail creation</li>
 * </ul>
 * 
 * @author Firefly Development Team
 * @since 1.0.0
 */
@Slf4j
public abstract class AbstractBackofficeContextResolver implements BackofficeContextResolver {
    
    @Override
    public Mono<BackofficeContext> resolveContext(ServerWebExchange exchange) {
        log.debug("Resolving backoffice context for request (deprecated - use version with explicit IDs)");
        
        return Mono.zip(
                resolveBackofficeUserId(exchange),
                resolveImpersonatedPartyId(exchange),
                resolveTenantId(exchange),
                resolveContractId(exchange).defaultIfEmpty(UUID.randomUUID()), // placeholder UUID if empty
                resolveProductId(exchange).defaultIfEmpty(UUID.randomUUID())  // placeholder UUID if empty
        )
        .flatMap(tuple -> {
            UUID backofficeUserId = tuple.getT1();
            UUID impersonatedPartyId = tuple.getT2();
            UUID tenantId = tuple.getT3();
            UUID contractId = tuple.getT4();
            UUID productId = tuple.getT5();
            
            // Validate impersonation permission
            return validateImpersonationPermission(backofficeUserId, impersonatedPartyId, exchange)
                .flatMap(isAuthorized -> {
                    if (!isAuthorized) {
                        return Mono.error(new SecurityException(
                            String.format("Backoffice user %s is not authorized to impersonate party %s", 
                                backofficeUserId, impersonatedPartyId)));
                    }
                    
                    return enrichContext(
                            BackofficeContext.builder()
                                    .backofficeUserId(backofficeUserId)
                                    .impersonatedPartyId(impersonatedPartyId)
                                    .tenantId(tenantId)
                                    .contractId(contractId)
                                    .productId(productId)
                                    .impersonationStartedAt(Instant.now())
                                    .build(),
                            exchange
                    );
                });
        })
        .doOnSuccess(context -> log.debug("Successfully resolved backoffice context: backoffice user={}, impersonated party={}", 
                context.getBackofficeUserId(), context.getImpersonatedPartyId()))
        .doOnError(error -> log.error("Failed to resolve backoffice context", error));
    }
    
    @Override
    public Mono<BackofficeContext> resolveContext(ServerWebExchange exchange, UUID contractId, UUID productId) {
        log.debug("Resolving backoffice context with explicit contract: {} and product: {}", contractId, productId);
        
        return Mono.zip(
                resolveBackofficeUserId(exchange),
                resolveImpersonatedPartyId(exchange),
                resolveTenantId(exchange),
                resolveImpersonationReason(exchange).defaultIfEmpty("Not specified")
        )
        .flatMap(tuple -> {
            UUID backofficeUserId = tuple.getT1();
            UUID impersonatedPartyId = tuple.getT2();
            UUID tenantId = tuple.getT3();
            String impersonationReason = tuple.getT4();
            
            // Validate impersonation permission
            return validateImpersonationPermission(backofficeUserId, impersonatedPartyId, exchange)
                .flatMap(isAuthorized -> {
                    if (!isAuthorized) {
                        return Mono.error(new SecurityException(
                            String.format("Backoffice user %s is not authorized to impersonate party %s", 
                                backofficeUserId, impersonatedPartyId)));
                    }
                    
                    // Extract IP address for audit
                    String ipAddress = extractIpAddress(exchange);
                    
                    return enrichContext(
                            BackofficeContext.builder()
                                    .backofficeUserId(backofficeUserId)
                                    .impersonatedPartyId(impersonatedPartyId)
                                    .tenantId(tenantId)
                                    .contractId(contractId)  // Explicit from controller
                                    .productId(productId)     // Explicit from controller
                                    .impersonationStartedAt(Instant.now())
                                    .impersonationReason(impersonationReason)
                                    .backofficeUserIpAddress(ipAddress)
                                    .build(),
                            exchange
                    );
                });
        })
        .doOnSuccess(context -> log.debug("Successfully resolved backoffice context: backoffice user={}, impersonated party={}, contract={}, product={}", 
                context.getBackofficeUserId(), context.getImpersonatedPartyId(), 
                context.getContractId(), context.getProductId()))
        .doOnError(error -> log.error("Failed to resolve backoffice context", error));
    }
    
    /**
     * Enriches the basic context with roles, permissions, and additional data.
     * This method should fetch data from platform services for both the backoffice user and impersonated party.
     * 
     * @param basicContext the basic context with IDs
     * @param exchange the server web exchange
     * @return Mono of enriched BackofficeContext
     */
    protected Mono<BackofficeContext> enrichContext(BackofficeContext basicContext, 
                                                    ServerWebExchange exchange) {
        return Mono.zip(
                resolveBackofficeRoles(basicContext, exchange),
                resolveBackofficePermissions(basicContext, exchange),
                resolveImpersonatedPartyRoles(basicContext, exchange),
                resolveImpersonatedPartyPermissions(basicContext, exchange)
        )
        .map(tuple -> basicContext.toBuilder()
                .backofficeRoles(tuple.getT1())
                .backofficePermissions(tuple.getT2())
                .impersonatedPartyRoles(tuple.getT3())
                .impersonatedPartyPermissions(tuple.getT4())
                .build())
        .defaultIfEmpty(basicContext);
    }
    
    /**
     * Resolves roles for the backoffice user.
     * These are backoffice-specific roles like "admin", "support", "analyst", etc.
     * 
     * @param context the backoffice context
     * @param exchange the server web exchange
     * @return Mono of role set
     */
    protected Mono<Set<String>> resolveBackofficeRoles(BackofficeContext context, ServerWebExchange exchange) {
        log.debug("Resolving backoffice roles for user: {}", context.getBackofficeUserId());
        return Mono.just(Set.of());
    }
    
    /**
     * Resolves permissions for the backoffice user.
     * These are derived from backoffice roles.
     * 
     * @param context the backoffice context
     * @param exchange the server web exchange
     * @return Mono of permission set
     */
    protected Mono<Set<String>> resolveBackofficePermissions(BackofficeContext context, ServerWebExchange exchange) {
        log.debug("Resolving backoffice permissions for user: {}", context.getBackofficeUserId());
        return Mono.just(Set.of());
    }
    
    /**
     * Resolves roles for the impersonated party in the context of the contract/product.
     * These are informational - the backoffice user's permissions take precedence.
     * 
     * @param context the backoffice context
     * @param exchange the server web exchange
     * @return Mono of role set
     */
    protected Mono<Set<String>> resolveImpersonatedPartyRoles(BackofficeContext context, ServerWebExchange exchange) {
        log.debug("Resolving impersonated party roles for party: {} in contract: {}", 
                context.getImpersonatedPartyId(), context.getContractId());
        return Mono.just(Set.of());
    }
    
    /**
     * Resolves permissions for the impersonated party in the context of the contract/product.
     * These are informational - the backoffice user's permissions take precedence.
     * 
     * @param context the backoffice context
     * @param exchange the server web exchange
     * @return Mono of permission set
     */
    protected Mono<Set<String>> resolveImpersonatedPartyPermissions(BackofficeContext context, ServerWebExchange exchange) {
        log.debug("Resolving impersonated party permissions for party: {} in contract: {}, product: {}", 
                context.getImpersonatedPartyId(), context.getContractId(), context.getProductId());
        return Mono.just(Set.of());
    }
    
    /**
     * Extracts UUID from request attribute or header.
     * 
     * @param exchange the server web exchange
     * @param attributeName the attribute name
     * @param headerName the header name
     * @return Mono of UUID
     */
    protected Mono<UUID> extractUUID(ServerWebExchange exchange, String attributeName, String headerName) {
        // Try to get from attribute first
        UUID fromAttribute = exchange.getAttribute(attributeName);
        if (fromAttribute != null) {
            return Mono.just(fromAttribute);
        }
        
        // Try to get from header
        String headerValue = exchange.getRequest().getHeaders().getFirst(headerName);
        if (headerValue != null && !headerValue.isEmpty()) {
            try {
                return Mono.just(UUID.fromString(headerValue));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid UUID format in header {}: {}", headerName, headerValue);
            }
        }
        
        return Mono.empty();
    }
    
    /**
     * Extracts string value from request attribute or header.
     * 
     * @param exchange the server web exchange
     * @param attributeName the attribute name
     * @param headerName the header name
     * @return Mono of String
     */
    protected Mono<String> extractString(ServerWebExchange exchange, String attributeName, String headerName) {
        // Try to get from attribute first
        String fromAttribute = exchange.getAttribute(attributeName);
        if (fromAttribute != null && !fromAttribute.isEmpty()) {
            return Mono.just(fromAttribute);
        }
        
        // Try to get from header
        String headerValue = exchange.getRequest().getHeaders().getFirst(headerName);
        if (headerValue != null && !headerValue.isEmpty()) {
            return Mono.just(headerValue);
        }
        
        return Mono.empty();
    }
    
    /**
     * Extracts IP address from the request.
     * 
     * @param exchange the server web exchange
     * @return IP address or "unknown"
     */
    protected String extractIpAddress(ServerWebExchange exchange) {
        // Try X-Forwarded-For first (for proxied requests)
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Take the first IP in the chain
            return xForwardedFor.split(",")[0].trim();
        }
        
        // Try X-Real-IP
        String xRealIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        // Fall back to remote address
        if (exchange.getRequest().getRemoteAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        
        return "unknown";
    }
    
    @Override
    public Mono<UUID> resolveContractId(ServerWebExchange exchange) {
        // Contract ID is not extracted here - it must be passed explicitly by controllers
        // Controllers extract contractId from @PathVariable and pass it to services
        log.debug("Contract ID resolution delegated to controller layer");
        return Mono.empty();
    }
    
    @Override
    public Mono<UUID> resolveProductId(ServerWebExchange exchange) {
        // Product ID is not extracted here - it must be passed explicitly by controllers
        // Controllers extract productId from @PathVariable and pass it to services
        log.debug("Product ID resolution delegated to controller layer");
        return Mono.empty();
    }
    
    @Override
    public Mono<String> resolveImpersonationReason(ServerWebExchange exchange) {
        log.debug("Resolving impersonation reason from request");
        return extractString(exchange, "impersonationReason", "X-Impersonation-Reason");
    }
}
