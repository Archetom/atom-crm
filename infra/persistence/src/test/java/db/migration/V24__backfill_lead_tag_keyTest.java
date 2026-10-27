package db.migration;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class V24__backfill_lead_tag_keyTest {

    @Test
    void noChangeUpdateIsAllowedButMissingDeleteIsRejected() {
        assertDoesNotThrow(() -> V24__backfill_lead_tag_key.requireUpdatedAtMostOneRow(0));
        assertDoesNotThrow(() -> V24__backfill_lead_tag_key.requireUpdatedAtMostOneRow(1));
        assertThrows(SQLException.class,
                () -> V24__backfill_lead_tag_key.requireDeletedSingleRow(0));
        assertDoesNotThrow(() -> V24__backfill_lead_tag_key.requireDeletedSingleRow(1));
    }
}
