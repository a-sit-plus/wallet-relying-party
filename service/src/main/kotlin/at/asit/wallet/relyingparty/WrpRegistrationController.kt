package at.asit.wallet.relyingparty

import org.springframework.http.MediaType
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.CookieManager
import java.net.CookiePolicy
import java.time.Duration
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate as JcaX509Certificate

data class WrpCertificateAvailability(
    val hasWrpac: Boolean,
    val hasWrprc: Boolean,
)

data class SaveWrprcRequest(
    val jws: String,
)

data class RegistrationStateResponse(
    val status: String,
    val wrpIdentifier: String?,
    val serviceUri: String?,
    val hasWrpac: Boolean,
    val hasWrprc: Boolean,
    val wrpRegistration: WrpRegistrationData,
    val serviceRegistration: ServiceRegistrationData,
)

data class RegisterWrpRequest(
    val endpointBaseUrl: String? = null,
    val registrarEmail: String? = null,
    val registrarPassword: String? = null,
    val displayName: String? = null,
    val tradeName: String? = null,
    val country: String? = null,
    val isPsb: Boolean? = null,
    val isIntermediary: Boolean? = null,
    val supportUri: String? = null,
    val privacyPolicyUri: String? = null,
    val entitlement: List<String>? = null,
    val providerType: Int? = null,
    val supervisoryAuthorityIdentifierType: String? = null,
    val supervisoryAuthorityIdentifier: String? = null,
)

data class ServiceCredentialRequest(
    val credentialType: String,
    val format: String,
    val claims: List<String> = emptyList(),
)

data class RegisterServiceRequest(
    val endpointBaseUrl: String? = null,
    val registrarEmail: String? = null,
    val registrarPassword: String? = null,
    val serviceUri: String,
    val serviceName: String? = null,
    val purpose: String? = null,
    val credentials: List<ServiceCredentialRequest> = emptyList(),
)

data class LoadWrprcRequest(
    val url: String,
    val endpointBaseUrl: String? = null,
    val registrarEmail: String? = null,
    val registrarPassword: String? = null,
)

data class LoadWrpacRequest(
    val url: String,
    val endpointBaseUrl: String? = null,
    val registrarEmail: String? = null,
    val registrarPassword: String? = null,
)

data class RefreshRegistrationRequest(
    val endpointBaseUrl: String? = null,
    val registrarEmail: String? = null,
    val registrarPassword: String? = null,
)

data class IssueWrpacRequest(
    val endpointBaseUrl: String? = null,
    val registrarEmail: String? = null,
    val registrarPassword: String? = null,
    val wrpacProviderUrl: String? = null,
    val wrpacProviderUser: String? = null,
    val wrpacProviderPassword: String? = null,
)

data class IssueWrprcRequest(
    val endpointBaseUrl: String? = null,
    val registrarEmail: String? = null,
    val registrarPassword: String? = null,
    val maxAttempts: Int? = null,
    val pollIntervalMs: Long? = null,
)

@Serializable
data class RemoteWrpacPayload(
    val chainPem: String,
    val keyStoreBase64: String,
    val thumbprint: String? = null,
)

@Serializable
data class RemoteWrprcPayload(
    val jws: String,
)

@RestController
@RequestMapping("/api/wrp")
class WrpRegistrationController(
    private val store: WrpCertificateStore,
) {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    @GetMapping("/cert-options")
    fun certificateOptions(): List<WrpCertificateOption> =
        store.certificateOptions()

    @GetMapping("/certs")
    fun certificatePreviews(): List<WrpCertificatePreview> =
        store.certificatePreviews()

    @GetMapping("/availability")
    fun certificateAvailability(): WrpCertificateAvailability =
        WrpCertificateAvailability(
            hasWrpac = store.hasWrpac(),
            hasWrprc = store.hasWrprc(),
        )

    @GetMapping("/registration")
    fun registrationState(): RegistrationStateResponse {
        val state = store.loadState()
        val status = state.requestStatus?.trim()?.ifBlank { null } ?: when {
            state.wrpIdentifier.isNullOrBlank() -> "unregistered"
            state.serviceUri.isNullOrBlank() -> "wrp_registered"
            else -> "service_registered"
        }
        return RegistrationStateResponse(
            status = status,
            wrpIdentifier = state.wrpIdentifier,
            serviceUri = state.serviceUri,
            hasWrpac = store.hasWrpac(),
            hasWrprc = store.hasWrprc(),
            wrpRegistration = state.wrpRegistration,
            serviceRegistration = state.serviceRegistration,
        )
    }

    @PostMapping("/registration/reset")
    fun resetRegistrationState(): RegistrationStateResponse {
        store.clearRegistrationState()
        return registrationState()
    }

    @PostMapping("/registration/wrp")
    fun registerWrp(
        @RequestBody request: RegisterWrpRequest,
    ): RegistrationStateResponse {
        val current = store.loadState()
        val displayName = request.displayName?.trim().orEmpty().ifBlank { current.wrpRegistration.displayName }
        val tradeName = request.tradeName?.trim().orEmpty().ifBlank { current.wrpRegistration.tradeName }
        val country = request.country?.trim().orEmpty().ifBlank { current.wrpRegistration.country }
        val supportUri = request.supportUri?.trim().orEmpty().ifBlank { current.wrpRegistration.supportUri }
        val privacyPolicyUri =
            request.privacyPolicyUri?.trim().orEmpty().ifBlank { current.wrpRegistration.privacyPolicyUri }
        val entitlement = request.entitlement
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.takeIf { it.isNotEmpty() }
            ?: current.wrpRegistration.entitlement
        val providerType = request.providerType ?: current.wrpRegistration.providerType
        val saIdentifierType = request.supervisoryAuthorityIdentifierType?.trim().orEmpty()
            .ifBlank { current.wrpRegistration.supervisoryAuthorityIdentifierType }
        val saIdentifier = request.supervisoryAuthorityIdentifier?.trim().orEmpty()
            .ifBlank { current.wrpRegistration.supervisoryAuthorityIdentifier }
        if (displayName.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "displayName is required")
        }
        val wrpRegistration = WrpRegistrationData(
            displayName = displayName,
            tradeName = tradeName,
            country = country,
            isPsb = request.isPsb ?: current.wrpRegistration.isPsb,
            isIntermediary = request.isIntermediary ?: current.wrpRegistration.isIntermediary,
            supportUri = supportUri,
            privacyPolicyUri = privacyPolicyUri,
            entitlement = entitlement,
            providerType = providerType,
            supervisoryAuthorityIdentifierType = saIdentifierType,
            supervisoryAuthorityIdentifier = saIdentifier,
        )
        val remoteBaseUrl = normalizeEndpointBaseUrl(request.endpointBaseUrl)
        val remoteWrpIdentifier = if (remoteBaseUrl.isNotBlank()) {
            val wrpPayload = buildWrpRegistrationPayload(wrpRegistration)
            val registrarClient = createRegistrarClient()
            loginToRegistrar(
                client = registrarClient,
                baseUrl = remoteBaseUrl,
                email = request.registrarEmail?.trim().orEmpty(),
                password = request.registrarPassword?.trim().orEmpty(),
            )
            val resolvedWrpIdentifier = try {
                val responseBody = postJson(
                    client = registrarClient,
                    url = "$remoteBaseUrl/api/rp/wrps",
                    body = wrpPayload
                )
                extractWrpIdentifier(responseBody)
                    ?: throw ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "remote registrar did not return wrpIdentifier"
                    )
            } catch (ex: ResponseStatusException) {
                if (ex.statusCode == HttpStatus.CONFLICT && ex.reason?.contains(
                        "wrp already exists",
                        ignoreCase = true
                    ) == true
                ) {
                    findExistingWrpIdentifier(registrarClient, remoteBaseUrl, displayName, tradeName)
                        ?: throw ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "WRP already exists remotely, but identifier could not be resolved via registrar API."
                        )
                } else {
                    throw ex
                }
            }
            runCatching {
                tryLoadWrpacForWrp(registrarClient, remoteBaseUrl, resolvedWrpIdentifier)
            }
            resolvedWrpIdentifier
        } else {
            null
        }
        val wrpRequestStatus = if (remoteBaseUrl.isBlank()) {
            if ((remoteWrpIdentifier ?: current.wrpIdentifier).isNullOrBlank()) "unregistered" else "wrp_registered"
        } else {
            resolveRemoteWrpStatus(
                client = createRegistrarClient(),
                baseUrl = remoteBaseUrl,
                email = request.registrarEmail?.trim().orEmpty(),
                password = request.registrarPassword?.trim().orEmpty(),
                fallbackWrpIdentifier = remoteWrpIdentifier ?: current.wrpIdentifier
            )
        }
        store.saveRegistrationState(
            wrpIdentifier = remoteWrpIdentifier ?: current.wrpIdentifier,
            requestStatus = wrpRequestStatus,
            wrpRegistration = wrpRegistration
        )
        return registrationState()
    }

    @PostMapping("/registration/service")
    fun registerService(
        @RequestBody request: RegisterServiceRequest,
    ): RegistrationStateResponse {
        val serviceUri = request.serviceUri.trim()
        if (!(serviceUri.startsWith("http://") || serviceUri.startsWith("https://"))) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "serviceUri must start with http:// or https://")
        }
        val current = store.loadState()
        val credentials = if (request.credentials.isNotEmpty()) {
            request.credentials.map {
                ServiceCredentialSelection(
                    credentialType = it.credentialType,
                    format = it.format,
                    claims = it.claims.map(String::trim).filter(String::isNotBlank)
                )
            }
        } else {
            current.serviceRegistration.credentials
        }
        val serviceRegistration = ServiceRegistrationData(
            serviceName = request.serviceName?.trim().orEmpty().ifBlank { current.serviceRegistration.serviceName },
            serviceUri = serviceUri,
            purpose = request.purpose?.trim().orEmpty().ifBlank { current.serviceRegistration.purpose },
            credentials = credentials
        )
        val remoteBaseUrl = normalizeEndpointBaseUrl(request.endpointBaseUrl)
        if (remoteBaseUrl.isNotBlank()) {
            val wrpIdentifier = current.wrpIdentifier
                ?: throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "WRP must be registered before service registration"
                )
            val intendedUse = buildIntendedUse(serviceRegistration)
            val registrarClient = createRegistrarClient()
            loginToRegistrar(
                client = registrarClient,
                baseUrl = remoteBaseUrl,
                email = request.registrarEmail?.trim().orEmpty(),
                password = request.registrarPassword?.trim().orEmpty(),
            )
            if (!store.hasWrpac()) {
                val loaded = tryLoadWrpacForWrp(registrarClient, remoteBaseUrl, wrpIdentifier)
                if (!loaded || !store.hasWrpac()) {
                    throw ResponseStatusException(
                        HttpStatus.PRECONDITION_FAILED,
                        "WRPAC is required before service registration. Register WRP, issue/approve WRPAC in registrar, then retry."
                    )
                }
            }
            val responseBody = postJson(
                client = registrarClient,
                url = "$remoteBaseUrl/api/rp/wrps/$wrpIdentifier/services",
                body = mapOf(
                    "serviceUri" to serviceRegistration.serviceUri,
                    "displayName" to serviceRegistration.serviceName,
                    "intendedUse" to intendedUse,
                )
            )
            val remoteServiceStatus = extractStatus(responseBody)
            val localServiceStatus = when (remoteServiceStatus?.uppercase()) {
                "PENDING" -> "service_pending"
                "APPROVED" -> "service_registered"
                else -> "unknown"
            }
            store.saveRegistrationState(
                serviceUri = serviceUri,
                requestStatus = localServiceStatus,
                serviceRegistration = serviceRegistration
            )
            return registrationState()
        }
        store.saveRegistrationState(
            serviceUri = serviceUri,
            requestStatus = "service_registered",
            serviceRegistration = serviceRegistration
        )
        return registrationState()
    }

    @PostMapping("/registration/load-wrprc")
    fun loadWrprcFromUrl(
        @RequestBody request: LoadWrprcRequest,
    ): RegistrationStateResponse {
        val source = request.url.trim()
        if (!(source.startsWith("http://") || source.startsWith("https://"))) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "url must start with http:// or https://")
        }
        val remoteBaseUrl = normalizeEndpointBaseUrl(request.endpointBaseUrl)
        val fetched = if (remoteBaseUrl.isNotBlank() && source.startsWith(remoteBaseUrl, ignoreCase = true)) {
            val registrarClient = createRegistrarClient()
            loginToRegistrar(
                client = registrarClient,
                baseUrl = remoteBaseUrl,
                email = request.registrarEmail?.trim().orEmpty(),
                password = request.registrarPassword?.trim().orEmpty(),
            )
            fetchText(registrarClient, source)
        } else {
            fetchText(source)
        }
        val jws = resolveWrprcJws(fetched)
        if (jws.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "received empty response from URL")
        }
        store.saveWrprc(jws)
        val current = store.loadState()
        val status = when {
            !current.wrpIdentifier.isNullOrBlank() && !current.serviceUri.isNullOrBlank() -> "service_registered"
            !current.wrpIdentifier.isNullOrBlank() -> "wrp_registered"
            else -> "unregistered"
        }
        store.saveRegistrationState(requestStatus = status)
        return registrationState()
    }

    @PostMapping("/registration/load-wrpac")
    fun loadWrpacFromUrl(
        @RequestBody request: LoadWrpacRequest,
    ): RegistrationStateResponse {
        val source = request.url.trim()
        if (!(source.startsWith("http://") || source.startsWith("https://"))) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "url must start with http:// or https://")
        }
        val remoteBaseUrl = normalizeEndpointBaseUrl(request.endpointBaseUrl)
        val fetched = if (remoteBaseUrl.isNotBlank() && source.startsWith(remoteBaseUrl, ignoreCase = true)) {
            val registrarClient = createRegistrarClient()
            loginToRegistrar(
                client = registrarClient,
                baseUrl = remoteBaseUrl,
                email = request.registrarEmail?.trim().orEmpty(),
                password = request.registrarPassword?.trim().orEmpty(),
            )
            fetchText(registrarClient, source)
        } else {
            fetchText(source)
        }
        val payload = runCatching { json.decodeFromString(RemoteWrpacPayload.serializer(), fetched) }.getOrNull()
        if (persistWrpac(fetched, payload)) {
            val current = store.loadState()
            if (!current.wrpIdentifier.isNullOrBlank()) {
                val status = if (current.serviceUri.isNullOrBlank()) "wrp_registered" else "service_registered"
                store.saveRegistrationState(requestStatus = status)
            }
            return registrationState()
        }
        throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "WRPAC endpoint must return JSON (chainPem/keyStoreBase64) or a PEM certificate chain"
        )
    }

    @PostMapping("/registration/issue-wrpac")
    fun issueWrpac(
        @RequestBody request: IssueWrpacRequest,
    ): RegistrationStateResponse {
        val remoteBaseUrl = normalizeEndpointBaseUrl(request.endpointBaseUrl)
        if (remoteBaseUrl.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "endpointBaseUrl is required")
        }
        val current = store.loadState()
        val wrpIdentifier = current.wrpIdentifier
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "WRP must be registered before issuing WRPAC")

        val providerUrl = request.wrpacProviderUrl?.trim().orEmpty().ifBlank { "http://localhost:8081/access-cert/csr" }
        if (!(providerUrl.startsWith("http://") || providerUrl.startsWith("https://"))) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "wrpacProviderUrl must start with http:// or https://"
            )
        }
        val providerUser = request.wrpacProviderUser?.trim().orEmpty().ifBlank { "user" }
        val providerPassword = request.wrpacProviderPassword?.trim().orEmpty().ifBlank { "password" }

        val keyPair = generateRsaKeyPair()
        val csrPem = buildCsrPem(current.wrpRegistration, keyPair)
        val csrThumbprint = computeCsrThumbprint(csrPem)

        val registrarClient = createRegistrarClient()
        loginToRegistrar(
            client = registrarClient,
            baseUrl = remoteBaseUrl,
            email = request.registrarEmail?.trim().orEmpty(),
            password = request.registrarPassword?.trim().orEmpty(),
        )
        val popToken = requestPopToken(
            client = registrarClient,
            baseUrl = remoteBaseUrl,
            wrpIdentifier = wrpIdentifier,
            csrThumbprint = csrThumbprint,
        )

        val providerResponse = try {
            postJsonBasicAuth(
                url = providerUrl,
                username = providerUser,
                password = providerPassword,
                body = mapOf(
                    "wrpIdentifier" to wrpIdentifier,
                    "csrPem" to csrPem,
                    "popToken" to popToken,
                )
            )
        } catch (ex: ResponseStatusException) {
            if (ex.statusCode == HttpStatus.CONFLICT && ex.reason == "wrpac_already_issued") {
                val fetched = tryLoadWrpacForWrp(registrarClient, remoteBaseUrl, wrpIdentifier)
                if (!fetched || !store.hasWrpac()) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "WRPAC already issued by provider, but fetch from registrar failed. Use 'Fetch Existing WRPAC'."
                    )
                }
                val status = if (current.serviceUri.isNullOrBlank()) "wrp_registered" else "service_registered"
                store.saveRegistrationState(requestStatus = status)
                return registrationState()
            }
            throw ex
        }
        try {
            val chainPem = resolveChainPemFromWrpacResponse(providerResponse)
            val certificateChain = parsePemCertificates(chainPem)
            if (certificateChain.isEmpty()) {
                throw ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "WRPAC provider response did not contain a valid certificate chain"
                )
            }
            val orderedChain = orderChainFromLeaf(certificateChain)
            val leaf = orderedChain.first()
            ensureLeafMatchesKeyPair(leaf.publicKey, keyPair.public)
            val thumbprint = computeLeafThumbprint(leaf)
            val chainPemNormalized = chainToPem(orderedChain)
            try {
                val keyStoreBytes = buildPkcs12FromKeyAndChain(keyPair.private, orderedChain)
                store.saveWrpac(
                    chainPem = chainPemNormalized,
                    keyStoreBytes = keyStoreBytes,
                    thumbprint = thumbprint,
                )
            } catch (_: ResponseStatusException) {
                // Keep demo flow usable even if provider chain cannot be imported into PKCS12 locally.
                store.saveWrpacChainOnly(chainPemNormalized, thumbprint)
            }
        } catch (ex: ResponseStatusException) {
            throw ex
        } catch (ex: Exception) {
            throw ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "WRPAC issuance processing failed: ${ex.message ?: ex::class.simpleName}"
            )
        }
        val status = if (current.serviceUri.isNullOrBlank()) "wrp_registered" else "service_registered"
        store.saveRegistrationState(requestStatus = status)
        return registrationState()
    }

    @PostMapping("/registration/issue-wrprc")
    fun issueWrprc(
        @RequestBody request: IssueWrprcRequest,
    ): RegistrationStateResponse {
        val remoteBaseUrl = normalizeEndpointBaseUrl(request.endpointBaseUrl)
        if (remoteBaseUrl.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "endpointBaseUrl is required")
        }
        val current = store.loadState()
        val wrpIdentifier = current.wrpIdentifier
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "WRP must be registered before issuing WRPRC")
        val serviceUri = current.serviceUri?.takeIf { it.isNotBlank() }
            ?: if (current.requestStatus?.startsWith("service_") == true) {
                current.serviceRegistration.serviceUri.takeIf { it.isNotBlank() }
            } else {
                null
            }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Service must be registered before issuing WRPRC")

        val maxAttempts = (request.maxAttempts ?: 20).coerceIn(1, 120)
        val pollIntervalMs = (request.pollIntervalMs ?: 2000L).coerceIn(250L, 15000L)
        val registrarClient = createRegistrarClient()
        loginToRegistrar(
            client = registrarClient,
            baseUrl = remoteBaseUrl,
            email = request.registrarEmail?.trim().orEmpty(),
            password = request.registrarPassword?.trim().orEmpty(),
        )

        val encodedWrp = URLEncoder.encode(wrpIdentifier, Charsets.UTF_8)
        val encodedServiceUri = URLEncoder.encode(serviceUri, Charsets.UTF_8)
        val wrprcUrl = "${remoteBaseUrl.trimEnd('/')}/api/rp/wrps/$encodedWrp/wrprc?serviceUri=$encodedServiceUri"

        var lastPendingReason = "WRPRC not available yet."
        repeat(maxAttempts) { attempt ->
            try {
                val fetched = fetchText(registrarClient, wrprcUrl)
                val jws = resolveWrprcJws(fetched)
                if (jws.isBlank()) {
                    throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "WRPRC endpoint returned empty content")
                }
                store.saveWrprc(jws)
                store.saveRegistrationState(requestStatus = "service_registered")
                return registrationState()
            } catch (ex: ResponseStatusException) {
                val reason = ex.reason.orEmpty()
                if (reason.contains("WRPRC not available yet", ignoreCase = true)) {
                    lastPendingReason = reason
                    if (attempt < maxAttempts - 1) {
                        Thread.sleep(pollIntervalMs)
                        return@repeat
                    }
                    throw ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "$lastPendingReason Timed out after $maxAttempts attempts; approve service in registrar admin and retry."
                    )
                }
                throw ex
            }
        }
        throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "WRPRC polling failed")
    }

    @PostMapping("/registration/refresh")
    fun refreshRegistrationState(
        @RequestBody request: RefreshRegistrationRequest,
    ): RegistrationStateResponse {
        val remoteBaseUrl = normalizeEndpointBaseUrl(request.endpointBaseUrl)
        if (remoteBaseUrl.isBlank()) {
            return registrationState()
        }

        val current = store.loadState()
        val wrpIdentifier = current.wrpIdentifier
        if (wrpIdentifier.isNullOrBlank()) {
            store.saveRegistrationState(requestStatus = "unregistered")
            return registrationState()
        }

        val wrpNode = try {
            val registrarClient = createRegistrarClient()
            loginToRegistrar(
                client = registrarClient,
                baseUrl = remoteBaseUrl,
                email = request.registrarEmail?.trim().orEmpty(),
                password = request.registrarPassword?.trim().orEmpty(),
            )
            val wrpsBody = fetchText(registrarClient, "${remoteBaseUrl.trimEnd('/')}/api/rp/wrps")
            val parsed = runCatching { json.parseToJsonElement(wrpsBody) }.getOrNull()
                ?: throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "remote registrar returned invalid WRP list JSON")
            findWrpNodeByIdentifier(parsed, wrpIdentifier)
        } catch (ex: ResponseStatusException) {
            val reason = ex.reason ?: ex.message.orEmpty()
            if (reason.contains("required endpoint not reachable", ignoreCase = true)) {
                store.saveState(
                    current.copy(
                        requestStatus = "unknown",
                    )
                )
                return registrationState()
            }
            throw ex
        }

        if (wrpNode == null) {
            // WRP entry is not present in the current registrar response snapshot.
            // Keep local linkage and mark status unknown until a later refresh confirms state.
            store.saveState(
                current.copy(
                    requestStatus = "unknown",
                )
            )
            return registrationState()
        }

        val remoteWrpStatus = extractStatus(json.encodeToString(JsonElement.serializer(), wrpNode))
        if (remoteWrpStatus.equals("PENDING", ignoreCase = true)) {
            store.saveState(
                current.copy(
                    serviceUri = null,
                    requestStatus = "wrp_pending",
                )
            )
            return registrationState()
        }

        val currentServiceUri = current.serviceUri?.takeIf { it.isNotBlank() }
            ?: if (current.requestStatus?.startsWith("service_") == true) {
                current.serviceRegistration.serviceUri.takeIf { it.isNotBlank() }
            } else {
                null
            }
        if (currentServiceUri.isNullOrBlank()) {
            store.saveRegistrationState(requestStatus = "wrp_registered")
            return registrationState()
        }

        val remoteServiceNode = findServiceNodeByUri(wrpNode, currentServiceUri)
        if (remoteServiceNode == null) {
            // Service entry is not present in the current registrar response snapshot.
            // Keep local linkage and mark status unknown until a later refresh confirms state.
            store.saveState(
                current.copy(
                    requestStatus = "unknown",
                )
            )
            return registrationState()
        }

        val remoteServiceStatus = extractStatus(json.encodeToString(JsonElement.serializer(), remoteServiceNode))
        val localServiceStatus = when (remoteServiceStatus?.uppercase()) {
            "PENDING" -> "service_pending"
            "APPROVED" -> "service_registered"
            else -> "unknown"
        }
        store.saveRegistrationState(requestStatus = localServiceStatus)
        return registrationState()
    }

    private fun persistWrpac(fetched: String, payload: RemoteWrpacPayload? = null): Boolean {
        val parsedPayload =
            payload ?: runCatching { json.decodeFromString(RemoteWrpacPayload.serializer(), fetched) }.getOrNull()
        if (parsedPayload != null) {
            val chainPem = parsedPayload.chainPem.trim()
            if (chainPem.isBlank()) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "WRPAC chainPem is empty")
            }
            val keyStoreBytes = try {
                Base64.getDecoder().decode(parsedPayload.keyStoreBase64.trim())
            } catch (ex: IllegalArgumentException) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "WRPAC keyStoreBase64 is not valid Base64")
            }
            store.saveWrpac(
                chainPem = chainPem,
                keyStoreBytes = keyStoreBytes,
                thumbprint = parsedPayload.thumbprint?.trim().orEmpty(),
            )
            return true
        }
        val chainPem = fetched.trim()
        if (!chainPem.contains("-----BEGIN CERTIFICATE-----")) {
            return false
        }
        store.saveWrpacChainOnly(chainPem)
        return true
    }

    private fun tryLoadWrpacForWrp(client: HttpClient, remoteBaseUrl: String, wrpIdentifier: String): Boolean {
        val encodedWrp = URLEncoder.encode(wrpIdentifier, Charsets.UTF_8)
        val wrpacUrl = "${remoteBaseUrl.trimEnd('/')}/api/rp/wrps/$encodedWrp/wrpac"
        return try {
            val fetched = fetchText(client, wrpacUrl)
            persistWrpac(fetched)
        } catch (_: ResponseStatusException) {
            false
        }
    }

    private fun findExistingWrpIdentifier(
        client: HttpClient,
        remoteBaseUrl: String,
        displayName: String,
        tradeName: String,
    ): String? {
        val body = fetchText(client, "${remoteBaseUrl.trimEnd('/')}/api/rp/wrps")
        val parsed = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return null
        return findWrpIdentifier(parsed, displayName, tradeName)
    }

    private fun findWrpIdentifier(node: JsonElement, displayName: String, tradeName: String): String? {
        return when (node) {
            is JsonObject -> {
                val currentDisplay = readStringField(node, "displayName") ?: readStringField(node, "tradeName")
                val id = extractWrpIdentifier(json.encodeToString(JsonElement.serializer(), node))
                if (!id.isNullOrBlank() && !currentDisplay.isNullOrBlank()) {
                    val matches = currentDisplay.equals(displayName, ignoreCase = true) ||
                            currentDisplay.equals(tradeName, ignoreCase = true)
                    if (matches) {
                        return id
                    }
                }
                node.values.firstNotNullOfOrNull { child -> findWrpIdentifier(child, displayName, tradeName) }
            }

            is JsonArray -> node.firstNotNullOfOrNull { child -> findWrpIdentifier(child, displayName, tradeName) }
            else -> null
        }
    }

    private fun findWrpNodeByIdentifier(node: JsonElement, wrpIdentifier: String): JsonObject? {
        return when (node) {
            is JsonObject -> {
                val directId = readStringField(node, "wrpIdentifier")
                    ?: readStringField(node, "identifier")
                    ?: readStringField(node, "id")
                if (directId.equals(wrpIdentifier, ignoreCase = true)) {
                    node
                } else {
                    node.values.firstNotNullOfOrNull { child -> findWrpNodeByIdentifier(child, wrpIdentifier) }
                }
            }

            is JsonArray -> node.firstNotNullOfOrNull { child -> findWrpNodeByIdentifier(child, wrpIdentifier) }
            else -> null
        }
    }

    private fun findServiceNodeByUri(node: JsonElement, serviceUri: String): JsonObject? {
        return when (node) {
            is JsonObject -> {
                val directUri = readStringField(node, "serviceUri")
                    ?: readStringField(node, "uri")
                if (directUri.equals(serviceUri, ignoreCase = true)) {
                    node
                } else {
                    node.values.firstNotNullOfOrNull { child -> findServiceNodeByUri(child, serviceUri) }
                }
            }

            is JsonArray -> node.firstNotNullOfOrNull { child -> findServiceNodeByUri(child, serviceUri) }
            else -> null
        }
    }

    private fun postJson(client: HttpClient, url: String, body: Any): String {
        val response = try {
            val jsonBody = toJsonElement(body)
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(JsonElement.serializer(), jsonBody)))
                .build()
            client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (ex: Exception) {
            throw ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "required endpoint not reachable: $url (${ex::class.simpleName ?: "request_error"})"
            )
        }
        if (response.statusCode() !in 200..299) {
            val bodyText = response.body().take(300)
            if (response.statusCode() == 409 && bodyText.contains("wrp_exists", ignoreCase = true)) {
                throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "remote registrar rejected request: wrp already exists"
                )
            }
            throw ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "remote endpoint failed ($url): HTTP ${response.statusCode()} $bodyText"
            )
        }
        return response.body().trim()
    }

    private fun postJsonBasicAuth(url: String, username: String, password: String, body: Any): String {
        val response = try {
            val jsonBody = toJsonElement(body)
            val authValue = Base64.getEncoder().encodeToString("$username:$password".toByteArray(Charsets.UTF_8))
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .header("Authorization", "Basic $authValue")
                .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(JsonElement.serializer(), jsonBody)))
                .build()
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (ex: Exception) {
            throw ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "required endpoint not reachable: $url (${ex::class.simpleName ?: "request_error"})"
            )
        }
        if (response.statusCode() !in 200..299) {
            if (response.statusCode() == 409 && response.body().contains("wrpac_already_issued", ignoreCase = true)) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "wrpac_already_issued")
            }
            throw ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "remote endpoint failed ($url): HTTP ${response.statusCode()} ${response.body().take(300)}"
            )
        }
        return response.body().trim()
    }

    @PostMapping("/wrprc")
    fun saveWrprc(
        @RequestBody request: SaveWrprcRequest,
    ) {
        store.saveWrprc(request.jws.trim())
    }

    @DeleteMapping("/wrprc")
    fun deleteWrprc() {
        store.deleteWrprc()
    }

    @PostMapping(
        value = ["/wrpac"],
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]
    )
    fun saveWrpac(
        @RequestPart("chainPem") chainPem: String,
        @RequestPart("keyStore") keyStore: MultipartFile,
        @RequestPart("thumbprint", required = false) thumbprint: String?,
    ) {
        store.saveWrpac(
            chainPem = chainPem.trim(),
            keyStoreBytes = keyStore.bytes,
            thumbprint = thumbprint?.trim().orEmpty(),
        )
    }

    @DeleteMapping("/wrpac")
    fun deleteWrpac() {
        store.deleteWrpac()
    }

    private fun fetchText(url: String): String = fetchText(httpClient, url)

    private fun fetchText(client: HttpClient, url: String): String {
        val response = try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build()
            client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (ex: Exception) {
            throw ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "required endpoint not reachable: $url (${ex::class.simpleName ?: "request_error"})"
            )
        }
        val status = response.statusCode()
        if (status !in 200..299) {
            val body = response.body().trim()
            val redirectedToLogin = status in 300..399 && (
                    body.contains("/rp/login", ignoreCase = true) ||
                            body.contains("name=\"email\"", ignoreCase = true)
                    )
            val detail = when {
                redirectedToLogin ->
                    "registrar authentication required for certificate retrieval. Log in to registrar and retry after admin approval/issuance."

                status == 404 && url.contains("/wrpac", ignoreCase = true) ->
                    "WRPAC not available yet. Issue WRPAC first (registrar/provider) and retry."

                status == 400 && url.contains("/wrprc", ignoreCase = true) ->
                    "WRPRC not available yet. Service is likely pending or not approved; approve it in registrar admin and retry."

                status == 404 && url.contains("/wrprc", ignoreCase = true) ->
                    "WRPRC not available yet. Service is likely pending; approve it in registrar admin and retry."

                status in listOf(401, 403) ->
                    "endpoint requires authentication: $url"

                else ->
                    "remote endpoint failed ($url): HTTP $status ${body.take(200)}"
            }
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, detail)
        }
        return response.body()
    }

    private fun createRegistrarClient(): HttpClient {
        val cookieManager = CookieManager()
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL)
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .cookieHandler(cookieManager)
            .build()
    }

    private fun loginToRegistrar(client: HttpClient, baseUrl: String, email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "registrarEmail and registrarPassword are required for registrar authenticated requests"
            )
        }
        postForm(
            client = client,
            url = "${baseUrl.trimEnd('/')}/rp/login",
            form = mapOf(
                "email" to email,
                "password" to password,
            )
        )
    }

    private fun postForm(client: HttpClient, url: String, form: Map<String, String>): String {
        val response = try {
            val formBody = form.entries.joinToString("&") { entry ->
                "${URLEncoder.encode(entry.key, Charsets.UTF_8)}=${URLEncoder.encode(entry.value, Charsets.UTF_8)}"
            }
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build()
            client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (ex: Exception) {
            throw ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "required endpoint not reachable: $url (${ex::class.simpleName ?: "request_error"})"
            )
        }
        if (response.statusCode() !in 200..299) {
            throw ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "remote endpoint failed ($url): HTTP ${response.statusCode()} ${response.body().take(300)}"
            )
        }
        val body = response.body()
        if (body.contains("Invalid credentials", ignoreCase = true)) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "registrar login failed (invalid credentials)")
        }
        if (body.contains("/rp/login", ignoreCase = true) && body.contains("name=\"email\"", ignoreCase = true)) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "registrar login failed")
        }
        if (body.contains("already exists", ignoreCase = true)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "remote registrar rejected request: identifier already exists"
            )
        }
        return body
    }

    private fun normalizeEndpointBaseUrl(endpointBaseUrl: String?): String {
        val normalized = endpointBaseUrl?.trim().orEmpty().trimEnd('/')
        if (normalized.isBlank()) {
            return ""
        }
        if (!(normalized.startsWith("http://") || normalized.startsWith("https://"))) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "endpointBaseUrl must start with http:// or https://")
        }
        return normalized
    }

    private fun requestPopToken(
        client: HttpClient,
        baseUrl: String,
        wrpIdentifier: String,
        csrThumbprint: String,
    ): String {
        val encodedWrp = URLEncoder.encode(wrpIdentifier, Charsets.UTF_8)
        val response = postJson(
            client = client,
            url = "${baseUrl.trimEnd('/')}/api/public/wrps/$encodedWrp/pop",
            body = mapOf("csrThumbprint" to csrThumbprint)
        )
        val token = runCatching { json.parseToJsonElement(response) }.getOrNull()
            ?.let { element ->
                when (element) {
                    is JsonObject -> readStringField(element, "token")
                    is JsonPrimitive -> element.contentOrNull?.trim()?.ifBlank { null }
                    else -> null
                }
            }
        return token ?: throw ResponseStatusException(
            HttpStatus.BAD_GATEWAY,
            "registrar PoP endpoint did not return token"
        )
    }

    private fun generateRsaKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        return generator.generateKeyPair()
    }

    private fun buildCsrPem(wrp: WrpRegistrationData, keyPair: KeyPair): String {
        val commonName = wrp.displayName.trim().ifBlank { "WRP Endpoint" }
        val organization = wrp.tradeName.trim().ifBlank { commonName }
        val country = wrp.country.trim().ifBlank { "AT" }.uppercase()
        val x500 = X500Name("CN=$commonName,O=$organization,C=$country")
        val csrBuilder = JcaPKCS10CertificationRequestBuilder(x500, keyPair.public)
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val csr = csrBuilder.build(signer)
        val out = StringWriter()
        JcaPEMWriter(out).use { writer ->
            writer.writeObject(csr)
        }
        return out.toString()
    }

    private fun computeCsrThumbprint(csrPem: String): String {
        val pemContent = csrPem
            .replace("-----BEGIN CERTIFICATE REQUEST-----", "")
            .replace("-----END CERTIFICATE REQUEST-----", "")
            .replace("\\s".toRegex(), "")
        val csrDer = try {
            Base64.getDecoder().decode(pemContent)
        } catch (_: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "generated CSR could not be encoded")
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(csrDer)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun resolveChainPemFromWrpacResponse(responseBody: String): String {
        if (responseBody.contains("-----BEGIN CERTIFICATE-----")) {
            return responseBody.trim()
        }
        val parsed = runCatching { json.parseToJsonElement(responseBody) }.getOrNull()
        if (parsed is JsonObject) {
            val chainPem = readStringField(parsed, "chainPem")
                ?: readStringField(parsed, "certificateChainPem")
                ?: readStringField(parsed, "pem")
            if (!chainPem.isNullOrBlank() && chainPem.contains("-----BEGIN CERTIFICATE-----")) {
                return chainPem.trim()
            }
        }
        throw ResponseStatusException(
            HttpStatus.BAD_GATEWAY,
            "WRPAC provider response did not include PEM certificate chain"
        )
    }

    private fun parsePemCertificates(pem: String): List<JcaX509Certificate> {
        val factory = CertificateFactory.getInstance("X.509")
        return pem.split("-----END CERTIFICATE-----")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { block ->
                val normalized = "$block\n-----END CERTIFICATE-----\n"
                factory.generateCertificate(ByteArrayInputStream(normalized.toByteArray(Charsets.UTF_8))) as JcaX509Certificate
            }
    }

    private fun orderChainFromLeaf(chain: List<JcaX509Certificate>): List<JcaX509Certificate> {
        if (chain.size <= 1) {
            return chain
        }
        val bySubject = chain.associateBy { it.subjectX500Principal.name }
        val leaves = chain.filter { cert ->
            chain.none { other -> other !== cert && other.subjectX500Principal == cert.issuerX500Principal }
        }
        val leaf = leaves.firstOrNull() ?: chain.first()
        val ordered = mutableListOf<JcaX509Certificate>()
        val visited = mutableSetOf<String>()
        var current: JcaX509Certificate? = leaf
        while (current != null) {
            val subject = current.subjectX500Principal.name
            if (!visited.add(subject)) break
            ordered += current
            if (current.subjectX500Principal == current.issuerX500Principal) break
            current = bySubject[current.issuerX500Principal.name]
        }
        if (ordered.size == chain.size) {
            return ordered
        }
        chain.forEach { cert ->
            if (ordered.none { it.subjectX500Principal == cert.subjectX500Principal }) {
                ordered += cert
            }
        }
        return ordered
    }

    private fun ensureLeafMatchesKeyPair(leafPublicKey: PublicKey, generatedPublicKey: PublicKey) {
        if (!leafPublicKey.encoded.contentEquals(generatedPublicKey.encoded)) {
            throw ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "WRPAC provider certificate does not match generated CSR key. Ensure provider uses submitted CSR."
            )
        }
    }

    private fun chainToPem(chain: List<JcaX509Certificate>): String {
        val out = StringWriter()
        JcaPEMWriter(out).use { writer ->
            chain.forEach { cert -> writer.writeObject(cert) }
        }
        return out.toString().trim() + "\n"
    }

    private fun buildPkcs12FromKeyAndChain(privateKey: PrivateKey, chain: List<JcaX509Certificate>): ByteArray {
        val keyStore = try {
            KeyStore.getInstance("PKCS12").apply {
                load(null, null)
                setKeyEntry(
                    "wrpac",
                    privateKey,
                    "changeit".toCharArray(),
                    chain.toTypedArray(),
                )
            }
        } catch (ex: Exception) {
            throw ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "WRPAC certificate chain could not be stored in PKCS12: ${ex.message ?: ex::class.simpleName}"
            )
        }
        return ByteArrayOutputStream().use { output ->
            keyStore.store(output, "changeit".toCharArray())
            output.toByteArray()
        }
    }

    private fun computeLeafThumbprint(leaf: JcaX509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(leaf.encoded)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun resolveRemoteWrpStatus(
        client: HttpClient,
        baseUrl: String,
        email: String,
        password: String,
        fallbackWrpIdentifier: String?
    ): String {
        return try {
            loginToRegistrar(client, baseUrl, email, password)
            val responseBody = fetchText(client, "${baseUrl.trimEnd('/')}/api/rp/wrps")
            val parsed = runCatching { json.parseToJsonElement(responseBody) }.getOrNull()
            val registration = if (parsed is JsonObject) parsed["registration"] else null
            val status = when (registration) {
                is JsonObject -> extractStatus(json.encodeToString(JsonElement.serializer(), registration))
                else -> null
            } ?: extractStatus(responseBody)
            when (status?.uppercase()) {
                "PENDING" -> "wrp_pending"
                "APPROVED" -> "wrp_registered"
                "SERVICE_PENDING", "SERVICE_REGISTERED" -> status.lowercase()
                else -> if (fallbackWrpIdentifier.isNullOrBlank()) "unregistered" else "wrp_registered"
            }
        } catch (_: Exception) {
            if (fallbackWrpIdentifier.isNullOrBlank()) "unregistered" else "wrp_registered"
        }
    }

    private fun buildWrpRegistrationPayload(wrpRegistration: WrpRegistrationData): Map<String, Any?> {
        val displayName = wrpRegistration.displayName.trim().ifBlank { "A-SIT EUDI Relying Party" }
        val tradeName = wrpRegistration.tradeName.trim().ifBlank { displayName }
        val country = wrpRegistration.country.trim().ifBlank { "AT" }.uppercase()
        val supportUri = wrpRegistration.supportUri.trim().ifBlank { "http://localhost:8080/support" }
        if (!(supportUri.startsWith("http://") || supportUri.startsWith("https://"))) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "supportUri must start with http:// or https://")
        }
        val privacyPolicy = wrpRegistration.privacyPolicyUri.trim().ifBlank { supportUri.trimEnd('/') + "/privacy" }
        if (!(privacyPolicy.startsWith("http://") || privacyPolicy.startsWith("https://"))) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "privacyPolicyUri must start with http:// or https://"
            )
        }
        val identifierValue = wrpRegistration.supervisoryAuthorityIdentifier.trim().ifBlank {
            "REG-" + UUID.randomUUID().toString().replace("-", "").take(16).uppercase()
        }
        val entitlement = wrpRegistration.entitlement
            .map(String::trim)
            .filter(String::isNotBlank)
            .ifEmpty { listOf("urn:eudi:entitlement:age-verification") }
        val identifierType = wrpRegistration.supervisoryAuthorityIdentifierType.trim().ifBlank { "NATIONAL" }

        val data = mapOf(
            "tradeName" to tradeName,
            "supportURI" to listOf(supportUri),
            "srvDescription" to listOf(
                listOf(mapOf("lang" to "en", "content" to "$displayName registration profile"))
            ),
            "intendedUse" to emptyList<Map<String, Any?>>(),
            "isPSB" to wrpRegistration.isPsb,
            "entitlement" to entitlement,
            "providesAttestations" to null,
            "supervisoryAuthority" to mapOf(
                "country" to country,
                "email" to emptyList<String>(),
                "identifier" to listOf(mapOf("identifier" to identifierValue, "type" to identifierType)),
                "infoURI" to listOf(supportUri),
                "legalPerson" to mapOf(
                    "legalName" to listOf(displayName),
                    "establishedByLaw" to emptyList<Map<String, String>>()
                ),
                "naturalPerson" to null,
                "phone" to emptyList<String>(),
                "postalAddress" to emptyList<String>()
            ),
            "usesIntermediary" to if (wrpRegistration.isIntermediary) emptyList<Map<String, Any?>>() else null,
            "isIntermediary" to wrpRegistration.isIntermediary,
            "policy" to listOf(mapOf("policyURI" to privacyPolicy, "type" to "PRIVACY")),
            "providerType" to wrpRegistration.providerType,
            "x5c" to null
        )

        return mapOf(
            "displayName" to displayName,
            "tradeName" to tradeName,
            "country" to country,
            "data" to data
        )
    }

    private fun buildIntendedUse(serviceRegistration: ServiceRegistrationData): List<Map<String, Any?>> {
        val privacyPolicy = serviceRegistration.serviceUri.trimEnd('/') + "/privacy"
        val createdAt = OffsetDateTime.now().toString()
        val credentials = serviceRegistration.credentials
        if (credentials.isEmpty()) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "at least one credential entry is required for service registration"
            )
        }

        val mappedCredentials = credentials.map { credential ->
            val claims = credential.claims
                .map(String::trim)
                .filter(String::isNotBlank)
                .map { claim ->
                    val normalized = if (claim.startsWith("/")) claim else "/$claim"
                    mapOf("path" to normalized, "values" to null)
                }
            if (claims.isEmpty()) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "each credential must define at least one claim path"
                )
            }
            val format = credential.format.trim().ifBlank {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "credential format is required")
            }
            val credentialType = credential.credentialType.trim().ifBlank {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "credential type is required")
            }
            mapOf(
                "format" to format,
                "meta" to credentialType,
                "claim" to claims
            )
        }

        return listOf(
            mapOf(
                "purpose" to listOf(mapOf("lang" to "en", "content" to serviceRegistration.purpose)),
                "privacyPolicy" to listOf(mapOf("policyURI" to privacyPolicy, "type" to "PRIVACY")),
                "createdAt" to createdAt,
                "revokedAt" to null,
                "credential" to mappedCredentials
            )
        )
    }

    private fun extractWrpIdentifier(responseBody: String): String? {
        if (responseBody.isBlank()) return null
        val parsed = runCatching { json.parseToJsonElement(responseBody) }.getOrNull() ?: return null
        if (parsed is JsonObject) {
            val direct = readStringField(parsed, "wrpIdentifier")
                ?: readStringField(parsed, "wrp_id")
                ?: readStringField(parsed, "identifier")
                ?: readStringField(parsed, "id")
            if (!direct.isNullOrBlank()) return direct
        }
        if (parsed is JsonPrimitive) {
            val value = parsed.contentOrNull
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun extractStatus(responseBody: String): String? {
        if (responseBody.isBlank()) return null
        val parsed = runCatching { json.parseToJsonElement(responseBody) }.getOrNull() ?: return null
        if (parsed is JsonObject) {
            val direct = readStringField(parsed, "status")
                ?: (parsed["registration"] as? JsonObject)?.let { readStringField(it, "status") }
            if (!direct.isNullOrBlank()) return direct
        }
        return null
    }

    private fun readStringField(obj: JsonObject, name: String): String? {
        val field = obj[name] ?: return null
        if (field is JsonPrimitive) {
            return field.contentOrNull?.trim()?.ifBlank { null }
        }
        return null
    }

    private fun toJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(
            value.entries
                .filter { it.key is String }
                .associate { it.key as String to toJsonElement(it.value) }
        )
        is List<*> -> JsonArray(value.map { toJsonElement(it) })
        else -> JsonPrimitive(value.toString())
    }

    private fun resolveWrprcJws(body: String): String {
        val trimmed = body.trim()
        if (trimmed.isBlank()) {
            return ""
        }
        return try {
            json.decodeFromString(RemoteWrprcPayload.serializer(), trimmed).jws.trim()
        } catch (_: Exception) {
            trimmed
        }
    }
}
