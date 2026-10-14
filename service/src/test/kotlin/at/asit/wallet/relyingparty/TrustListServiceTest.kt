package at.asit.wallet.relyingparty

import at.asitplus.wallet.lib.etsi.LoTEStage
import at.asitplus.wallet.lib.etsi.LoteProfile
import at.asitplus.wallet.lib.validation.CredentialTrustScopes
import at.asitplus.wallet.lib.validation.TrustPurpose
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrustListServiceTest {

    private val service = TrustListService(TrustListCache(), AppConfigurationProperties())

    @Test
    fun `lists are fetched from the configured stages`() {
        assertEquals(LoteProfile.fetchUrls(LoTEStage.DEVELOPMENT, LoTEStage.ACCEPTANCE), service.trustListUrls)
        val stages = listOf(LoTEStage.ACCEPTANCE)
        assertEquals(
            LoteProfile.fetchUrls(stages),
            TrustListService(TrustListCache(), AppConfigurationProperties(trustListStages = stages)).trustListUrls,
        )
    }

    @Test
    fun `a list that can not be refreshed does not keep the other lists from being refreshed`() = runTest {
        val service = TrustListService(
            TrustListCache(),
            AppConfigurationProperties(trustListStages = listOf(LoTEStage.DEVELOPMENT, LoTEStage.ACCEPTANCE)),
        )
        val failing = LoteProfile.EAA.fetchUrl(LoTEStage.DEVELOPMENT)
        val refreshed = mutableListOf<String>()

        service.refreshAll { url ->
            if (url == failing) throw IllegalStateException("403 Access Denied")
            refreshed += url
        }

        assertEquals(service.trustListUrls - failing, refreshed)
    }

    @Test
    fun `every catalog type is trusted through exactly one scope, its list and the A-SIT root`() = runTest {
        val asitRoot = CredentialValidationConfiguration().asitRootCertificate()
        val stages = listOf(LoTEStage.ACCEPTANCE)
        val requestedLists = mutableSetOf<String>()
        // Throws if an identifier would be in two scopes
        val anchors = CredentialTrustScopes(
            credentialTrustScopes(CredentialCatalog.entries.map { it.identifier }, stages, asitRoot) {
                requestedLists += it
                emptyList()
            }
        )

        CredentialCatalog.entries.forEach { entry ->
            TrustPurpose.entries.forEach { purpose ->
                val anchorSet = checkNotNull(anchors.resolve(entry.identifier, purpose)) { entry.identifier }
                assertEquals(setOf(asitRoot), anchorSet.certificates, entry.identifier)
            }
        }
        val pid = anchors.resolve("urn:eudi:pid:1", TrustPurpose.ISSUANCE)!!.source
        assertEquals(pid, anchors.resolve("eu.europa.ec.eudi.pid.1", TrustPurpose.ISSUANCE)!!.source)
        assertTrue(pid.contains(LoteProfile.PID.fetchUrl(LoTEStage.ACCEPTANCE)), pid)
        val mdl = anchors.resolve("org.iso.18013.5.1.mDL", TrustPurpose.STATUS)!!.source
        assertTrue(mdl.contains(LoteProfile.mDL.fetchUrl(LoTEStage.ACCEPTANCE)), mdl)
        val ehic = anchors.resolve("urn:eudi:ehic:1", TrustPurpose.ISSUANCE)!!.source
        assertTrue(ehic.contains(LoteProfile.EAA.fetchUrl(LoTEStage.ACCEPTANCE)), ehic)
        assertEquals(
            setOf(LoteProfile.PID, LoteProfile.mDL, LoteProfile.EAA).map { it.fetchUrl(LoTEStage.ACCEPTANCE) }.toSet(),
            requestedLists,
        )
        assertNull(anchors.resolve("urn:example:unknown:1", TrustPurpose.ISSUANCE))
    }
}
