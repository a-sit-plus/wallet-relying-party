package at.asit.apps.terminal_sp.prototype.server

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
class WebSecurityConfiguration {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.oauth2Login {
        }.logout {
            it.logoutSuccessUrl("/")
        }.authorizeHttpRequests {
            it.requestMatchers("/oauth2").authenticated()
        }.authorizeHttpRequests {
            it.anyRequest().permitAll()
        }.headers {
            it.frameOptions { it.sameOrigin() }
        }.csrf {
            it.disable() // to allow POST in SIOPv2 case
        }
        return http.build()
    }
}