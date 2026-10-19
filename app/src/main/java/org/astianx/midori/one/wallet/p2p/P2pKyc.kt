package org.astianx.midori.one.wallet.p2p

import java.security.MessageDigest
import java.security.SecureRandom
import java.math.BigInteger
import java.net.URI
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.math.ec.ECAlgorithms

internal const val P2P_REPUTATION_MANAGER = "0xcf613e08ee1b4c2669ddcf06a7d22c9856f6aa1d"
internal const val KYC_CLOCK_SKEW_SECONDS = 60L
internal fun validateKycWidget(s: P2pKycSession) {
    val u = URI(s.widgetUrl)
    p2pRequire(s.widgetOrigin == "https://passport.p2p.cool" && u.scheme == "https" && u.rawAuthority == "passport.p2p.cool" &&
        u.rawUserInfo == null && u.rawFragment == null && u.rawPath == "/embed" &&
        u.rawQuery?.matches(Regex("handoff=[a-zA-Z0-9_-]{20,128}")) == true)
}
internal data class P2pKycCapability(val enabled: Boolean, val reason: String, val countries: List<String>)
internal data class P2pKycProof(val user: String, val country: String, val nullifier: String,
    val limit: String, val expiry: String, val signature: String, val signer: String)
internal data class P2pKycSession(val nonce: String, val user: String, val country: String,
    val widgetUrl: String, val widgetOrigin: String, val expiresAt: Long) {
    override fun toString() = "P2pKycSession(redacted)"
}
internal data class P2pKycStatus(val user: String, val country: String, val status: String, val proof: P2pKycProof?)
internal data class P2pKycPrepared(val proof: P2pKycProof, val to: String, val data: String, val quotedAt: Long, val expiresAt: Long)
internal data class P2pKycReview(val prepared: P2pKycPrepared, val gas: String)
internal data class P2pKycRecord(val owner: String, val country: String, val nonce: String, val token: String,
    val state: String = "STARTING", val session: P2pKycSession? = null, val proof: P2pKycProof? = null,
    val data: String = "", val hash: String? = null) {
    override fun toString() = "P2pKycRecord(redacted)"
}
internal interface P2pKycStore {
    suspend fun load(owner: String): P2pKycRecord?
    suspend fun save(owner: String, record: P2pKycRecord)
}
internal interface P2pKycGateway {
    val origin: String
    suspend fun capabilities(): P2pKycCapability
    suspend fun verified(user: String): Boolean
    suspend fun account(user: String, country: String): P2pAccount
    suspend fun session(record: P2pKycRecord, expiresAt: Long, signature: String): P2pKycSession
    suspend fun status(record: P2pKycRecord): P2pKycStatus
    suspend fun prepare(record: P2pKycRecord): P2pKycPrepared
    suspend fun receipt(record: P2pKycRecord, hash: String): P2pReceipt
}
internal fun kycMessage(origin: String, r: P2pKycRecord, expiresAt: Long): String = listOf(
    "Midori account verification", origin, "Chain: 8453", "Wallet: ${r.owner.lowercase()}",
    "Country: ${r.country}", "Nonce: ${r.nonce}",
    "Token hash: ${MessageDigest.getInstance("SHA-256").digest(r.token.toByteArray()).p2pHex()}",
    "Expires: $expiresAt", "This signature starts identity verification. It does not authorize a transaction.").joinToString("\n")

/** EIP-712 validation and exact SDK calldata. No private wallet or identity keys. */
internal object P2pKycEncoding {
    private fun word(value: String) = value.toBigInteger().toString(16).padStart(64, '0')
    private fun address(value: String) = value.removePrefix("0x").lowercase().padStart(64, '0')
    private fun hash(value: String) = p2pKeccak(value.toByteArray()).p2pHex()
    fun calldata(p: P2pKycProof): String {
        require(p.signature.matches(Regex("0x[a-fA-F0-9]{130}")) && p.nullifier.matches(Regex("0x[a-fA-F0-9]{64}")))
        require(p2pInteger(p.limit, true) && p2pInteger(p.expiry, true))
        return "0x" + hash("submitKycAttestation(bytes32,uint256,uint256,bytes)").take(8) +
            p.nullifier.removePrefix("0x") + word(p.limit) + word(p.expiry) + word("128") + word("65") +
            p.signature.removePrefix("0x").padEnd(192, '0')
    }
    fun validate(p: P2pKycProof, owner: String, country: String, now: Long, allowExpired: Boolean = false) {
        p2pRequire(p.user.equals(owner, true) && p.country == country && p.nullifier != "0x" + "0".repeat(64))
        p2pRequire(owner.matches(Regex("0x[a-fA-F0-9]{40}")) && country.matches(Regex("[A-Z]{2}")))
        p2pRequire(p2pInteger(p.limit, true) && p2pInteger(p.expiry, true) &&
            (allowExpired || p.expiry.toBigInteger() > BigInteger.valueOf(now)) && p.expiry.toBigInteger() <= BigInteger.valueOf(now + 3660))
        calldata(p)
        val domain = hash("EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)") +
            hash("KycVerifier") + hash("1") + word("8453") + address(P2P_REPUTATION_MANAGER)
        val message = hash("KycAttestation(address wallet,bytes32 nullifier,uint256 limit,uint256 expiry)") +
            address(owner) + p.nullifier.removePrefix("0x") + word(p.limit) + word(p.expiry)
        val digest = p2pKeccak(byteArrayOf(0x19, 0x01) + p2pKeccak(p2pBytes(domain)) + p2pKeccak(p2pBytes(message)))
        val sig = p2pBytes(p.signature.removePrefix("0x")); val r = BigInteger(1, sig.copyOfRange(0, 32)); val s = BigInteger(1, sig.copyOfRange(32, 64))
        val curve = SECNamedCurves.getByName("secp256k1"); val v = sig[64].toInt() and 255
        p2pRequire(v in 27..28 && r > BigInteger.ZERO && r < curve.n && s > BigInteger.ZERO && s <= curve.n.shiftRight(1))
        val point = curve.curve.decodePoint(byteArrayOf((2 + v - 27).toByte()) + p2pBytes(r.toString(16).padStart(64, '0')))
        p2pRequire(point.isValid && !point.isInfinity && point.multiply(curve.n).isInfinity)
        val inverse = r.modInverse(curve.n)
        val key = ECAlgorithms.sumOfTwoMultiplies(curve.g, BigInteger(1, digest).negate().multiply(inverse).mod(curve.n),
            point, s.multiply(inverse).mod(curve.n)).normalize()
        p2pRequire(!key.isInfinity)
        val signer = "0x" + p2pKeccak(key.getEncoded(false).drop(1).toByteArray()).takeLast(20).toByteArray().p2pHex()
        p2pRequire(p.signer.matches(Regex("0x[a-fA-F0-9]{40}")) && signer.equals(p.signer, true))
    }
}

/** Separate verification journal and explicit transaction review. Never re-submit an uncertain claim. */
internal class P2pKycInteractor(private val owner: String, private val gateway: P2pKycGateway,
    private val store: P2pKycStore, private val wallet: P2pWalletPort,
    private val sign: suspend (P2pKycRecord, Long) -> String,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 }) {
    private val mutex = Mutex(); private var review: P2pKycReview? = null
    private fun current() = p2pRequire(wallet.isCurrent(owner), "wallet_changed")
    private suspend fun commit(r: P2pKycRecord) { current(); store.save(owner, r) }
    suspend fun capabilities() = gateway.capabilities()
    suspend fun verified(): Boolean { current(); return gateway.verified(owner).also { current() } }
    suspend fun account(country: String): P2pAccount {
        current(); val a = gateway.account(owner, country); current()
        p2pRequire(a.user.equals(owner, true) && a.country == country && p2pTimeValid(a.quotedAt, a.expiresAt, clock()))
        return a
    }
    suspend fun local() = store.load(owner).also { current(); p2pRequire(it == null || it.owner.equals(owner, true), "local_storage_failed") }
    suspend fun start(country: String) = mutex.withLock {
        current(); val previous = local()
        p2pRequire(previous?.state !in setOf("SUBMITTING", "PENDING", "UNKNOWN", "EXCHANGE_UNKNOWN", "DUPLICATE_IDENTITY"), "submission_unknown")
        val resumable = previous != null && previous.state in setOf("WAITING", "READY") && !(previous.state == "WAITING" && previous.session == null)
        p2pRequire(!resumable, "verification_pending")
        val caps = gateway.capabilities(); p2pRequire(caps.enabled && country in caps.countries, "verification_service_unavailable")
        p2pRequire(!gateway.verified(owner), "already_verified")
        val random = SecureRandom(); fun token() = ByteArray(32).also(random::nextBytes).p2pHex()
        var r = P2pKycRecord(owner, country, token(), token()); commit(r)
        val expires = clock() + 120
        val signature = sign(r, expires); current()
        val session = gateway.session(r, expires, signature)
        p2pRequire(session.user.equals(owner, true) && session.country == country && session.nonce == r.nonce &&
            session.expiresAt > clock() && session.expiresAt <= clock() + 900 + KYC_CLOCK_SKEW_SECONDS)
        validateKycWidget(session)
        r = r.copy(session = session, state = "WAITING"); commit(r); review = null; r
    }
    suspend fun refresh() = mutex.withLock {
        current(); var r = local() ?: return@withLock null
        if (r.hash != null && r.state in setOf("PENDING", "SUBMITTING", "UNKNOWN")) {
            val receipt = gateway.receipt(r, checkNotNull(r.hash)); current()
            p2pRequire(receipt.hash.equals(r.hash, true))
            r = r.copy(state = receipt.status.name); commit(r)
        } else if (r.session == null && r.proof == null && r.state in setOf("STARTING", "WAITING")) {
            return@withLock r
        } else if (r.state in setOf("WAITING", "READY", "STARTING", "EXCHANGE_UNKNOWN")) {
            if (r.proof?.expiry?.toBigInteger()?.let { it <= BigInteger.valueOf(clock()) } == true) {
                r = r.copy(state = "EXPIRED"); commit(r); return@withLock r
            }
            val status = gateway.status(r); current()
            p2pRequire(status.user.equals(owner, true) && status.country == r.country && status.status in
                setOf("WAITING", "READY", "UNKNOWN", "EXPIRED", "REJECTED", "CANCELLED", "DUPLICATE_IDENTITY"))
            status.proof?.let { P2pKycEncoding.validate(it, owner, r.country, clock(), status.status == "EXPIRED") }
            p2pRequire(status.status != "READY" || status.proof != null)
            // UNKNOWN without a broadcast concerns the provider exchange, not a wallet transaction.
            r = r.copy(state = if (status.status == "UNKNOWN") "EXCHANGE_UNKNOWN" else status.status, proof = status.proof); commit(r)
        }
        r
    }
    suspend fun review() = mutex.withLock {
        current(); val r = checkNotNull(local()); p2pRequire(r.state == "READY", "verification_required")
        val prepared = gateway.prepare(r); validate(prepared, r)
        val gas = wallet.quote(owner, prepared.to, prepared.data, "0"); current()
        p2pRequire(p2pInteger(gas, true)); P2pKycReview(prepared, gas).also { review = it }
    }
    suspend fun widget(): String = mutex.withLock {
        current(); val r = checkNotNull(local()); val s = checkNotNull(r.session)
        p2pRequire(r.state == "WAITING" && s.expiresAt > clock(), "expired_session"); validateKycWidget(s); s.widgetUrl
    }
    private fun validate(p: P2pKycPrepared, r: P2pKycRecord) {
        P2pKycEncoding.validate(p.proof, owner, r.country, clock())
        p2pRequire(p.proof == r.proof && p.to.equals(P2P_REPUTATION_MANAGER, true) && p.data.equals(P2pKycEncoding.calldata(p.proof), true) &&
            p2pTimeValid(p.quotedAt, p.expiresAt, clock()))
    }
    fun dismiss() { review = null }
    suspend fun submit(candidate: P2pKycReview) = mutex.withLock {
        current(); var r = checkNotNull(local()); p2pRequire(review == candidate && r.state == "READY" && candidate.prepared.expiresAt > clock(), "quote_expired")
        val fresh = gateway.prepare(r); validate(fresh, r)
        p2pRequire(fresh.data.equals(candidate.prepared.data, true), "quote_changed")
        var started = false
        try {
            val hash = wallet.submit(owner, fresh.to, fresh.data, candidate.gas, "0") {
                current(); p2pRequire(candidate.prepared.expiresAt > clock(), "quote_expired")
                r = r.copy(state = "SUBMITTING", data = fresh.data); commit(r); started = true; review = null
            }
            p2pRequire(started && hash.matches(Regex("0x[a-fA-F0-9]{64}")))
            r = r.copy(state = "PENDING", hash = hash); commit(r)
        } catch (e: Exception) {
            if (!started) throw e
            runCatching { commit(r.copy(state = "UNKNOWN")) }; throw P2pOrderFailure("submission_unknown")
        }
        r
    }
    suspend fun recover(hash: String) = mutex.withLock {
        current(); val r = checkNotNull(local())
        p2pRequire(r.state in setOf("SUBMITTING", "UNKNOWN") && r.hash == null && hash.matches(Regex("0x[a-fA-F0-9]{64}")), "submission_unknown")
        val receipt = gateway.receipt(r, hash); p2pRequire(receipt.hash.equals(hash, true) && receipt.status in setOf(P2pSubmission.CONFIRMED, P2pSubmission.REVERTED))
        r.copy(hash = hash, state = receipt.status.name).also { commit(it) }
    }
}
