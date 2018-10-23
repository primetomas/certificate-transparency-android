@file:JvmName("CertificateInfo")

package org.certificatetransparency.ctlog.internal.utils

import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.certificatetransparency.ctlog.exceptions.CertificateTransparencyException
import org.certificatetransparency.ctlog.exceptions.UnsupportedCryptoPrimitiveException
import org.certificatetransparency.ctlog.internal.serialization.CTConstants.POISON_EXTENSION_OID
import org.certificatetransparency.ctlog.internal.serialization.CTConstants.PRECERTIFICATE_SIGNING_OID
import org.certificatetransparency.ctlog.internal.serialization.CTConstants.SCT_CERTIFICATE_OID
import org.certificatetransparency.ctlog.internal.verifier.model.IssuerInformation
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.cert.Certificate
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateParsingException
import java.security.cert.X509Certificate
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/** Helper class for finding out all kinds of information about a certificate.  */

private const val X509_AUTHORITY_KEY_IDENTIFIER = "2.5.29.35"

@UseExperimental(ExperimentalContracts::class)
internal fun Certificate.isPreCertificateSigningCert(): Boolean {
    contract {
        returns(true) implies (this@isPreCertificateSigningCert is X509Certificate)
    }
    try {
        return this is X509Certificate && extendedKeyUsage?.contains(PRECERTIFICATE_SIGNING_OID) == true
    } catch (e: CertificateParsingException) {
        throw CertificateTransparencyException("Error parsing signer cert: ${e.message}", e)
    }
}

@UseExperimental(ExperimentalContracts::class)
internal fun Certificate.isPreCertificate(): Boolean {
    contract {
        returns(true) implies (this@isPreCertificate is X509Certificate)
    }
    return this is X509Certificate && criticalExtensionOIDs?.contains(POISON_EXTENSION_OID) == true
}

@UseExperimental(ExperimentalContracts::class)
internal fun Certificate.hasEmbeddedSct(): Boolean {
    contract {
        returns(true) implies (this@hasEmbeddedSct is X509Certificate)
    }
    return this is X509Certificate && nonCriticalExtensionOIDs?.contains(SCT_CERTIFICATE_OID) == true
}

// Produces issuer information in case the PreCertificate is signed by a regular CA cert,
// not PreCertificate Signing Cert. In this case, the only thing that's needed is the
// issuer key hash - the Precertificate will already have the right value for the issuer
// name and K509 Authority Key Identifier extension.
internal fun Certificate.issuerInformation(): IssuerInformation {
    return IssuerInformation(keyHash = keyHash(), issuedByPreCertificateSigningCert = false)
}

internal fun Certificate.issuerInformationFromPreCertificate(preCertificate: Certificate): IssuerInformation {
    try {
        ASN1InputStream(encoded).use { aIssuerIn ->
            val parsedIssuerCert = org.bouncycastle.asn1.x509.Certificate.getInstance(aIssuerIn.readObject())

            val issuerExtensions = parsedIssuerCert.tbsCertificate.extensions
            val x509authorityKeyIdentifier = issuerExtensions?.getExtension(ASN1ObjectIdentifier(X509_AUTHORITY_KEY_IDENTIFIER))

            return IssuerInformation(parsedIssuerCert.issuer, preCertificate.keyHash(), x509authorityKeyIdentifier, true)
        }
    } catch (e: CertificateEncodingException) {
        throw CertificateTransparencyException("Certificate could not be encoded: ${e.message}", e)
    } catch (e: IOException) {
        throw CertificateTransparencyException("Error during ASN.1 parsing of certificate: ${e.message}", e)
    }
}

private fun Certificate.keyHash(): ByteArray {
    try {
        return MessageDigest.getInstance("SHA-256").digest(publicKey.encoded)
    } catch (e: NoSuchAlgorithmException) {
        throw UnsupportedCryptoPrimitiveException("SHA-256 not supported: ${e.message}", e)
    }
}
