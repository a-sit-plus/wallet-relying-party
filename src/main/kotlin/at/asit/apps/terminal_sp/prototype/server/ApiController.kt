package at.asit.apps.terminal_sp.prototype.server

import at.asit.apps.terminal_sp.prototype.server.security.siop2.core.IdaWalletSiop2User
import at.asitplus.wallet.idaustria.IdAustriaCredential
import at.asitplus.wallet.lib.agent.CryptoService
import at.asitplus.wallet.lib.agent.DefaultCryptoService
import at.asitplus.wallet.lib.agent.VerifierAgent
import at.asitplus.wallet.lib.data.CredentialSubject
import at.asitplus.wallet.lib.data.VerifiableCredential
import at.asitplus.wallet.lib.data.VerifiableCredentialJws
import at.asitplus.wallet.lib.oidc.AuthenticationResponseParameters
import at.asitplus.wallet.lib.oidc.OidcSiopVerifier
import at.asitplus.wallet.lib.oidc.OpenIdConstants
import at.asitplus.wallet.lib.oidvci.decodeFromUrlQuery
import io.github.aakira.napier.Napier
import jakarta.servlet.ServletContext
import org.springframework.security.core.AuthenticatedPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.ui.set
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


class CodeNotFoundException(code: String) : Exception(code)

@Controller
class ApiController(
    private val context: ServletContext,
) {

    companion object {
        private val secureRandomGenerator = SecureRandom()
        private val lock: Lock = ReentrantLock()

        // assuming a user can have multiple identifiers
        private val identifierToPrincipalMap: MutableMap<String, AuthenticatedPrincipal> = HashMap()
        private val codeToIdentifierMap: MutableMap<String, Set<String>> = HashMap()

        private val verifierCryptoService: CryptoService = DefaultCryptoService()
        private val verifier: VerifierAgent =
            VerifierAgent.Companion.newDefaultInstance(verifierCryptoService.identifier)

        private val verifierProtocolMap: MutableMap<String, OidcSiopVerifier?> = HashMap()
        private val walletUrl = "https://wallet.a-sit.at/mobile"
    }

    @GetMapping(path = ["/terminal/oauth2"])
    fun customerLogin(model: Model, @AuthenticationPrincipal user: OidcUser): String {
        lock.withLock {
            model["baseUrl"] = context.contextPath

            val codes = getExistingOrEmplacedCodes(user)
            model["title"] = "Welcome!"
            model["code"] = codes[0]
            model["userCard"] = customerCard(user, codes)
            return "customerCodeScreen"
        }
    }


    @GetMapping("/terminal/siop2")
    fun siop2LoginPage(model: Model): String {
        lock.withLock {
            val state = try {
                var state: String
                do {
                    val randomBytes = ByteArray(256 / 8)
                    secureRandomGenerator.nextBytes(randomBytes)
                    state = Base64.getEncoder().encodeToString(randomBytes)
                } while (verifierProtocolMap.containsKey(state))
                state
            } catch (exception: Exception) {
                model["baseUrl"] = context.contextPath

                model["title"] = "Exception"
                model["message"] = exception.message ?: "Unknown"

                return "customerSiop2ErrorScreen"
            }

            val verifierProtocol: OidcSiopVerifier = OidcSiopVerifier.newInstance(
                verifier = verifier,
                cryptoService = verifierCryptoService,
                relyingPartyUrl = ServletUriComponentsBuilder.fromCurrentRequest().build().toUriString() + "/success",
            )
            verifierProtocolMap[state] = verifierProtocol

            val request = verifierProtocol.createAuthnRequestUrl(
                walletUrl,
                OpenIdConstants.ResponseModes.QUERY,
                state = state,
            )

            return "redirect:$request"
        }
    }

    @GetMapping("/terminal/siop2/success")
    fun siop2SuccessPage(model: Model, @RequestParam allRequestParams: Map<String, String>): String {
        lock.withLock {
            model["baseUrl"] = context.contextPath

            val params: AuthenticationResponseParameters = allRequestParams.decodeFromUrlQuery()
            val url = ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUriString().split("?")[0]
            Napier.i {
                "Expected aud: $url"
            }

            val state = params.state
            val verifierProtocol = verifierProtocolMap[state]

            if(state == null || verifierProtocol == null) {
                model["title"] = "Exception"
                model["message"] = "Bad state"

                return "customerSiop2ErrorScreen"
            }

            try {
                val result = verifierProtocol.validateAuthnResponse(
                    params
                )
                when (result) {
                    is OidcSiopVerifier.AuthnResponseResult.Success -> {
                        val user = IdaWalletSiop2User(result.vp)

                        val codes = getExistingOrEmplacedCodes(user)
                        model["title"] = "Welcome!"
                        model["code"] = codes[0]
                        model["userCard"] = customerCard(user, codes)

                        return "customerCodeScreen"
                    }

                    is OidcSiopVerifier.AuthnResponseResult.Error -> {
                        model["title"] = "Exception"
                        model["message"] = result.reason

                        return "customerSiop2ErrorScreen"
                    }

                    is OidcSiopVerifier.AuthnResponseResult.ValidationError -> {
                        model["title"] = "Exception"
                        model["message"] = "Validation failed for field: ${result.field}"

                        return "customerSiop2ErrorScreen"
                    }
                }
            } catch (error: Exception) {
                model["title"] = "Exception"
                model["message"] = error.toString()
                return "customerSiop2ErrorScreen"
            }
        }
    }

    @GetMapping("/")
    fun listCustomers(model: Model, @RequestParam(required = false) removedCode: String?): String {
        lock.withLock {
            model["baseUrl"] = context.contextPath

            if (removedCode != null) {
                try {
                    removeUserPrincipalsByCode(removedCode)
                } catch (_: Throwable) {

                }
            }

            val customerCards = codeToIdentifierMap.entries.map { code2subject ->
                val user = identifierToPrincipalMap[code2subject.value.first()]
                customerCard(user, listOf(code2subject.key))
            }.joinToString("")

            model["title"] = "Reception"
            model["customerCards"] = customerCards

            return "customerList"
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////
    // Data
    ////////////////////////////////////////////////////////////////////////////////////////////////////


    private val AuthenticatedPrincipal.identifiers: List<String>
        get() = when (this) {
            is OidcUser -> {
                listOf(this.subject)
            }

            is IdaWalletSiop2User -> {
                this.presentation.verifiableCredentials.map {
                    it.vc.credentialSubject
                }.filterIsInstance<IdAustriaCredential>().map {
                    it.id
                }
            }

            else -> listOf()
        }

    private val AuthenticatedPrincipal.principals: List<AuthenticatedPrincipal>
        get() = this.identifiers.mapNotNull {
            identifierToPrincipalMap[it]
        }


    private fun getExistingOrEmplacedCodes(user: AuthenticatedPrincipal): List<String> {
        var codes = getExistingUserCodes(user)
        if (codes.isEmpty()) {
            codes = listOf(createUserCode(user))
        }
        return codes
    }


    // assuming a user has multiple codes
    private fun removeUserPrincipalsByCode(code: String) {
        removeUserPrincipalsByCodes(listOf(code))
    }

    private fun removeUserPrincipalsByCodes(codes: List<String>) {
        var remainingCodes = codes

        while (remainingCodes.isNotEmpty()) {
            remainingCodes = remainingCodes.map {
                val identifiers = codeToIdentifierMap.remove(it) ?: throw CodeNotFoundException(it)
                identifiers.mapNotNull {
                    identifierToPrincipalMap.remove(it)
                }
            }.flatten().map {
                getExistingUserCodes(it)
            }.flatten()
        }
    }

    private fun getExistingUserCodes(user: AuthenticatedPrincipal): List<String> {
        val principals = user.principals

        val ids = identifierToPrincipalMap.entries.filter {
            principals.contains(it.value)
        }.map {
            it.key
        }

        return codeToIdentifierMap.entries.filter { entry ->
            ids.any {
                entry.value.contains(it)
            }
        }.map {
            it.key
        }
    }

    private fun createUserCode(user: AuthenticatedPrincipal): String {
        // new user - issue authentication code
        var code: String
        val randomBytes = ByteArray(256 / 8)
        do {
            secureRandomGenerator.nextBytes(randomBytes)
            code = Base64.getEncoder().encodeToString(randomBytes)
        } while (codeToIdentifierMap.containsKey(code))

        user.identifiers.let {
            codeToIdentifierMap.put(code, it.toSet())
            it.forEach {
                identifierToPrincipalMap.put(it, user)
            }
        }

        return code
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////
    // UI
    ////////////////////////////////////////////////////////////////////////////////////////////////////


    data class CustomerCardData(
        val familyName: String?,
        val givenName: String?,
        val birthdate: String?,
        val codes: List<String>,
        val other: String?,
    )

    private fun customerCard(data: CustomerCardData): String {
        return """
        <div class="card">
            <div class="container">
                <h4><b>Kunde: ${data.familyName} ${data.givenName}, ${data.birthdate}</b></h4>
                <p>Code${if (data.codes.size < 2) "s" else ""}: ${
            if (data.codes.isEmpty()) "Kein Code vorhanden..." else if (data.codes.size == 1) data.codes[0] else "<ul>${
                data.codes.joinToString("") {
                    "<li>$it</li>"
                }
            }</ul>"
        }
                <p>Sonstige Daten: <ul>
                ${data.other}
                </ul></p>
                ${
            if (data.codes.isEmpty()) "" else """
                    <p><a href="?removedCode=${
                URLEncoder.encode(
                    data.codes[0], StandardCharsets.UTF_8
                )
            }"><button type="button">Entfernen</button></a></p>"""
        }
            </div>
        </div>"""
    }

    private fun customerCard(user: AuthenticatedPrincipal?, codes: List<String>): String {
        val cardData = when (user) {
            is OidcUser -> {
                CustomerCardData(
                    familyName = user.familyName,
                    givenName = user.givenName,
                    birthdate = user.birthdate,
                    codes = codes,
                    other = user.claims.entries.map {
                        "<li>${it.key}: ${it.value}</li>"
                    }.joinToString("")
                )
            }

            is IdaWalletSiop2User -> {
                val idAustriaCredentials = user.presentation.verifiableCredentials.map {
                    it.vc.credentialSubject
                }.filterIsInstance<IdAustriaCredential>()

                // just take any ida credential for now
                val idAustriaCredential = idAustriaCredentials.getOrNull(0)

                val other = mapOf(
                    "id" to user.presentation.id,
                    "type" to user.presentation.type,
                    "verifiableCredentials" to user.presentation.verifiableCredentials.joinToString("") {
                        it.toCredentialJwsInfo()
                    },
                    "revokedVerifiableCredentials" to user.presentation.revokedVerifiableCredentials.joinToString("") {
                        it.toCredentialJwsInfo()
                    },
                    "invalidVerifiableCredentials" to "<ul>${
                        user.presentation.invalidVerifiableCredentials.map { "<li>$it</li>" }.joinToString("")
                    }</ul>"
                ).map {
                    "<li>${it.key}: ${it.value}</li>"
                }.joinToString("")

                CustomerCardData(
                    familyName = idAustriaCredential?.firstname,
                    givenName = idAustriaCredential?.lastname,
                    birthdate = idAustriaCredential?.dateOfBirth.toString(),
                    codes = codes,
                    other = other
                )
            }

            else -> {
                Napier.i {
                    "Unknown user type"
                }
                null
            }
        }
        return if (cardData == null) "user not supported" else customerCard(cardData)
    }

    private fun VerifiableCredentialJws.toCredentialJwsInfo(): String {
        return """<ul><li>subject: ${this.subject}</li><ul>${
            mapOf(
                "issuer" to this.issuer,
                "jwtId" to this.jwtId,
                "exp" to this.expiration,
                "nbf" to this.notBefore,
                "vc" to this.vc.toCredentialInfo()
            ).map { "<li>${it.key}: ${it.value}</li>" }.joinToString("")
        }</ul></ul>"""
    }

    private fun VerifiableCredential.toCredentialInfo(): String {
        return """<ul>${
            mapOf(
                "id" to this.id,
                "issuer" to this.issuer,
                "credentialStatus" to this.credentialStatus,
                "credentialSubject" to this.credentialSubject.toSubjectInfo(),
                "expirationDate" to this.expirationDate,
                "issuanceDate" to this.issuanceDate,
                "type" to this.type.joinToString(", ")
            ).map { "<li>${it.key}: ${it.value}</li>" }.joinToString("")
        }</ul>"""
    }

    private fun CredentialSubject.toSubjectInfo(): String {
        return """<ul>${
            mapOf(
                "id" to this.id,
            ).entries.plus(
                when (val it = this) {
                    is IdAustriaCredential -> mapOf(
                        "id" to it.id,
                        "firstname" to it.firstname,
                        "lastname" to it.lastname,
                        "dateOfBirth" to it.dateOfBirth,
                    )

                    else -> mapOf(
                        "type" to "unknown"
                    )
                }.entries
            ).joinToString("") { "<li>${it.key}: ${it.value}</li>" }
        }</ul>"""
    }
}
