package at.asit.wallet.relyingparty

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.get
import java.net.InetSocketAddress

@SpringBootTest
@AutoConfigureMockMvc
class WrpRegistrationControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var store: WrpCertificateStore

    @BeforeEach
    fun setUp() {
        Mockito.`when`(store.loadState()).thenReturn(
            WrpStoredState(
                wrpIdentifier = "WRP-TEST-1",
                serviceUri = null,
                wrpRegistration = WrpRegistrationData(),
                serviceRegistration = ServiceRegistrationData(serviceUri = ""),
            )
        )
        Mockito.`when`(store.hasWrpac()).thenReturn(false)
        Mockito.`when`(store.hasWrprc()).thenReturn(false)
    }

    @Test
    fun `register wrp rejects endpointBaseUrl without scheme`() {
        val result = mockMvc.post("/api/wrp/registration/wrp") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "endpointBaseUrl": "localhost:5000",
                  "displayName": "A-SIT EUDI Relying Party"
                }
            """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
        }.andReturn()

        assertContains(result.resolvedException?.message, "endpointBaseUrl must start with http:// or https://")
    }

    @Test
    fun `register wrp validates supportUri before remote login`() {
        val result = mockMvc.post("/api/wrp/registration/wrp") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "endpointBaseUrl": "http://localhost:1",
                  "registrarEmail": "asit-demo@wrp.test",
                  "registrarPassword": "demo123",
                  "displayName": "A-SIT EUDI Relying Party",
                  "tradeName": "A-SIT EUDI Relying Party",
                  "country": "AT",
                  "supportUri": "ftp://invalid"
                }
            """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
        }.andReturn()

        assertContains(result.resolvedException?.message, "supportUri must start with http:// or https://")
    }

    @Test
    fun `register wrp maps unreachable registrar login to bad gateway`() {
        val result = mockMvc.post("/api/wrp/registration/wrp") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "endpointBaseUrl": "http://localhost:1",
                  "registrarEmail": "asit-demo@wrp.test",
                  "registrarPassword": "demo123",
                  "displayName": "A-SIT EUDI Relying Party",
                  "tradeName": "A-SIT EUDI Relying Party",
                  "country": "AT",
                  "supportUri": "http://localhost:8080/"
                }
            """.trimIndent()
        }.andExpect {
            status { isBadGateway() }
        }.andReturn()

        assertContains(result.resolvedException?.message, "required endpoint not reachable: http://localhost:1/rp/login")
    }

    @Test
    fun `register service rejects empty claims before remote login`() {
        val result = mockMvc.post("/api/wrp/registration/service") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "endpointBaseUrl": "http://localhost:1",
                  "registrarEmail": "asit-demo@wrp.test",
                  "registrarPassword": "demo123",
                  "serviceUri": "http://localhost:8080/custom.html",
                  "serviceName": "Terminal Service Point",
                  "purpose": "Age verification 18+",
                  "credentials": [
                    {
                      "credentialType": "AgeVerification",
                      "format": "vc+sd-jwt",
                      "claims": []
                    }
                  ]
                }
            """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
        }.andReturn()

        assertContains(result.resolvedException?.message, "each credential must define at least one claim path")
    }

    @Test
    fun `load wrpac rejects endpointBaseUrl without scheme`() {
        val result = mockMvc.post("/api/wrp/registration/load-wrpac") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "url": "http://localhost:5000/api/rp/wrps/WRP-1/wrpac",
                  "endpointBaseUrl": "localhost:5000"
                }
            """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
        }.andReturn()

        assertContains(result.resolvedException?.message, "endpointBaseUrl must start with http:// or https://")
    }

    @Test
    fun `registration state defaults to wrp_registered for seeded state`() {
        mockMvc.get("/api/wrp/registration")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("wrp_registered") }
                jsonPath("$.wrpIdentifier") { value("WRP-TEST-1") }
            }
    }

    @Test
    fun `refresh rejects endpointBaseUrl without scheme`() {
        val result = mockMvc.post("/api/wrp/registration/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "endpointBaseUrl": "localhost:5000",
                  "registrarEmail": "asit-demo@wrp.test",
                  "registrarPassword": "demo123"
                }
            """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
        }.andReturn()

        assertContains(result.resolvedException?.message, "endpointBaseUrl must start with http:// or https://")
    }

    @Test
    fun `refresh returns unregistered without remote call when no local wrp identifier`() {
        Mockito.`when`(store.loadState()).thenReturn(
            WrpStoredState(
                wrpIdentifier = null,
                serviceUri = null,
                wrpRegistration = WrpRegistrationData(),
                serviceRegistration = ServiceRegistrationData(),
            )
        )
        val result = mockMvc.post("/api/wrp/registration/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "endpointBaseUrl": "http://localhost:5000",
                  "registrarEmail": "asit-demo@wrp.test",
                  "registrarPassword": "demo123"
                }
            """.trimIndent()
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("unregistered") }
        }.andReturn()

        assertNotNull(result.response.contentAsString)
    }

    @Test
    fun `refresh returns unknown when registrar is unreachable`() {
        val result = mockMvc.post("/api/wrp/registration/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "endpointBaseUrl": "http://localhost:1",
                  "registrarEmail": "asit-demo@wrp.test",
                  "registrarPassword": "demo123"
                }
            """.trimIndent()
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val saveStateInvocation = Mockito.mockingDetails(store).invocations
            .lastOrNull { it.method.name == "saveState" }
        assertNotNull(saveStateInvocation)
        val saved = saveStateInvocation!!.arguments[0] as WrpStoredState
        assertEquals("unknown", saved.requestStatus)
        assertNotNull(result.response.contentAsString)
    }

    @Test
    fun `refresh maps missing remote wrp node to unknown`() {
        val initialState = WrpStoredState(
            wrpIdentifier = "WRP-TEST-1",
            serviceUri = "http://localhost:8080/custom.html",
            requestStatus = "wrp_pending",
            wrpRegistration = WrpRegistrationData(),
            serviceRegistration = ServiceRegistrationData(serviceUri = "http://localhost:8080/custom.html"),
        )
        Mockito.`when`(store.loadState()).thenReturn(initialState)

        val registrar = HttpServer.create(InetSocketAddress(0), 0)
        registrar.createContext("/rp/login") { exchange ->
            val body = "ok"
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        registrar.createContext("/api/rp/wrps") { exchange ->
            val body = """{"ok":true,"registration":{"wrpIdentifier":"WRP-OTHER-1","status":"APPROVED"}}"""
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        registrar.start()

        try {
            val baseUrl = "http://localhost:${registrar.address.port}"
            mockMvc.post("/api/wrp/registration/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = """
                    {
                      "endpointBaseUrl": "$baseUrl",
                      "registrarEmail": "asit-demo@wrp.test",
                      "registrarPassword": "demo123"
                    }
                """.trimIndent()
            }.andExpect {
                status { isOk() }
            }

            val saveStateInvocation = Mockito.mockingDetails(store).invocations
                .lastOrNull { it.method.name == "saveState" }
            assertNotNull(saveStateInvocation)
            val saved = saveStateInvocation!!.arguments[0] as WrpStoredState
            assertEquals("WRP-TEST-1", saved.wrpIdentifier)
            assertEquals("http://localhost:8080/custom.html", saved.serviceUri)
            assertEquals("unknown", saved.requestStatus)
        } finally {
            registrar.stop(0)
        }
    }

    @Test
    fun `refresh maps unknown remote service status to unknown`() {
        val initialState = WrpStoredState(
            wrpIdentifier = "WRP-TEST-1",
            serviceUri = "http://localhost:8080/custom.html",
            requestStatus = "service_pending",
            wrpRegistration = WrpRegistrationData(),
            serviceRegistration = ServiceRegistrationData(serviceUri = "http://localhost:8080/custom.html"),
        )
        Mockito.`when`(store.loadState()).thenReturn(initialState)

        val registrar = HttpServer.create(InetSocketAddress(0), 0)
        registrar.createContext("/rp/login") { exchange ->
            val body = "ok"
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        registrar.createContext("/api/rp/wrps") { exchange ->
            val body = """
                {
                  "wrps": [
                    {
                      "wrpIdentifier": "WRP-TEST-1",
                      "status": "APPROVED",
                      "services": [
                        {
                          "serviceUri": "http://localhost:8080/custom.html",
                          "status": "UNRECOGNIZED"
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent()
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        registrar.start()

        try {
            val baseUrl = "http://localhost:${registrar.address.port}"
            mockMvc.post("/api/wrp/registration/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = """
                    {
                      "endpointBaseUrl": "$baseUrl",
                      "registrarEmail": "asit-demo@wrp.test",
                      "registrarPassword": "demo123"
                    }
                """.trimIndent()
            }.andExpect {
                status { isOk() }
            }

            val saveRegistrationInvocation = Mockito.mockingDetails(store).invocations
                .lastOrNull { it.method.name == "saveRegistrationState" }
            assertNotNull(saveRegistrationInvocation)
            assertTrue(saveRegistrationInvocation!!.arguments.contains("unknown"))
        } finally {
            registrar.stop(0)
        }
    }

    @Test
    fun `refresh maps missing remote service node to unknown`() {
        val initialState = WrpStoredState(
            wrpIdentifier = "WRP-TEST-1",
            serviceUri = "http://localhost:8080/custom.html",
            requestStatus = "service_pending",
            wrpRegistration = WrpRegistrationData(),
            serviceRegistration = ServiceRegistrationData(serviceUri = "http://localhost:8080/custom.html"),
        )
        Mockito.`when`(store.loadState()).thenReturn(initialState)

        val registrar = HttpServer.create(InetSocketAddress(0), 0)
        registrar.createContext("/rp/login") { exchange ->
            val body = "ok"
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        registrar.createContext("/api/rp/wrps") { exchange ->
            val body = """
                {
                  "wrps": [
                    {
                      "wrpIdentifier": "WRP-TEST-1",
                      "status": "APPROVED",
                      "services": []
                    }
                  ]
                }
            """.trimIndent()
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        registrar.start()

        try {
            val baseUrl = "http://localhost:${registrar.address.port}"
            mockMvc.post("/api/wrp/registration/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = """
                    {
                      "endpointBaseUrl": "$baseUrl",
                      "registrarEmail": "asit-demo@wrp.test",
                      "registrarPassword": "demo123"
                    }
                """.trimIndent()
            }.andExpect {
                status { isOk() }
            }

            val saveStateInvocation = Mockito.mockingDetails(store).invocations
                .lastOrNull { it.method.name == "saveState" }
            assertNotNull(saveStateInvocation)
            val saved = saveStateInvocation!!.arguments[0] as WrpStoredState
            assertEquals("unknown", saved.requestStatus)
        } finally {
            registrar.stop(0)
        }
    }

    @Test
    fun `issue wrpac rejects provider url without scheme`() {
        val result = mockMvc.post("/api/wrp/registration/issue-wrpac") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "endpointBaseUrl": "http://localhost:5000",
                  "registrarEmail": "asit-demo@wrp.test",
                  "registrarPassword": "demo123",
                  "wrpacProviderUrl": "localhost:8081/access-cert/csr"
                }
            """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
        }.andReturn()

        assertContains(result.resolvedException?.message, "wrpacProviderUrl must start with http:// or https://")
    }

    @Test
    fun `issue wrprc requires service registration`() {
        Mockito.`when`(store.loadState()).thenReturn(
            WrpStoredState(
                wrpIdentifier = "WRP-TEST-1",
                serviceUri = null,
                wrpRegistration = WrpRegistrationData(),
                serviceRegistration = ServiceRegistrationData(serviceUri = ""),
            )
        )

        val result = mockMvc.post("/api/wrp/registration/issue-wrprc") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "endpointBaseUrl": "http://localhost:5000",
                  "registrarEmail": "asit-demo@wrp.test",
                  "registrarPassword": "demo123"
                }
            """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
        }.andReturn()

        assertContains(result.resolvedException?.message, "Service must be registered before issuing WRPRC")
    }

    private fun assertContains(message: String?, expectedFragment: String) {
        assertNotNull(message)
        assertTrue(message!!.contains(expectedFragment), "Expected '$expectedFragment' in '$message'")
    }
}
