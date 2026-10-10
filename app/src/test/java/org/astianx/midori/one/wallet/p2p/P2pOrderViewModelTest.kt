package org.astianx.midori.one.wallet.p2p

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.astianx.midori.one.R
import org.astianx.midori.one.UiText
import org.astianx.midori.one.wallet.fiat.FiatOperation
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class P2pOrderViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { models.clear(); Dispatchers.resetMain() }
    private fun vm(f: P2pOrderFixture) = f.viewModel().also { models.put("orders", it) }
    @Test fun zeroOrFreshlyRevokedLimitsBlockBeforeRelayCreationOrWalletSubmission() = runTest(dispatcher) {
        val f = P2pOrderFixture(); val vm = vm(f); advanceUntilIdle()
        vm.country("CO"); advanceUntilIdle(); assertEquals("100000000", vm.state.value.account?.buyLimit)
        f.buyLimit = "0"; vm.amount("1"); vm.quote(); advanceUntilIdle()
        assertEquals(UiText(R.string.p2po_verification), vm.state.value.error)
        assertEquals("0", vm.state.value.account?.buyLimit); assertNull(vm.state.value.review)
        assertEquals(0, f.identities); assertEquals(0, f.emissions); assertTrue(f.store.rows.isEmpty())
    }
    @Test fun aFailedAccountReadClearsStaleLimitsInsteadOfAssumingThePreviousAllowance() = runTest(dispatcher) {
        val f = P2pOrderFixture(); val vm = vm(f); advanceUntilIdle(); vm.country("CO"); advanceUntilIdle()
        f.accountFailure = true; vm.amount("1"); vm.quote(); advanceUntilIdle()
        assertNull(vm.state.value.account); assertNull(vm.state.value.review); assertEquals(0, f.identities)
    }
    @Test fun withdrawNavigationDuringLoadingIsAppliedAndIndiaBuyRemainsHidden() = runTest(dispatcher) {
        val f = P2pOrderFixture(); f.catalogGate = CompletableDeferred()
        val vm = vm(f); runCurrent(); vm.enter(FiatOperation.SELL)
        f.catalogGate!!.complete(Unit); advanceUntilIdle()
        assertEquals(FiatOperation.SELL, vm.state.value.operation)
        assertTrue(vm.state.value.available.any { it.country == "IN" })
        vm.operation(FiatOperation.BUY); assertFalse(vm.state.value.available.any { it.country == "IN" })
    }
    @Test fun visibleRefreshDetectsCompletedIndexWithoutCreatingAKeyOrOrder() = runTest(dispatcher) {
        val f = P2pOrderFixture(); f.ready = false
        val vm = vm(f); advanceUntilIdle(); assertFalse(vm.state.value.availability!!.enabled)
        f.ready = true; vm.refreshPending(); advanceUntilIdle()
        assertTrue(vm.state.value.availability!!.enabled); assertEquals(0, f.identities); assertEquals(0, f.emissions)
    }
    @Test fun reentryReconcilesKnownTransactionsAndDoesNotRepeatUnknownSubmissions() = runTest(dispatcher) {
        val f = P2pOrderFixture(); val vm = vm(f); advanceUntilIdle(); vm.country("CO"); advanceUntilIdle()
        vm.amount("1,0"); vm.quote(); advanceUntilIdle(); f.loseHash = true; vm.submit(false); advanceUntilIdle()
        assertEquals(P2pSubmission.UNKNOWN, vm.state.value.records.single().submission)
        val restarted = f.viewModel().also { models.put("restart", it) }; advanceUntilIdle()
        restarted.country("CO"); advanceUntilIdle(); restarted.amount("1"); restarted.quote(); advanceUntilIdle()
        assertEquals(P2pSubmission.UNKNOWN, restarted.state.value.records.single().submission)
        assertEquals(UiText(R.string.p2po_active), restarted.state.value.error)
        assertEquals(1, f.emissions); assertEquals(1, f.identities)
    }
}
