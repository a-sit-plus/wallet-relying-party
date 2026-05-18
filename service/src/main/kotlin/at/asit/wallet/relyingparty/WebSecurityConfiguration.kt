package at.asit.wallet.relyingparty

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.User as SecurityUser
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.session.MapSessionRepository
import java.util.concurrent.ConcurrentHashMap

@Configuration
class WebSecurityConfiguration(
    @Value("\${spring.boot.admin.client.enabled:false}") private val adminClientEnabled: Boolean,
    @Value("\${spring.boot.admin.client.instance.metadata.user.name:#{null}}") private val adminUsername: String?,
    @Value("\${spring.boot.admin.client.instance.metadata.user.password:#{null}}") private val adminPassword: String?,
) {
    // Non-null only when the admin client is enabled and both credentials are provided.
    private val actuatorCredentials: Pair<String, String>?
        get() = if (adminClientEnabled && adminUsername != null && adminPassword != null)
            adminUsername to adminPassword
        else null

    @Bean
    @Order(1)
    fun actuatorSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.securityMatcher("/actuator/**")
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .csrf { it.disable() }
        val creds = actuatorCredentials
        if (creds != null) {
            http.authorizeHttpRequests { it.anyRequest().hasRole("ACTUATOR") }
                .httpBasic(Customizer.withDefaults())
        } else {
            http.authorizeHttpRequests { it.anyRequest().denyAll() }
        }
        return http.build()
    }

    @Bean
    @Order(2)
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain = http.logout {
        it.logoutSuccessUrl("/")
    }.sessionManagement {
        it.sessionCreationPolicy(SessionCreationPolicy.ALWAYS)
    }.authorizeHttpRequests {
        it.anyRequest().permitAll()
    }.headers {
        it.frameOptions { it.sameOrigin() }
    }.csrf {
        it.disable() // to allow POST for OpenID4VP
    }.build()

    @Bean
    fun userDetailsService(): UserDetailsService {
        val (username, password) = actuatorCredentials ?: return InMemoryUserDetailsManager()
        return InMemoryUserDetailsManager(
            SecurityUser.withUsername(username)
                .password("{noop}$password")
                .roles("ACTUATOR")
                .build()
        )
    }

    @Bean
    fun sessionRepository() = MapSessionRepository(ConcurrentHashMap())
}