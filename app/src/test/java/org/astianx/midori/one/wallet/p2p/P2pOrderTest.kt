package org.astianx.midori.one.wallet.p2p

import kotlinx.coroutines.test.runTest
import org.astianx.midori.one.wallet.fiat.FiatOperation
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class P2pOrderTest {
    private val fixture = JSONObject(checkNotNull(javaClass.getResource("/p2p-sdk-interop.json")).readText())
    private val owner = "0x" + "1".repeat(40)
    private val hash = "0x" + "a".repeat(64)
    private val now = 1700000000L
    private fun quote(operation: FiatOperation = FiatOperation.BUY) = P2pOrderQuote("a".repeat(64), owner, operation,
        "CO", "COP", "COP", "1000000", if (operation == FiatOperation.BUY) "1000000" else "1100000",
        "3182690000", if (operation == FiatOperation.BUY) "3500959000" else "3182690000", "100000", "3182690000",
        fixture.getString("publicKey"), "4", P2P_DIAMOND, fixture.getString(if (operation == FiatOperation.BUY) "buyData" else "sellData"), now, now+60)
    private fun order(operation: FiatOperation = FiatOperation.BUY, status: String = "accepted") = P2pOrder("7",owner,operation,
        "COP","1000000","3182690000",if(operation==FiatOperation.BUY) "1000000" else "1100000","3500959000",
        "4",status,"none",fixture.getString("publicKey"),fixture.getString("encrypted"))
    private inner class Store : P2pOrderStore {
        var rows = emptyList<P2pPrivateRecord>(); var fail = false
        override suspend fun load(owner: String) = rows
        override suspend fun save(owner: String, records: List<P2pPrivateRecord>) {
            if(fail) throw P2pOrderFailure("local_storage_failed"); rows=records.toList()
        }
    }
    private inner class Gateway : P2pOrderGateway {
        var q = quote(); var o = order(); var tamper = false; var approval = "1000000"
        var receipt = P2pReceipt(hash,P2pSubmission.CONFIRMED,"7")
        override suspend fun availability() = P2pAvailability(true,"ready")
        override suspend fun quote(user: String, operation: FiatOperation, country: String, amount: String, relayPublicKey: String) = q
        override suspend fun prepare(user: String, quoteId: String) = q
        override suspend fun order(user: String, orderId: String) = o
        override suspend fun history(user: String) = listOf(o)
        override suspend fun action(user: String, orderId: String, action: P2pAction, encryptedDestination: String?, evidenceId: String?): P2pPreparedAction {
            val (to,data)=P2pAbi.action(action,orderId,approval,encryptedDestination,evidenceId)
            return P2pPreparedAction(user,orderId,action,o,to,if(tamper) data+"00" else data,approval,now,now+60)
        }
        override suspend fun receipt(user: String, hash: String, to: String, data: String) = receipt
    }
    private inner class Wallet : P2pWalletPort {
        var current = true; var emits = 0; var failAfter = false; var increased = false
        override fun isCurrent(owner: String) = current
        override suspend fun quote(owner: String, to: String, data: String, requiredUsdc: String) = "1000"
        override suspend fun submit(owner: String, to: String, data: String, maxGas: String, requiredUsdc: String, beforeBroadcast: suspend () -> Unit): String {
            if(increased) throw P2pOrderFailure("quote_changed")
            beforeBroadcast(); emits++
            if(failAfter) throw IllegalStateException("Timeout")
            return hash
        }
    }
    private val crypto = object : P2pRelayCrypto {
        override fun createSecret() = fixture.getString("secret")
        override fun publicKey(secret: String) = fixture.getString("publicKey")
        override fun encrypt(secret: String, recipientPublicKey: String, message: String) = "a".repeat(194)
        override fun decrypt(secret: String, encrypted: String) = "fixture instructions"
    }
    private inner class Environment {
        val store=Store(); val gateway=Gateway(); val wallet=Wallet(); var time=now
        fun interactor()=P2pOrderInteractor(owner,gateway,store,crypto,wallet){time}
        val flow=interactor()
        suspend fun place() { val review=flow.quote(gateway.q.operation,"CO","1000000","3001234567"); flow.submit(review) }
    }
    private suspend fun fails(code: String? = null, block: suspend () -> Unit) {
        try { block(); fail("Expected failure") } catch(e: Exception) { if(code!=null) assertEquals(code,(e as? P2pOrderFailure)?.code) }
    }
    @Test fun sdkFixturesDecryptAndLocalAbiExactlyMatchesBothSdkDirections() {
        val ecies=P2pEcies()
        assertEquals(fixture.getString("publicKey"),ecies.publicKey(fixture.getString("secret")))
        assertEquals(fixture.getString("message"),ecies.decrypt(fixture.getString("secret"),fixture.getString("encrypted")))
        assertEquals(fixture.getString("buyData"),P2pAbi.place(quote()))
        assertEquals(fixture.getString("sellData"),P2pAbi.place(quote(FiatOperation.SELL)))
        val encrypted=ecies.encrypt("2".repeat(64),fixture.getString("publicKey"),fixture.getString("message"))
        assertEquals(fixture.getString("message"),ecies.decrypt(fixture.getString("secret"),encrypted))
        File("/tmp/midori-p2p-native-vector.json").writeText(JSONObject().put("encrypted",encrypted).toString())
        assertTrue(runCatching { ecies.decrypt("3".repeat(64),encrypted) }.isFailure)
        val mutated=encrypted.substring(0,100)+(if(encrypted[100]=='a') "b" else "a")+encrypted.substring(101)
        assertTrue(runCatching { ecies.decrypt(fixture.getString("secret"),mutated) }.isFailure)
    }
    @Test fun corruptOrConflictingJournalBlocksBeforeAnyWalletSubmission() = runTest {
        val e=Environment(); e.store.fail=true
        fails("local_storage_failed"){e.flow.quote(FiatOperation.BUY,"CO","1000000","")}
        assertEquals(0,e.wallet.emits)
    }
    @Test fun persistenceMustCommitIntentBeforeBroadcast() = runTest {
        val e=Environment();val review=e.flow.quote(FiatOperation.BUY,"CO","1000000",""); e.store.fail=true
        fails("local_storage_failed"){e.flow.submit(review)};assertEquals(0,e.wallet.emits)
    }
    @Test fun timeoutIsRecoverableAndNeverAutomaticallyResubmittedEvenAfterRestart() = runTest {
        val e=Environment(); val review=e.flow.quote(FiatOperation.BUY,"CO","1000000",""); e.wallet.failAfter=true
        fails("submission_unknown"){e.flow.submit(review)}; assertEquals(1,e.wallet.emits)
        val restarted=e.interactor();assertEquals(P2pSubmission.UNKNOWN,restarted.localRecords().single().submission)
        fails("order_already_active"){restarted.quote(FiatOperation.BUY,"CO","1000000","")}
        val id=restarted.localRecords().single().localId
        restarted.recoverHash(id,hash);val recovered=restarted.refresh(id)
        assertEquals("7",recovered.orderId);assertEquals(P2pSubmission.CONFIRMED,recovered.submission);assertEquals(1,e.wallet.emits)
    }
    @Test fun pendingReceiptSurvivesRestartAndBindsRealOrderOwnerAmountCurrency() = runTest {
        val e=Environment();e.place();val restarted=e.interactor();val id=restarted.localRecords().single().localId
        e.gateway.receipt=P2pReceipt(hash,P2pSubmission.PENDING,null)
        assertEquals(P2pSubmission.PENDING,restarted.refresh(id).submission)
        e.gateway.receipt=P2pReceipt(hash,P2pSubmission.CONFIRMED,"7")
        e.gateway.o=e.gateway.o.copy(user="0x"+"2".repeat(40))
        fails("invalid_protocol_response"){restarted.refresh(id)}
        assertEquals(1,e.wallet.emits)
    }
    @Test fun expirationFeeIncreaseAndChangedQuoteRequireNewReview() = runTest {
        val e=Environment();val review=e.flow.quote(FiatOperation.BUY,"CO","1000000","")
        e.wallet.increased=true;fails("quote_changed"){e.flow.submit(review)};e.wallet.increased=false
        e.gateway.q=e.gateway.q.copy(fee="200000")
        fails {e.flow.submit(review)}
        e.time=now+60;fails("quote_expired"){e.flow.submit(review)};assertEquals(0,e.wallet.emits)
    }
    @Test fun walletSwitchAndArbitraryProviderCalldataCannotBeSigned() = runTest {
        val e=Environment();e.gateway.q=e.gateway.q.copy(data="0x12345678")
        fails {e.flow.quote(FiatOperation.BUY,"CO","1000000","")}
        e.gateway.q=quote();val review=e.flow.quote(FiatOperation.BUY,"CO","1000000","");e.wallet.current=false
        fails("wallet_changed"){e.flow.submit(review)};assertEquals(0,e.wallet.emits)
    }
    @Test fun countryCurrencyBindingAndMandatoryIndiaPrecheckCannotBeBypassedByGateway() = runTest {
        val e=Environment();e.gateway.q=quote().copy(currency="USD",fiat="USD")
        fails("invalid_protocol_response"){e.flow.quote(FiatOperation.BUY,"CO","1000000","")}
        e.gateway.q=quote().copy(country="IN",currency="INR",fiat="INR")
        fails("verification_required"){e.flow.quote(FiatOperation.BUY,"IN","1000000","")}
        assertEquals(0,e.wallet.emits)
    }
    @Test fun paymentReportingRequiresExplicitConfirmationAndFreshOrder() = runTest {
        val e=Environment();e.place();val id=e.flow.localRecords().single().localId;e.flow.refresh(id)
        val review=e.flow.action(id,P2pAction.PAID)
        fails("payment_confirmation_required"){e.flow.submit(review)}
        e.gateway.o=e.gateway.o.copy(status="completed");fails("order_changed"){e.flow.submit(review,true)}
        assertEquals(1,e.wallet.emits)
    }
    @Test fun sellHandoffEncryptsLocallyAndApprovalCannotExceedExactOrderAmount() = runTest {
        val e=Environment();e.gateway.q=quote(FiatOperation.SELL);e.gateway.o=order(FiatOperation.SELL);e.gateway.approval="1100000"
        e.place();val id=e.flow.localRecords().single().localId;e.flow.refresh(id)
        val review=e.flow.action(id,P2pAction.HANDOFF)
        assertEquals("a".repeat(194),review.encryptedDestination)
        assertFalse(review.data.contains("3001234567"))
        e.gateway.approval=(java.math.BigInteger.ONE.shiftLeft(256)-java.math.BigInteger.ONE).toString()
        fails("invalid_protocol_response"){e.flow.action(id,P2pAction.APPROVE)}
    }
    @Test fun missingPlacementEventCannotBeMistakenForSuccessfulOrderCreation() = runTest {
        val e=Environment();e.place();e.gateway.receipt=P2pReceipt(hash,P2pSubmission.CONFIRMED,null)
        fails("invalid_protocol_response"){e.flow.refresh(e.flow.localRecords().single().localId)}
        assertEquals(P2pSubmission.PENDING,e.flow.localRecords().single().submission)
    }
    @Test fun nativePaymentFormatsMatchPublicSdkFixturesAndRejectMissingOrInjectedFields() {
        val rows=org.json.JSONArray(checkNotNull(javaClass.getResource("/p2p-payment-ids.json")).readText())
        for(i in 0 until rows.length()) {
            val row=rows.getJSONObject(i);val country=row.getString("country");val value=row.getString("value")
            assertTrue(country,P2pPaymentDetails.valid(country,value))
            assertFalse(country,P2pPaymentDetails.valid(country,""))
            assertFalse(country,P2pPaymentDetails.valid(country,value+"|injected"))
        }
        assertFalse(P2pPaymentDetails.valid("CO","bank account"))
        assertFalse(P2pPaymentDetails.valid("EC","Fixture|Savings|1234567890|Test Person|0100000000"))
        assertFalse(P2pPaymentDetails.valid("KE","||123456|"))
        assertFalse(P2pPaymentDetails.valid("CO","@fixture\n"))
    }
    @Test fun vaultRoundTripRetainsRelayWithoutExposingItInPresentation() {
        val row=P2pPrivateRecord("id",owner,"CO",FiatOperation.BUY,"1000000",fixture.getString("secret"),"bank",quote=quote(),order=order())
        assertEquals(row,P2pJson.record(P2pJson.record(row)));assertEquals("P2pPrivateRecord(redacted)",row.toString())
        assertTrue(runCatching {P2pJson.quote(P2pJson.quote(quote()).put("amountUnits",1.5))}.isFailure)
    }
}
