//package at.asit.terminal.prototype.server;
//
//import at.asitplus.wallet.lib.agent.CryptoService;
//import at.asitplus.wallet.lib.agent.DefaultCryptoService;
//import at.asitplus.wallet.lib.agent.DefaultVerifierCryptoService;
//import at.asitplus.wallet.lib.agent.VerifierAgent;
//import at.asitplus.wallet.lib.jws.DefaultJwsService;
//import at.asitplus.wallet.lib.jws.DefaultVerifierJwsService;
//import at.asitplus.wallet.lib.oidc.OidcSiopProtocol;
//import kotlinx.datetime.Clock;
//import org.springframework.security.authentication.AuthenticationProvider;
//import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
//import org.springframework.security.core.Authentication;
//import org.springframework.security.core.AuthenticationException;
//import org.springframework.stereotype.Component;
//
//import java.util.ArrayList;
//import java.util.UUID;
//
//@Component
//public class Siopv2AuthenticationProvider implements AuthenticationProvider {
//
//    private final CryptoService verifierCryptoService = new DefaultCryptoService();
//    private final VerifierAgent verifier = VerifierAgent.Companion.newDefaultInstance(verifierCryptoService.getIdentifier());
//    private final OidcSiopProtocol verifierOidcSiopProtocol = OidcSiopProtocol.Companion.newVerifierInstance(
//            verifier,
//            verifierCryptoService,
//            new DefaultVerifierJwsService(new DefaultVerifierCryptoService()),
//            new DefaultJwsService(verifierCryptoService),
//            UUID.randomUUID().toString(),
//            300L,
//            Clock.System.INSTANCE
//    );
//
//    @Override
//    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
//
//        String name = authentication.getName();
//        String password = authentication.getCredentials().toString();
//
//        if (shouldAuthenticateAgainstThirdPartySystem()) {
//
//            // use the credentials
//            // and authenticate against the third-party system
//            return new UsernamePasswordAuthenticationToken(name, password, new ArrayList<>());
//        } else {
//            return null;
//        }
//    }
//
//    @Override
//    public boolean supports(Class<?> authentication) {
//        return authentication.equals(UsernamePasswordAuthenticationToken.class);
//    }
//}