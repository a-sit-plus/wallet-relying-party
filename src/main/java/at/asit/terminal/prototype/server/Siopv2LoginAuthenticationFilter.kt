//package at.asit.terminal.prototype.server
//
//import jakarta.servlet.ServletException
//import jakarta.servlet.http.HttpServletRequest
//import jakarta.servlet.http.HttpServletResponse
//import org.springframework.security.core.Authentication
//import org.springframework.security.core.AuthenticationException
//import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter
//import java.io.IOException
//
///**
// * Constructs an `Siopv2LoginAuthenticationFilter` using the provided
// * parameters.
// * @param filterProcessesUrl the `URI` where this `Filter` will process
// * the authentication requests
// * @since 5.1
// */
//class Siopv2LoginAuthenticationFilter(filterProcessesUrl: String) : AbstractAuthenticationProcessingFilter(filterProcessesUrl) {
//    companion object {
//        /**
//         * The default `URI` where this `Filter` processes authentication
//         * requests.
//         */
//        const val DEFAULT_FILTER_PROCESSES_URI = "/login/siopv2/code/*"
//    }
//
//    constructor() : this(DEFAULT_FILTER_PROCESSES_URI)
//
//    override fun attemptAuthentication(request: HttpServletRequest?, response: HttpServletResponse?): Authentication {
//        TODO("Not yet implemented")
//    }
//}
