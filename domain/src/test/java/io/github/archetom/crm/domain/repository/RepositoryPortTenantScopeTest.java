package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the rule from AGENTS.md: "Every repository and cache operation after identity resolution is
 * tenant scoped and fails when tenant is absent."
 *
 * <p>Cross-tenant isolation is otherwise only exercised by the Testcontainers suite, which is
 * skipped whenever Docker is absent. A port method that forgets its tenant is a compile-time fact,
 * so it can be caught in the fast suite: every method must either take a {@link TenantId} or take a
 * type that carries one, and the few genuine exceptions are listed here with their reason. Adding a
 * new unscoped method therefore fails immediately and forces that reason to be written down.</p>
 *
 * <p>Both halves of the rule are stated by allow-list rather than by naming convention, because a
 * convention only covers the ports someone remembered to name that way. Which <em>types</em> are
 * ports is decided by {@code isInterface}, so a port that does not end in {@code Repository} is
 * still checked; which <em>methods</em> are exempt is keyed by full signature, so an overload
 * cannot inherit the reason written for its sibling.</p>
 */
class RepositoryPortTenantScopeTest {

    private static final String PORT_PACKAGE = "io.github.archetom.crm.domain.repository";

    /**
     * Operations that legitimately carry no tenant. Each is either global data, a background sweep
     * that derives its tenant from the row it claims, or a lookup keyed by the verified caller.
     *
     * <p>Keys carry the parameter list because a reason is written about one signature and holds
     * only for that signature. A bare method name would hand the same exemption to a later overload
     * whose arguments nobody weighed.</p>
     */
    private static final Map<String, String> UNSCOPED_BY_DESIGN = Map.ofEntries(
            Map.entry("MemberProfileRepository#save(MemberProfile)",
                    "MemberProfile is global data bound to the verified actor,"
                            + " not a tenant-owned aggregate"),
            Map.entry("MemberProfileRepository#findById(MemberId)",
                    "MemberProfile is global data bound to the verified actor,"
                            + " not a tenant-owned aggregate"),
            Map.entry("OrganizationMembershipRepository#findActiveByMember(MemberId)",
                    "resolves which tenants an actor belongs to, so it cannot presuppose one"),
            Map.entry("MembershipRequestRepository#findByIdForApplicant"
                            + "(MemberId,MembershipRequestId)",
                    "keyed by the verified applicant; a request names a target tenant"
                            + " it does not yet belong to"),
            Map.entry("MembershipRequestRepository#findByApplicant(MemberId,int,int)",
                    "keyed by the verified applicant; a request names a target tenant"
                            + " it does not yet belong to"),
            Map.entry("NotificationRepository#purgeExpired(LocalDateTime,int)",
                    "retention sweep across tenants; it reads no business data and returns none"),
            Map.entry("CrmImportDraftRepository#findDueActive(LocalDateTime,int)",
                    "background due scan; each claimed draft carries its own tenant"
                            + " before any business read"),
            Map.entry("WorkTaskReminderRepository#claimDue(UUID,LocalDateTime,Duration,int)",
                    "background claim; the claim result carries the tenant"
                            + " used by every later operation"),
            Map.entry("WorkTaskReminderRepository#currentUtc()",
                    "reads the shared database clock and touches no tenant data"),
            Map.entry("WorkTaskReminderRepository#purgeTerminal(LocalDateTime,int)",
                    "retention sweep across tenants; it reads no business data and returns none"));

    /**
     * Types in the port package that declare no operation, so there is nothing to scope. Naming a
     * carrier here is the cost of adding one; the alternative — trusting a {@code Repository} name
     * suffix — silently exempts any future port that is named something else.
     */
    private static final Map<String, String> NOT_A_PORT = Map.of(
            "PageResult",
            "page carrier returned by port queries; its accessors read an already-fetched page",
            "FileFieldRepository$StagedFile",
            "record returned by this port's staged-file queries, each of which takes the tenant",
            "FileFieldRepository$BoundFile",
            "record returned by this port's bound-value queries, each of which takes the tenant",
            "MarketingFileDraftRepository$StagedFile",
            "record returned by this port's staged-file queries, each of which takes the tenant",
            "ProductImageDraftRepository$StagedFile",
            "record passed to and returned by this port's staged-file operations,"
                    + " each of which takes the tenant");

    @Test
    void everyRepositoryPortOperationIsTenantScoped() {
        List<Class<?>> ports = repositoryPorts();
        assertTrue(ports.size() >= 60,
                () -> "expected the compiled repository ports, found " + ports.size());

        List<String> unscoped = new ArrayList<>();
        for (Class<?> port : ports) {
            for (Method method : port.getDeclaredMethods()) {
                // Default methods are checked too: they are ordinary port operations, and every
                // one of them already takes a tenant, so skipping them only hid future ones.
                if (method.isSynthetic()) {
                    continue;
                }
                String id = signature(port, method);
                if (UNSCOPED_BY_DESIGN.containsKey(id) || carriesTenant(method)) {
                    continue;
                }
                unscoped.add(id + " takes no TenantId and no argument that carries one;"
                        + " add the tenant, or record why it is exempt in UNSCOPED_BY_DESIGN");
            }
        }
        assertTrue(unscoped.isEmpty(), () -> String.join("\n", unscoped));
    }

    /**
     * The port check reaches a type only by deciding that it is a port. That decision used to be a
     * name suffix, which let a port named anything else past without a single method being read.
     * Every type compiled into the package is therefore accounted for here: an interface is a port
     * and is checked above, and anything else has to say why it holds no operation.
     */
    @Test
    void everyTypeInThePortPackageIsAPortOrARecordedCarrier() {
        List<Class<?>> types = portPackageTypes();
        assertTrue(types.size() >= 60,
                () -> "expected the compiled port package, found " + types.size());

        List<String> unclassified = types.stream()
                .filter(type -> !type.isInterface())
                .map(RepositoryPortTenantScopeTest::binaryName)
                .filter(name -> !NOT_A_PORT.containsKey(name))
                .sorted()
                .toList();
        assertEquals(List.of(), unclassified,
                "types in the port package that are neither a port nor a recorded carrier;"
                        + " make it an interface, move it out, or record it in NOT_A_PORT");
    }

    @Test
    void everyRecordedExemptionStillExists() {
        Set<String> declaredTypes = new HashSet<>();
        Set<String> declaredMethods = new HashSet<>();
        for (Class<?> type : portPackageTypes()) {
            declaredTypes.add(binaryName(type));
            // Only a port method can be exempt from the port check, so a reason written for
            // anything else is already stale even while the carrier it names still compiles.
            if (type.isInterface()) {
                for (Method method : type.getDeclaredMethods()) {
                    declaredMethods.add(signature(type, method));
                }
            }
        }
        // A stale exemption silently widens the rule for a method or type that no longer exists.
        List<String> stale = Stream.concat(
                        UNSCOPED_BY_DESIGN.keySet().stream().filter(id -> !declaredMethods.contains(id)),
                        NOT_A_PORT.keySet().stream().filter(name -> !declaredTypes.contains(name)))
                .sorted()
                .toList();
        assertEquals(List.of(), stale, "exemptions that no longer match a port package declaration");
    }

    /**
     * Identifies the exact overload, so a reason recorded for one signature never covers another.
     * The owner is its binary name, so a port nested in one interface cannot share a key with a
     * same-named port nested in a different one.
     */
    private static String signature(Class<?> owner, Method method) {
        StringBuilder id = new StringBuilder(binaryName(owner))
                .append('#').append(method.getName()).append('(');
        Class<?>[] parameters = method.getParameterTypes();
        for (int index = 0; index < parameters.length; index++) {
            if (index > 0) {
                id.append(',');
            }
            id.append(parameters[index].getSimpleName());
        }
        return id.append(')').toString();
    }

    private static String binaryName(Class<?> type) {
        return type.getName().substring(PORT_PACKAGE.length() + 1);
    }

    private static boolean carriesTenant(Method method) {
        for (Type parameter : method.getGenericParameterTypes()) {
            if (typeCarriesTenant(parameter)) {
                return true;
            }
        }
        return false;
    }

    private static boolean typeCarriesTenant(Type type) {
        if (type instanceof ParameterizedType parameterized) {
            for (Type argument : parameterized.getActualTypeArguments()) {
                if (typeCarriesTenant(argument)) {
                    return true;
                }
            }
            return typeCarriesTenant(parameterized.getRawType());
        }
        if (!(type instanceof Class<?> candidate)) {
            return false;
        }
        if (TenantId.class.equals(candidate)) {
            return true;
        }
        // One level is enough: a tenant travels either as its own argument or as a field of the
        // aggregate, membership record, or query object being passed.
        for (Field field : candidate.getDeclaredFields()) {
            if (TenantId.class.equals(field.getType())) {
                return true;
            }
        }
        return false;
    }

    private static List<Class<?>> repositoryPorts() {
        return portPackageTypes().stream().filter(Class::isInterface).toList();
    }

    /**
     * Every type the package compiles to, nested ones included: a port nested inside another port
     * is still a port, and a carrier nested inside one still has to say that it is a carrier.
     */
    private static List<Class<?>> portPackageTypes() {
        Path root = Path.of("target/classes/" + PORT_PACKAGE.replace('.', '/'));
        List<Class<?>> types = new ArrayList<>();
        try (Stream<Path> files = Files.list(root)) {
            for (Path file : files.sorted().toList()) {
                String name = file.getFileName().toString();
                // package-info holds the package's annotations and declares no type.
                if (!name.endsWith(".class") || name.equals("package-info.class")) {
                    continue;
                }
                types.add(Class.forName(
                        PORT_PACKAGE + "." + name.substring(0, name.length() - ".class".length())));
            }
        } catch (Exception exception) {
            throw new IllegalStateException("repository ports are unavailable", exception);
        }
        return types;
    }
}
