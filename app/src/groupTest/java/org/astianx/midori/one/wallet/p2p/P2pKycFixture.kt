package org.astianx.midori.one.wallet.p2p

/** SDK-signed public, unfunded fixture. No HTTP, real wallet or identity capture. */
internal class P2pKycFixture {
    val owner = "0x19e7e376e7c213b7e7e7e46cc70a5dd086daff2a"
    val proof = P2pKycProof(owner, "CO", "0x" + "c".repeat(64), "100", "1700003600",
        "0xccf4ec99182c61e89c1cdc43458722ead8c08fe9167ab7ba8eb3bfc5b917dd7c3d5d197e1e55cb95dc44c00c3b95f4f546b15245da37f43e49ccb28afc963f911b",
        "0x1563915e194d8cfba1943570603f7606a3115508")
    var now = 1700000000L
    var stage = "WAITING"
    var emissions = 0
    var sessions = 0
    var failAfterBroadcast = false
    var changedFee = false
    var current = true
    var tamper = false
    var remoteFailure = false
    var receiptStatus = P2pSubmission.CONFIRMED
    var verified = false
    val hash = "0x" + "d".repeat(64)
    class Store : P2pKycStore {
        var row: P2pKycRecord? = null
        var fail = false
        override suspend fun load(owner: String) = row
        override suspend fun save(owner: String, record: P2pKycRecord) {
            if (fail) throw P2pOrderFailure("local_storage_failed")
            row = record
        }
    }
    val store = Store()
    val gateway = object : P2pKycGateway {
        override val origin = "https://midori.test"
        override suspend fun capabilities() = P2pKycCapability(true, "ready", listOf("CO", "BR"))
        override suspend fun verified(user: String) = verified
        override suspend fun account(user: String, country: String) = P2pAccount(user, country, "COP", "COP", "1000000", "100000000", "300000000", "20", now, now + 60)
        override suspend fun session(record: P2pKycRecord, expiresAt: Long, signature: String): P2pKycSession {
            check(store.row?.state == "STARTING"); sessions++
            return P2pKycSession(record.nonce, owner, record.country, "https://passport.p2p.cool/embed?handoff=" + "a".repeat(32),
                "https://passport.p2p.cool", now + 900)
        }
        override suspend fun status(record: P2pKycRecord): P2pKycStatus {
            if (remoteFailure) throw P2pOrderFailure("provider_unavailable")
            return P2pKycStatus(owner, record.country, stage, if (stage == "READY") proof else null)
        }
        override suspend fun prepare(record: P2pKycRecord): P2pKycPrepared = P2pKycPrepared(proof, P2P_REPUTATION_MANAGER,
            P2pKycEncoding.calldata(proof) + if (tamper) "00" else "", now, now + 60)
        override suspend fun receipt(record: P2pKycRecord, hash: String) = P2pReceipt(hash, receiptStatus, null)
    }
    val wallet = object : P2pWalletPort {
        override fun isCurrent(owner: String) = current && owner == this@P2pKycFixture.owner
        override suspend fun quote(owner: String, to: String, data: String, requiredUsdc: String) = "1000"
        override suspend fun submit(owner: String, to: String, data: String, maxGas: String, requiredUsdc: String,
            beforeBroadcast: suspend () -> Unit): String {
            if (changedFee) throw P2pOrderFailure("quote_changed")
            beforeBroadcast(); check(store.row?.state == "SUBMITTING"); emissions++
            if (failAfterBroadcast) throw P2pOrderFailure("provider_unavailable")
            return hash
        }
    }
    fun interactor() = P2pKycInteractor(owner, gateway, store, wallet, { _, _ -> "0x" + "a".repeat(130) }, { now })
    fun viewModel() = P2pKycViewModel(owner, "CO", interactor())
    suspend fun ready(flow: P2pKycInteractor) { flow.start("CO"); stage = "READY"; flow.refresh() }
}
