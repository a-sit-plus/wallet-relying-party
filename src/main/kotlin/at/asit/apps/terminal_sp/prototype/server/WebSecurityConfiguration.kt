package at.asit.apps.terminal_sp.prototype.server

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
class WebSecurityConfiguration {
    @Bean
    fun secretPageAuthentication(http: HttpSecurity): SecurityFilterChain {
        http.oauth2Login {
//            it.apply {
//                loginPage("/login/oauth2")
//            }
        }.authorizeHttpRequests {
            it.apply {
                requestMatchers("/terminal/oauth2").authenticated()
            }
//        }.siop2Login {
//            it.apply {
//                loginPage("/login/siop2")
//            }
//        }.authorizeHttpRequests {
//            it.apply {
//                requestMatchers("/siop2Page").authenticated()
//            }
        }.authorizeHttpRequests {
            it.apply {
                anyRequest().permitAll()
            }
        }
        return http.build()
    }
}