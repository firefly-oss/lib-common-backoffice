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

import com.firefly.common.backoffice.context.BackofficeContext;
import com.firefly.common.backoffice.resolver.BackofficeContextResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * <h1>Abstract Base Controller for Backoffice Customer Resource Endpoints</h1>
 * 
 * <p>This base class is for controllers that operate on <strong>customer resources with impersonation</strong>.
 * It automatically resolves the full backoffice context including backoffice user, impersonated customer,
 * contract, and product. Perfect for backoffice staff accessing customer data.</p>
 * 
 * <h2>When to Use</h2>
 * <p>Extend this class when building REST endpoints for backoffice staff to access customer resources:</p>
 * <ul>
 *   <li><strong>Customer Accounts:</strong> {@code /backoffice/customers/{partyId}/contracts/{contractId}/accounts}</li>
 *   <li><strong>Transactions:</strong> {@code /backoffice/customers/{partyId}/contracts/{contractId}/transactions}</li>
 *   <li><strong>Customer Profile:</strong> {@code /backoffice/customers/{partyId}/profile}</li>
 *   <li><strong>Support Operations:</strong> Managing customer issues, disputes</li>
 * </ul>
 * 
 * <h2>Architecture</h2>
 * <p>This controller automatically resolves:</p>
 * <ul>
 *   <li><strong>Backoffice User ID:</strong> From Istio-injected <code>X-User-Id</code> header</li>
 *   <li><strong>Impersonated Party ID:</strong> From <code>X-Impersonate-Party-Id</code> header</li>
 *   <li><strong>Contract ID:</strong> From {@code @PathVariable UUID contractId} (REQUIRED)</li>
 *   <li><strong>Product ID:</strong> From {@code @PathVariable UUID productId} (REQUIRED)</li>
 *   <li><strong>Backoffice Roles:</strong> Admin, support, analyst roles</li>
 *   <li><strong>Customer Validation:</strong> Ensures customer has access to contract/product</li>
 * </ul>
 * 
 * <h2>Quick Example</h2>
 * <pre>
 * {@code
 * @RestController
 * @RequestMapping("/backoffice/api/v1/customers/{partyId}/contracts/{contractId}")
 * public class BackofficeAccountController extends AbstractBackofficeResourceController {
 *     
 *     @Autowired
 *     private AccountService accountService;
 *     
 *     @GetMapping("/accounts")
 *     @BackofficeSecure(roles = "customer_support", impersonationRequired = true)
 *     public Mono<List<AccountDTO>> getCustomerAccounts(
 *             @PathVariable UUID partyId,
 *             @PathVariable UUID contractId,
 *             ServerWebExchange exchange) {
 *         
 *         // Automatically resolved context with backoffice user + impersonated customer
 *         return resolveBackofficeContext(exchange, partyId, contractId, null)
 *             .flatMap(context -> {
 *                 logImpersonationOperation(context, "getCustomerAccounts");
 *                 return accountService.getAccountsForCustomer(context);
 *             });
 *     }
 * }
 * }
 * </pre>
 * 
 * <h2>What You Get</h2>
 * <ul>
 *   <li><strong>Full Context Resolution:</strong> {@link #resolveBackofficeContext}</li>
 *   <li><strong>Customer Impersonation:</strong> Backoffice user + impersonated customer</li>
 *   <li><strong>Security Validation:</strong> Customer access rights verification</li>
 *   <li><strong>Audit Logging:</strong> {@link #logImpersonationOperation}</li>
 *   <li><strong>Path Variable Validation:</strong> {@link #validatePartyId}</li>
 * </ul>
 * 
 * @author Firefly Development Team
 * @since 1.0.0
 * @see AbstractBackofficeController For administrative endpoints (no customer impersonation)
 */
@Slf4j
public abstract class AbstractBackofficeResourceController {
    
    @Autowired
    private BackofficeContextResolver contextResolver;
    
    /**
     * Resolves the full backoffice context with customer impersonation.
     * 
     * <p>This method:</p>
     * <ol>
     *   <li>Extracts backoffice user ID from <code>X-User-Id</code> header (Istio-injected)</li>
     *   <li>Extracts impersonated party ID from <code>X-Impersonate-Party-Id</code> header</li>
     *   <li>Validates the impersonated party matches the path variable</li>
     *   <li>Uses the provided contractId and productId from {@code @PathVariable}</li>
     *   <li>Validates customer has access to the contract/product via Security Center</li>
     *   <li>Enriches with roles and permissions for both users</li>
     *   <li>Creates audit trail</li>
     * </ol>
     * 
     * @param exchange the server web exchange
     * @param partyId the impersonated party ID from path variable (must match header)
     * @param contractId the contract ID from path variable (nullable)
     * @param productId the product ID from path variable (nullable)
     * @return Mono of BackofficeContext with complete impersonation context
     */
    protected Mono<BackofficeContext> resolveBackofficeContext(
            ServerWebExchange exchange, 
            UUID partyId,
            UUID contractId, 
            UUID productId) {
        
        log.debug("Resolving backoffice context for customer: {}, contract: {}, product: {}", 
                partyId, contractId, productId);
        
        return contextResolver.resolveContext(exchange, contractId, productId)
                .flatMap(context -> validatePartyId(context, partyId))
                .doOnSuccess(context -> log.debug(
                    "Successfully resolved backoffice context: backoffice user={}, impersonated party={}, contract={}, product={}",
                    context.getBackofficeUserId(), context.getImpersonatedPartyId(),
                    context.getContractId(), context.getProductId()))
                .doOnError(error -> log.error("Failed to resolve backoffice context", error));
    }
    
    /**
     * Validates that the impersonated party ID matches the path variable.
     * 
     * <p>This ensures consistency between the impersonation header and the URL path.</p>
     * 
     * @param context the resolved backoffice context
     * @param expectedPartyId the party ID from the path variable
     * @return Mono of validated context
     */
    protected Mono<BackofficeContext> validatePartyId(BackofficeContext context, UUID expectedPartyId) {
        if (!expectedPartyId.equals(context.getImpersonatedPartyId())) {
            log.error("Party ID mismatch: path variable={}, impersonated party={}", 
                    expectedPartyId, context.getImpersonatedPartyId());
            return Mono.error(new IllegalArgumentException(
                    String.format("Party ID in path (%s) does not match impersonated party (%s)",
                            expectedPartyId, context.getImpersonatedPartyId())));
        }
        return Mono.just(context);
    }
    
    /**
     * Logs a customer impersonation operation for audit trail.
     * 
     * <p>This creates a detailed audit log of who accessed whose data and why.</p>
     * 
     * @param context the backoffice context
     * @param operation description of the operation
     */
    protected void logImpersonationOperation(BackofficeContext context, String operation) {
        log.info("[Backoffice Impersonation] Backoffice User: {}, Impersonated Customer: {}, Contract: {}, Product: {}, Operation: {}, Reason: {}",
                context.getBackofficeUserId(),
                context.getImpersonatedPartyId(),
                context.getContractId(),
                context.getProductId(),
                operation,
                context.getImpersonationReason() != null ? context.getImpersonationReason() : "Not specified");
    }
    
    /**
     * Validates that required context components are present.
     * 
     * @param context the backoffice context
     * @param requireContract whether contract ID is required
     * @param requireProduct whether product ID is required
     * @return Mono of validated context
     */
    protected Mono<BackofficeContext> requireContext(BackofficeContext context,
                                                     boolean requireContract,
                                                     boolean requireProduct) {
        if (requireContract && !context.hasContract()) {
            return Mono.error(new IllegalStateException("Contract ID is required but not present"));
        }
        
        if (requireProduct && !context.hasProduct()) {
            return Mono.error(new IllegalStateException("Product ID is required but not present"));
        }
        
        return Mono.just(context);
    }
    
    /**
     * Checks if the backoffice user has the required permission.
     * 
     * @param context the backoffice context
     * @param permission the required permission (e.g., "customers:read")
     * @return Mono that completes if permission is granted, errors otherwise
     */
    protected Mono<Void> requireBackofficePermission(BackofficeContext context, String permission) {
        if (!context.hasBackofficePermission(permission)) {
            return Mono.error(new org.springframework.security.access.AccessDeniedException(
                    "Required backoffice permission not granted: " + permission));
        }
        return Mono.empty();
    }
    
    /**
     * Checks if the backoffice user has the required role.
     * 
     * @param context the backoffice context
     * @param role the required role (e.g., "admin", "customer_support")
     * @return Mono that completes if role is present, errors otherwise
     */
    protected Mono<Void> requireBackofficeRole(BackofficeContext context, String role) {
        if (!context.hasBackofficeRole(role)) {
            return Mono.error(new org.springframework.security.access.AccessDeniedException(
                    "Required backoffice role not present: " + role));
        }
        return Mono.empty();
    }
}
