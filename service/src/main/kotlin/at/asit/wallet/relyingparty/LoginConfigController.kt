package at.asit.wallet.relyingparty

import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation.ISO_MDOC
import at.asitplus.wallet.lib.ktor.openid.RemoteCredentialMetadataRegistry
import at.asitplus.wallet.sdjwt.SdJwtTypeMetadata
import at.asitplus.wallet.sdjwt.SdJwtTypeMetadataClaimInformation
import at.asitplus.wallet.sdjwt.SdJwtTypeMetadataClaimInformationPathSegmentName
import at.asitplus.wallet.sdjwt.SelectiveDisclosureConstraints.NEVER
import io.github.aakira.napier.Napier
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController

/**
 * Serves the verifier UI's credential/attribute picker as an ES module, generated from the SD-JWT Type Metadata
 * documents (see [CredentialCatalog]) instead of a hand-maintained static file. Served at the same path the
 * `importmap` already points to, so the frontend is unchanged.
 */
@RestController
class LoginConfigController(
    private val registry: RemoteCredentialMetadataRegistry,
) {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    @GetMapping("/js/login-config.js", produces = ["application/javascript"])
    @ResponseBody
    suspend fun loginConfig(): String {
        val schemeTypes = coroutineScope {
            CredentialCatalog.entries
                .map { entry -> async { entry to registry.findEntry(entry.identifier, entry.representation)?.metadata } }
                .awaitAll()
        }.mapNotNull { (entry, metadata) ->
            if (metadata == null) {
                Napier.w("No type metadata for ${entry.identifier} (${entry.url}); omitting from login config")
                null
            } else entry.toUiSchemeType(metadata)
        }
        return "export default ${json.encodeToString(LoginConfig(schemeTypes))};"
    }

    private fun CredentialCatalog.Entry.toUiSchemeType(metadata: SdJwtTypeMetadata) = UiSchemeType(
        label = metadata.display?.firstEnglishOrFirst { it.locale.string }?.name ?: metadata.name ?: vct,
        value = identifier,
        sd = metadata.claims?.none { it.selectiveDisclosureConstraints == NEVER } ?: true,
        validRepresentations = listOf(representation.name),
        attributes = metadata.claims?.mapNotNull { it.toUiAttribute(isIso = representation == ISO_MDOC) }
            ?: emptyList(),
    )

    private fun SdJwtTypeMetadataClaimInformation.toUiAttribute(isIso: Boolean): UiAttribute? {
        val names = path.filterIsInstance<SdJwtTypeMetadataClaimInformationPathSegmentName>().map { it.string }
        val leaf = names.lastOrNull() ?: return null
        // ISO mdoc element identifiers are the leaf name (the namespace prefix is added by VC-K); JSON credentials use
        // the dotted path as nested-claim shorthand, e.g. "address.formatted".
        return UiAttribute(
            label = display?.firstEnglishOrFirst { it.locale.string }?.label ?: leaf,
            value = if (isIso) leaf else names.joinToString("."),
            isSelected = isMandatory == true,
        )
    }
}

private inline fun <T> Iterable<T>.firstEnglishOrFirst(locale: (T) -> String): T? =
    firstOrNull { locale(it).startsWith("en", ignoreCase = true) } ?: firstOrNull()

// --- UI model, shaped to match the former static login-config.js ---

@Serializable
private data class LoginConfig(
    val schemeTypes: List<UiSchemeType>,
    val representation: List<UiLabelValue> = listOf(
        UiLabelValue("SD-JWT", "SD_JWT"),
        UiLabelValue("ISO mDoc", "ISO_MDOC"),
    ),
    val profiles: List<UiProfile> = listOf(
        UiProfile("Custom", listOf(UiProfileCredential())),
    ),
)

@Serializable
private data class UiSchemeType(
    val label: String,
    val value: String,
    val sd: Boolean,
    val validRepresentations: List<String>,
    val attributes: List<UiAttribute>,
)

@Serializable
private data class UiAttribute(val label: String, val value: String, val isSelected: Boolean = false)

@Serializable
private data class UiLabelValue(val label: String, val value: String)

@Serializable
private data class UiProfile(val label: String, val credentials: List<UiProfileCredential>)

@Serializable
private data class UiProfileCredential(
    val schemeType: String? = null,
    val representation: String? = null,
    val sd: Boolean = true,
    val attributes: List<String> = emptyList(),
)
