package io.github.archetom.crm.application.reference;

import io.github.archetom.crm.application.vo.NavigationItemVO;
import io.github.archetom.crm.domain.entity.NavigationPreference;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Code-defined navigation reference catalog; it is not an authorization source. */
@Component
public class NavigationCatalog {

    private static final List<Item> ITEMS = List.of(
            new Item("workspace", null, null, "navigation.workspace", 10, null),
            new Item("workspace.home", "workspace", "/", "navigation.home", 10,
                    AuthorityCatalog.NAVIGATION_READ),
            new Item("crm", null, null, "navigation.crm", 15, null),
            new Item("crm.customers", "crm", "/crm/customers", "navigation.customers", 10,
                    AuthorityCatalog.CUSTOMERS_READ),
            new Item("crm.customer-pools", "crm", "/crm/customer-pools", "navigation.customerPools", 20,
                    AuthorityCatalog.CUSTOMER_POOLS_READ),
            new Item("crm.leads", "crm", "/crm/leads", "navigation.leads", 25,
                    AuthorityCatalog.LEADS_READ),
            new Item("crm.contacts", "crm", "/crm/contacts", "navigation.contacts", 30,
                    AuthorityCatalog.CONTACTS_READ),
            new Item("crm.contact-pools", "crm", "/crm/contact-pools", "navigation.contactPools", 40,
                    AuthorityCatalog.CONTACT_POOLS_READ),
            new Item("crm.opportunities", "crm", "/crm/opportunities", "navigation.opportunities", 50,
                    AuthorityCatalog.OPPORTUNITIES_READ),
            new Item("crm.products", "crm", "/crm/products", "navigation.products", 60,
                    AuthorityCatalog.PRODUCTS_READ),
            new Item("crm.marketing-activities", "crm", "/crm/marketing-activities",
                    "navigation.marketingActivities", 70,
                    AuthorityCatalog.MARKETING_ACTIVITIES_READ),
            new Item("marketing", null, null, "navigation.marketing", 18, null),
            new Item("marketing.forms", "marketing", "/marketing/forms", "navigation.marketingForms", 10,
                    AuthorityCatalog.MARKETING_FORMS_READ),
            new Item("oa", null, null, "navigation.oa", 19, null),
            new Item("oa.forms", "oa", "/oa/forms", "navigation.oaForms", 10,
                    AuthorityCatalog.OA_FORMS_READ),
            new Item("oa.requests", "oa", "/oa/requests", "navigation.oaRequests", 20,
                    AuthorityCatalog.OA_REQUESTS_READ),
            new Item("oa.announcements", "oa", "/oa/announcements", "navigation.oaAnnouncements", 30,
                    AuthorityCatalog.OA_ANNOUNCEMENTS_READ),
            new Item("reports", null, "/reports", "navigation.reports", 19,
                    AuthorityCatalog.REPORTS_READ),
            new Item("organization", null, null, "navigation.organization", 20, null),
            new Item("organization.tenants", "organization", "/organization/tenants",
                    "navigation.tenants", 5, AuthorityCatalog.ORGANIZATION_REQUEST),
            new Item("organization.departments", "organization", "/organization/departments",
                    "navigation.departments", 10, AuthorityCatalog.ORGANIZATION_READ),
            new Item("organization.members", "organization", "/organization/members",
                    "navigation.members", 20, AuthorityCatalog.ORGANIZATION_READ),
            new Item("administration", null, null, "navigation.administration", 30, null),
            new Item("administration.users", "administration", "/administration/users",
                    "navigation.users", 10, AuthorityCatalog.USERS_READ),
            new Item("administration.roles", "administration", "/administration/roles",
                    "navigation.roles", 20, AuthorityCatalog.AUTHORIZATION_READ),
            new Item("administration.fields", "administration", "/administration/fields",
                    "navigation.fields", 30, AuthorityCatalog.FIELDS_READ),
            new Item("administration.performance-targets", "administration",
                    "/administration/performance-targets", "navigation.performanceTargets", 35,
                    AuthorityCatalog.DASHBOARD_TARGETS_READ),
            new Item("administration.embedded-pages", "administration", "/administration/embedded-pages",
                    "navigation.embeddedPages", 40, AuthorityCatalog.EMBEDDED_PAGES_READ),
            new Item("workflow", null, null, "navigation.workflow", 35, null),
            new Item("workflow.definitions", "workflow", "/workflows",
                    "navigation.workflowDefinitions", 10, AuthorityCatalog.WORKFLOWS_READ));

    public Set<String> codes() {
        return ITEMS.stream().map(Item::code).collect(Collectors.toUnmodifiableSet());
    }

    public List<NavigationItemVO> catalogTree() {
        return buildTree(Set.of(), null, false);
    }

    public List<NavigationItemVO> visibleTree(Set<String> authorities, NavigationPreference preference) {
        return buildTree(authorities == null ? Set.of() : Set.copyOf(authorities), preference, true);
    }

    private List<NavigationItemVO> buildTree(Set<String> authorities, NavigationPreference preference,
                                              boolean filterVisibility) {
        Map<String, Integer> preferenceOrder = preferenceOrder(preference);
        Set<String> hidden = preference == null ? Set.of() : preference.getHiddenCodes();
        Map<String, List<Item>> children = ITEMS.stream().collect(Collectors.groupingBy(
                item -> item.parentCode() == null ? "" : item.parentCode(), LinkedHashMap::new,
                Collectors.toCollection(ArrayList::new)));
        Comparator<Item> comparator = Comparator
                .comparingInt((Item item) -> preferenceOrder.getOrDefault(item.code(), Integer.MAX_VALUE))
                .thenComparingInt(Item::order)
                .thenComparing(Item::code);
        children.values().forEach(items -> items.sort(comparator));
        return children.getOrDefault("", List.of()).stream()
                .map(item -> toNode(item, children, authorities, hidden, filterVisibility))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private NavigationItemVO toNode(Item item, Map<String, List<Item>> children, Set<String> authorities,
                                    Set<String> hidden, boolean filterVisibility) {
        if (filterVisibility && hidden.contains(item.code())) {
            return null;
        }
        List<NavigationItemVO> visibleChildren = children.getOrDefault(item.code(), List.of()).stream()
                .map(child -> toNode(child, children, authorities, hidden, filterVisibility))
                .filter(java.util.Objects::nonNull)
                .toList();
        boolean directory = item.route() == null;
        if (filterVisibility) {
            if (directory && visibleChildren.isEmpty()) {
                return null;
            }
            if (!directory && item.requiredAuthority() != null
                    && !authorities.contains(item.requiredAuthority())) {
                return null;
            }
        }
        return new NavigationItemVO(item.code(), item.parentCode(), item.route(), item.titleKey(), item.order(),
                item.requiredAuthority(), visibleChildren);
    }

    private Map<String, Integer> preferenceOrder(NavigationPreference preference) {
        if (preference == null) {
            return Map.of();
        }
        return java.util.stream.IntStream.range(0, preference.getOrderedCodes().size()).boxed()
                .collect(Collectors.toMap(index -> preference.getOrderedCodes().get(index),
                        Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    private record Item(String code, String parentCode, String route, String titleKey, int order,
                        String requiredAuthority) {
    }
}
