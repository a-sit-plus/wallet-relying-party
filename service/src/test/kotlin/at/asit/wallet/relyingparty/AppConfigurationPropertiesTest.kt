package at.asit.wallet.relyingparty

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
}
