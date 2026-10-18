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
