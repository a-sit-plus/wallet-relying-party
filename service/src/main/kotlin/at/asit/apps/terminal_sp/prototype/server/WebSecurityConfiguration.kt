package at.asit.apps.terminal_sp.prototype.server

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.session.MapSessionRepository
import java.util.concurrent.ConcurrentHashMap

@Configuration
class WebSecurityConfiguration {

    @Bean
    fun securityFilterChain(http: HttpSecurity) = http.logout {
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
    fun sessionRepository() = MapSessionRepository(ConcurrentHashMap())

}