package org.astianx.midori.one.chat

import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLHandshakeException
import org.astianx.midori.one.R
import org.astianx.midori.one.UiText
import org.astianx.midori.one.BuildConfig
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration

internal object ChatTls {
    fun applyLocalPin(builder: XMPPTCPConnectionConfiguration.Builder, account: ChatAccount, pin: String) {
        if (!BuildConfig.DEBUG) return
        // A development pin never changes trust for another domain or host.
        if (account.address.substringAfter('@') != "localhost" || account.host !in setOf("10.0.2.2", "127.0.0.1")) return
        if (pin.isEmpty()) throw ChatException(UiText(R.string.chat_tls_lab_unconfigured))
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = throw CertificateException()
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                val leaf = chain.firstOrNull() ?: throw CertificateException()
                leaf.checkValidity()
                if (fingerprint(leaf) != pin.lowercase()) throw CertificateException("Local certificate changed")
            }
        }
        builder.setCustomX509TrustManager(trust)
        builder.setHostnameVerifier { name, session ->
            name == "localhost" && runCatching {
                fingerprint(session.peerCertificates.first() as X509Certificate) == pin.lowercase()
            }.getOrDefault(false)
        }
    }

    private fun fingerprint(cert: X509Certificate) = MessageDigest.getInstance("SHA-256")
        .digest(cert.encoded).joinToString("") { "%02x".format(it) }

    // An EOF/closed TLS socket is a connectivity failure, not evidence of a bad identity.
    fun isIdentityFailure(error: Throwable): Boolean = generateSequence(error) { it.cause }.take(20).any {
        it is CertificateException || it is SSLPeerUnverifiedException || it is SSLHandshakeException
    }
}
