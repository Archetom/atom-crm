package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MyBatis-Plus defaults to {@link FieldStrategy#NOT_NULL}, which drops null fields from the
 * generated {@code SET} clause. When an aggregate clears an optional value the converter writes
 * the null correctly, the ORM discards it, and the previous value survives the update — a silent
 * data-integrity failure rather than an error.
 *
 * <p>These are the root columns whose owning aggregate can genuinely clear a stored value, so
 * each must opt into {@link FieldStrategy#ALWAYS}. Insert-only and counter columns are
 * deliberately out of scope: they are never written back as null.</p>
 */
class ClearableColumnUpdateStrategyTest {

    /**
     * {@code Customer.enterPool()} clears the owner, and {@code Customer.applyDetails(...)} clears
     * every optional detail. {@code MarketingActivity} unbinds its form and clears optional
     * campaign attributes.
     */
    private static final Map<Class<?>, Set<String>> CLEARABLE_COLUMNS = Map.of(
            CustomerPO.class, Set.of(
                    "ownerActorId", "parentCustomerId", "phoneNumber", "telephone",
                    "email", "website", "description", "dealTime"),
            MarketingActivityPO.class, Set.of(
                    "marketingFormId", "activityType", "budget", "address", "description"));

    @Test
    void everyClearableColumnSurvivesAnUpdateAsNull() {
        List<String> violations = new ArrayList<>();
        CLEARABLE_COLUMNS.forEach((po, fields) -> fields.forEach(name -> {
            Field field;
            try {
                field = po.getDeclaredField(name);
            } catch (NoSuchFieldException exception) {
                violations.add(po.getSimpleName() + " no longer declares " + name);
                return;
            }
            TableField annotation = field.getAnnotation(TableField.class);
            if (annotation == null || annotation.updateStrategy() != FieldStrategy.ALWAYS) {
                violations.add(po.getSimpleName() + "." + name
                        + " must declare updateStrategy = FieldStrategy.ALWAYS,"
                        + " otherwise clearing it is silently ignored");
            }
        }));

        assertTrue(violations.isEmpty(),
                () -> "Clearable columns an update cannot null out:\n" + String.join("\n", violations));
    }
}
