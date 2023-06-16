//package at.asit.terminal.prototype.server;
//
//import jakarta.servlet.ServletException;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import org.springframework.security.core.Authentication;
//import org.springframework.security.core.AuthenticationException;
//import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
//
//import java.io.IOException;
//
//public class Siopv2LoginAuthenticationFilter extends AbstractAuthenticationProcessingFilter {
//    /**
//     * The default {@code URI} where this {@code Filter} processes authentication
//     * requests.
//     */
//    public static final String DEFAULT_FILTER_PROCESSES_URI = "/login/siopv2/response/*";
//
//    protected Siopv2LoginAuthenticationFilter() {
//        super(DEFAULT_FILTER_PROCESSES_URI);
//    }
//    protected Siopv2LoginAuthenticationFilter(String defaultFilterProcessesUrl) {
//        super(defaultFilterProcessesUrl != null ? defaultFilterProcessesUrl : DEFAULT_FILTER_PROCESSES_URI);
//    }
//
//    @Override
//    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException, IOException, ServletException {
//        return null;
//    }
//}