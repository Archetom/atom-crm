package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.exception.AuthorizationDomainException;
import io.github.archetom.crm.domain.model.AuthorityDefinition;
import io.github.archetom.crm.domain.valueobject.AuthorityCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Collections;

/** Single code-defined catalog for platform and tenant-assignable capabilities. */
public final class AuthorityCatalog {

    public static final String USERS_READ = "users:read";
    public static final String USERS_WRITE = "users:write";
    public static final String USERS_DELETE = "users:delete";
    public static final String ORGANIZATION_READ = "organization:read";
    public static final String ORGANIZATION_WRITE = "organization:write";
    public static final String ORGANIZATION_BOOTSTRAP = "organization:bootstrap";
    public static final String ORGANIZATION_REQUEST = "organization:request";
    public static final String AUTHORIZATION_READ = "authorization:read";
    public static final String AUTHORIZATION_WRITE = "authorization:write";
    public static final String AUTHORIZATION_ASSIGN = "authorization:assign";
    public static final String NAVIGATION_READ = "navigation:read";
    public static final String NAVIGATION_WRITE = "navigation:write";
    public static final String REPORTS_READ = "reports:read";
    public static final String REPORTS_WRITE = "reports:write";
    public static final String DASHBOARD_TARGETS_READ = "dashboard-targets:read";
    public static final String DASHBOARD_TARGETS_WRITE = "dashboard-targets:write";
    public static final String ENTERPRISE_DATA_READ = "enterprise-data:read";
    public static final String MARKETING_FORMS_READ = "marketing-forms:read";
    public static final String MARKETING_FORMS_WRITE = "marketing-forms:write";
    public static final String MARKETING_ACTIVITIES_READ = "marketing-activities:read";
    public static final String MARKETING_ACTIVITIES_WRITE = "marketing-activities:write";
    public static final String MARKETING_ACTIVITIES_DELETE = "marketing-activities:delete";
    public static final String OA_FORMS_READ = "oa-forms:read";
    public static final String OA_FORMS_WRITE = "oa-forms:write";
    public static final String OA_REQUESTS_READ = "oa-requests:read";
    public static final String OA_REQUESTS_SUBMIT = "oa-requests:submit";
    public static final String OA_REQUESTS_MANAGE = "oa-requests:manage";
    public static final String OA_REQUESTS_COLLABORATE = "oa-requests:collaborate";
    public static final String OA_ANNOUNCEMENTS_READ = "oa-announcements:read";
    public static final String OA_ANNOUNCEMENTS_WRITE = "oa-announcements:write";
    public static final String FIELDS_READ = "fields:read";
    public static final String FIELDS_WRITE = "fields:write";
    public static final String SAVED_VIEWS_READ = "saved-views:read";
    public static final String SAVED_VIEWS_WRITE = "saved-views:write";
    public static final String EMBEDDED_PAGES_READ = "embedded-pages:read";
    public static final String EMBEDDED_PAGES_WRITE = "embedded-pages:write";
    public static final String CUSTOMERS_READ = "customers:read";
    public static final String CUSTOMERS_EXPORT = "customers:export";
    public static final String CUSTOMERS_WRITE = "customers:write";
    public static final String CUSTOMERS_DELETE = "customers:delete";
    public static final String CUSTOMERS_ASSIGN = "customers:assign";
    public static final String CUSTOMER_POOLS_READ = "customer-pools:read";
    public static final String CUSTOMER_POOLS_MANAGE = "customer-pools:manage";
    public static final String CUSTOMER_POOLS_RELEASE = "customer-pools:release";
    public static final String CUSTOMER_POOLS_CLAIM = "customer-pools:claim";
    public static final String CUSTOMER_POOLS_ASSIGN = "customer-pools:assign";
    public static final String CUSTOMER_POOLS_DELETE = "customer-pools:delete";
    public static final String LEADS_READ = "leads:read";
    public static final String LEADS_EXPORT = "leads:export";
    public static final String LEADS_WRITE = "leads:write";
    public static final String LEADS_DELETE = "leads:delete";
    public static final String LEADS_ASSIGN = "leads:assign";
    public static final String LEADS_CONVERT = "leads:convert";
    public static final String LEADS_INTAKE_MANAGE = "leads:intake-manage";
    public static final String CONTACTS_READ = "contacts:read";
    public static final String CONTACTS_EXPORT = "contacts:export";
    public static final String CONTACTS_WRITE = "contacts:write";
    public static final String CONTACTS_DELETE = "contacts:delete";
    public static final String CONTACTS_ASSIGN = "contacts:assign";
    public static final String CONTACT_POOLS_READ = "contact-pools:read";
    public static final String CONTACT_POOLS_MANAGE = "contact-pools:manage";
    public static final String CONTACT_POOLS_RELEASE = "contact-pools:release";
    public static final String CONTACT_POOLS_CLAIM = "contact-pools:claim";
    public static final String CONTACT_POOLS_ASSIGN = "contact-pools:assign";
    public static final String CONTACT_POOLS_DELETE = "contact-pools:delete";
    public static final String OPPORTUNITIES_READ = "opportunities:read";
    public static final String OPPORTUNITIES_WRITE = "opportunities:write";
    public static final String OPPORTUNITIES_DELETE = "opportunities:delete";
    public static final String OPPORTUNITIES_ASSIGN = "opportunities:assign";
    public static final String OPPORTUNITY_PIPELINES_READ = "opportunity-pipelines:read";
    public static final String OPPORTUNITY_PIPELINES_MANAGE = "opportunity-pipelines:manage";
    public static final String PRODUCTS_READ = "products:read";
    public static final String PRODUCTS_EXPORT = "products:export";
    public static final String PRODUCTS_WRITE = "products:write";
    public static final String PRODUCTS_DELETE = "products:delete";
    public static final String PRODUCTS_ASSIGN = "products:assign";
    public static final String CONTRACTS_READ = "contracts:read";
    public static final String CONTRACTS_EXPORT = "contracts:export";
    public static final String CONTRACTS_WRITE = "contracts:write";
    public static final String CONTRACTS_DELETE = "contracts:delete";
    public static final String CONTRACTS_ASSIGN = "contracts:assign";
    public static final String INVOICES_READ = "invoices:read";
    public static final String INVOICES_WRITE = "invoices:write";
    public static final String INVOICES_DELETE = "invoices:delete";
    public static final String INVOICES_ASSIGN = "invoices:assign";
    public static final String INVOICES_ISSUE = "invoices:issue";
    public static final String RECEIVABLES_READ = "receivables:read";
    public static final String RECEIVABLES_WRITE = "receivables:write";
    public static final String RECEIVABLES_DELETE = "receivables:delete";
    public static final String RECEIVABLES_ASSIGN = "receivables:assign";
    public static final String RECEIVABLE_PLANS_READ = "receivable-plans:read";
    public static final String RECEIVABLE_PLANS_EXPORT = "receivable-plans:export";
    public static final String RECEIVABLE_PLANS_WRITE = "receivable-plans:write";
    public static final String RECEIVABLE_PLANS_DELETE = "receivable-plans:delete";
    public static final String ACTIVITIES_READ = "activities:read";
    public static final String ACTIVITIES_WRITE = "activities:write";
    public static final String ACTIVITIES_DELETE = "activities:delete";
    public static final String RETURN_VISITS_READ = "return-visits:read";
    public static final String RETURN_VISITS_WRITE = "return-visits:write";
    public static final String RETURN_VISITS_DELETE = "return-visits:delete";
    public static final String RETURN_VISITS_ASSIGN = "return-visits:assign";
    public static final String WORKLIST_READ = "worklist:read";
    public static final String WORK_TASKS_READ = "work-tasks:read";
    public static final String WORK_TASKS_WRITE = "work-tasks:write";
    public static final String WORK_TASKS_MANAGE = "work-tasks:manage";
    public static final String NOTIFICATIONS_READ = "notifications:read";
    public static final String FILES_READ = "files:read";
    public static final String FILES_WRITE = "files:write";
    public static final String CONNECTORS_READ = "connectors:read";
    public static final String CONNECTORS_MANAGE = "connectors:manage";
    public static final String APPROVALS_READ = "approvals:read";
    public static final String APPROVALS_SUBMIT = "approvals:submit";
    public static final String APPROVALS_REVIEW = "approvals:review";
    public static final String APPROVALS_MANAGE = "approvals:manage";
    public static final String WORKFLOWS_READ = "workflows:read";
    public static final String WORKFLOWS_WRITE = "workflows:write";
    public static final String WORKFLOWS_MANAGE = "workflows:manage";

    private static final Map<AuthorityCode, AuthorityDefinition> DEFINITIONS = definitions();

    private AuthorityCatalog() {
    }

    public static List<AuthorityDefinition> all() {
        return List.copyOf(DEFINITIONS.values());
    }

    public static List<AuthorityDefinition> tenantAssignable() {
        return DEFINITIONS.values().stream()
                .filter(AuthorityDefinition::tenantAssignable)
                .toList();
    }

    public static Optional<AuthorityDefinition> find(AuthorityCode code) {
        return Optional.ofNullable(code == null ? null : DEFINITIONS.get(code));
    }

    public static AuthorityDefinition requireTenantAssignable(AuthorityCode code) {
        return find(code)
                .filter(AuthorityDefinition::tenantAssignable)
                .orElseThrow(() -> new AuthorizationDomainException(
                        "authority is unknown or cannot be assigned by a tenant role"));
    }

    private static Map<AuthorityCode, AuthorityDefinition> definitions() {
        Map<AuthorityCode, AuthorityDefinition> definitions = new LinkedHashMap<>();
        add(definitions, USERS_READ, "Read users", true, true);
        add(definitions, USERS_WRITE, "Manage users", true, true);
        add(definitions, USERS_DELETE, "Delete users", true, true);
        add(definitions, ORGANIZATION_READ, "Read organization", true, false);
        add(definitions, ORGANIZATION_WRITE, "Manage organization", true, false);
        add(definitions, ORGANIZATION_BOOTSTRAP, "Bootstrap organization", false, false);
        add(definitions, ORGANIZATION_REQUEST, "Request membership", false, false);
        add(definitions, AUTHORIZATION_READ, "Read authorization", true, false);
        add(definitions, AUTHORIZATION_WRITE, "Manage roles", true, false);
        add(definitions, AUTHORIZATION_ASSIGN, "Assign roles", true, false);
        add(definitions, NAVIGATION_READ, "Read navigation", true, false);
        add(definitions, NAVIGATION_WRITE, "Manage navigation preferences", true, false);
        add(definitions, REPORTS_READ, "Read reports", true, false);
        add(definitions, REPORTS_WRITE, "Manage personal reports", true, false);
        add(definitions, DASHBOARD_TARGETS_READ, "Read dashboard performance targets", true, false);
        add(definitions, DASHBOARD_TARGETS_WRITE, "Manage dashboard performance targets", true, false);
        add(definitions, ENTERPRISE_DATA_READ, "Query governed enterprise data", true, false);
        add(definitions, MARKETING_FORMS_READ, "Read marketing forms", true, false);
        add(definitions, MARKETING_FORMS_WRITE, "Manage marketing forms", true, false);
        add(definitions, MARKETING_ACTIVITIES_READ, "Read marketing activities", true, false);
        add(definitions, MARKETING_ACTIVITIES_WRITE, "Manage marketing activities", true, false);
        add(definitions, MARKETING_ACTIVITIES_DELETE, "Delete marketing activities", true, false);
        add(definitions, OA_FORMS_READ, "Read OA forms", true, false);
        add(definitions, OA_FORMS_WRITE, "Manage OA forms", true, false);
        add(definitions, OA_REQUESTS_READ, "Read participating OA requests", true, false);
        add(definitions, OA_REQUESTS_SUBMIT, "Submit OA requests", true, false);
        add(definitions, OA_REQUESTS_MANAGE, "Manage tenant OA requests", true, false);
        add(definitions, OA_REQUESTS_COLLABORATE,
                "Collaborate on pending OA request attachments", true, false);
        add(definitions, OA_ANNOUNCEMENTS_READ, "Read delivered OA announcements", true, false);
        add(definitions, OA_ANNOUNCEMENTS_WRITE, "Manage OA announcements", true, false);
        add(definitions, FIELDS_READ, "Read field metadata", true, false);
        add(definitions, FIELDS_WRITE, "Manage field metadata", true, false);
        add(definitions, SAVED_VIEWS_READ, "Read saved views", true, false);
        add(definitions, SAVED_VIEWS_WRITE, "Manage personal saved views", true, false);
        add(definitions, EMBEDDED_PAGES_READ, "Read embedded pages", true, false);
        add(definitions, EMBEDDED_PAGES_WRITE, "Manage embedded pages", true, false);
        add(definitions, CUSTOMERS_READ, "Read customers", true, true);
        add(definitions, CUSTOMERS_EXPORT, "Export customers", true, true);
        add(definitions, CUSTOMERS_WRITE, "Manage customers", true, true);
        add(definitions, CUSTOMERS_DELETE, "Delete customers", true, true);
        add(definitions, CUSTOMERS_ASSIGN, "Assign customers", true, true);
        add(definitions, CUSTOMER_POOLS_READ, "Read customer pools", true, false);
        add(definitions, CUSTOMER_POOLS_MANAGE, "Manage customer pools", true, false);
        add(definitions, CUSTOMER_POOLS_RELEASE, "Release customers to pools", true, false);
        add(definitions, CUSTOMER_POOLS_CLAIM, "Claim customers from pools", true, false);
        add(definitions, CUSTOMER_POOLS_ASSIGN, "Assign customers from pools", true, false);
        add(definitions, CUSTOMER_POOLS_DELETE, "Delete customers from pools", true, false);
        add(definitions, LEADS_READ, "Read leads", true, true);
        add(definitions, LEADS_EXPORT, "Export leads", true, true);
        add(definitions, LEADS_WRITE, "Manage leads", true, true);
        add(definitions, LEADS_DELETE, "Delete leads", true, true);
        add(definitions, LEADS_ASSIGN, "Assign leads", true, true);
        add(definitions, LEADS_CONVERT, "Convert leads", true, true);
        add(definitions, LEADS_INTAKE_MANAGE, "Manage lead intake clients", true, false);
        add(definitions, CONTACTS_READ, "Read contacts", true, true);
        add(definitions, CONTACTS_EXPORT, "Export contacts", true, true);
        add(definitions, CONTACTS_WRITE, "Manage contacts", true, true);
        add(definitions, CONTACTS_DELETE, "Delete contacts", true, true);
        add(definitions, CONTACTS_ASSIGN, "Assign contacts", true, true);
        add(definitions, CONTACT_POOLS_READ, "Read contact pools", true, false);
        add(definitions, CONTACT_POOLS_MANAGE, "Manage contact pools", true, false);
        add(definitions, CONTACT_POOLS_RELEASE, "Release contacts to pools", true, false);
        add(definitions, CONTACT_POOLS_CLAIM, "Claim contacts from pools", true, false);
        add(definitions, CONTACT_POOLS_ASSIGN, "Assign contacts from pools", true, false);
        add(definitions, CONTACT_POOLS_DELETE, "Delete contacts from pools", true, false);
        add(definitions, OPPORTUNITIES_READ, "Read opportunities", true, true);
        add(definitions, OPPORTUNITIES_WRITE, "Manage opportunities", true, true);
        add(definitions, OPPORTUNITIES_DELETE, "Delete opportunities", true, true);
        add(definitions, OPPORTUNITIES_ASSIGN, "Assign opportunities", true, true);
        add(definitions, OPPORTUNITY_PIPELINES_READ,
                "Read opportunity pipelines", true, false);
        add(definitions, OPPORTUNITY_PIPELINES_MANAGE,
                "Manage opportunity pipelines", true, false);
        add(definitions, PRODUCTS_READ, "Read products", true, false);
        add(definitions, PRODUCTS_EXPORT, "Export products", true, false);
        add(definitions, PRODUCTS_WRITE, "Manage products", true, false);
        add(definitions, PRODUCTS_DELETE, "Delete products", true, false);
        add(definitions, PRODUCTS_ASSIGN, "Assign products", true, false);
        add(definitions, CONTRACTS_READ, "Read contracts", true, true);
        add(definitions, CONTRACTS_EXPORT, "Export contracts", true, true);
        add(definitions, CONTRACTS_WRITE, "Manage contracts", true, true);
        add(definitions, CONTRACTS_DELETE, "Delete contracts", true, true);
        add(definitions, CONTRACTS_ASSIGN, "Assign contracts", true, true);
        add(definitions, INVOICES_READ, "Read invoices", true, true);
        add(definitions, INVOICES_WRITE, "Manage invoices", true, true);
        add(definitions, INVOICES_DELETE, "Delete invoices", true, true);
        add(definitions, INVOICES_ASSIGN, "Assign invoices", true, true);
        add(definitions, INVOICES_ISSUE, "Issue invoices", true, true);
        add(definitions, RECEIVABLES_READ, "Read receivables", true, true);
        add(definitions, RECEIVABLES_WRITE, "Manage receivables", true, true);
        add(definitions, RECEIVABLES_DELETE, "Delete receivables", true, true);
        add(definitions, RECEIVABLES_ASSIGN, "Assign receivables", true, true);
        add(definitions, RECEIVABLE_PLANS_READ, "Read receivable plans", true, true);
        add(definitions, RECEIVABLE_PLANS_EXPORT, "Export receivable plans", true, true);
        add(definitions, RECEIVABLE_PLANS_WRITE, "Manage receivable plans", true, true);
        add(definitions, RECEIVABLE_PLANS_DELETE, "Delete receivable plans", true, true);
        add(definitions, ACTIVITIES_READ, "Read activities", true, false);
        add(definitions, ACTIVITIES_WRITE, "Create activities", true, false);
        add(definitions, ACTIVITIES_DELETE, "Delete activities", true, false);
        add(definitions, RETURN_VISITS_READ, "Read return visits", true, true);
        add(definitions, RETURN_VISITS_WRITE, "Manage return visits", true, true);
        add(definitions, RETURN_VISITS_DELETE, "Delete return visits", true, true);
        add(definitions, RETURN_VISITS_ASSIGN, "Assign return visits", true, true);
        add(definitions, WORKLIST_READ, "Read local worklist", true, false);
        add(definitions, WORK_TASKS_READ, "Read participating work tasks", true, false);
        add(definitions, WORK_TASKS_WRITE, "Create and progress work tasks", true, false);
        add(definitions, WORK_TASKS_MANAGE, "Manage tenant work tasks", true, false);
        add(definitions, NOTIFICATIONS_READ, "Read own in-app notifications", true, false);
        add(definitions, FILES_READ, "Read target attachments", true, false);
        add(definitions, FILES_WRITE, "Collaborate on target attachments", true, false);
        add(definitions, CONNECTORS_READ, "Read connector registrations", true, false);
        add(definitions, CONNECTORS_MANAGE, "Manage connector registrations", true, false);
        add(definitions, APPROVALS_READ, "Read approvals", true, false);
        add(definitions, APPROVALS_SUBMIT, "Submit approvals", true, false);
        add(definitions, APPROVALS_REVIEW, "Review approvals", true, false);
        add(definitions, APPROVALS_MANAGE, "Manage approval definitions", true, false);
        add(definitions, WORKFLOWS_READ, "Read stage workflows", true, false);
        add(definitions, WORKFLOWS_WRITE, "Advance stage workflows", true, false);
        add(definitions, WORKFLOWS_MANAGE, "Manage stage workflows", true, false);
        return Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
    }

    private static void add(Map<AuthorityCode, AuthorityDefinition> definitions, String code,
                            String displayName, boolean tenantAssignable, boolean supportsDataScope) {
        AuthorityCode authorityCode = new AuthorityCode(code);
        definitions.put(authorityCode,
                new AuthorityDefinition(authorityCode, displayName, tenantAssignable, supportsDataScope));
    }
}
