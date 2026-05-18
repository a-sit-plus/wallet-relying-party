package at.asit.wallet.relyingparty

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class ActuatorSecurityDefaultTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `actuator is denied when spring boot admin is not configured`() {
        mockMvc.get("/actuator/health").andExpect {
            status { isForbidden() }
        }
    }
}

@SpringBootTest(
    properties = [
        "spring.boot.admin.client.enabled=true",
        "spring.boot.admin.client.instance.metadata.user.name=testuser",
        "spring.boot.admin.client.instance.metadata.user.password=testpass",
    ]
)
@AutoConfigureMockMvc
class ActuatorSecurityWithAdminTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `actuator requires authentication when spring boot admin is configured`() {
        mockMvc.get("/actuator/health").andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `actuator is accessible with correct credentials`() {
        mockMvc.get("/actuator/health") {
            headers { setBasicAuth("testuser", "testpass") }
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `actuator rejects wrong credentials`() {
        mockMvc.get("/actuator/health") {
            headers { setBasicAuth("testuser", "wrongpass") }
        }.andExpect {
            status { isUnauthorized() }
        }
    }
}
