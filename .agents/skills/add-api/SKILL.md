---
name: add-api
description: Add or change a CRM HTTP endpoint with verified subject mapping, current authorization and field policy, OpenAPI, and API regression coverage.
---

# Add a CRM HTTP API

Read the relevant [application rules](../../../AGENTS.md), [HTTP workflow](../../../docs/usage-guide.md#add-an-http-endpoint), [API contract](../../../docs/api-reference.md), and [configuration](../../../docs/configuration.md). Load only sections affected by the endpoint.

1. Define the method, route, bounded input, validation, response shape, and stable error/status outcomes. Keep ordinary Request/Response contracts in `api`; preserve established streaming/export use cases rather than exposing repositories or application VOs from controllers.
2. Follow the existing identity boundary. Human controllers map verified Spring Security `Authentication` with `AuthenticatedCallerMapper` and pass `api.context.AuthenticatedCaller` through the facade to the use case. Connector endpoints use `AuthenticatedConnectorMapper` and `ConnectorGuard`. Public installation, authentication, forms, and other exceptions need their existing dedicated contracts; an ordinary body/header never establishes trusted identity.
3. Declare the HTTP route rule in `infra/rest/config/SecurityConfig` and enforce the capability again through the application's current guard/access resolver. Register a new capability in domain `AuthorityCatalog` when needed. Recheck membership, record visibility, relationships, and field policy; a menu entry or trusted-header setting does not grant permission. Preserve cookie-session CSRF and same-origin rules.
4. Keep filtering, counts, pagination, and exported fields within the caller's resolved scope. Apply hidden/masked field policy to all response paths, including mutation and batch results. Update boundary assemblers and OpenAPI metadata; retain safe public errors and private-data handling.
5. Cover valid/invalid input, missing identity, forbidden capability, revoked membership, cross-tenant access, record scope and hidden/masked fields as applicable. Test the use-case authorization directly as well as HTTP mapping. Coordinate the frontend OpenAPI snapshot, generated types, and caller tests when the public contract changes.
6. Run focused tests, then the required scopes from [verify-change](../verify-change/SKILL.md). Identity, authorization, tenant, persistence, and transaction behavior require full Docker/database evidence.

Acceptance: route and use-case authorization agree, public input cannot forge a subject, the documented response respects current policy, and regression tests cover the actual boundary and rejected cases.
