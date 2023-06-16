//package at.asit.terminal.prototype.server
//
//import at.asitplus.wallet.lib.agent.CryptoService
//import at.asitplus.wallet.lib.agent.DefaultCryptoService
//import at.asitplus.wallet.lib.agent.DefaultVerifierCryptoService
//import at.asitplus.wallet.lib.agent.VerifierAgent.Companion.newDefaultInstance
//import at.asitplus.wallet.lib.jws.DefaultJwsService
//import at.asitplus.wallet.lib.jws.DefaultVerifierJwsService
//import at.asitplus.wallet.lib.oidc.OidcSiopProtocol.Companion.newVerifierInstance
//import kotlinx.datetime.Clock
//import org.springframework.security.config.annotation.SecurityConfigurerAdapter
//import org.springframework.security.config.annotation.web.HttpSecurityBuilder
//import org.springframework.security.config.annotation.web.builders.HttpSecurity
//import org.springframework.security.config.annotation.web.configurers.AbstractAuthenticationFilterConfigurer
//import org.springframework.security.web.DefaultSecurityFilterChain
//import org.springframework.security.web.util.matcher.RequestMatcher
//import java.util.*
//
//@Throws(java.lang.Exception::class)
//private fun <C : SecurityConfigurerAdapter<DefaultSecurityFilterChain?, HttpSecurity>> HttpSecurity.getOrApply(configurer: C): C {
//    return getConfigurer(configurer::class.java) ?: apply(configurer)
//}
//
//fun HttpSecurity.siopv2Login(): Siopv2LoginConfigurer<HttpSecurity> {
//    return getOrApply(Siopv2LoginConfigurer())
//}
//
//fun siopv2Login(http: HttpSecurity): Siopv2LoginConfigurer<HttpSecurity> {
//    return http.getOrApply(Siopv2LoginConfigurer())
//}
//
//class Siopv2LoginConfigurer<B : HttpSecurityBuilder<B>?> :
//    AbstractAuthenticationFilterConfigurer<B, Siopv2LoginConfigurer<B>?, Siopv2LoginAuthenticationFilter?>() {
//
//    private val verifierCryptoService: CryptoService = DefaultCryptoService()
//    private val verifier = newDefaultInstance(verifierCryptoService.identifier)
//    private val verifierOidcSiopProtocol = newVerifierInstance(
//        verifier,
//        verifierCryptoService,
//        DefaultVerifierJwsService(DefaultVerifierCryptoService()),
//        DefaultJwsService(verifierCryptoService),
//        UUID.randomUUID().toString(),
//        300L,
//        Clock.System
//    )
//
//    override fun createLoginProcessingUrlMatcher(loginProcessingUrl: String?): RequestMatcher {
//        TODO("Not yet implemented")
//    }
//
//}