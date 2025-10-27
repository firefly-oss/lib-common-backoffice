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

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable security context for backoffice requests with customer impersonation.
 * Contains security-related information including endpoint-role mappings, authorization results,
 * and impersonation audit trail.
 * 
 * <p>This class extends the standard security context with backoffice-specific features:</p>
 * <ul>
 *   <li>Tracks which backoffice user is performing the action</li>
 *   <li>Records which customer (party) is being impersonated</li>
 *   <li>Maintains audit trail for compliance and security</li>
 *   <li>Validates backoffice user has permission to impersonate</li>
 * </ul>
 * 
 * <p>Security context can be configured in two ways:</p>
 * <ul>
 *   <li>Declarative: Using @BackofficeSecure annotation on endpoints/controllers</li>
 *   <li>Programmatic: Explicit endpoint-role mapping registration</li>
 * </ul>
 * 
 * @author Firefly Development Team
 * @since 1.0.0
 */
@Value
@Builder(toBuilder = true)
@With
public class BackofficeSecurityContext {
    
    /**
     * The endpoint being accessed (e.g., "/backoffice/api/v1/customers/{partyId}/accounts")
     */
    String endpoint;
    
    /**
     * The HTTP method being used (GET, POST, PUT, DELETE, etc.)
     */
    String httpMethod;
    
    /**
     * Backoffice roles required to access this endpoint
     * Examples: "admin", "customer_support", "analyst", "auditor"
     */
    Set<String> requiredBackofficeRoles;
    
    /**
     * Backoffice permissions required to access this endpoint
     * Examples: "customers:read", "accounts:write", "transactions:delete"
     */
    Set<String> requiredBackofficePermissions;
    
    /**
     * Whether impersonation is allowed for this endpoint
     */
    @Builder.Default
    boolean impersonationAllowed = true;
    
    /**
     * Whether impersonation is required for this endpoint
     * If true, the X-Impersonate-Party-Id header must be present
     */
    @Builder.Default
    boolean impersonationRequired = true;
    
    /**
     * Whether authorization was successful
     */
    boolean authorized;
    
    /**
     * Reason for authorization failure (if applicable)
     */
    String authorizationFailureReason;
    
    /**
     * The backoffice user ID that was authenticated
     */
    UUID backofficeUserId;
    
    /**
     * The customer (party) being impersonated
     */
    UUID impersonatedPartyId;
    
    /**
     * Whether the backoffice user has permission to impersonate this customer
     */
    boolean impersonationAuthorized;
    
    /**
     * Reason if impersonation was denied
     */
    String impersonationDenialReason;
    
    /**
     * Timestamp when impersonation was authorized
     */
    Instant impersonationAuthorizedAt;
    
    /**
     * Source of the security configuration (ANNOTATION, EXPLICIT_MAP, SECURITY_CENTER)
     */
    SecurityConfigSource configSource;
    
    /**
     * Additional security attributes
     */
    Map<String, Object> securityAttributes;
    
    /**
     * Whether this endpoint requires authentication
     */
    @Builder.Default
    boolean requiresAuthentication = true;
    
    /**
     * Whether this endpoint allows anonymous access (typically false for backoffice)
     */
    @Builder.Default
    boolean allowAnonymous = false;
    
    /**
     * Custom security evaluation result from SecurityCenter
     */
    SecurityEvaluationResult evaluationResult;
    
    /**
     * Audit trail information for impersonation
     */
    ImpersonationAuditTrail auditTrail;
    
    /**
     * Checks if the security context requires any backoffice roles
     * 
     * @return true if roles are required
     */
    public boolean hasRequiredBackofficeRoles() {
        return requiredBackofficeRoles != null && !requiredBackofficeRoles.isEmpty();
    }
    
    /**
     * Checks if the security context requires any backoffice permissions
     * 
     * @return true if permissions are required
     */
    public boolean hasRequiredBackofficePermissions() {
        return requiredBackofficePermissions != null && !requiredBackofficePermissions.isEmpty();
    }
    
    /**
     * Checks if a specific backoffice role is required
     * 
     * @param role the role to check
     * @return true if the role is required
     */
    public boolean requiresBackofficeRole(String role) {
        return requiredBackofficeRoles != null && requiredBackofficeRoles.contains(role);
    }
    
    /**
     * Checks if a specific backoffice permission is required
     * 
     * @param permission the permission to check
     * @return true if the permission is required
     */
    public boolean requiresBackofficePermission(String permission) {
        return requiredBackofficePermissions != null && requiredBackofficePermissions.contains(permission);
    }
    
    /**
     * Gets a security attribute
     * 
     * @param key the attribute key
     * @param <T> the expected type
     * @return the attribute value or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T getSecurityAttribute(String key) {
        return securityAttributes != null ? (T) securityAttributes.get(key) : null;
    }
    
    /**
     * Checks if impersonation is both allowed and successfully authorized
     * 
     * @return true if impersonation is valid
     */
    public boolean isImpersonationValid() {
        return impersonationAllowed && impersonationAuthorized && impersonatedPartyId != null;
    }
    
    /**
     * Source of security configuration
     */
    public enum SecurityConfigSource {
        /**
         * Security configuration from @BackofficeSecure annotation
         */
        ANNOTATION,
        
        /**
         * Security configuration from explicit endpoint-role mapping
         */
        EXPLICIT_MAP,
        
        /**
         * Security configuration from Firefly SecurityCenter
         */
        SECURITY_CENTER,
        
        /**
         * Security configuration from default/fallback rules
         */
        DEFAULT
    }
    
    /**
     * Result of security evaluation from SecurityCenter
     */
    @Value
    @Builder(toBuilder = true)
    @With
    public static class SecurityEvaluationResult {
        
        /**
         * Whether access is granted
         */
        boolean granted;
        
        /**
         * Reason for the decision
         */
        String reason;
        
        /**
         * Rule or policy that was evaluated
         */
        String evaluatedPolicy;
        
        /**
         * Additional evaluation details
         */
        Map<String, Object> evaluationDetails;
        
        /**
         * Timestamp of evaluation
         */
        Instant evaluatedAt;
        
        /**
         * Gets an evaluation detail
         * 
         * @param key the detail key
         * @param <T> the expected type
         * @return the detail value or null if not found
         */
        @SuppressWarnings("unchecked")
        public <T> T getEvaluationDetail(String key) {
            return evaluationDetails != null ? (T) evaluationDetails.get(key) : null;
        }
    }
    
    /**
     * Audit trail for customer impersonation
     */
    @Value
    @Builder(toBuilder = true)
    @With
    public static class ImpersonationAuditTrail {
        
        /**
         * Backoffice user who initiated impersonation
         */
        UUID backofficeUserId;
        
        /**
         * Customer (party) being impersonated
         */
        UUID impersonatedPartyId;
        
        /**
         * Timestamp when impersonation started
         */
        Instant startedAt;
        
        /**
         * IP address of backoffice user
         */
        String ipAddress;
        
        /**
         * User agent of backoffice user
         */
        String userAgent;
        
        /**
         * Reason for impersonation (e.g., support ticket number)
         */
        String reason;
        
        /**
         * Endpoint being accessed
         */
        String endpoint;
        
        /**
         * HTTP method
         */
        String httpMethod;
        
        /**
         * Session ID for correlation
         */
        String sessionId;
        
        /**
         * Request ID for tracing
         */
        String requestId;
        
        /**
         * Additional audit metadata
         */
        Map<String, Object> metadata;
        
        /**
         * Gets audit metadata
         * 
         * @param key the metadata key
         * @param <T> the expected type
         * @return the metadata value or null if not found
         */
        @SuppressWarnings("unchecked")
        public <T> T getMetadata(String key) {
            return metadata != null ? (T) metadata.get(key) : null;
        }
    }
}
