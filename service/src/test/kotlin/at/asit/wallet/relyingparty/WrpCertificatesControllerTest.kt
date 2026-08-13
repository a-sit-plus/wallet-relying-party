package at.asit.wallet.relyingparty

import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class WrpCertificatesControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var store: WrpCertificateStore

    @Test
    fun `access certificate preview exposes configured certificate artifact`() {
        Mockito.`when`(store.accessCertificatePreview()).thenReturn(
            WrpPreviewData(
                label = "wrpac",
                content = "chain",
            )
        )

        mockMvc.get("/api/wrp/certs/access")
            .andExpect {
                status { isOk() }
                jsonPath("$.label") { value("wrpac") }
                jsonPath("$.content") { value("chain") }
            }
    }

    @Test
    fun `registration certificate preview exposes configured certificate artifacts`() {
        Mockito.`when`(store.registrationCertificatePreview()).thenReturn(
            listOf(
                RegistrationCertificatePreviewData(
                    id = 0,
                    label = "Identitätsprofil",
                    content = "token",
                )
            )
        )

        mockMvc.get("/api/wrp/certs/registrations")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].id") { value(0) }
                jsonPath("$[0].label") { value("Identitätsprofil") }
                jsonPath("$[0].content") { value("token") }
            }
    }

    @Test
    fun `availability exposes current certificate flags`() {
        Mockito.`when`(store.hasCertificateChain()).thenReturn(true)
        Mockito.`when`(store.hasRegistrationCertificates).thenReturn(true)

        mockMvc.get("/api/wrp/availability")
            .andExpect {
                status { isOk() }
                jsonPath("$.hasCertificateChain") { value(true) }
                jsonPath("$.hasRegistrationCertificates") { value(true) }
            }
    }
}
