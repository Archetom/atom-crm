package io.github.archetom.crm.infra.external.file;

import io.github.archetom.crm.infra.external.file.SafeFileComposedFixture.AbsenceEvidence;
import io.github.archetom.crm.infra.external.file.SafeFileComposedFixture.ByteState;
import io.github.archetom.crm.infra.external.file.SafeFileComposedFixture.CoreSnapshot;
import io.github.archetom.crm.infra.external.file.SafeFileComposedFixture.DeletionClaim;
import io.github.archetom.crm.infra.external.file.SafeFileComposedFixture.DownloadHandle;
import io.github.archetom.crm.infra.external.file.SafeFileComposedFixture.FileRef;
import io.github.archetom.crm.infra.external.file.SafeFileComposedFixture.InspectionClaim;
import io.github.archetom.crm.infra.external.file.SafeFileComposedFixture.InspectionProfile;
import io.github.archetom.crm.infra.external.file.SafeFileComposedFixture.InspectionState;
import io.github.archetom.crm.infra.external.file.SafeFileComposedFixture.IntentResult;
import io.github.archetom.crm.infra.external.file.SafeFileComposedFixture.Lane;
import io.github.archetom.crm.infra.external.file.SafeFileComposedFixture.NativeInspectionEvidence;
import io.github.archetom.crm.infra.external.file.SafeFileComposedFixture.PublicationState;
import io.github.archetom.crm.infra.external.file.SafeFileComposedFixture.RangeMode;
import io.github.archetom.crm.infra.external.file.SafeFileComposedFixture.SafeOutcome;
import io.github.archetom.crm.infra.external.file.SafeFileComposedFixture.SafeReport;
import io.github.archetom.crm.infra.external.file.SafeFileComposedFixture.ScannerObservation;
import io.github.archetom.crm.infra.external.file.SafeFileComposedFixture.StoreResult;
import io.github.archetom.crm.infra.external.file.SafeFileComposedFixture.StreamResult;
import io.github.archetom.crm.infra.external.file.SafeFileComposedFixture.TargetRef;
import io.github.archetom.crm.infra.external.file.SafeFileComposedFixture.Tenant;
import io.github.archetom.crm.infra.external.file.SafeFileComposedFixture.TenantCounters;
import io.github.archetom.crm.infra.external.file.SafeFileComposedFixture.TypeObservation;
import io.github.archetom.crm.infra.external.file.SafeFileComposedFixture.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Executable model layer for the B24-X/1 X-01..X-08 composed fixture.
 *
 * <p>All content and native results are synthetic. Passing these tests is not MySQL,
 * filesystem, object-storage, scanner or production-readiness evidence.</p>
 */
class SafeFileComposedFixtureTest {

    @Test
    void x01BindsEqualTenantInputsToIndependentTargetsLocatorsAndReadiness() {
        World world = World.create(Lane.XS, RangeMode.NATIVE_SINGLE_RANGE);

        IntentResult alpha = world.createIntent(Tenant.A);
        IntentResult beta = world.createIntent(Tenant.B);

        assertEquals(SafeOutcome.ACCEPTED, alpha.outcome());
        assertEquals(SafeOutcome.ACCEPTED, beta.outcome());
        assertNotEquals(world.snapshot(alpha.file()).plannedIdentityDigest(),
                world.snapshot(beta.file()).plannedIdentityDigest());
        assertEquals(ByteState.ABSENT, world.snapshot(alpha.file()).bytes());
        assertEquals(ByteState.ABSENT, world.snapshot(beta.file()).bytes());
        assertEquals(0, world.storageCounters(Tenant.A).storeAttempts());
        assertEquals(0, world.storageCounters(Tenant.B).storeAttempts());

        int fileReadsBeforeCrossTenant = world.coreFileReads();
        assertEquals(SafeOutcome.NOT_FOUND,
                world.createIntentAs(Tenant.A, world.defaultTarget(Tenant.B)).outcome());
        assertEquals(fileReadsBeforeCrossTenant, world.coreFileReads());
        assertEquals(0, world.storageCounters(Tenant.A).storeAttempts());

        world.setStorageReady(Tenant.A, false);
        assertEquals(SafeOutcome.NOT_READY, world.createIntent(Tenant.A).outcome());
        assertEquals(SafeOutcome.ACCEPTED, world.createIntent(Tenant.B).outcome());

        World local = World.create(Lane.XL, RangeMode.NATIVE_SINGLE_RANGE);
        assertEquals(SafeOutcome.UNSUPPORTED,
                local.inspectionCompatibility(InspectionProfile.MANAGED_EVENT));
        assertEquals(0, local.inspectionCounters(Tenant.A).opens());
        assertCleanReport(world.report());
    }

    @Test
    void x02RecoversStoreEffectFinalizeRollbackAndCommitAckLossWithoutSecondObject() {
        World world = World.create(Lane.XS, RangeMode.NATIVE_SINGLE_RANGE);
        FileRef file = acceptedIntent(world, Tenant.A);
        FileRef betaFile = acceptedIntent(world, Tenant.B);
        CoreSnapshot betaBefore = world.snapshot(betaFile);

        StoreResult lostReceipt = world.store(file, SafeFileComposedFixture.benignBytes(), true);
        assertEquals(SafeOutcome.CRASHED, lostReceipt.outcome());
        assertEquals(ByteState.UPLOADING, world.snapshot(file).bytes());
        assertEquals(1, world.storageCounters(Tenant.A).storeEffects());

        world.restartStorage();
        StoreResult reconciled = world.reconcileStore(file);
        assertEquals(SafeOutcome.ACCEPTED, reconciled.outcome());
        assertNotNull(reconciled.receipt());
        assertEquals(ByteState.UPLOADING, world.snapshot(file).bytes());

        assertEquals(SafeOutcome.CRASHED,
                world.finalizeStored(file, reconciled.receipt(), true));
        CoreSnapshot committed = world.snapshot(file);
        assertEquals(ByteState.STORED, committed.bytes());
        assertEquals(InspectionState.PENDING, committed.inspection());
        assertNotNull(committed.inspectionJobId());
        assertEquals(PublicationState.PRIVATE, committed.publication());

        world.restartCore();
        long versionAfterCommit = world.snapshot(file).version();
        assertEquals(SafeOutcome.ACCEPTED,
                world.finalizeStored(file, reconciled.receipt(), false));
        assertEquals(versionAfterCommit, world.snapshot(file).version());
        assertEquals(1, world.storageCounters(Tenant.A).storeEffects());
        assertEquals(betaBefore, world.snapshot(betaFile));
        assertCleanReport(world.report());
    }

    @ParameterizedTest
    @EnumSource(Lane.class)
    void x03CommitsInspectionOnlyFromExactDurableEvidenceAfterScannerRestart(Lane lane) {
        World world = World.create(lane, RangeMode.NATIVE_SINGLE_RANGE);
        FileRef file = stored(world, Tenant.A);
        InspectionClaim claim = world.claimInspection(file);
        assertNotNull(claim);

        assertNull(world.driveInspection(claim, TypeObservation.ALLOWED_PDF,
                ScannerObservation.NO_THREAT_DETECTED, true, true));
        assertEquals(InspectionState.PENDING, world.snapshot(file).inspection());

        world.restartInspector();
        NativeInspectionEvidence replayed = world.reconcileInspection(claim);
        assertNotNull(replayed);
        assertEquals(SafeOutcome.OK, world.commitInspection(replayed));
        assertEquals(InspectionState.CLEAN, world.snapshot(file).inspection());
        assertEquals(PublicationState.PRIVATE, world.snapshot(file).publication());
        assertNotNull(world.snapshot(file).publicationWorkId());

        NativeInspectionEvidence foreign = new NativeInspectionEvidence(file,
                claim.attemptId(), claim.fence(), Tenant.B.inspectionRevision,
                replayed.contentDigest(), TypeObservation.ALLOWED_PDF,
                ScannerObservation.NO_THREAT_DETECTED, true,
                SafeFileComposedFixture.START);
        assertEquals(SafeOutcome.STALE, world.commitInspection(foreign));
        assertEquals(InspectionState.CLEAN, world.snapshot(file).inspection());
        assertEquals(0, world.inspectionCounters(Tenant.A).activeAdmissions());
        assertEquals(0, world.storageCounters(Tenant.A).activeHandles());
        assertCleanReport(world.report());
    }

    @Test
    void x03MapsThreatAndUnknownEvidenceFailClosedAndRejectsAnOldFence() {
        World threatWorld = World.create(Lane.XM, RangeMode.FULL_ONLY);
        FileRef threatFile = stored(threatWorld, Tenant.A);
        InspectionClaim first = threatWorld.claimInspection(threatFile);
        InspectionClaim takeover = threatWorld.claimInspection(threatFile);
        NativeInspectionEvidence stale = threatWorld.driveInspection(first,
                TypeObservation.ALLOWED_PDF, ScannerObservation.NO_THREAT_DETECTED,
                true, false);
        assertEquals(SafeOutcome.STALE, threatWorld.commitInspection(stale));

        NativeInspectionEvidence threat = threatWorld.driveInspection(takeover,
                TypeObservation.ALLOWED_PDF, ScannerObservation.THREAT_DETECTED,
                true, false);
        assertEquals(SafeOutcome.OK, threatWorld.commitInspection(threat));
        assertEquals(InspectionState.REJECTED,
                threatWorld.snapshot(threatFile).inspection());
        assertNull(threatWorld.snapshot(threatFile).publicationWorkId());

        World unknownWorld = World.create(Lane.XL, RangeMode.FULL_ONLY);
        FileRef unknownFile = stored(unknownWorld, Tenant.B);
        InspectionClaim unknownClaim = unknownWorld.claimInspection(unknownFile);
        NativeInspectionEvidence unknown = unknownWorld.driveInspection(unknownClaim,
                TypeObservation.UNKNOWN, ScannerObservation.UNSUPPORTED,
                false, false);
        assertEquals(SafeOutcome.OK, unknownWorld.commitInspection(unknown));
        assertEquals(InspectionState.INDETERMINATE,
                unknownWorld.snapshot(unknownFile).inspection());
        assertEquals(PublicationState.PRIVATE,
                unknownWorld.snapshot(unknownFile).publication());
        assertCleanReport(threatWorld.report());
        assertCleanReport(unknownWorld.report());
    }

    @Test
    void x04SerializesCleanPublicationWithdrawalParentDeleteAndLateThreat() {
        World world = World.create(Lane.XS, RangeMode.NATIVE_SINGLE_RANGE);
        Published published = published(world, Tenant.A);
        assertEquals(PublicationState.AVAILABLE,
                world.snapshot(published.file()).publication());
        assertEquals(1, world.snapshot(published.file()).publicationWrites());
        assertEquals(SafeOutcome.PENDING, world.runPublisher(published.file()));
        assertEquals(1, world.snapshot(published.file()).publicationWrites());

        NativeInspectionEvidence lateThreat = world.driveInspection(published.claim(),
                TypeObservation.ALLOWED_PDF, ScannerObservation.THREAT_DETECTED,
                true, false);
        assertEquals(SafeOutcome.OK, world.commitInspection(lateThreat));
        CoreSnapshot rejected = world.snapshot(published.file());
        assertEquals(InspectionState.REJECTED, rejected.inspection());
        assertEquals(PublicationState.WITHDRAWN, rejected.publication());
        assertEquals(ByteState.REMOVING, rejected.bytes());
        assertNotNull(rejected.deletionWorkId());

        World withdrawRace = World.create(Lane.XL, RangeMode.FULL_ONLY);
        Clean clean = clean(withdrawRace, Tenant.A);
        assertEquals(SafeOutcome.OK, withdrawRace.withdraw(Tenant.A,
                withdrawRace.defaultTarget(Tenant.A), clean.file()));
        assertEquals(SafeOutcome.PENDING, withdrawRace.runPublisher(clean.file()));
        assertEquals(0, withdrawRace.snapshot(clean.file()).publicationWrites());

        World parentRace = World.create(Lane.XM, RangeMode.FULL_ONLY);
        Clean cleanBeforeDelete = clean(parentRace, Tenant.B);
        assertEquals(SafeOutcome.OK,
                parentRace.deleteParent(parentRace.defaultTarget(Tenant.B)));
        assertEquals(SafeOutcome.NOT_FOUND,
                parentRace.runPublisher(cleanBeforeDelete.file()));
        assertEquals(PublicationState.DELETED,
                parentRace.snapshot(cleanBeforeDelete.file()).publication());
        assertEquals(0, parentRace.snapshot(cleanBeforeDelete.file()).publicationWrites());
        assertCleanReport(world.report());
        assertCleanReport(withdrawRace.report());
        assertCleanReport(parentRace.report());
    }

    @Test
    void x05NormalizesNativeSingleRangesAndNeverOpensInvalidOrUnsupportedRequests() {
        World world = World.create(Lane.XS, RangeMode.NATIVE_SINGLE_RANGE);
        FileRef file = published(world, Tenant.A).file();
        TargetRef target = world.defaultTarget(Tenant.A);

        List<ExpectedRange> ranges = List.of(
                new ExpectedRange("bytes=512-1535", 512, 1024),
                new ExpectedRange("bytes=3584-", 3584, 512),
                new ExpectedRange("bytes=-256", 3840, 256),
                new ExpectedRange("bytes=0-8191", 0, 4096));
        for (ExpectedRange expected : ranges) {
            StreamResult response = world.requestRange(Tenant.A, target, file,
                    expected.header());
            assertEquals(SafeOutcome.OK, response.outcome());
            assertEquals(206, response.httpStatus());
            assertEquals(expected.offset(), response.offset());
            assertEquals(expected.length(), response.length());
            assertNotNull(response.bodyDigest());
        }

        TenantCounters beforeInvalid = world.storageCounters(Tenant.A);
        for (String invalid : List.of("bytes=4096-", "bytes=0-1,3-4", "bytes=a-b",
                "bytes=7-3", "bytes=-0", "bytes=9223372036854775808-")) {
            StreamResult response = world.requestRange(Tenant.A, target, file, invalid);
            assertEquals(SafeOutcome.RANGE_NOT_SATISFIABLE, response.outcome());
            assertEquals(416, response.httpStatus());
        }
        assertEquals(beforeInvalid.rangeOpens(),
                world.storageCounters(Tenant.A).rangeOpens());
        assertEquals(0, world.storageCounters(Tenant.A).invalidRangeOpens());
        assertEquals(5888, world.storageCounters(Tenant.A).upstreamBytes());

        World fullOnly = World.create(Lane.XL, RangeMode.FULL_ONLY);
        FileRef fullOnlyFile = published(fullOnly, Tenant.A).file();
        assertEquals(SafeOutcome.RANGE_NOT_SATISFIABLE,
                fullOnly.requestRange(Tenant.A, fullOnly.defaultTarget(Tenant.A),
                        fullOnlyFile, "bytes=0-9").outcome());
        assertEquals(0, fullOnly.storageCounters(Tenant.A).rangeOpens());

        world.setFilesRead(Tenant.A, false);
        int opensBeforeDenied = world.storageCounters(Tenant.A).fullOpens();
        assertEquals(SafeOutcome.NOT_FOUND,
                world.requestFull(Tenant.A, target, file).outcome());
        assertEquals(opensBeforeDenied, world.storageCounters(Tenant.A).fullOpens());
        world.setFilesRead(Tenant.A, true);
        assertEquals(SafeOutcome.OK, world.requestFull(Tenant.A, target, file).outcome());
        assertEquals(0, world.storageCounters(Tenant.A).activeHandles());
        assertEquals(0, world.storageCounters(Tenant.A).transparentReopens());
        assertCleanReport(world.report());
        assertCleanReport(fullOnly.report());
    }

    @Test
    void x06ReauthorizesTaskCustomerPoolAndParentLifecycleWithoutDeletingOnAccessLoss() {
        World world = World.create(Lane.XS, RangeMode.NATIVE_SINGLE_RANGE);
        FileRef workFile = published(world, Tenant.A).file();
        TargetRef task = world.defaultTarget(Tenant.A);
        assertTrue(world.targetHasHiddenCrmLink(task));
        assertEquals(SafeOutcome.OK, world.listAttachments(Tenant.A, task));

        int deleteEffectsBeforeLoss = world.storageCounters(Tenant.A).deleteEffects();
        world.setTargetRead(Tenant.A, task, false);
        assertEquals(SafeOutcome.NOT_FOUND, world.listAttachments(Tenant.A, task));
        assertEquals(SafeOutcome.NOT_FOUND,
                world.requestFull(Tenant.A, task, workFile).outcome());
        assertEquals(deleteEffectsBeforeLoss,
                world.storageCounters(Tenant.A).deleteEffects());
        world.setTargetRead(Tenant.A, task, true);
        assertEquals(SafeOutcome.OK,
                world.requestFull(Tenant.A, task, workFile).outcome());
        assertEquals(SafeOutcome.CONFLICT, world.deleteTask(task));

        FileRef customerFile = published(world, Tenant.B).file();
        TargetRef customer = world.defaultTarget(Tenant.B);
        int customerDeleteEffects = world.storageCounters(Tenant.B).deleteEffects();
        world.enterPool(customer);
        assertEquals(SafeOutcome.NOT_FOUND,
                world.requestFull(Tenant.B, customer, customerFile).outcome());
        assertEquals(SafeOutcome.NOT_FOUND, world.listAttachments(Tenant.B, customer));
        assertEquals(customerDeleteEffects,
                world.storageCounters(Tenant.B).deleteEffects());
        world.restoreFromPool(customer);
        assertEquals(SafeOutcome.OK,
                world.requestFull(Tenant.B, customer, customerFile).outcome());

        assertEquals(SafeOutcome.OK, world.deleteParent(customer));
        assertEquals(PublicationState.DELETED,
                world.snapshot(customerFile).publication());
        assertEquals(ByteState.REMOVING, world.snapshot(customerFile).bytes());
        assertEquals(SafeOutcome.NOT_FOUND,
                world.requestFull(Tenant.B, customer, customerFile).outcome());

        World acceptedWork = World.create(Lane.XM, RangeMode.FULL_ONLY);
        FileRef acceptedFile = stored(acceptedWork, Tenant.A);
        InspectionClaim acceptedClaim = acceptedWork.claimInspection(acceptedFile);
        NativeInspectionEvidence acceptedEvidence = acceptedWork.driveInspection(
                acceptedClaim, TypeObservation.ALLOWED_PDF,
                ScannerObservation.NO_THREAT_DETECTED, true, false);
        acceptedWork.setFilesWrite(Tenant.A, false);
        assertEquals(SafeOutcome.OK,
                acceptedWork.commitInspection(acceptedEvidence));
        assertEquals(SafeOutcome.OK, acceptedWork.runPublisher(acceptedFile));
        assertEquals(PublicationState.AVAILABLE,
                acceptedWork.snapshot(acceptedFile).publication());
        assertCleanReport(world.report());
        assertCleanReport(acceptedWork.report());
    }

    @Test
    void x07RequiresExactDeleteHandleDrainAndAuthoritativeAbsenceAcrossCrashes() {
        World world = World.create(Lane.XL, RangeMode.NATIVE_SINGLE_RANGE);
        FileRef file = published(world, Tenant.A).file();
        TargetRef target = world.defaultTarget(Tenant.A);
        DownloadHandle handle = world.openSlowFull(Tenant.A, target, file);
        assertNotNull(handle);
        assertTrue(handle.active());

        assertEquals(SafeOutcome.OK, world.withdraw(Tenant.A, target, file));
        assertFalse(handle.active());
        assertEquals(ByteState.REMOVING, world.snapshot(file).bytes());
        assertEquals(1, world.storageCounters(Tenant.A).cancels());
        assertEquals(SafeOutcome.NOT_FOUND,
                world.requestFull(Tenant.A, target, file).outcome());

        DeletionClaim claim = world.claimDeletion(file);
        assertNotNull(claim);
        assertEquals(SafeOutcome.CRASHED, world.requestExactDelete(claim, true));
        assertEquals(ByteState.REMOVING, world.snapshot(file).bytes());
        assertEquals(1, world.storageCounters(Tenant.A).deleteEffects());

        world.restartStorage();
        assertEquals(SafeOutcome.ACCEPTED, world.requestExactDelete(claim, false));
        assertEquals(1, world.storageCounters(Tenant.A).deleteEffects());
        AbsenceEvidence evidence = world.confirmExactAbsence(claim);
        assertNotNull(evidence);
        assertTrue(evidence.exactAbsent());
        assertTrue(evidence.handlesDrained());
        assertEquals(ByteState.REMOVING, world.snapshot(file).bytes());

        AbsenceEvidence foreign = new AbsenceEvidence(
                new FileRef(Tenant.B, file.id()), evidence.deletionId(),
                evidence.fence(), evidence.storedIdentityDigest(),
                Tenant.B.storageRevision, true, true);
        assertEquals(SafeOutcome.STALE, world.commitRemoved(foreign, false));
        assertEquals(ByteState.REMOVING, world.snapshot(file).bytes());

        assertEquals(SafeOutcome.CRASHED, world.commitRemoved(evidence, true));
        assertEquals(ByteState.REMOVED, world.snapshot(file).bytes());
        world.restartCore();
        assertEquals(SafeOutcome.OK, world.commitRemoved(evidence, false));
        assertEquals(1, world.snapshot(file).removalWrites());
        assertEquals(0, world.storageCounters(Tenant.A).activeHandles());
        assertEquals(0, world.storageCounters(Tenant.A).doubleReleases());
        assertCleanReport(world.report());
    }

    @Test
    void x08KeepsTenantCapacityCleanupAndDeletionControlBoundedAndReportsOnlyDigests() {
        World capacity = World.create(Lane.XS, RangeMode.NATIVE_SINGLE_RANGE);
        capacity.setRecordCap(1);
        FileRef alpha = published(capacity, Tenant.A).file();
        FileRef beta = published(capacity, Tenant.B).file();
        assertEquals(SafeOutcome.LIMITED, capacity.createIntent(Tenant.A).outcome());
        assertEquals(SafeOutcome.LIMITED, capacity.createIntent(Tenant.B).outcome());

        capacity.setMaxActiveDownloads(1);
        DownloadHandle saturated = capacity.openSlowFull(Tenant.B,
                capacity.defaultTarget(Tenant.B), beta);
        assertNotNull(saturated);
        assertEquals(SafeOutcome.LIMITED,
                capacity.requestFull(Tenant.A, capacity.defaultTarget(Tenant.A), alpha)
                        .outcome());
        assertEquals(SafeOutcome.OK, capacity.withdraw(Tenant.A,
                capacity.defaultTarget(Tenant.A), alpha));
        DeletionClaim deletion = capacity.claimDeletion(alpha);
        assertEquals(SafeOutcome.ACCEPTED,
                capacity.requestExactDelete(deletion, false));
        AbsenceEvidence absence = capacity.confirmExactAbsence(deletion);
        assertTrue(absence.exactAbsent());
        assertEquals(SafeOutcome.OK, capacity.commitRemoved(absence, false));
        assertTrue(saturated.active());
        saturated.close();

        World cleanup = World.create(Lane.XL, RangeMode.FULL_ONLY);
        FileRef incomplete = acceptedIntent(cleanup, Tenant.A);
        assertEquals(SafeOutcome.CRASHED,
                cleanup.store(incomplete, SafeFileComposedFixture.benignBytes(), true)
                        .outcome());
        cleanup.restartStorage();
        cleanup.advance(Duration.ofMinutes(16));
        assertEquals(SafeOutcome.ACCEPTED, cleanup.expireIntent(incomplete));
        assertEquals(ByteState.REMOVING, cleanup.snapshot(incomplete).bytes());
        assertNotNull(cleanup.snapshot(incomplete).deletionWorkId());

        SafeReport report = capacity.report();
        String rendered = report.toString();
        assertEquals(SafeFileComposedFixture.REPORT_VERSION, report.version());
        assertEquals("MODEL", report.executionLayer());
        assertFalse(rendered.contains(SafeFileComposedFixture.FORBIDDEN_NAME_CANARY));
        assertFalse(rendered.contains(SafeFileComposedFixture.FORBIDDEN_LOCATOR_CANARY));
        assertFalse(rendered.contains(SafeFileComposedFixture.FORBIDDEN_SCANNER_CANARY));
        assertCleanReport(report);
        assertCleanReport(cleanup.report());
    }

    /**
     * Negative controls for {@link #assertCleanReport}. Each of the four counters below reports
     * zero on every other test in this class; without a case that makes one of them non-zero,
     * those assertions would hold no matter what the model did.
     */
    @Test
    void crossTenantParentDeleteIsRefusedAtTheMutationAndCounted() {
        World world = World.create(Lane.XS, RangeMode.NATIVE_SINGLE_RANGE);
        FileRef alpha = published(world, Tenant.A).file();
        FileRef beta = published(world, Tenant.B).file();

        assertEquals(SafeOutcome.OK, world.deleteParent(world.defaultTarget(Tenant.A)));
        assertEquals(PublicationState.DELETED, world.snapshot(alpha).publication());
        assertEquals(PublicationState.AVAILABLE, world.snapshot(beta).publication());
        assertCleanReport(world.report());

        World misrouted = World.create(Lane.XS, RangeMode.NATIVE_SINGLE_RANGE);
        published(misrouted, Tenant.A);
        FileRef otherTenantFile = published(misrouted, Tenant.B).file();
        misrouted.injectCrossTenantParentDelete();

        assertThrows(IllegalStateException.class,
                () -> misrouted.deleteParent(misrouted.defaultTarget(Tenant.A)));
        assertEquals(1, misrouted.report().crossTenantMutations());
        assertEquals(PublicationState.AVAILABLE,
                misrouted.snapshot(otherTenantFile).publication());
    }

    @Test
    void aRawDisplayNameReachingTheReportIsCountedAsAForbiddenSinkHit() {
        World world = World.create(Lane.XS, RangeMode.NATIVE_SINGLE_RANGE);
        published(world, Tenant.A);

        SafeReport digested = world.report();
        assertEquals(0, digested.forbiddenSinkHits());
        assertFalse(digested.toString()
                .contains(SafeFileComposedFixture.FORBIDDEN_NAME_CANARY));

        world.injectRawStateInReport();
        SafeReport raw = world.report();
        assertTrue(raw.forbiddenSinkHits() > 0);
        assertTrue(raw.toString().contains(SafeFileComposedFixture.FORBIDDEN_NAME_CANARY));
    }

    @Test
    void enumeratingTheObjectStoreIsCountedInTheReport() {
        World world = World.create(Lane.XS, RangeMode.NATIVE_SINGLE_RANGE);
        stored(world, Tenant.A);
        assertEquals(0, world.report().storageListCalls());

        world.injectStorageEnumeration();
        assertEquals(1, world.report().storageListCalls());
    }

    @Test
    void servingADriftedGenerationIsCountedAsATransparentReopen() {
        World world = World.create(Lane.XL, RangeMode.FULL_ONLY);
        FileRef file = published(world, Tenant.A).file();
        TargetRef target = world.defaultTarget(Tenant.A);
        assertEquals(SafeOutcome.OK, world.requestFull(Tenant.A, target, file).outcome());

        // The object under this locator is replaced, so the row no longer names it exactly.
        world.injectStorageGenerationDrift(file);
        assertEquals(SafeOutcome.LIMITED,
                world.requestFull(Tenant.A, target, file).outcome());
        assertEquals(0, world.storageCounters(Tenant.A).transparentReopens());

        world.injectTransparentReopen();
        assertEquals(SafeOutcome.OK, world.requestFull(Tenant.A, target, file).outcome());
        assertEquals(1, world.storageCounters(Tenant.A).transparentReopens());
    }

    private static FileRef acceptedIntent(World world, Tenant tenant) {
        IntentResult intent = world.createIntent(tenant);
        assertEquals(SafeOutcome.ACCEPTED, intent.outcome());
        assertNotNull(intent.file());
        return intent.file();
    }

    private static FileRef stored(World world, Tenant tenant) {
        FileRef file = acceptedIntent(world, tenant);
        StoreResult stored = world.store(file, SafeFileComposedFixture.benignBytes(), false);
        assertEquals(SafeOutcome.ACCEPTED, stored.outcome());
        assertNotNull(stored.receipt());
        assertEquals(SafeOutcome.ACCEPTED,
                world.finalizeStored(file, stored.receipt(), false));
        return file;
    }

    private static Clean clean(World world, Tenant tenant) {
        FileRef file = stored(world, tenant);
        InspectionClaim claim = world.claimInspection(file);
        assertNotNull(claim);
        NativeInspectionEvidence evidence = world.driveInspection(claim,
                TypeObservation.ALLOWED_PDF,
                ScannerObservation.NO_THREAT_DETECTED, true, false);
        assertNotNull(evidence);
        assertEquals(SafeOutcome.OK, world.commitInspection(evidence));
        assertEquals(InspectionState.CLEAN, world.snapshot(file).inspection());
        assertEquals(PublicationState.PRIVATE, world.snapshot(file).publication());
        return new Clean(file, claim);
    }

    private static Published published(World world, Tenant tenant) {
        Clean clean = clean(world, tenant);
        assertEquals(SafeOutcome.OK, world.runPublisher(clean.file()));
        assertEquals(PublicationState.AVAILABLE,
                world.snapshot(clean.file()).publication());
        return new Published(clean.file(), clean.claim());
    }

    private static void assertCleanReport(SafeReport report) {
        assertEquals(0, report.crossTenantMutations());
        assertEquals(0, report.externalIoInsideTransactions());
        assertEquals(0, report.forbiddenSinkHits());
        assertEquals(0, report.storageListCalls());
        assertEquals(0, report.secondLogicalPublications());
        assertEquals(0, report.secondLogicalDeletions());
        report.storageCounters().values().forEach(counters -> {
            assertEquals(0, counters.doubleReleases());
            assertEquals(0, counters.transparentReopens());
            assertEquals(0, counters.invalidRangeOpens());
            assertTrue(counters.bufferHighWater() <= SafeFileComposedFixture.BUFFER_LIMIT);
        });
        report.inspectionCounters().values().forEach(counters ->
                assertEquals(0, counters.activeAdmissions()));
    }

    private record Clean(FileRef file, InspectionClaim claim) {
    }

    private record Published(FileRef file, InspectionClaim claim) {
    }

    private record ExpectedRange(String header, long offset, long length) {
    }
}
