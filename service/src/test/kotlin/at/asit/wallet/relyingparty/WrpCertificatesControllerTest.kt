package at.asit.wallet.relyingparty

import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
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
                    id = "wrpac",
                    label = "WRPAC",
                    type = "x509-chain",
                    content = "chain",
                ),
                WrpCertificatePreview(
                    id = "wrprc",
                    label = "WRPRC",
                    type = "jws",
                    content = "token",
                )
            )
        )

        mockMvc.get("/api/wrp/certs")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].id") { value("wrpac") }
                jsonPath("$[0].type") { value("x509-chain") }
                jsonPath("$[1].id") { value("wrprc") }
                jsonPath("$[1].type") { value("jws") }
            }
    }

    @Test
    fun `availability exposes current certificate flags`() {
        Mockito.`when`(store.hasWrpac()).thenReturn(true)
        Mockito.`when`(store.hasWrpacChain()).thenReturn(true)
        Mockito.`when`(store.hasWrpacKeyMaterial()).thenReturn(false)
        Mockito.`when`(store.hasWrprc()).thenReturn(true)

        mockMvc.get("/api/wrp/availability")
            .andExpect {
                status { isOk() }
                jsonPath("$.hasWrpac") { value(true) }
                jsonPath("$.hasWrpacChain") { value(true) }
                jsonPath("$.hasWrpacKeyMaterial") { value(false) }
                jsonPath("$.hasWrprc") { value(true) }
            }
    }
}
