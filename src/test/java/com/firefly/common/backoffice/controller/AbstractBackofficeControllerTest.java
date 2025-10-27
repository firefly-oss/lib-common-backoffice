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

import com.firefly.common.backoffice.resolver.BackofficeContextResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AbstractBackofficeControllerTest {

    @Mock
    private BackofficeContextResolver contextResolver;

    private TestBackofficeController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new TestBackofficeController();
        controller.setContextResolver(contextResolver);
    }

    @Test
    void shouldExtractBackofficeUserId() {
        // Given
        UUID expectedUserId = UUID.randomUUID();
        ServerWebExchange exchange = createExchange();
        
        when(contextResolver.resolveBackofficeUserId(any(ServerWebExchange.class)))
                .thenReturn(Mono.just(expectedUserId));

        // When
        Mono<UUID> result = controller.extractBackofficeUserId(exchange);

        // Then
        StepVerifier.create(result)
                .expectNext(expectedUserId)
                .verifyComplete();

        verify(contextResolver).resolveBackofficeUserId(exchange);
    }

    @Test
    void shouldHandleErrorWhenExtractingBackofficeUserId() {
        // Given
        ServerWebExchange exchange = createExchange();
        RuntimeException error = new RuntimeException("User ID not found");
        
        when(contextResolver.resolveBackofficeUserId(any(ServerWebExchange.class)))
                .thenReturn(Mono.error(error));

        // When
        Mono<UUID> result = controller.extractBackofficeUserId(exchange);

        // Then
        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldLogAdminOperation() {
        // Given
        UUID backofficeUserId = UUID.randomUUID();
        String operation = "testOperation";

        // When/Then - should not throw
        controller.logAdminOperation(backofficeUserId, operation);
    }

    @Test
    void shouldLogOperationWithExchange() {
        // Given
        UUID expectedUserId = UUID.randomUUID();
        ServerWebExchange exchange = createExchange();
        String operation = "testOperation";
        
        when(contextResolver.resolveBackofficeUserId(any(ServerWebExchange.class)))
                .thenReturn(Mono.just(expectedUserId));

        // When
        controller.logOperation(exchange, operation);

        // Then - verify the resolver was called
        verify(contextResolver, timeout(1000)).resolveBackofficeUserId(exchange);
    }

    private ServerWebExchange createExchange() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .build();
        return MockServerWebExchange.from(request);
    }

    // Test implementation of AbstractBackofficeController
    static class TestBackofficeController extends AbstractBackofficeController {
        
        // Expose protected methods for testing
        @Override
        public Mono<UUID> extractBackofficeUserId(ServerWebExchange exchange) {
            return super.extractBackofficeUserId(exchange);
        }

        @Override
        public void logAdminOperation(UUID backofficeUserId, String operation) {
            super.logAdminOperation(backofficeUserId, operation);
        }

        @Override
        public void logOperation(ServerWebExchange exchange, String operation) {
            super.logOperation(exchange, operation);
        }

        // Setter for injecting mock in tests
        public void setContextResolver(BackofficeContextResolver resolver) {
            // Use reflection to set the private field
            try {
                var field = AbstractBackofficeController.class.getDeclaredField("contextResolver");
                field.setAccessible(true);
                field.set(this, resolver);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
