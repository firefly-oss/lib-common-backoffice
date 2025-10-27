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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AbstractBackofficeResourceControllerTest {

    @Mock
    private BackofficeContextResolver contextResolver;

    private TestBackofficeResourceController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new TestBackofficeResourceController();
        controller.setContextResolver(contextResolver);
    }

    @Test
    void shouldResolveBackofficeContextSuccessfully() {
        // Given
        UUID partyId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        ServerWebExchange exchange = createExchange();

        BackofficeContext context = BackofficeContext.builder()
                .backofficeUserId(UUID.randomUUID())
                .impersonatedPartyId(partyId)
                .contractId(contractId)
                .productId(productId)
                .build();

        when(contextResolver.resolveContext(any(ServerWebExchange.class), eq(contractId), eq(productId)))
                .thenReturn(Mono.just(context));

        // When
        Mono<BackofficeContext> result = controller.resolveBackofficeContext(exchange, partyId, contractId, productId);

        // Then
        StepVerifier.create(result)
                .expectNext(context)
                .verifyComplete();

        verify(contextResolver).resolveContext(exchange, contractId, productId);
    }

    @Test
    void shouldFailWhenPartyIdDoesNotMatch() {
        // Given
        UUID partyIdInPath = UUID.randomUUID();
        UUID differentPartyId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        ServerWebExchange exchange = createExchange();

        BackofficeContext context = BackofficeContext.builder()
                .backofficeUserId(UUID.randomUUID())
                .impersonatedPartyId(differentPartyId)
                .contractId(contractId)
                .build();

        when(contextResolver.resolveContext(any(ServerWebExchange.class), eq(contractId), eq(null)))
                .thenReturn(Mono.just(context));

        // When
        Mono<BackofficeContext> result = controller.resolveBackofficeContext(exchange, partyIdInPath, contractId, null);

        // Then
        StepVerifier.create(result)
                .expectErrorMatches(error ->
                        error instanceof IllegalArgumentException &&
                        error.getMessage().contains("does not match impersonated party"))
                .verify();
    }

    @Test
    void shouldValidatePartyIdSuccessfully() {
        // Given
        UUID partyId = UUID.randomUUID();
        BackofficeContext context = BackofficeContext.builder()
                .backofficeUserId(UUID.randomUUID())
                .impersonatedPartyId(partyId)
                .build();

        // When
        Mono<BackofficeContext> result = controller.validatePartyId(context, partyId);

        // Then
        StepVerifier.create(result)
                .expectNext(context)
                .verifyComplete();
    }

    @Test
    void shouldFailValidationWhenPartyIdMismatch() {
        // Given
        UUID partyId = UUID.randomUUID();
        UUID differentPartyId = UUID.randomUUID();
        BackofficeContext context = BackofficeContext.builder()
                .backofficeUserId(UUID.randomUUID())
                .impersonatedPartyId(partyId)
                .build();

        // When
        Mono<BackofficeContext> result = controller.validatePartyId(context, differentPartyId);

        // Then
        StepVerifier.create(result)
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void shouldLogImpersonationOperation() {
        // Given
        BackofficeContext context = BackofficeContext.builder()
                .backofficeUserId(UUID.randomUUID())
                .impersonatedPartyId(UUID.randomUUID())
                .contractId(UUID.randomUUID())
                .impersonationReason("Support ticket #123")
                .build();

        // When/Then - should not throw
        controller.logImpersonationOperation(context, "testOperation");
    }

    @Test
    void shouldRequireContractWhenPresent() {
        // Given
        BackofficeContext context = BackofficeContext.builder()
                .backofficeUserId(UUID.randomUUID())
                .impersonatedPartyId(UUID.randomUUID())
                .contractId(UUID.randomUUID())
                .build();

        // When
        Mono<BackofficeContext> result = controller.requireContext(context, true, false);

        // Then
        StepVerifier.create(result)
                .expectNext(context)
                .verifyComplete();
    }

    @Test
    void shouldFailWhenContractRequiredButNotPresent() {
        // Given
        BackofficeContext context = BackofficeContext.builder()
                .backofficeUserId(UUID.randomUUID())
                .impersonatedPartyId(UUID.randomUUID())
                .build();

        // When
        Mono<BackofficeContext> result = controller.requireContext(context, true, false);

        // Then
        StepVerifier.create(result)
                .expectErrorMatches(error ->
                        error instanceof IllegalStateException &&
                        error.getMessage().contains("Contract ID is required"))
                .verify();
    }

    @Test
    void shouldRequireProductWhenPresent() {
        // Given
        BackofficeContext context = BackofficeContext.builder()
                .backofficeUserId(UUID.randomUUID())
                .impersonatedPartyId(UUID.randomUUID())
                .productId(UUID.randomUUID())
                .build();

        // When
        Mono<BackofficeContext> result = controller.requireContext(context, false, true);

        // Then
        StepVerifier.create(result)
                .expectNext(context)
                .verifyComplete();
    }

    @Test
    void shouldFailWhenProductRequiredButNotPresent() {
        // Given
        BackofficeContext context = BackofficeContext.builder()
                .backofficeUserId(UUID.randomUUID())
                .impersonatedPartyId(UUID.randomUUID())
                .build();

        // When
        Mono<BackofficeContext> result = controller.requireContext(context, false, true);

        // Then
        StepVerifier.create(result)
                .expectErrorMatches(error ->
                        error instanceof IllegalStateException &&
                        error.getMessage().contains("Product ID is required"))
                .verify();
    }

    @Test
    void shouldPassBackofficePermissionCheck() {
        // Given
        BackofficeContext context = BackofficeContext.builder()
                .backofficeUserId(UUID.randomUUID())
                .impersonatedPartyId(UUID.randomUUID())
                .backofficePermissions(Set.of("customers:read", "customers:write"))
                .build();

        // When
        Mono<Void> result = controller.requireBackofficePermission(context, "customers:read");

        // Then
        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void shouldFailBackofficePermissionCheck() {
        // Given
        BackofficeContext context = BackofficeContext.builder()
                .backofficeUserId(UUID.randomUUID())
                .impersonatedPartyId(UUID.randomUUID())
                .backofficePermissions(Set.of("customers:read"))
                .build();

        // When
        Mono<Void> result = controller.requireBackofficePermission(context, "customers:delete");

        // Then
        StepVerifier.create(result)
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void shouldPassBackofficeRoleCheck() {
        // Given
        BackofficeContext context = BackofficeContext.builder()
                .backofficeUserId(UUID.randomUUID())
                .impersonatedPartyId(UUID.randomUUID())
                .backofficeRoles(Set.of("admin", "support"))
                .build();

        // When
        Mono<Void> result = controller.requireBackofficeRole(context, "admin");

        // Then
        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void shouldFailBackofficeRoleCheck() {
        // Given
        BackofficeContext context = BackofficeContext.builder()
                .backofficeUserId(UUID.randomUUID())
                .impersonatedPartyId(UUID.randomUUID())
                .backofficeRoles(Set.of("support"))
                .build();

        // When
        Mono<Void> result = controller.requireBackofficeRole(context, "admin");

        // Then
        StepVerifier.create(result)
                .expectError(AccessDeniedException.class)
                .verify();
    }

    private ServerWebExchange createExchange() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .build();
        return MockServerWebExchange.from(request);
    }

    // Test implementation of AbstractBackofficeResourceController
    static class TestBackofficeResourceController extends AbstractBackofficeResourceController {

        // Expose protected methods for testing
        @Override
        public Mono<BackofficeContext> resolveBackofficeContext(
                ServerWebExchange exchange, UUID partyId, UUID contractId, UUID productId) {
            return super.resolveBackofficeContext(exchange, partyId, contractId, productId);
        }

        @Override
        public Mono<BackofficeContext> validatePartyId(BackofficeContext context, UUID expectedPartyId) {
            return super.validatePartyId(context, expectedPartyId);
        }

        @Override
        public void logImpersonationOperation(BackofficeContext context, String operation) {
            super.logImpersonationOperation(context, operation);
        }

        @Override
        public Mono<BackofficeContext> requireContext(BackofficeContext context, boolean requireContract, boolean requireProduct) {
            return super.requireContext(context, requireContract, requireProduct);
        }

        @Override
        public Mono<Void> requireBackofficePermission(BackofficeContext context, String permission) {
            return super.requireBackofficePermission(context, permission);
        }

        @Override
        public Mono<Void> requireBackofficeRole(BackofficeContext context, String role) {
            return super.requireBackofficeRole(context, role);
        }

        // Setter for injecting mock in tests
        public void setContextResolver(BackofficeContextResolver resolver) {
            // Use reflection to set the private field
            try {
                var field = AbstractBackofficeResourceController.class.getDeclaredField("contextResolver");
                field.setAccessible(true);
                field.set(this, resolver);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
