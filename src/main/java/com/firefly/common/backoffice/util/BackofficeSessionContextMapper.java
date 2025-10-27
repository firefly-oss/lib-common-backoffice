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

package com.firefly.common.backoffice.util;

import com.firefly.security.center.interfaces.dtos.SessionContextDTO;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility class for mapping SessionContextDTO to backoffice roles and permissions.
 * 
 * <p>This mapper extracts backoffice-specific roles and permissions from the session.
 * Unlike the standard SessionContextMapper which focuses on contract/product roles,
 * this mapper focuses on backoffice/administrative roles.</p>
 * 
 * <p><strong>Backoffice Role Examples:</strong></p>
 * <ul>
 *   <li>admin - Full system administrator</li>
 *   <li>customer_support - Can view and assist customers</li>
 *   <li>financial_analyst - Can view financial data</li>
 *   <li>auditor - Read-only access for compliance</li>
 *   <li>operations - Can manage operational tasks</li>
 * </ul>
 * 
 * <p><strong>Backoffice Permission Format:</strong> {resource}:{action}</p>
 * <p>Examples:</p>
 * <ul>
 *   <li>customers:read</li>
 *   <li>customers:write</li>
 *   <li>accounts:read</li>
 *   <li>transactions:read</li>
 *   <li>transactions:write</li>
 *   <li>system:admin</li>
 * </ul>
 * 
 * @author Firefly Development Team
 * @since 1.0.0
 */
@Slf4j
public final class BackofficeSessionContextMapper {
    
    private BackofficeSessionContextMapper() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Extracts backoffice roles from the session context.
     * 
     * <p>Backoffice roles are typically stored at the party level (not contract-specific)
     * and represent the user's administrative privileges.</p>
     * 
     * @param sessionContext The session context from FireflySessionManager
     * @return Set of backoffice role codes (e.g., "admin", "customer_support", "analyst")
     */
    public static Set<String> extractBackofficeRoles(SessionContextDTO sessionContext) {
        if (sessionContext == null) {
            log.debug("Session context is null, returning empty backoffice roles");
            return Collections.emptySet();
        }
        
        Set<String> roles = new HashSet<>();
        
        // TODO: Implement extraction of backoffice roles from session
        // Backoffice roles should be stored differently from contract roles
        // They might be in a separate field like sessionContext.getBackofficeRoles()
        // or sessionContext.getAdministrativeRoles()
        
        /*
        // Example implementation when SDK supports backoffice roles:
        if (sessionContext.getBackofficeRoles() != null) {
            for (BackofficeRoleDTO role : sessionContext.getBackofficeRoles()) {
                if (Boolean.TRUE.equals(role.getIsActive())) {
                    String roleCode = role.getRoleCode();
                    if (roleCode != null && !roleCode.isBlank()) {
                        roles.add(roleCode);
                        log.debug("Extracted backoffice role: {}", roleCode);
                    }
                }
            }
        }
        */
        
        log.debug("Extracted {} backoffice roles: {}", roles.size(), roles);
        return roles;
    }
    
    /**
     * Extracts backoffice permissions from the session context.
     * 
     * <p>Permissions are derived from backoffice roles and represent specific
     * actions the user can perform in the backoffice system.</p>
     * 
     * @param sessionContext The session context from FireflySessionManager
     * @return Set of permission strings (e.g., "customers:read", "accounts:write")
     */
    public static Set<String> extractBackofficePermissions(SessionContextDTO sessionContext) {
        if (sessionContext == null) {
            log.debug("Session context is null, returning empty backoffice permissions");
            return Collections.emptySet();
        }
        
        Set<String> permissions = new HashSet<>();
        
        // TODO: Implement extraction of backoffice permissions from session
        // Permissions should be derived from backoffice roles
        
        /*
        // Example implementation when SDK supports backoffice permissions:
        if (sessionContext.getBackofficeRoles() != null) {
            for (BackofficeRoleDTO role : sessionContext.getBackofficeRoles()) {
                if (Boolean.TRUE.equals(role.getIsActive()) && role.getPermissions() != null) {
                    for (PermissionDTO permission : role.getPermissions()) {
                        if (Boolean.TRUE.equals(permission.getIsActive())) {
                            // Format: {resource}:{action}
                            String permissionStr = String.format("%s:%s", 
                                    permission.getResourceType(), 
                                    permission.getActionType());
                            
                            permissions.add(permissionStr);
                            log.debug("Extracted backoffice permission: {}", permissionStr);
                        }
                    }
                }
            }
        }
        */
        
        log.debug("Extracted {} backoffice permissions: {}", permissions.size(), permissions);
        return permissions;
    }
    
    /**
     * Checks if the backoffice user has a specific role.
     * 
     * @param sessionContext The session context from FireflySessionManager
     * @param role The role to check (e.g., "admin", "customer_support")
     * @return true if the user has the role, false otherwise
     */
    public static boolean hasBackofficeRole(SessionContextDTO sessionContext, String role) {
        if (sessionContext == null || role == null) {
            return false;
        }
        
        Set<String> roles = extractBackofficeRoles(sessionContext);
        boolean hasRole = roles.contains(role);
        
        log.debug("Backoffice role check for '{}': {}", role, hasRole);
        return hasRole;
    }
    
    /**
     * Checks if the backoffice user has a specific permission.
     * 
     * @param sessionContext The session context from FireflySessionManager
     * @param resource The resource type (e.g., "customers", "accounts")
     * @param action The action type (e.g., "read", "write", "delete")
     * @return true if the user has the permission, false otherwise
     */
    public static boolean hasBackofficePermission(SessionContextDTO sessionContext, 
                                                  String resource, 
                                                  String action) {
        if (sessionContext == null || resource == null || action == null) {
            return false;
        }
        
        Set<String> permissions = extractBackofficePermissions(sessionContext);
        String permissionStr = String.format("%s:%s", resource, action);
        boolean hasPermission = permissions.contains(permissionStr);
        
        log.debug("Backoffice permission check for '{}': {}", permissionStr, hasPermission);
        return hasPermission;
    }
    
    /**
     * Checks if the backoffice user has any of the specified roles.
     * 
     * @param sessionContext The session context from FireflySessionManager
     * @param roles The roles to check
     * @return true if the user has any of the roles, false otherwise
     */
    public static boolean hasAnyBackofficeRole(SessionContextDTO sessionContext, String... roles) {
        if (sessionContext == null || roles == null || roles.length == 0) {
            return false;
        }
        
        Set<String> userRoles = extractBackofficeRoles(sessionContext);
        for (String role : roles) {
            if (userRoles.contains(role)) {
                log.debug("Backoffice user has role: {}", role);
                return true;
            }
        }
        
        log.debug("Backoffice user does not have any of the required roles: {}", (Object[]) roles);
        return false;
    }
    
    /**
     * Checks if the backoffice user has all of the specified roles.
     * 
     * @param sessionContext The session context from FireflySessionManager
     * @param roles The roles to check
     * @return true if the user has all roles, false otherwise
     */
    public static boolean hasAllBackofficeRoles(SessionContextDTO sessionContext, String... roles) {
        if (sessionContext == null || roles == null || roles.length == 0) {
            return false;
        }
        
        Set<String> userRoles = extractBackofficeRoles(sessionContext);
        for (String role : roles) {
            if (!userRoles.contains(role)) {
                log.debug("Backoffice user missing required role: {}", role);
                return false;
            }
        }
        
        log.debug("Backoffice user has all required roles: {}", (Object[]) roles);
        return true;
    }
    
    /**
     * Checks if the backoffice user is an administrator.
     * This is a convenience method that checks for the "admin" role.
     * 
     * @param sessionContext The session context from FireflySessionManager
     * @return true if the user is an admin, false otherwise
     */
    public static boolean isAdmin(SessionContextDTO sessionContext) {
        return hasBackofficeRole(sessionContext, "admin");
    }
    
    /**
     * Checks if the backoffice user has read access to customer data.
     * 
     * @param sessionContext The session context from FireflySessionManager
     * @return true if the user can read customer data, false otherwise
     */
    public static boolean canReadCustomers(SessionContextDTO sessionContext) {
        return hasBackofficePermission(sessionContext, "customers", "read");
    }
    
    /**
     * Checks if the backoffice user has write access to customer data.
     * 
     * @param sessionContext The session context from FireflySessionManager
     * @return true if the user can write customer data, false otherwise
     */
    public static boolean canWriteCustomers(SessionContextDTO sessionContext) {
        return hasBackofficePermission(sessionContext, "customers", "write");
    }
}
