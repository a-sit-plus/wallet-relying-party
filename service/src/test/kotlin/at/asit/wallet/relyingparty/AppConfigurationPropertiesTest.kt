package at.asit.wallet.relyingparty

import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatus
import at.asitplus.wallet.lib.validation.StatusPolicy
import at.asitplus.wallet.lib.validation.TrustPolicy
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource

class AppConfigurationPropertiesTest {

    @Test
    fun `binds multiple labeled WRP access certificates`() {
        val properties = mapOf(
            "app.wrp.ac[0].label" to "First service",
            "app.wrp.ac[0].keystore.path" to "file:first.p12",
            "app.wrp.ac[0].keystore.type" to "PKCS12",
            "app.wrp.ac[0].keystore.alias" to "first",
            "app.wrp.ac[1].label" to "Second service",
            "app.wrp.ac[1].keystore.path" to "file:second.p12",
            "app.wrp.ac[1].keystore.type" to "PKCS12",
            "app.wrp.ac[1].keystore.alias" to "second",
        )

        val configuration = Binder(MapConfigurationPropertySource(properties))
            .bind("app", Bindable.of(AppConfigurationProperties::class.java))
            .get()

        assertEquals(listOf("First service", "Second service"), configuration.wrp?.ac?.map { it.label })
        assertEquals(listOf("first", "second"), configuration.wrp?.ac?.map { it.keystore.alias })
    }

    @Test
    fun `validation policy is strict by default`() {
        val policy = bind(emptyMap()).validation.toValidationPolicy()

        assertEquals(TrustPolicy.RequireAuthorizedSigner, policy.trust)
        assertEquals(
            StatusPolicy.ValidateIfPresent(TrustPolicy.RequireAuthorizedSigner, setOf(TokenStatus.Valid)),
            policy.status,
        )
        assertEquals(true, policy.requireTimeliness)
        assertEquals(5.minutes, policy.timeLeeway)
    }

    @Test
    fun `validation policy can be relaxed for test setups`() {
        val policy = bind(
            mapOf(
                "app.validation.trust" to "INTEGRITY_ONLY",
                "app.validation.status" to "REQUIRE_CLAIM",
                "app.validation.status-signer-trust" to "INTEGRITY_ONLY",
                "app.validation.accepted-status" to "0,2",
                "app.validation.require-timeliness" to "false",
                "app.validation.time-leeway" to "30s",
            )
        ).validation.toValidationPolicy()

        assertEquals(TrustPolicy.IntegrityOnly, policy.trust)
        assertEquals(
            StatusPolicy.RequireClaim(TrustPolicy.IntegrityOnly, setOf(TokenStatus.Valid, TokenStatus.Suspended)),
            policy.status,
        )
        assertEquals(false, policy.requireTimeliness)
        assertEquals(30.seconds, policy.timeLeeway)

        assertEquals(
            StatusPolicy.Skip,
            bind(mapOf("app.validation.status" to "SKIP")).validation.toValidationPolicy().status,
        )
    }

    private fun bind(properties: Map<String, String>) =
        Binder(MapConfigurationPropertySource(properties))
            .bindOrCreate("app", Bindable.of(AppConfigurationProperties::class.java))
}
