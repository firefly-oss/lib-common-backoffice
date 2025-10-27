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

package com.firefly.common.backoffice.context;

import lombok.Builder;
import lombok.Value;
import lombok.With;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable backoffice context container for internal portal/backoffice requests.
 * Contains information about the backoffice user performing the action and the customer being impersonated.
 * 
 * <p>This class extends the standard application context with customer impersonation capabilities:</p>
 * <ul>
 *   <li><strong>backofficeUserId</strong>: The actual backoffice/admin user performing the action</li>
 *   <li><strong>impersonatedPartyId</strong>: The customer (party) being impersonated</li>
 *   <li><strong>contractId</strong>: What contract/agreement is being accessed</li>
 *   <li><strong>productId</strong>: What product is being accessed/modified</li>
 * </ul>
 * </p>
 * 
 * <p>The impersonation is tracked for audit purposes and security validation.</p>
 * 
 * <p><strong>Usage:</strong> Backoffice systems must send the X-Impersonate-Party-Id header to indicate
 * which customer they are accessing. The X-User-Id header identifies the backoffice user.</p>
 * 
 * @author Firefly Development Team
 * @since 1.0.0
 */
@Value
@Builder(toBuilder = true)
@With
public class BackofficeContext {
    
    /**
     * Unique identifier of the backoffice user (admin/support) performing the action.
     * This is the authenticated user in the backoffice system.
     * Comes from X-User-Id header (injected by Istio for backoffice routes).
     */
    @NotNull
    UUID backofficeUserId;
    
    /**
     * Unique identifier of the customer (party) being impersonated.
     * This is the customer whose data is being accessed or modified.
     * Comes from X-Impersonate-Party-Id header (required for all backoffice operations on customer data).
     */
    @NotNull
    UUID impersonatedPartyId;
    
    /**
     * Unique identifier of the contract associated with this request.
     * This comes from common-platform-contract-mgmt.
     * Optional for operations that don't require a contract context.
     */
    UUID contractId;
    
    /**
     * Unique identifier of the product being accessed or modified.
     * This comes from common-platform-product-mgmt.
     * Optional for operations that don't require a product context.
     */
    UUID productId;
    
    /**
     * Roles that the backoffice user has.
     * Used for authorization decisions (e.g., "admin", "support", "analyst").
     */
    Set<String> backofficeRoles;
    
    /**
     * Permissions that the backoffice user has.
     * Derived from roles and used for fine-grained authorization.
     */
    Set<String> backofficePermissions;
    
    /**
     * Roles that the impersonated party has in the context of this contract/product.
     * These are informational - the backoffice user's permissions take precedence.
     */
    Set<String> impersonatedPartyRoles;
    
    /**
     * Permissions that the impersonated party has in this context.
     * These are informational - the backoffice user's permissions take precedence.
     */
    Set<String> impersonatedPartyPermissions;
    
    /**
     * The tenant/organization this context belongs to.
     * Links to the tenant of the impersonated party.
     */
    UUID tenantId;
    
    /**
     * Timestamp when the impersonation started (for audit trail).
     */
    @Builder.Default
    Instant impersonationStartedAt = Instant.now();
    
    /**
     * Reason for impersonation (optional, for audit purposes).
     * Example: "Customer support ticket #12345", "Administrative review"
     */
    String impersonationReason;
    
    /**
     * IP address of the backoffice user (for audit trail).
     */
    String backofficeUserIpAddress;
    
    /**
     * Additional context-specific attributes.
     * Can be used to store domain-specific context information.
     */
    java.util.Map<String, Object> attributes;
    
    /**
     * Checks if the backoffice user has a specific role
     * 
     * @param role the role to check
     * @return true if the role is present
     */
    public boolean hasBackofficeRole(String role) {
        return backofficeRoles != null && backofficeRoles.contains(role);
    }
    
    /**
     * Checks if the backoffice user has any of the specified roles
     * 
     * @param roles the roles to check
     * @return true if any of the roles are present
     */
    public boolean hasAnyBackofficeRole(String... roles) {
        if (this.backofficeRoles == null || roles == null) {
            return false;
        }
        for (String role : roles) {
            if (this.backofficeRoles.contains(role)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Checks if the backoffice user has all of the specified roles
     * 
     * @param roles the roles to check
     * @return true if all roles are present
     */
    public boolean hasAllBackofficeRoles(String... roles) {
        if (this.backofficeRoles == null || roles == null) {
            return false;
        }
        for (String role : roles) {
            if (!this.backofficeRoles.contains(role)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Checks if the backoffice user has a specific permission
     * 
     * @param permission the permission to check
     * @return true if the permission is present
     */
    public boolean hasBackofficePermission(String permission) {
        return backofficePermissions != null && backofficePermissions.contains(permission);
    }
    
    /**
     * Checks if the impersonated party has a specific role (informational)
     * 
     * @param role the role to check
     * @return true if the role is present for the impersonated party
     */
    public boolean impersonatedPartyHasRole(String role) {
        return impersonatedPartyRoles != null && impersonatedPartyRoles.contains(role);
    }
    
    /**
     * Checks if this context has a contract association
     * 
     * @return true if contractId is present
     */
    public boolean hasContract() {
        return contractId != null;
    }
    
    /**
     * Checks if this context has a product association
     * 
     * @return true if productId is present
     */
    public boolean hasProduct() {
        return productId != null;
    }
    
    /**
     * Gets an attribute from the context
     * 
     * @param key the attribute key
     * @param <T> the expected type
     * @return the attribute value or null if not present
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return attributes != null ? (T) attributes.get(key) : null;
    }
    
    /**
     * Checks if this is a valid impersonation context
     * 
     * @return true if both backoffice user and impersonated party are set
     */
    public boolean isValidImpersonation() {
        return backofficeUserId != null && impersonatedPartyId != null;
    }
}
