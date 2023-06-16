package at.asit.terminal.prototype.server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

@Configuration
public class WebSecurityConfiguration {

    @Bean
    public SecurityFilterChain customerTerminalAuthentication(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests()
                .requestMatchers("/terminal")
                .authenticated().and().oauth2Login();
//        siopv2Login(http.authorizeHttpRequests()
//                .requestMatchers("/terminal/wallet")
//                .authenticated().and())
//                .failureHandler(new RedirectingAuthenticationFailureHandler());
//                .permitAll();
        http.authorizeHttpRequests()
                .anyRequest()
                .permitAll();
//                .authenticated().and().oauth2Login(); // employee login configuration
        return http.build();
    }
}