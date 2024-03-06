package at.asit.apps.terminal_sp.prototype.server

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity

@Configuration
class WebSecurityConfiguration {

    @Bean
    fun securityFilterChain(http: HttpSecurity) = http.logout {
        it.logoutSuccessUrl("/")
    }.authorizeHttpRequests {
        it.anyRequest().permitAll()
    }.headers {
        it.frameOptions { it.sameOrigin() }
    }.csrf {
        it.disable() // to allow POST in SIOPv2 case
    }.build()
}