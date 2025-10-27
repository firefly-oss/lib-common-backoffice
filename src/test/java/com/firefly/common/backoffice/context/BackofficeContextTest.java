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

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BackofficeContextTest {

    @Test
    void shouldCreateBackofficeContextWithBuilder() {
        UUID backofficeUserId = UUID.randomUUID();
        UUID impersonatedPartyId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now();

        BackofficeContext context = BackofficeContext.builder()
                .backofficeUserId(backofficeUserId)
                .impersonatedPartyId(impersonatedPartyId)
                .contractId(contractId)
                .productId(productId)
                .tenantId(tenantId)
                .backofficeRoles(Set.of("admin", "support"))
                .backofficePermissions(Set.of("customers:read", "customers:write"))
                .impersonatedPartyRoles(Set.of("owner"))
                .impersonatedPartyPermissions(Set.of("account:read"))
                .impersonationStartedAt(now)
                .impersonationReason("Support ticket #12345")
                .backofficeUserIpAddress("192.168.1.1")
                .build();

        assertNotNull(context);
        assertEquals(backofficeUserId, context.getBackofficeUserId());
        assertEquals(impersonatedPartyId, context.getImpersonatedPartyId());
        assertEquals(contractId, context.getContractId());
        assertEquals(productId, context.getProductId());
        assertEquals(tenantId, context.getTenantId());
        assertEquals(2, context.getBackofficeRoles().size());
        assertEquals(2, context.getBackofficePermissions().size());
        assertEquals(now, context.getImpersonationStartedAt());
        assertEquals("Support ticket #12345", context.getImpersonationReason());
        assertEquals("192.168.1.1", context.getBackofficeUserIpAddress());
    }

    @Test
    void shouldCheckBackofficeRoleCorrectly() {
        BackofficeContext context = BackofficeContext.builder()
                .backofficeUserId(UUID.randomUUID())
                .impersonatedPartyId(UUID.randomUUID())
                .backofficeRoles(Set.of("admin", "support"))
                .build();

        assertTrue(context.hasBackofficeRole("admin"));
        assertTrue(context.hasBackofficeRole("support"));
        assertFalse(context.hasBackofficeRole("analyst"));
    }

    @Test
    void shouldCheckAnyBackofficeRoleCorrectly() {
        BackofficeContext context = BackofficeContext.builder()
                .backofficeUserId(UUID.randomUUID())
                .impersonatedPartyId(UUID.randomUUID())
                .backofficeRoles(Set.of("admin", "support"))
                .build();

        assertTrue(context.hasAnyBackofficeRole("admin", "analyst"));
        assertTrue(context.hasAnyBackofficeRole("support"));
        assertFalse(context.hasAnyBackofficeRole("analyst", "auditor"));
    }

    @Test
    void shouldCheckAllBackofficeRolesCorrectly() {
        BackofficeContext context = BackofficeContext.builder()
                .backofficeUserId(UUID.randomUUID())
                .impersonatedPartyId(UUID.randomUUID())
                .backofficeRoles(Set.of("admin", "support"))
                .build();

        assertTrue(context.hasAllBackofficeRoles("admin", "support"));
        assertFalse(context.hasAllBackofficeRoles("admin", "analyst"));
    }

    @Test
    void shouldCheckBackofficePermissionCorrectly() {
        BackofficeContext context = BackofficeContext.builder()
                .backofficeUserId(UUID.randomUUID())
                .impersonatedPartyId(UUID.randomUUID())
                .backofficePermissions(Set.of("customers:read", "customers:write"))
                .build();

        assertTrue(context.hasBackofficePermission("customers:read"));
        assertTrue(context.hasBackofficePermission("customers:write"));
        assertFalse(context.hasBackofficePermission("customers:delete"));
    }

    @Test
    void shouldCheckImpersonatedPartyRoleCorrectly() {
        BackofficeContext context = BackofficeContext.builder()
                .backofficeUserId(UUID.randomUUID())
                .impersonatedPartyId(UUID.randomUUID())
                .impersonatedPartyRoles(Set.of("owner", "viewer"))
                .build();

        assertTrue(context.impersonatedPartyHasRole("owner"));
        assertTrue(context.impersonatedPartyHasRole("viewer"));
        assertFalse(context.impersonatedPartyHasRole("admin"));
    }

    @Test
    void shouldCheckHasContractCorrectly() {
        BackofficeContext withContract = BackofficeContext.builder()
                .backofficeUserId(UUID.randomUUID())
                .impersonatedPartyId(UUID.randomUUID())
                .contractId(UUID.randomUUID())
                .build();

        BackofficeContext withoutContract = BackofficeContext.builder()
                .backofficeUserId(UUID.randomUUID())
                .impersonatedPartyId(UUID.randomUUID())
                .build();

        assertTrue(withContract.hasContract());
        assertFalse(withoutContract.hasContract());
    }

    @Test
    void shouldCheckHasProductCorrectly() {
        BackofficeContext withProduct = BackofficeContext.builder()
                .backofficeUserId(UUID.randomUUID())
                .impersonatedPartyId(UUID.randomUUID())
                .productId(UUID.randomUUID())
                .build();

        BackofficeContext withoutProduct = BackofficeContext.builder()
                .backofficeUserId(UUID.randomUUID())
                .impersonatedPartyId(UUID.randomUUID())
                .build();

        assertTrue(withProduct.hasProduct());
        assertFalse(withoutProduct.hasProduct());
    }

    @Test
    void shouldGetAttributeCorrectly() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("customKey", "customValue");
        attributes.put("numericKey", 42);

        BackofficeContext context = BackofficeContext.builder()
                .backofficeUserId(UUID.randomUUID())
                .impersonatedPartyId(UUID.randomUUID())
                .attributes(attributes)
                .build();

        assertEquals("customValue", context.getAttribute("customKey"));
        assertEquals(Integer.valueOf(42), context.getAttribute("numericKey"));
        assertNull(context.getAttribute("nonExistent"));
    }

    @Test
    void shouldValidateImpersonationCorrectly() {
        BackofficeContext validContext = BackofficeContext.builder()
                .backofficeUserId(UUID.randomUUID())
                .impersonatedPartyId(UUID.randomUUID())
                .build();

        assertTrue(validContext.isValidImpersonation());
    }

    @Test
    void shouldHandleNullRolesGracefully() {
        BackofficeContext context = BackofficeContext.builder()
                .backofficeUserId(UUID.randomUUID())
                .impersonatedPartyId(UUID.randomUUID())
                .build();

        assertFalse(context.hasBackofficeRole("admin"));
        assertFalse(context.hasAnyBackofficeRole("admin", "support"));
        assertFalse(context.hasAllBackofficeRoles("admin"));
    }

    @Test
    void shouldHandleNullPermissionsGracefully() {
        BackofficeContext context = BackofficeContext.builder()
                .backofficeUserId(UUID.randomUUID())
                .impersonatedPartyId(UUID.randomUUID())
                .build();

        assertFalse(context.hasBackofficePermission("customers:read"));
    }

    @Test
    void shouldHandleNullAttributesGracefully() {
        BackofficeContext context = BackofficeContext.builder()
                .backofficeUserId(UUID.randomUUID())
                .impersonatedPartyId(UUID.randomUUID())
                .build();

        assertNull(context.getAttribute("anyKey"));
    }

    @Test
    void shouldUseToBuilderCorrectly() {
        UUID originalUserId = UUID.randomUUID();
        UUID newUserId = UUID.randomUUID();

        BackofficeContext original = BackofficeContext.builder()
                .backofficeUserId(originalUserId)
                .impersonatedPartyId(UUID.randomUUID())
                .backofficeRoles(Set.of("admin"))
                .build();

        BackofficeContext modified = original.toBuilder()
                .backofficeUserId(newUserId)
                .build();

        assertEquals(originalUserId, original.getBackofficeUserId());
        assertEquals(newUserId, modified.getBackofficeUserId());
        assertEquals(original.getImpersonatedPartyId(), modified.getImpersonatedPartyId());
    }
}
