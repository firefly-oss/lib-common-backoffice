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
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BackofficeSessionContextMapperTest {

    @Test
    void shouldHandleNullSessionContextForRoles() {
        Set<String> roles = BackofficeSessionContextMapper.extractBackofficeRoles(null);
        
        assertNotNull(roles);
        assertTrue(roles.isEmpty());
    }

    @Test
    void shouldHandleNullSessionContextForPermissions() {
        Set<String> permissions = BackofficeSessionContextMapper.extractBackofficePermissions(null);
        
        assertNotNull(permissions);
        assertTrue(permissions.isEmpty());
    }

    @Test
    void shouldReturnFalseForNullSessionInRoleCheck() {
        assertFalse(BackofficeSessionContextMapper.hasBackofficeRole(null, "admin"));
    }

    @Test
    void shouldReturnFalseForNullRoleInRoleCheck() {
        SessionContextDTO session = SessionContextDTO.builder().build();
        assertFalse(BackofficeSessionContextMapper.hasBackofficeRole(session, null));
    }

    @Test
    void shouldReturnFalseForNullSessionInPermissionCheck() {
        assertFalse(BackofficeSessionContextMapper.hasBackofficePermission(null, "customers", "read"));
    }

    @Test
    void shouldReturnFalseForNullResourceInPermissionCheck() {
        SessionContextDTO session = SessionContextDTO.builder().build();
        assertFalse(BackofficeSessionContextMapper.hasBackofficePermission(session, null, "read"));
    }

    @Test
    void shouldReturnFalseForNullActionInPermissionCheck() {
        SessionContextDTO session = SessionContextDTO.builder().build();
        assertFalse(BackofficeSessionContextMapper.hasBackofficePermission(session, "customers", null));
    }

    @Test
    void shouldReturnFalseForNullSessionInAnyRoleCheck() {
        assertFalse(BackofficeSessionContextMapper.hasAnyBackofficeRole(null, "admin", "support"));
    }

    @Test
    void shouldReturnFalseForNullRolesInAnyRoleCheck() {
        SessionContextDTO session = SessionContextDTO.builder().build();
        assertFalse(BackofficeSessionContextMapper.hasAnyBackofficeRole(session, (String[]) null));
    }

    @Test
    void shouldReturnFalseForEmptyRolesInAnyRoleCheck() {
        SessionContextDTO session = SessionContextDTO.builder().build();
        assertFalse(BackofficeSessionContextMapper.hasAnyBackofficeRole(session));
    }

    @Test
    void shouldReturnFalseForNullSessionInAllRolesCheck() {
        assertFalse(BackofficeSessionContextMapper.hasAllBackofficeRoles(null, "admin", "support"));
    }

    @Test
    void shouldReturnFalseForNullRolesInAllRolesCheck() {
        SessionContextDTO session = SessionContextDTO.builder().build();
        assertFalse(BackofficeSessionContextMapper.hasAllBackofficeRoles(session, (String[]) null));
    }

    @Test
    void shouldReturnFalseForEmptyRolesInAllRolesCheck() {
        SessionContextDTO session = SessionContextDTO.builder().build();
        assertFalse(BackofficeSessionContextMapper.hasAllBackofficeRoles(session));
    }

    @Test
    void shouldCheckIsAdminCorrectly() {
        assertFalse(BackofficeSessionContextMapper.isAdmin(null));
        
        SessionContextDTO session = SessionContextDTO.builder().build();
        assertFalse(BackofficeSessionContextMapper.isAdmin(session));
    }

    @Test
    void shouldCheckCanReadCustomersCorrectly() {
        assertFalse(BackofficeSessionContextMapper.canReadCustomers(null));
        
        SessionContextDTO session = SessionContextDTO.builder().build();
        assertFalse(BackofficeSessionContextMapper.canReadCustomers(session));
    }

    @Test
    void shouldCheckCanWriteCustomersCorrectly() {
        assertFalse(BackofficeSessionContextMapper.canWriteCustomers(null));
        
        SessionContextDTO session = SessionContextDTO.builder().build();
        assertFalse(BackofficeSessionContextMapper.canWriteCustomers(session));
    }

    @Test
    void shouldExtractEmptyRolesForEmptySession() {
        SessionContextDTO session = SessionContextDTO.builder().build();
        
        Set<String> roles = BackofficeSessionContextMapper.extractBackofficeRoles(session);
        
        assertNotNull(roles);
        assertTrue(roles.isEmpty());
    }

    @Test
    void shouldExtractEmptyPermissionsForEmptySession() {
        SessionContextDTO session = SessionContextDTO.builder().build();
        
        Set<String> permissions = BackofficeSessionContextMapper.extractBackofficePermissions(session);
        
        assertNotNull(permissions);
        assertTrue(permissions.isEmpty());
    }
}
