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
    fun `cert previews expose configured certificate artifacts`() {
        Mockito.`when`(store.certificatePreviews()).thenReturn(
            listOf(
                WrpCertificatePreview(
                    id = -1,
                    label = "WRPAC",
                    category = "wrpac",
                    type = "x509-chain",
                    content = "chain",
                ),
                WrpCertificatePreview(
                    id = 0,
                    label = "Identitätsprofil",
                    category = "wrprc",
                    type = "jws",
                    content = "token",
                )
            )
        )

        mockMvc.get("/api/wrp/certs")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].id") { value(-1) }
                jsonPath("$[0].category") { value("wrpac") }
                jsonPath("$[0].type") { value("x509-chain") }
                jsonPath("$[1].id") { value(0) }
                jsonPath("$[1].category") { value("wrprc") }
                jsonPath("$[1].type") { value("jws") }
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
