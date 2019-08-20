package com.babylon.certificatetransparency.chaincleaner

import java.security.cert.X509Certificate
import java.util.ServiceLoader
import javax.net.ssl.X509TrustManager

/**
 * Computes the effective certificate chain from the raw array returned by Java's built in TLS APIs.
 * Cleaning a chain returns a list of certificates where the first element is `chain[0]`, each
 * certificate is signed by the certificate that follows, and the last certificate is a trusted CA
 * certificate.
 *
 *
 * Use of the chain cleaner is necessary to omit unexpected certificates that aren't relevant to
 * the TLS handshake and to extract the trusted CA certificate for the benefit of certificate
 * pinning.
 */
interface CertificateChainCleaner {
    fun clean(chain: List<X509Certificate>, hostname: String): List<X509Certificate>

    companion object {
        private val factoryLoader: ServiceLoader<CertificateChainCleanerFactory> by lazy {
            ServiceLoader.load<CertificateChainCleanerFactory>(CertificateChainCleanerFactory::class.java)
        }

        fun get(trustManager: X509TrustManager): CertificateChainCleaner {
            return factoryLoader.firstOrNull()?.get(trustManager) ?: BasicCertificateChainCleaner(trustManager)
        }
    }
}