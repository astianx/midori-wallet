package org.astianx.midori.one.wallet.p2p

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class P2pKycTest {
    private suspend fun fails(code: String? = null, block: suspend () -> Unit) {
        try { block(); fail("Expected failure") } catch (e: Exception) {
            if (code != null) assertEquals(code, (e as? P2pOrderFailure)?.code)
        }
    }
    @Test fun eip712SignatureCalldataAndSessionMessageMatchPinnedSdk() {
        val f = P2pKycFixture()
        val j = JSONObject(checkNotNull(javaClass.getResource("/p2p-kyc-interop.json")).readText())
        P2pKycEncoding.validate(f.proof, f.owner, "CO", f.now)
        assertEquals(j.getString("data"), P2pKycEncoding.calldata(f.proof))
        val record = P2pKycRecord(f.owner, "CO", "a".repeat(64), "b".repeat(64))
        assertEquals(j.getString("message"), kycMessage("https://midori.test", record, f.now + 120))
        for (p in listOf(f.proof.copy(user = "0x" + "1".repeat(40)), f.proof.copy(country = "BR"),
            f.proof.copy(signer = "0x" + "1".repeat(40)), f.proof.copy(limit = "101"), f.proof.copy(expiry = f.now.toString()))) {
            assertTrue(runCatching { P2pKycEncoding.validate(p, f.owner, "CO", f.now) }.isFailure)
        }
    }
    @Test fun startingAndSubmittingRequireDurableWritesBeforeExternalSideEffects() = runTest {
        val f = P2pKycFixture(); val flow = f.interactor(); f.store.fail = true
        fails("local_storage_failed") { flow.start("CO") }; assertEquals(0, f.sessions)
        f.store.fail = false; f.ready(flow); val review = flow.review(); f.store.fail = true
        fails("local_storage_failed") { flow.submit(review) }; assertEquals(0, f.emissions)
        assertEquals("READY", f.store.row?.state)
    }
    @Test fun anAlreadyVerifiedWalletDoesNotSignOrCreateAnotherPassportSession() = runTest {
        val f = P2pKycFixture(); f.verified = true
        fails("already_verified") { f.interactor().start("CO") }
        assertEquals(0, f.sessions); assertEquals(0, f.emissions); assertNull(f.store.row)
    }
    @Test fun proofSurvivesCodeExpiryAndRestartAndIsSubmittedOnlyAfterReview() = runTest {
        val f = P2pKycFixture(); val flow = f.interactor(); f.ready(flow)
        f.now += 1200; val restarted = f.interactor()
        assertEquals("READY", restarted.refresh()?.state); assertEquals(0, f.emissions)
        val review = restarted.review(); restarted.submit(review)
        assertEquals(1, f.emissions); assertEquals("PENDING", f.store.row?.state)
        assertEquals("CONFIRMED", f.interactor().refresh()?.state)
    }
    @Test fun feeIncreaseAndExpiredReviewNeverBroadcast() = runTest {
        val f = P2pKycFixture(); val flow = f.interactor(); f.ready(flow)
        val review = flow.review(); f.changedFee = true
        fails("quote_changed") { flow.submit(review) }; assertEquals(0, f.emissions)
        f.changedFee = false; f.now += 61
        fails("quote_expired") { flow.submit(review) }; assertEquals(0, f.emissions)
    }
    @Test fun ambiguousSubmissionCannotRepeatAndItsHashCanReconcileAfterExpiry() = runTest {
        val f = P2pKycFixture(); val flow = f.interactor(); f.ready(flow); f.failAfterBroadcast = true
        fails("submission_unknown") { flow.submit(flow.review()) }; assertEquals("UNKNOWN", f.store.row?.state)
        val restarted = f.interactor(); fails("submission_unknown") { restarted.start("CO") }
        f.now += 7200; assertEquals("CONFIRMED", restarted.recover(f.hash).state); assertEquals(1, f.emissions)
    }
    @Test fun tamperedCalldataChangedWalletAndExpiredProofCannotBeReviewed() = runTest {
        val f = P2pKycFixture(); val flow = f.interactor(); f.ready(flow); f.tamper = true
        fails { flow.review() }; f.tamper = false; f.current = false; fails("wallet_changed") { flow.review() }
        f.current = true; f.now += 3601
        assertEquals("EXPIRED", flow.refresh()?.state); fails { flow.review() }; assertEquals(0, f.emissions)
    }
    @Test fun refusalAndNetworkFailuresPreserveStateWithoutRestartingVerification() = runTest {
        val f = P2pKycFixture(); val flow = f.interactor(); flow.start("CO"); f.stage = "DUPLICATE_IDENTITY"
        assertEquals("DUPLICATE_IDENTITY", flow.refresh()?.state); fails("submission_unknown") { flow.start("CO") }
        val other = P2pKycFixture(); val next = other.interactor(); other.ready(next); other.remoteFailure = true
        fails { next.refresh() }; assertEquals("READY", other.store.row?.state); assertEquals(0, other.emissions)
    }
    @Test fun aSmallClockSkewBetweenServerAndDeviceDoesNotRejectAValidSession() = runTest {
        val f = P2pKycFixture(); f.sessionSkew = 1; val flow = f.interactor()
        val record = flow.start("CO")
        assertEquals("WAITING", record.state); assertNotNull(record.session); assertEquals(1, f.sessions)
        assertEquals(record.session!!.widgetUrl, flow.widget())
    }
    @Test fun aSessionFarBeyondTheProviderWindowIsStillRejectedAndCanBeRestarted() = runTest {
        val f = P2pKycFixture(); f.sessionSkew = 600; val flow = f.interactor()
        fails { flow.start("CO") }
        assertEquals("STARTING", f.store.row?.state); assertNull(f.store.row?.session)
        f.sessionSkew = 0; assertEquals("WAITING", flow.start("CO").state)
    }
    @Test fun anInterruptedAttemptWithoutASessionIsRecoverableInsteadOfStuckWaiting() = runTest {
        val f = P2pKycFixture(); f.store.row = P2pKycRecord(f.owner, "CO", "a".repeat(64), "b".repeat(64), "WAITING")
        val vm = f.viewModel(); val flow = f.interactor()
        assertEquals("WAITING", flow.refresh()?.state); assertNull(f.store.row?.session)
        val restarted = flow.start("CO")
        assertEquals("WAITING", restarted.state); assertNotNull(restarted.session); assertEquals(1, f.sessions)
        assertNotNull(vm)
    }
    @Test fun faceOnlyUsesItsOwnSignedMessageSelectorAndWizardOriginWhileThePassportFormatIsUnchanged() {
        val f = P2pKycFixture()
        val passport = P2pKycRecord(f.owner, "CO", "a".repeat(64), "b".repeat(64))
        val face = passport.copy(method = KYC_LIVENESS)
        assertTrue(kycMessage("https://midori.test", passport, f.now).startsWith("Midori account verification\n"))
        assertTrue(kycMessage("https://midori.test", face, f.now).startsWith("Midori face verification\n"))
        assertEquals("0x6d692e79", P2pKycEncoding.calldata(f.proof).take(10))
        assertEquals("0x2bd54ab8", P2pKycEncoding.calldata(f.proof, KYC_LIVENESS).take(10))
        assertEquals("passport.p2p.cool", kycWidgetHost(KYC_PASSPORT)); assertEquals("liveness.p2p.cool", kycWidgetHost(KYC_LIVENESS))
        val url = "https://liveness.p2p.cool/embed?handoff=" + "a".repeat(32)
        val session = P2pKycSession("a".repeat(64), f.owner, "CO", url, "https://liveness.p2p.cool", f.now + 900)
        validateKycWidget(session, KYC_LIVENESS)
        assertTrue(runCatching { validateKycWidget(session, KYC_PASSPORT) }.isFailure)
        assertTrue(runCatching { validateKycWidget(session.copy(widgetUrl = url.replace("liveness", "passport"), widgetOrigin = "https://passport.p2p.cool"), KYC_LIVENESS) }.isFailure)
    }
    @Test fun aFaceOnlyVerificationRunsTheWholeJournalWithItsOwnCalldata() = runTest {
        val f = P2pKycFixture(); val flow = f.interactor(); f.ready(flow, KYC_LIVENESS)
        assertEquals(KYC_LIVENESS, f.store.row?.method); assertEquals("READY", f.store.row?.state)
        assertTrue(checkNotNull(f.store.row?.session).widgetUrl.startsWith("https://liveness.p2p.cool/embed"))
        val review = flow.review()
        assertEquals(P2pKycEncoding.calldata(f.proof, KYC_LIVENESS), review.prepared.data)
        val done = flow.submit(review); assertEquals("PENDING", done.state); assertEquals(1, f.emissions)
        assertEquals(KYC_LIVENESS, done.method)
    }
    @Test fun aFaceOnlyReviewRejectsPassportCalldataAndTheTierFlagsAreIndependent() = runTest {
        val f = P2pKycFixture(); val flow = f.interactor(); f.ready(flow, KYC_LIVENESS)
        f.tamper = true; fails { flow.review() }
        val other = P2pKycFixture(); other.faceVerified = true
        fails("already_verified") { other.interactor().start("CO", KYC_LIVENESS) }
        assertEquals("WAITING", other.interactor().start("CO", KYC_PASSPORT).state)
        val passportDone = P2pKycFixture(); passportDone.verified = true
        assertEquals("WAITING", passportDone.interactor().start("CO", KYC_LIVENESS).state)
        fails("verification_service_unavailable") { P2pKycFixture().interactor().start("CO", "SELFIE") }
    }
    @Test fun aFaceOnlyRecordSurvivesTheEncryptedJsonRoundTripAndDefaultsOldRecordsToPassport() {
        val f = P2pKycFixture()
        val record = P2pKycRecord(f.owner, "CO", "a".repeat(64), "b".repeat(64), "READY", proof = f.proof, method = KYC_LIVENESS)
        assertEquals(KYC_LIVENESS, P2pKycJson.record(P2pKycJson.record(record)).method)
        val legacy = P2pKycJson.record(record.copy(method = KYC_PASSPORT)); legacy.remove("method")
        assertEquals(KYC_PASSPORT, P2pKycJson.record(legacy).method)
        val tampered = P2pKycJson.record(record); tampered.put("method", "SELFIE")
        assertTrue(runCatching { P2pKycJson.record(tampered) }.isFailure)
    }
    @Test fun widgetAndReturnValidationRejectForeignHostsExtraParametersAndInjectedCredentials() {
        val f = P2pKycFixture()
        val session = P2pKycSession("a".repeat(64), f.owner, "CO", "https://passport.p2p.cool/embed?handoff=" + "a".repeat(32),
            "https://passport.p2p.cool", f.now + 900)
        validateKycWidget(session)
        for (url in listOf(session.widgetUrl + "&state=evil", session.widgetUrl + "#fragment",
            session.widgetUrl.replace("passport.p2p.cool", "evil.test"), session.widgetUrl.replace("https:", "http:"))) {
            assertTrue(runCatching { validateKycWidget(session.copy(widgetUrl = url)) }.isFailure)
        }
        assertTrue(isP2pVerificationReturn("midori://p2p/verification"))
        assertFalse(isP2pVerificationReturn("midori://p2p/verification?code=evil"))
        assertFalse(isP2pVerificationReturn("midori://p2p/verification/"))
    }
}
