package io.github.archetom.crm.infra.rest.config;

import io.github.archetom.crm.application.security.TenantAuthorityResolver;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.valueobject.AuthorityCode;
import io.github.archetom.crm.infra.rest.security.TrustedHeaderAuthenticationFilter;
import io.github.archetom.crm.infra.rest.security.BrowserSessionAuthenticationFilter;
import io.github.archetom.crm.infra.rest.security.BrowserSessionCsrfFilter;
import io.github.archetom.crm.infra.rest.security.LeadIntakeAuthenticationFilter;
import io.github.archetom.crm.infra.rest.security.MarketingFormSubmissionRateLimitFilter;
import io.github.archetom.crm.infra.rest.security.ConnectorAuthenticationFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.authorization.AuthorizationDecision;

import java.util.Arrays;
import java.util.List;

/**
 * Secure-by-default HTTP configuration.
 *
 * <p>Without an explicitly configured authentication adapter, application APIs
 * reject anonymous requests with HTTP 401. Health remains available for probes;
 * API documentation is public only when its endpoints are enabled.</p>
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final String TRUSTED_HEADER_AUTHORITIES =
            "atom.security.trusted-header.authorities";

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            org.springframework.beans.factory.ObjectProvider<TrustedHeaderAuthenticationFilter> trustedHeaderFilter,
            BrowserSessionAuthenticationFilter browserSessionAuthenticationFilter,
            BrowserSessionCsrfFilter browserSessionCsrfFilter,
            LeadIntakeAuthenticationFilter leadIntakeAuthenticationFilter,
            MarketingFormSubmissionRateLimitFilter marketingFormRateLimitFilter,
            ConnectorAuthenticationFilter connectorAuthenticationFilter
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(unauthorizedEntryPoint()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/api/health",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml",
                                "/api/v1/open/marketing-forms/**",
                                "/api/v1/installation",
                                "/api/v1/installation/tenant-admin",
                                "/api/v1/authentication/session",
                                "/api/v1/authentication/password/login-options",
                                "/api/v1/authentication/password/sessions",
                                "/api/v1/authentication/password/reset-requests",
                                "/api/v1/authentication/password/reset-confirmations"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/**")
                            .hasAuthority("users:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/users/**")
                            .hasAuthority("users:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/users/**")
                            .hasAuthority("users:write")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/users/**")
                            .hasAuthority("users:delete")
                        .requestMatchers(HttpMethod.GET, "/api/v1/reference/**")
                            .hasAuthority("organization:request")
                        .requestMatchers(HttpMethod.GET, "/api/v1/authorization/me")
                            .hasAuthority("navigation:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/authorization/members/roles/query")
                            .hasAuthority("authorization:read")
                        .requestMatchers(HttpMethod.GET, "/api/v1/authorization/**")
                            .hasAuthority("authorization:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/authorization/roles/*/assignments")
                            .hasAuthority("authorization:assign")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/authorization/roles/*/assignments/*")
                            .hasAuthority("authorization:assign")
                        .requestMatchers(HttpMethod.POST, "/api/v1/authorization/roles")
                            .hasAllAuthorities("authorization:write", "authorization:assign")
                        .requestMatchers(HttpMethod.POST, "/api/v1/authorization/roles/*/copy")
                            .hasAllAuthorities("authorization:write", "authorization:assign")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/authorization/roles/**")
                            .hasAllAuthorities("authorization:write", "authorization:assign")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/authorization/roles/*")
                            .hasAllAuthorities("authorization:write", "authorization:assign")
                        .requestMatchers(HttpMethod.GET, "/api/v1/navigation/catalog")
                            .hasAuthority("authorization:read")
                        .requestMatchers(HttpMethod.GET, "/api/v1/navigation/**", "/api/v1/navigation")
                            .hasAuthority("navigation:read")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/navigation/preferences")
                            .hasAuthority("navigation:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/reports/**", "/api/v1/reports")
                            .hasAuthority("reports:read")
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/reports/trends", "/api/v1/reports/rankings",
                                "/api/v1/reports/definitions/query",
                                "/api/v1/reports/definitions/*/run")
                            .hasAuthority("reports:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/reports/definitions")
                            .hasAuthority("reports:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/reports/definitions/**")
                            .hasAuthority("reports:write")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/reports/definitions/**")
                            .hasAuthority("reports:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/dashboard/preferences")
                            .hasAuthority("reports:read")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/dashboard/preferences")
                            .hasAuthority("reports:read")
                        .requestMatchers(HttpMethod.GET, "/api/v1/dashboard/overview")
                            .hasAuthority("reports:read")
                        .requestMatchers("/api/v1/enterprise-data", "/api/v1/enterprise-data/**")
                            .hasAuthority(AuthorityCatalog.ENTERPRISE_DATA_READ)
                        .requestMatchers(HttpMethod.GET, "/api/v1/marketing-forms/**",
                                "/api/v1/marketing-forms")
                            .hasAuthority("marketing-forms:read")
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/marketing-forms/*/submissions/trends")
                            .hasAuthority("marketing-forms:read")
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/marketing-forms/*/submissions/query")
                            .hasAllAuthorities("marketing-forms:read", "leads:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/marketing-forms")
                            .hasAuthority("marketing-forms:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/marketing-forms/**")
                            .hasAuthority("marketing-forms:write")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/marketing-forms/**")
                            .hasAuthority("marketing-forms:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/marketing-activities/**",
                                "/api/v1/marketing-activities")
                            .hasAuthority(AuthorityCatalog.MARKETING_ACTIVITIES_READ)
                        .requestMatchers(HttpMethod.POST, "/api/v1/marketing-activities")
                            .hasAuthority(AuthorityCatalog.MARKETING_ACTIVITIES_WRITE)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/marketing-activities/**")
                            .hasAuthority(AuthorityCatalog.MARKETING_ACTIVITIES_WRITE)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/marketing-activities/**")
                            .hasAuthority(AuthorityCatalog.MARKETING_ACTIVITIES_DELETE)
                        .requestMatchers(HttpMethod.GET, "/api/v1/fields/roles/**")
                            .hasAuthority("fields:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/fields/**")
                            .hasAuthority("fields:read")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/fields/objects/*/layout")
                            .hasAuthority("fields:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/fields/**")
                            .hasAuthority("fields:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/fields/**")
                            .hasAuthority("fields:write")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/fields/**")
                            .hasAuthority("fields:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/saved-views/**", "/api/v1/saved-views")
                            .hasAuthority("saved-views:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/saved-views/**", "/api/v1/saved-views")
                            .hasAuthority("saved-views:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/saved-views/**")
                            .hasAuthority("saved-views:write")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/saved-views/**")
                            .hasAuthority("saved-views:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/embedded-pages/**", "/api/v1/embedded-pages")
                            .hasAuthority("embedded-pages:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/embedded-pages/**", "/api/v1/embedded-pages")
                            .hasAuthority("embedded-pages:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/embedded-pages/**")
                            .hasAuthority("embedded-pages:write")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/embedded-pages/**")
                            .hasAuthority("embedded-pages:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/customers/*/collaborators")
                            .hasAuthority("customers:assign")
                        .requestMatchers(HttpMethod.GET, "/api/v1/customers/**", "/api/v1/customers")
                            .hasAuthority("customers:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/customers/duplicate-check")
                            .hasAuthority("customers:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/customers/export/csv")
                            .hasAuthority("customers:export")
                        .requestMatchers(HttpMethod.POST, "/api/v1/customers/**", "/api/v1/customers")
                            .hasAuthority("customers:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/customers/*/star")
                            .hasAuthority("customers:read")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/customers/*/owner",
                                "/api/v1/customers/*/collaborators")
                            .hasAuthority("customers:assign")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/customers/**")
                            .hasAuthority("customers:write")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/customers/*/collaborators/me")
                            .hasAuthority("customers:read")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/customers/**")
                            .hasAuthority("customers:delete")
                        .requestMatchers(HttpMethod.GET, "/api/v1/customer-pools/catalog",
                                "/api/v1/customer-pools/*/customers/**")
                            .hasAuthority("customer-pools:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/customer-pools/*/customers/release")
                            .hasAuthority("customer-pools:release")
                        .requestMatchers(HttpMethod.POST, "/api/v1/customer-pools/*/customers/claim")
                            .hasAuthority("customer-pools:claim")
                        .requestMatchers(HttpMethod.POST, "/api/v1/customer-pools/*/customers/assign")
                            .hasAuthority("customer-pools:assign")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/customer-pools/*/customers")
                            .hasAuthority("customer-pools:delete")
                        .requestMatchers(HttpMethod.GET, "/api/v1/customer-pools/**",
                                "/api/v1/customer-pools")
                            .hasAuthority("customer-pools:manage")
                        .requestMatchers(HttpMethod.POST, "/api/v1/customer-pools/**",
                                "/api/v1/customer-pools")
                            .hasAuthority("customer-pools:manage")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/customer-pools/**")
                            .hasAuthority("customer-pools:manage")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/customer-pools/**")
                            .hasAuthority("customer-pools:manage")
                        .requestMatchers(HttpMethod.GET, "/api/v1/leads/*/collaborators")
                            .hasAuthority("leads:assign")
                        .requestMatchers(HttpMethod.GET, "/api/v1/leads/**", "/api/v1/leads")
                            .hasAuthority("leads:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/leads/duplicate-check")
                            .hasAuthority("leads:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/leads/export/csv")
                            .hasAuthority("leads:export")
                        .requestMatchers(HttpMethod.POST, "/api/v1/leads/*/convert")
                            .hasAuthority("leads:convert")
                        .requestMatchers(HttpMethod.POST, "/api/v1/leads/**", "/api/v1/leads")
                            .hasAuthority("leads:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/leads/*/star")
                            .hasAuthority("leads:read")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/leads/*/owner",
                                "/api/v1/leads/*/collaborators")
                            .hasAuthority("leads:assign")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/leads/**")
                            .hasAuthority("leads:write")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/leads/*/collaborators/me")
                            .hasAuthority("leads:read")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/leads/**")
                            .hasAuthority("leads:delete")
                        .requestMatchers(HttpMethod.GET, "/api/v1/contacts/*/collaborators")
                            .hasAuthority("contacts:assign")
                        .requestMatchers(HttpMethod.GET, "/api/v1/contacts/**", "/api/v1/contacts")
                            .hasAuthority("contacts:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/contacts/duplicate-check")
                            .hasAuthority("contacts:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/contacts/export/csv")
                            .hasAuthority("contacts:export")
                        .requestMatchers(HttpMethod.POST, "/api/v1/contacts/**", "/api/v1/contacts")
                            .hasAuthority("contacts:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/contacts/*/star")
                            .hasAuthority("contacts:read")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/contacts/*/owner",
                                "/api/v1/contacts/*/collaborators")
                            .hasAuthority("contacts:assign")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/contacts/**")
                            .hasAuthority("contacts:write")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/contacts/*/collaborators/me")
                            .hasAuthority("contacts:read")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/contacts/**")
                            .hasAuthority("contacts:delete")
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/opportunities/*/products")
                            .hasAllAuthorities("opportunities:read", "products:read")
                        .requestMatchers(HttpMethod.PUT,
                                "/api/v1/opportunities/*/products")
                            .hasAllAuthorities("opportunities:write", "products:read")
                        .requestMatchers(HttpMethod.GET, "/api/v1/opportunities/**")
                            .hasAuthority("opportunities:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/opportunities/query",
                                "/api/v1/opportunities/summary",
                                "/api/v1/opportunities/*/contacts/query")
                            .hasAuthority("opportunities:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/opportunities")
                            .hasAuthority("opportunities:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/opportunities/*/star")
                            .hasAuthority("opportunities:read")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/opportunities/*/owner",
                                "/api/v1/opportunities/*/collaborators")
                            .hasAuthority("opportunities:assign")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/opportunities/**")
                            .hasAuthority("opportunities:write")
                        .requestMatchers(HttpMethod.DELETE,
                                "/api/v1/opportunities/*/collaborators/me")
                            .hasAuthority("opportunities:read")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/opportunities/**")
                            .hasAuthority("opportunities:delete")
                        .requestMatchers(HttpMethod.GET, "/api/v1/opportunity-pipelines/**")
                            .hasAuthority(AuthorityCatalog.OPPORTUNITY_PIPELINES_READ)
                        .requestMatchers(HttpMethod.POST, "/api/v1/opportunity-pipelines/query",
                                "/api/v1/opportunity-pipelines/*/stages/query")
                            .hasAuthority(AuthorityCatalog.OPPORTUNITY_PIPELINES_READ)
                        .requestMatchers(HttpMethod.POST, "/api/v1/opportunity-pipelines/**",
                                "/api/v1/opportunity-pipelines")
                            .hasAuthority(AuthorityCatalog.OPPORTUNITY_PIPELINES_MANAGE)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/opportunity-pipelines/**")
                            .hasAuthority(AuthorityCatalog.OPPORTUNITY_PIPELINES_MANAGE)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/opportunity-pipelines/**")
                            .hasAuthority(AuthorityCatalog.OPPORTUNITY_PIPELINES_MANAGE)
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/**",
                                "/api/v1/products", "/api/v1/product-categories/**",
                                "/api/v1/product-categories")
                            .hasAuthority("products:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/products/query")
                            .hasAuthority("products:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/products",
                                "/api/v1/product-categories")
                            .hasAuthority("products:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/products/*/owner")
                            .hasAuthority("products:assign")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/products/**",
                                "/api/v1/product-categories/**")
                            .hasAuthority("products:write")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/products/**",
                                "/api/v1/product-categories/**")
                            .hasAuthority("products:delete")
                        .requestMatchers(HttpMethod.POST, "/api/v1/contracts/query",
                                "/api/v1/contracts/summary")
                            .hasAuthority("contracts:read")
                        .requestMatchers(HttpMethod.GET, "/api/v1/contracts/**")
                            .hasAuthority("contracts:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/contracts")
                            .hasAuthority("contracts:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/contracts/*/owner",
                                "/api/v1/contracts/*/collaborators")
                            .hasAuthority("contracts:assign")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/contracts/**")
                            .hasAuthority("contracts:write")
                        .requestMatchers(HttpMethod.DELETE,
                                "/api/v1/contracts/*/collaborators/me")
                            .hasAuthority("contracts:write")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/contracts/**")
                            .hasAuthority("contracts:delete")
                        .requestMatchers(HttpMethod.POST, "/api/v1/invoices/query")
                            .hasAuthority("invoices:read")
                        .requestMatchers(HttpMethod.GET, "/api/v1/invoices/**")
                            .hasAuthority("invoices:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/invoices")
                            .hasAuthority("invoices:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/invoices/*/owner")
                            .hasAuthority("invoices:assign")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/invoices/*/issue",
                                "/api/v1/invoices/*/reset")
                            .hasAuthority("invoices:issue")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/invoices/**")
                            .hasAuthority("invoices:write")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/invoices/**")
                            .hasAuthority("invoices:delete")
                        .requestMatchers(HttpMethod.GET, "/api/v1/invoice-profiles")
                            .hasAuthority("invoices:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/invoice-profiles")
                            .hasAuthority("invoices:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/invoice-profiles/**")
                            .hasAuthority("invoices:write")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/invoice-profiles/**")
                            .hasAuthority("invoices:delete")
                        .requestMatchers(HttpMethod.POST, "/api/v1/receivables/query",
                                "/api/v1/receivables/summary")
                            .hasAuthority("receivables:read")
                        .requestMatchers(HttpMethod.GET, "/api/v1/receivables/**")
                            .hasAuthority("receivables:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/receivables")
                            .hasAuthority("receivables:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/receivables/*/owner")
                            .hasAuthority("receivables:assign")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/receivables/**")
                            .hasAuthority("receivables:write")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/receivables/**")
                            .hasAuthority("receivables:delete")
                        .requestMatchers(HttpMethod.POST, "/api/v1/receivable-plans/query",
                                "/api/v1/receivable-plans/summary")
                            .hasAuthority("receivable-plans:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/receivable-plans/export/csv")
                            .hasAuthority("receivable-plans:export")
                        .requestMatchers(HttpMethod.GET, "/api/v1/receivable-plans/**")
                            .hasAuthority("receivable-plans:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/receivable-plans/**",
                                "/api/v1/receivable-plans")
                            .hasAuthority("receivable-plans:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/receivable-plans/**")
                            .hasAuthority("receivable-plans:write")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/receivable-plans/**")
                            .hasAuthority("receivable-plans:delete")
                        .requestMatchers(HttpMethod.POST, "/api/v1/activities/query")
                            .hasAuthority("activities:read")
                        .requestMatchers(HttpMethod.GET, "/api/v1/activities/**")
                            .hasAuthority("activities:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/activities/**")
                            .hasAuthority("activities:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/activities/**")
                            .hasAuthority("activities:write")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/activities/**")
                            .hasAuthority("activities:delete")
                        .requestMatchers(HttpMethod.POST, "/api/v1/return-visits/query")
                            .hasAuthority("return-visits:read")
                        .requestMatchers(HttpMethod.GET, "/api/v1/return-visits/**")
                            .hasAuthority("return-visits:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/return-visits")
                            .hasAuthority("return-visits:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/return-visits/*/owner")
                            .hasAuthority("return-visits:assign")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/return-visits/**")
                            .hasAuthority("return-visits:write")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/return-visits/**")
                            .hasAuthority("return-visits:delete")
                        .requestMatchers(HttpMethod.GET, "/api/v1/worklist")
                            .hasAuthority("worklist:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/worklist/acknowledgements")
                            .hasAuthority("worklist:read")
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/attachment-targets/*/*/attachments/*/upload-status")
                            .hasAuthority(AuthorityCatalog.FILES_WRITE)
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/attachment-targets/*/*/attachments/*/content",
                                "/api/v1/attachment-targets/*/*/attachments/*",
                                "/api/v1/attachment-targets/*/*/attachments")
                            .hasAuthority(AuthorityCatalog.FILES_READ)
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/attachment-targets/*/*/attachments/upload-intents")
                            .hasAuthority(AuthorityCatalog.FILES_WRITE)
                        .requestMatchers(HttpMethod.PUT,
                                "/api/v1/attachment-targets/*/*/attachments/*/content")
                            .hasAuthority(AuthorityCatalog.FILES_WRITE)
                        .requestMatchers(HttpMethod.DELETE,
                                "/api/v1/attachment-targets/*/*/attachments/*")
                            .hasAuthority(AuthorityCatalog.FILES_WRITE)
                        .requestMatchers(HttpMethod.GET, "/api/v1/work/task-series/**", "/api/v1/work/task-series")
                            .hasAuthority("work-tasks:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/work/task-series/**", "/api/v1/work/task-series")
                            .hasAuthority("work-tasks:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/work/task-series/**")
                            .hasAuthority("work-tasks:write")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/work/task-series/**")
                            .hasAuthority("work-tasks:write")
                        .requestMatchers(HttpMethod.POST, "/api/v1/work/tasks/query")
                            .hasAuthority("work-tasks:read")
                        .requestMatchers(HttpMethod.GET, "/api/v1/work/tasks/**")
                            .hasAuthority("work-tasks:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/work/tasks/**",
                                "/api/v1/work/tasks")
                            .hasAuthority("work-tasks:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/work/tasks/**")
                            .hasAuthority("work-tasks:write")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/work/tasks/**")
                            .hasAuthority("work-tasks:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/notifications")
                            .hasAllAuthorities(AuthorityCatalog.NOTIFICATIONS_READ,
                                    AuthorityCatalog.WORK_TASKS_READ)
                        .requestMatchers(HttpMethod.POST, "/api/v1/notifications/*/read")
                            .hasAllAuthorities(AuthorityCatalog.NOTIFICATIONS_READ,
                                    AuthorityCatalog.WORK_TASKS_READ)
                        .requestMatchers(HttpMethod.GET, "/api/v1/notifications/preferences")
                            .hasAuthority(AuthorityCatalog.NOTIFICATIONS_READ)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/notifications/preferences")
                            .hasAuthority(AuthorityCatalog.NOTIFICATIONS_READ)
                        .requestMatchers(HttpMethod.GET, "/api/v1/approval-definitions/**")
                            .hasAuthority("approvals:manage")
                        .requestMatchers(HttpMethod.POST, "/api/v1/approval-definitions/query")
                            .hasAuthority("approvals:manage")
                        .requestMatchers(HttpMethod.POST, "/api/v1/approval-definitions/*/preview")
                            .hasAuthority("approvals:submit")
                        .requestMatchers(HttpMethod.POST, "/api/v1/approval-definitions")
                            .hasAuthority("approvals:manage")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/approval-definitions/**")
                            .hasAuthority("approvals:manage")
                        .requestMatchers(HttpMethod.GET, "/api/v1/approvals/**")
                            .hasAuthority("approvals:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/approvals/query")
                            .hasAuthority("approvals:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/approvals/actions/batch",
                                "/api/v1/approvals/*/actions", "/api/v1/approvals/*/cancel")
                            .hasAuthority("approvals:review")
                        .requestMatchers(HttpMethod.POST, "/api/v1/approvals/*/withdraw",
                                "/api/v1/approvals")
                            .hasAuthority("approvals:submit")
                        .requestMatchers(HttpMethod.GET, "/api/v1/oa/forms/available",
                                "/api/v1/oa/forms/available/**")
                            .hasAllAuthorities("oa-forms:read", "oa-requests:submit")
                        .requestMatchers(HttpMethod.GET, "/api/v1/oa/forms/configuration-options")
                            .hasAuthority("oa-forms:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/oa/forms",
                                "/api/v1/oa/forms/**")
                            .hasAuthority("oa-forms:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/oa/forms")
                            .hasAuthority("oa-forms:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/oa/forms/**")
                            .hasAuthority("oa-forms:write")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/oa/forms/**")
                            .hasAuthority("oa-forms:write")
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/oa/announcements/mine",
                                "/api/v1/oa/announcements/mine/**",
                                "/api/v1/oa/announcements/unread-count")
                            .hasAuthority(AuthorityCatalog.OA_ANNOUNCEMENTS_READ)
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/oa/announcements/mine/*/read")
                            .hasAuthority(AuthorityCatalog.OA_ANNOUNCEMENTS_READ)
                        .requestMatchers(
                                "/api/v1/oa/announcements",
                                "/api/v1/oa/announcements/**")
                            .hasAuthority(AuthorityCatalog.OA_ANNOUNCEMENTS_WRITE)
                        .requestMatchers(HttpMethod.GET, "/api/v1/stage-workflows/**")
                            .hasAuthority("workflows:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/stage-workflows/query")
                            .hasAuthority("workflows:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/stage-workflows/*/records/*",
                                "/api/v1/stage-workflows/records/*/comments")
                            .hasAuthority("workflows:write")
                        .requestMatchers(HttpMethod.POST, "/api/v1/stage-workflows")
                            .hasAuthority("workflows:manage")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/stage-workflows/records/**")
                            .hasAuthority("workflows:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/stage-workflows/**")
                            .hasAuthority("workflows:manage")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/stage-workflows/records/**")
                            .hasAuthority("workflows:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/contact-pools/catalog",
                                "/api/v1/contact-pools/*/contacts/**")
                            .hasAuthority("contact-pools:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/contact-pools/*/contacts/release")
                            .hasAuthority("contact-pools:release")
                        .requestMatchers(HttpMethod.POST, "/api/v1/contact-pools/*/contacts/claim")
                            .hasAuthority("contact-pools:claim")
                        .requestMatchers(HttpMethod.POST, "/api/v1/contact-pools/*/contacts/assign")
                            .hasAuthority("contact-pools:assign")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/contact-pools/*/contacts")
                            .hasAuthority("contact-pools:delete")
                        .requestMatchers(HttpMethod.GET, "/api/v1/contact-pools/**",
                                "/api/v1/contact-pools")
                            .hasAuthority("contact-pools:manage")
                        .requestMatchers(HttpMethod.POST, "/api/v1/contact-pools/**",
                                "/api/v1/contact-pools")
                            .hasAuthority("contact-pools:manage")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/contact-pools/**")
                            .hasAuthority("contact-pools:manage")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/contact-pools/**")
                            .hasAuthority("contact-pools:manage")
                        .requestMatchers(HttpMethod.POST, "/api/v1/lead-intake-clients")
                            .hasAuthority("leads:intake-manage")
                        .requestMatchers(HttpMethod.GET, "/api/v1/lead-intake-clients")
                            .hasAuthority("leads:intake-manage")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/lead-intake-clients/**")
                            .hasAuthority("leads:intake-manage")
                        .requestMatchers("/api/v1/connector-ingress/**")
                            .access((authentication, context) -> new AuthorizationDecision(
                                    authentication.get().isAuthenticated()
                                            && authentication.get().getPrincipal()
                                            instanceof io.github.archetom.crm.infra.rest.security.ConnectorPrincipal))
                        .requestMatchers(HttpMethod.POST, "/api/v1/connectors/query")
                            .hasAuthority(AuthorityCatalog.CONNECTORS_READ)
                        .requestMatchers(HttpMethod.GET, "/api/v1/connectors/**")
                            .hasAuthority(AuthorityCatalog.CONNECTORS_READ)
                        .requestMatchers(HttpMethod.POST, "/api/v1/connectors/**",
                                "/api/v1/connectors")
                            .hasAuthority(AuthorityCatalog.CONNECTORS_MANAGE)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/connectors/**")
                            .hasAuthority(AuthorityCatalog.CONNECTORS_MANAGE)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/connectors/**")
                            .hasAuthority(AuthorityCatalog.CONNECTORS_MANAGE)
                        .requestMatchers(HttpMethod.POST, "/api/v1/open/leads")
                            .hasAuthority("leads:write")
                        .requestMatchers(HttpMethod.POST, "/api/v1/organization/bootstrap")
                            .hasAuthority("organization:bootstrap")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/organization/profile")
                            .hasAuthority("organization:request")
                        .requestMatchers(HttpMethod.POST, "/api/v1/organization/membership-requests")
                            .hasAuthority("organization:request")
                        .requestMatchers(HttpMethod.POST, "/api/v1/organization/membership-requests/*/withdraw")
                            .hasAuthority("organization:request")
                        .requestMatchers(HttpMethod.GET, "/api/v1/organization/tenants",
                                "/api/v1/organization/tenants/*/access")
                            .hasAuthority("organization:request")
                        .requestMatchers(HttpMethod.GET, "/api/v1/organization/membership-requests/mine")
                            .hasAuthority("organization:request")
                        .requestMatchers(HttpMethod.GET, "/api/v1/organization/membership-requests")
                            .hasAuthority("organization:write")
                        .requestMatchers(HttpMethod.POST, "/api/v1/organization/**")
                            .hasAuthority("organization:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/organization/**")
                            .hasAuthority("organization:write")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/organization/**")
                            .hasAuthority("organization:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/organization/**")
                            .hasAuthority("organization:read")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll());

        TrustedHeaderAuthenticationFilter filter = trustedHeaderFilter.getIfAvailable();
        if (filter != null) {
            http.addFilterBefore(filter, AnonymousAuthenticationFilter.class);
            http.addFilterAfter(browserSessionAuthenticationFilter,
                    TrustedHeaderAuthenticationFilter.class);
            http.addFilterBefore(connectorAuthenticationFilter,
                    TrustedHeaderAuthenticationFilter.class);
        } else {
            http.addFilterBefore(browserSessionAuthenticationFilter, AnonymousAuthenticationFilter.class);
            http.addFilterBefore(connectorAuthenticationFilter,
                    BrowserSessionAuthenticationFilter.class);
        }
        http.addFilterAfter(browserSessionCsrfFilter,
                BrowserSessionAuthenticationFilter.class);
        http.addFilterBefore(leadIntakeAuthenticationFilter, AnonymousAuthenticationFilter.class);
        http.addFilterBefore(marketingFormRateLimitFilter, AnonymousAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint unauthorizedEntryPoint() {
        return new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
    }

    @Bean
    @Profile("(dev | test) & !prod")
    @ConditionalOnProperty(
            prefix = "atom.security.trusted-header",
            name = "enabled",
            havingValue = "true"
    )
    public TrustedHeaderAuthenticationFilter trustedHeaderAuthenticationFilter(
            Environment environment, TenantAuthorityResolver tenantAuthorityResolver) {
        return new TrustedHeaderAuthenticationFilter(configuredPlatformAuthorities(environment),
                tenantAuthorityResolver);
    }

    /**
     * A Filter bean is otherwise registered by the servlet container in
     * addition to Spring Security. Disable that registration so it runs once,
     * at the deliberate position in the security chain.
     */
    @Bean
    @Profile("(dev | test) & !prod")
    @ConditionalOnProperty(
            prefix = "atom.security.trusted-header",
            name = "enabled",
            havingValue = "true"
    )
    public FilterRegistrationBean<TrustedHeaderAuthenticationFilter> trustedHeaderFilterRegistration(
            TrustedHeaderAuthenticationFilter filter
    ) {
        FilterRegistrationBean<TrustedHeaderAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<LeadIntakeAuthenticationFilter> leadIntakeFilterRegistration(
            LeadIntakeAuthenticationFilter filter
    ) {
        FilterRegistrationBean<LeadIntakeAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<MarketingFormSubmissionRateLimitFilter>
            marketingFormRateLimitFilterRegistration(
                    MarketingFormSubmissionRateLimitFilter filter) {
        FilterRegistrationBean<MarketingFormSubmissionRateLimitFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<ConnectorAuthenticationFilter>
            connectorAuthenticationFilterRegistration(ConnectorAuthenticationFilter filter) {
        FilterRegistrationBean<ConnectorAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    private List<String> configuredPlatformAuthorities(Environment environment) {
        String value = environment.getProperty(TRUSTED_HEADER_AUTHORITIES, "");
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(authority -> !authority.isEmpty())
                .filter(this::isPlatformAuthority)
                .distinct()
                .toList();
    }

    private boolean isPlatformAuthority(String authority) {
        try {
            return AuthorityCatalog.find(new AuthorityCode(authority))
                    .filter(definition -> !definition.tenantAssignable())
                    .isPresent();
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
