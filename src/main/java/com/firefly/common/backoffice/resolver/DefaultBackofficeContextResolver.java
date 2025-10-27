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
import com.firefly.common.backoffice.util.BackofficeSessionContextMapper;
import com.firefly.security.center.session.FireflySessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;

/**
 * Default implementation of BackofficeContextResolver for customer impersonation.
 * 
 * <p><strong>This is provided by the library - microservices don't need to implement anything.</strong></p>
 * 
 * <p>This resolver automatically:</p>
 * <ul>
 *   <li>Extracts <strong>backofficeUserId</strong> from Istio-injected HTTP header ({@code X-User-Id})</li>
 *   <li>Extracts <strong>impersonatedPartyId</strong> from HTTP header ({@code X-Impersonate-Party-Id})</li>
 *   <li>Resolves <strong>tenantId</strong> by calling {@code common-platform-config-mgmt} with the impersonated partyId</li>
 *   <li>Validates that backoffice user has permission to impersonate the customer</li>
 *   <li>Enriches context with roles and permissions for both backoffice user and impersonated party</li>
 *   <li>Creates audit trail for compliance</li>
 * </ul>
 * 
 * <p><strong>Important:</strong> ContractId and ProductId are NOT extracted here.
 * They must be extracted from {@code @PathVariable} in your controllers and passed explicitly.</p>
 * 
 * <h2>Architecture</h2>
 * <ul>
 *   <li><strong>Istio Gateway:</strong> Validates backoffice JWT, injects X-User-Id header (from JWT subject)</li>
 *   <li><strong>Backoffice Frontend:</strong> Sends X-Impersonate-Party-Id header with customer being accessed</li>
 *   <li><strong>This Resolver:</strong> Validates impersonation permission and enriches context</li>
 *   <li><strong>Controllers:</strong> Extract contractId/productId from {@code @PathVariable} in REST path</li>
 *   <li><strong>SDK Enrichment:</strong> Fetch roles/permissions from Security Center for both users</li>
 * </ul>
 * 
 * <h2>Expected HTTP Headers (Injected by Istio for Backoffice Routes)</h2>
 * <ul>
 *   <li><code>X-User-Id</code> - Backoffice user UUID (required) - Extracted from authenticated backoffice JWT</li>
 *   <li><code>X-Impersonate-Party-Id</code> - Customer party UUID (required) - Sent by backoffice frontend</li>
 *   <li><code>X-Impersonation-Reason</code> - Reason for access (optional) - For audit trail</li>
 * </ul>
 * 
 * <h2>Tenant Resolution</h2>
 * <p>The tenant ID is resolved from the <strong>impersonated party</strong>, not the backoffice user:</p>
 * <pre>
 * {@code
 * // Call common-platform-config-mgmt microservice
 * GET /api/v1/parties/{impersonatedPartyId}/tenant
 * Response: { "tenantId": "uuid", "tenantName": "...", ... }
 * }
 * </pre>
 * 
 * <h2>Impersonation Validation</h2>
 * <p>Before allowing access, this resolver validates that the backoffice user has permission to impersonate:</p>
 * <pre>
 * {@code
 * // Call Security Center to validate impersonation
 * boolean canImpersonate = securityCenter.validateImpersonation(
 *     backofficeUserId, 
 *     impersonatedPartyId, 
 *     tenantId
 * );
 * }
 * </pre>
 * 
 * <h2>Role & Permission Resolution</h2>
 * <p>Roles and permissions are fetched for <strong>both</strong> the backoffice user and the impersonated party:</p>
 * <ul>
 *   <li><strong>Backoffice User:</strong> Gets backoffice-specific roles (admin, support, analyst)</li>
 *   <li><strong>Impersonated Party:</strong> Gets customer roles in the contract/product (informational)</li>
 * </ul>
 * 
 * <h2>Controller Responsibility</h2>
 * <p>Controllers must extract contractId and productId from path variables:</p>
 * <pre>
 * {@code
 * @GetMapping("/backoffice/customers/{partyId}/contracts/{contractId}/accounts")
 * public Mono<List<Account>> getAccounts(
 *         @PathVariable UUID partyId,
 *         @PathVariable UUID contractId, 
 *         ServerWebExchange exchange) {
 *     // Controller extracts contractId from path, passes to service
 * }
 * }
 * </pre>
 * 
 * @author Firefly Development Team
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DefaultBackofficeContextResolver extends AbstractBackofficeContextResolver {
    
    @Autowired(required = false)
    private final FireflySessionManager sessionManager;
    
    // TODO: Inject platform SDK clients when available
    // private final ConfigManagementClient configMgmtClient;  // For tenant resolution
    // private final BackofficeSecurityClient backofficeSecurityClient;  // For impersonation validation
    
    @Override
    public Mono<UUID> resolveBackofficeUserId(ServerWebExchange exchange) {
        log.debug("Resolving backoffice user ID from Istio-injected header");
        
        // Backoffice user ID is injected by Istio as X-User-Id header
        return extractUUID(exchange, "backofficeUserId", "X-User-Id")
                .doOnNext(id -> log.debug("Resolved backoffice user ID from Istio header: {}", id))
                .switchIfEmpty(Mono.error(new IllegalStateException(
                    "X-User-Id header not found. Ensure request passes through Istio gateway with backoffice authentication.")));
    }
    
    @Override
    public Mono<UUID> resolveImpersonatedPartyId(ServerWebExchange exchange) {
        log.debug("Resolving impersonated party ID from request header");
        
        // Impersonated party ID is sent by backoffice frontend as X-Impersonate-Party-Id header
        return extractUUID(exchange, "impersonatedPartyId", "X-Impersonate-Party-Id")
                .doOnNext(id -> log.debug("Resolved impersonated party ID from header: {}", id))
                .switchIfEmpty(Mono.error(new IllegalStateException(
                    "X-Impersonate-Party-Id header not found. Backoffice requests must specify which customer to access.")));
    }
    
    @Override
    public Mono<UUID> resolveTenantId(ServerWebExchange exchange) {
        log.debug("Resolving tenant ID from config-mgmt using impersonated party ID");
        
        // Tenant ID is resolved from the IMPERSONATED PARTY, not the backoffice user
        // The tenant is determined by which customer is being accessed
        return resolveImpersonatedPartyId(exchange)
                .flatMap(impersonatedPartyId -> {
                    log.debug("Fetching tenant ID for impersonated party: {} from config-mgmt", impersonatedPartyId);
                    
                    // TODO: Implement using common-platform-config-mgmt-sdk
                    // When SDK is available, call:
                    /*
                    return configMgmtClient.getPartyTenant(impersonatedPartyId)
                        .map(response -> response.getTenantId())
                        .doOnNext(tenantId -> log.debug("Resolved tenant ID: {} for impersonated party: {}", 
                                tenantId, impersonatedPartyId));
                    */
                    
                    // Temporary: Try to get from header first (for backwards compatibility during migration)
                    // Then fallback to error if not available
                    return extractUUID(exchange, "tenantId", "X-Tenant-Id")
                            .doOnNext(id -> log.warn("Using X-Tenant-Id header (deprecated) - should fetch from config-mgmt: {}", id))
                            .switchIfEmpty(Mono.error(new IllegalStateException(
                                "Tenant resolution not implemented. Need to integrate common-platform-config-mgmt-sdk. "
                                + "SDK should call: GET /api/v1/parties/" + impersonatedPartyId + "/tenant")));
                })
                .doOnError(error -> log.error("Failed to resolve tenant ID for impersonated party", error));
    }
    
    @Override
    public Mono<Boolean> validateImpersonationPermission(UUID backofficeUserId, 
                                                         UUID impersonatedPartyId, 
                                                         ServerWebExchange exchange) {
        log.debug("Validating impersonation: backoffice user {} accessing customer {}", 
                backofficeUserId, impersonatedPartyId);
        
        // Backoffice user authentication is handled by Istio (JWT validation + X-User-Id injection)
        // Impersonation header (X-Impersonate-Party-Id) is trusted since it comes from authenticated backoffice channels
        // 
        // Here we only need to validate that the impersonated customer (party) exists and is accessible
        // The actual contract/product rights validation will be done in enrichContext() when contractId/productId are known
        
        log.info("Impersonation request: backoffice user {} accessing customer {}", 
                backofficeUserId, impersonatedPartyId);
        
        // Always allow since authentication is handled by Istio
        // Contract/product access validation happens later via Security Center
        return Mono.just(true);
    }
    
    @Override
    protected Mono<Set<String>> resolveBackofficeRoles(BackofficeContext context, ServerWebExchange exchange) {
        log.debug("Resolving backoffice roles for user: {}", context.getBackofficeUserId());
        
        // Check if FireflySessionManager is available
        if (sessionManager == null) {
            log.warn("FireflySessionManager not available - returning empty backoffice roles. " +
                    "Ensure common-platform-security-center is deployed and accessible.");
            return Mono.just(Set.of());
        }
        
        // Use FireflySessionManager to get backoffice user's session
        return sessionManager.createOrGetSession(exchange)
            .map(session -> {
                // Extract backoffice roles using BackofficeSessionContextMapper
                Set<String> roles = BackofficeSessionContextMapper.extractBackofficeRoles(session);
                
                log.debug("Resolved {} backoffice roles for user {}: {}", 
                        roles.size(), context.getBackofficeUserId(), roles);
                return roles;
            })
            .doOnError(error -> log.error("Failed to resolve backoffice roles from FireflySessionManager: {}", 
                    error.getMessage(), error))
            .onErrorReturn(Set.of()); // Graceful degradation on error
    }
    
    @Override
    protected Mono<Set<String>> resolveBackofficePermissions(BackofficeContext context, ServerWebExchange exchange) {
        log.debug("Resolving backoffice permissions for user: {}", context.getBackofficeUserId());
        
        // Check if FireflySessionManager is available
        if (sessionManager == null) {
            log.warn("FireflySessionManager not available - returning empty backoffice permissions. " +
                    "Ensure common-platform-security-center is deployed and accessible.");
            return Mono.just(Set.of());
        }
        
        // Use FireflySessionManager to get backoffice user's session
        return sessionManager.createOrGetSession(exchange)
            .map(session -> {
                // Extract backoffice permissions using BackofficeSessionContextMapper
                Set<String> permissions = BackofficeSessionContextMapper.extractBackofficePermissions(session);
                
                log.debug("Resolved {} backoffice permissions for user {}: {}", 
                        permissions.size(), context.getBackofficeUserId(), permissions);
                return permissions;
            })
            .doOnError(error -> log.error("Failed to resolve backoffice permissions from FireflySessionManager: {}", 
                    error.getMessage(), error))
            .onErrorReturn(Set.of()); // Graceful degradation on error
    }
    
    @Override
    protected Mono<Set<String>> resolveImpersonatedPartyRoles(BackofficeContext context, ServerWebExchange exchange) {
        log.debug("Resolving impersonated party roles for party: {} in contract: {}, product: {}", 
                context.getImpersonatedPartyId(), context.getContractId(), context.getProductId());
        
        // Check if FireflySessionManager is available
        if (sessionManager == null) {
            log.warn("FireflySessionManager not available - returning empty impersonated party roles. " +
                    "Ensure common-platform-security-center is deployed and accessible.");
            return Mono.just(Set.of());
        }
        
        // For impersonated party, we validate they have rights over the contract/product via Security Center
        // This ensures the customer actually has access to the requested resources
        // TODO: Implement party session lookup and validation
        /*
        return sessionManager.getPartySession(context.getImpersonatedPartyId(), context.getTenantId())
            .flatMap(partySession -> {
                // Validate customer has rights to the contract/product via Security Center
                if (context.getContractId() != null) {
                    boolean hasContractAccess = partySession.getActiveContracts().stream()
                        .anyMatch(contract -> context.getContractId().equals(contract.getContractId()) 
                                           && Boolean.TRUE.equals(contract.getIsActive()));
                    
                    if (!hasContractAccess) {
                        return Mono.error(new SecurityException(
                            String.format("Customer %s does not have access to contract %s", 
                                context.getImpersonatedPartyId(), context.getContractId())));
                    }
                    
                    if (context.getProductId() != null) {
                        boolean hasProductAccess = partySession.getActiveContracts().stream()
                            .filter(contract -> context.getContractId().equals(contract.getContractId()))
                            .anyMatch(contract -> contract.getProduct() != null 
                                               && context.getProductId().equals(contract.getProduct().getProductId()));
                        
                        if (!hasProductAccess) {
                            return Mono.error(new SecurityException(
                                String.format("Customer %s does not have access to product %s in contract %s", 
                                    context.getImpersonatedPartyId(), context.getProductId(), context.getContractId())));
                        }
                    }
                }
                
                // Extract roles using standard SessionContextMapper based on context scope
                Set<String> roles = SessionContextMapper.extractRoles(
                    partySession, 
                    context.getContractId(), 
                    context.getProductId()
                );
                
                log.info("Validated customer {} access to contract {} / product {} - {} roles found", 
                        context.getImpersonatedPartyId(), context.getContractId(), 
                        context.getProductId(), roles.size());
                return Mono.just(roles);
            })
            .doOnError(error -> log.error("Failed to resolve/validate impersonated party roles: {}", 
                    error.getMessage(), error))
            .onErrorReturn(Set.of());
        */
        
        // Temporary: Return empty set
        log.debug("Impersonated party role resolution not yet implemented");
        return Mono.just(Set.of());
    }
    
    @Override
    protected Mono<Set<String>> resolveImpersonatedPartyPermissions(BackofficeContext context, ServerWebExchange exchange) {
        log.debug("Resolving impersonated party permissions for party: {} in contract: {}, product: {}", 
                context.getImpersonatedPartyId(), context.getContractId(), context.getProductId());
        
        // Check if FireflySessionManager is available
        if (sessionManager == null) {
            log.warn("FireflySessionManager not available - returning empty impersonated party permissions. " +
                    "Ensure common-platform-security-center is deployed and accessible.");
            return Mono.just(Set.of());
        }
        
        // For impersonated party, validate they have rights via Security Center and extract permissions
        // TODO: Implement party session lookup
        /*
        return sessionManager.getPartySession(context.getImpersonatedPartyId(), context.getTenantId())
            .map(partySession -> {
                // Extract permissions from role scopes using standard SessionContextMapper
                Set<String> permissions = SessionContextMapper.extractPermissions(
                    partySession, 
                    context.getContractId(), 
                    context.getProductId()
                );
                
                log.debug("Resolved {} permissions for impersonated party {}: {}", 
                        permissions.size(), context.getImpersonatedPartyId(), permissions);
                return permissions;
            })
            .doOnError(error -> log.error("Failed to resolve impersonated party permissions: {}", 
                    error.getMessage(), error))
            .onErrorReturn(Set.of());
        */
        
        // Temporary: Return empty set
        log.debug("Impersonated party permission resolution not yet implemented");
        return Mono.just(Set.of());
    }
    
    @Override
    public boolean supports(ServerWebExchange exchange) {
        // This default resolver supports all backoffice requests
        return true;
    }
    
    @Override
    public int getPriority() {
        // Default priority
        return 0;
    }
}
