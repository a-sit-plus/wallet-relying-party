package at.asitplus.wallet.rp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PidFormatTest {
    @Test
    fun requestsThePidAsMdocWithItsRemoteCase() {
        val credential = PidFormat.MDOC.credentialJson()

        assertEquals("eu.europa.ec.eudi.pid.1", credential.getString("credentialType"))
        assertEquals("ISO_MDOC", credential.getString("representation"))
        assertEquals("6", PidFormat.MDOC.remoteCaseId)
    }

    @Test
    fun requestsThePidAsSdJwtWithSelectedClaimsAndItsRemoteCase() {
        val credential = PidFormat.SD_JWT.credentialJson()

        assertEquals("urn:eudi:pid:1", credential.getString("credentialType"))
        assertEquals("SD_JWT", credential.getString("representation"))
        // ISO Annex C can not request all claims of an SD-JWT VC, so claims are always selected
        assertTrue(credential.getJSONArray("attributes").length() > 0)
        assertEquals("5", PidFormat.SD_JWT.remoteCaseId)
    }
}
