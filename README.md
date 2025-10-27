# Firefly Common Backoffice Library

[![Maven Central](https://img.shields.io/maven-central/v/com.firefly/lib-common-backoffice.svg)](https://search.maven.org/artifact/com.firefly/lib-common-backoffice)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A Spring Boot library that extends the Firefly application layer architecture for internal backoffice and portal systems with **customer impersonation**, **audit trail**, and **enhanced security context management**.

## Overview

The `lib-common-backoffice` library provides a secure and auditable way for backoffice users (admins, support staff, analysts) to access customer data with proper impersonation tracking. Unlike the standard `lib-common-application` which serves public-facing APIs, this library is designed specifically for internal systems where staff need to view and manage customer accounts.

### Key Features

- **Customer Impersonation**: Backoffice users can securely access customer data while maintaining clear audit trails
- **Dual Context Management**: Tracks both the actual backoffice user and the impersonated customer
- **Security Validation**: Validates customer has rights to the requested contract and product via Security Center
- **Audit Trail**: Comprehensive logging of all impersonation requests (who, when, why, from where)
- **Istio Integration**: Seamless authentication through Istio service mesh (JWT validation + header injection)
- **Role-Based Access**: Supports backoffice-specific roles (admin, support, analyst, auditor)

## Architecture

### Request Flow

```
┌─────────────────┐       ┌──────────────┐       ┌────────────────────┐
│ Backoffice UI   │──────▶│ Istio Gateway│──────▶│ Backoffice Service │
│                 │       │              │       │                    │
│ Sends:          │       │ Validates:   │       │ Uses:              │
│ - JWT Token     │       │ - JWT        │       │ - X-User-Id        │
│ - X-Impersonate │       │              │       │ - X-Impersonate    │
│   -Party-Id     │       │ Injects:     │       │   -Party-Id        │
└─────────────────┘       │ - X-User-Id  │       └────────────────────┘
                          └──────────────┘                │
                                                          ▼
                          ┌──────────────────────────────────────────┐
                          │ BackofficeContextResolver                │
                          │                                          │
                          │ 1. Extract backoffice user from headers  │
                          │ 2. Extract impersonated party            │
                          │ 3. Validate customer access via          │
                          │    Security Center (contract/product)    │
                          │ 4. Enrich with roles & permissions       │
                          │ 5. Create audit trail                    │
                          └──────────────────────────────────────────┘
```

### Security Model

1. **Authentication**: Handled by Istio (validates backoffice JWT, injects `X-User-Id`)
2. **Impersonation Headers**: Trusted from authenticated backoffice channels (`X-Impersonate-Party-Id`)
3. **Authorization**: Security Center validates customer has rights to contract/product
4. **Audit**: All impersonation requests logged with full context

## Installation

### Maven

```xml
<dependency>
    <groupId>com.firefly</groupId>
    <artifactId>lib-common-backoffice</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Gradle

```gradle
implementation 'com.firefly:lib-common-backoffice:1.0.0-SNAPSHOT'
```

## Usage

### Basic Setup

The library auto-configures through Spring Boot. Simply add the dependency and it will automatically register the `DefaultBackofficeContextResolver` component.

### Controller Example

```java
@RestController
@RequestMapping("/backoffice/api/v1/customers")
@RequiredArgsConstructor
public class BackofficeCustomerController {

    private final BackofficeContextResolver contextResolver;
    private final AccountService accountService;

    /**
     * Get customer accounts (with impersonation)
     * 
     * Expected headers:
     * - X-User-Id: <backoffice-user-uuid> (injected by Istio)
     * - X-Impersonate-Party-Id: <customer-uuid>
     * - X-Impersonation-Reason: "Support ticket #12345" (optional)
     */
    @GetMapping("/{partyId}/contracts/{contractId}/accounts")
    public Mono<List<AccountDTO>> getCustomerAccounts(
            @PathVariable UUID partyId,
            @PathVariable UUID contractId,
            ServerWebExchange exchange) {
        
        // Resolve backoffice context with impersonation
        return contextResolver.resolveContext(exchange, contractId, null)
            .flatMap(backofficeContext -> {
                // Validate impersonated party matches path variable
                if (!partyId.equals(backofficeContext.getImpersonatedPartyId())) {
                    return Mono.error(new IllegalArgumentException(
                        "Party ID in path does not match impersonated party"));
                }
                
                // Call service with context
                return accountService.getAccountsForCustomer(backofficeContext);
            });
    }
}
```

### Service Example

```java
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    public Mono<List<AccountDTO>> getAccountsForCustomer(BackofficeContext context) {
        // Log for audit trail
        log.info("Backoffice user {} accessing accounts for customer {} in contract {}",
                context.getBackofficeUserId(),
                context.getImpersonatedPartyId(),
                context.getContractId());
        
        // Validate backoffice user has required permissions
        if (!context.hasBackofficePermission("accounts:read")) {
            return Mono.error(new AccessDeniedException("Insufficient permissions"));
        }
        
        // Fetch accounts for the impersonated customer
        return accountRepository.findByPartyIdAndContractId(
                context.getImpersonatedPartyId(),
                context.getContractId())
            .map(this::toDTO)
            .collectList();
    }
}
```

## Core Components

### BackofficeContext

Immutable context containing:

- `backofficeUserId`: The actual admin/support user performing the action
- `impersonatedPartyId`: The customer being accessed
- `contractId`, `productId`: Business context identifiers
- `backofficeRoles`: Roles of the backoffice user (admin, support, etc.)
- `backofficePermissions`: Permissions derived from roles
- `impersonatedPartyRoles`: Customer's roles (informational)
- `impersonatedPartyPermissions`: Customer's permissions (informational)
- `impersonationStartedAt`: Timestamp for audit
- `impersonationReason`: Optional reason for accessing customer data
- `backofficeUserIpAddress`: IP address for audit trail

**Methods:**

```java
// Check backoffice user roles
boolean hasBackofficeRole(String role);
boolean hasAnyBackofficeRole(String... roles);
boolean hasAllBackofficeRoles(String... roles);

// Check backoffice user permissions
boolean hasBackofficePermission(String permission);

// Check impersonated customer roles (informational)
boolean impersonatedPartyHasRole(String role);

// Validate context
boolean isValidImpersonation();
```

### BackofficeContextResolver

Interface for resolving backoffice context from requests.

**Key Methods:**

```java
// Resolve full context
Mono<BackofficeContext> resolveContext(ServerWebExchange exchange);

// Resolve with explicit contract/product IDs (recommended)
Mono<BackofficeContext> resolveContext(
    ServerWebExchange exchange, 
    UUID contractId, 
    UUID productId);

// Resolve individual IDs
Mono<UUID> resolveBackofficeUserId(ServerWebExchange exchange);
Mono<UUID> resolveImpersonatedPartyId(ServerWebExchange exchange);
Mono<String> resolveImpersonationReason(ServerWebExchange exchange);

// Validate impersonation (calls Security Center)
Mono<Boolean> validateImpersonationPermission(
    UUID backofficeUserId, 
    UUID impersonatedPartyId, 
    ServerWebExchange exchange);
```

### BackofficeSessionContextMapper

Utility for extracting backoffice roles and permissions from Security Center sessions.

**Methods:**

```java
// Extract roles and permissions
Set<String> extractBackofficeRoles(SessionContextDTO session);
Set<String> extractBackofficePermissions(SessionContextDTO session);

// Check roles and permissions
boolean hasBackofficeRole(SessionContextDTO session, String role);
boolean hasBackofficePermission(SessionContextDTO session, String resource, String action);

// Convenience methods
boolean isAdmin(SessionContextDTO session);
boolean canReadCustomers(SessionContextDTO session);
boolean canWriteCustomers(SessionContextDTO session);
```

## HTTP Headers

### Required Headers

| Header | Source | Description | Example |
|--------|--------|-------------|---------|
| `X-User-Id` | Istio (auto-injected) | Backoffice user UUID from JWT | `550e8400-e29b-41d4-a716-446655440000` |
| `X-Impersonate-Party-Id` | Backoffice Frontend | Customer being accessed | `650e8400-e29b-41d4-a716-446655440000` |

### Optional Headers

| Header | Source | Description | Example |
|--------|--------|-------------|---------|
| `X-Impersonation-Reason` | Backoffice Frontend | Reason for accessing customer | `Support ticket #12345` |
| `X-Tenant-Id` | Istio (optional) | Tenant ID (can be resolved) | `750e8400-e29b-41d4-a716-446655440000` |

## Security Center Integration

The library integrates with Firefly Security Center to:

1. **Validate Customer Access**: Ensures the impersonated customer has active contracts/products
2. **Fetch Roles & Permissions**: Retrieves both backoffice and customer roles/permissions
3. **Audit Impersonation**: Logs all access attempts for compliance

### Validation Logic

```java
// When resolving impersonated party roles, the library:
1. Fetches customer's session from Security Center
2. Validates customer has access to the requested contract
3. Validates customer has access to the requested product (if specified)
4. Extracts customer's roles and permissions
5. Returns error if validation fails
```

## Backoffice Roles

Common backoffice roles:

- `admin`: Full system administrator
- `customer_support`: Can view and assist customers
- `financial_analyst`: Can view financial data
- `auditor`: Read-only access for compliance
- `operations`: Can manage operational tasks

## Backoffice Permissions

Permission format: `resource:action`

Examples:

- `customers:read`: View customer information
- `customers:write`: Modify customer information
- `accounts:read`: View customer accounts
- `accounts:write`: Modify customer accounts
- `transactions:read`: View transaction history
- `transactions:write`: Create/modify transactions
- `system:admin`: Administrative operations

## Testing

Run the test suite:

```bash
mvn test
```

Test Results:
- **32 tests** passing
- Full coverage of context management, role checking, and permission validation

Example test:

```java
@Test
void shouldResolveBackofficeContext() {
    UUID backofficeUserId = UUID.randomUUID();
    UUID impersonatedPartyId = UUID.randomUUID();
    
    BackofficeContext context = BackofficeContext.builder()
            .backofficeUserId(backofficeUserId)
            .impersonatedPartyId(impersonatedPartyId)
            .backofficeRoles(Set.of("admin", "support"))
            .build();
    
    assertTrue(context.isValidImpersonation());
    assertTrue(context.hasBackofficeRole("admin"));
}
```

## Configuration

The library auto-configures with Spring Boot. No additional configuration required.

### Optional Configuration

To customize behavior, implement your own `BackofficeContextResolver`:

```java
@Component
@Primary
public class CustomBackofficeContextResolver extends AbstractBackofficeContextResolver {
    
    @Override
    public Mono<UUID> resolveBackofficeUserId(ServerWebExchange exchange) {
        // Custom logic
        return extractUUID(exchange, "backofficeUserId", "X-User-Id");
    }
    
    // Override other methods as needed
}
```

## Comparison with lib-common-application

| Feature | lib-common-application | lib-common-backoffice |
|---------|------------------------|------------------------|
| **Target Audience** | Public customers | Backoffice staff |
| **Authentication** | Customer JWT | Backoffice JWT + Istio |
| **Context** | Single party | Dual (backoffice user + customer) |
| **Impersonation** | ❌ No | ✅ Yes |
| **Audit Trail** | Basic | Comprehensive |
| **Roles** | Customer roles | Backoffice + customer roles |
| **Use Case** | Customer-facing APIs | Internal admin tools |

## Best Practices

1. **Always log impersonation**: Include backoffice user, customer, and reason in logs
2. **Validate party ID**: Check that path variable matches impersonated party
3. **Use explicit IDs**: Pass `contractId` and `productId` explicitly to `resolveContext()`
4. **Check permissions**: Always validate backoffice user has required permissions
5. **Provide reasons**: Encourage backoffice users to provide impersonation reasons
6. **Monitor access**: Set up alerts for unusual impersonation patterns

## Troubleshooting

### X-User-Id header not found

**Cause**: Request not passing through Istio gateway

**Solution**: Ensure backoffice traffic routes through Istio with proper authentication

### X-Impersonate-Party-Id header not found

**Cause**: Backoffice frontend not sending impersonation header

**Solution**: Update frontend to include customer ID in impersonation header

### Customer does not have access to contract/product

**Cause**: Customer's Security Center session doesn't include the requested contract

**Solution**: Verify customer has active contract and product associations

## Contributing

Contributions are welcome! Please follow the Firefly contribution guidelines.

## License

This library is licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.

## Support

For questions or issues, contact the Firefly Development Team.

---

Built with ❤️ by the Firefly Development Team
