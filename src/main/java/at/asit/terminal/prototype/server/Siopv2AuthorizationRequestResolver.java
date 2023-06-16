//package at.asit.terminal.prototype.server;
//
//import at.asitplus.wallet.lib.agent.CryptoService;
//import at.asitplus.wallet.lib.agent.DefaultCryptoService;
//import at.asitplus.wallet.lib.agent.DefaultVerifierCryptoService;
//import at.asitplus.wallet.lib.agent.VerifierAgent;
//import at.asitplus.wallet.lib.jws.DefaultJwsService;
//import at.asitplus.wallet.lib.jws.DefaultVerifierJwsService;
//import at.asitplus.wallet.lib.oidc.OidcSiopProtocol;
//import jakarta.servlet.http.HttpServletRequest;
//import kotlinx.datetime.Clock;
//import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
//import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
//import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
//import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
//
//import java.util.HashMap;
//import java.util.Map;
//import java.util.UUID;
//
//public class Siopv2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {
//
//    private final OAuth2AuthorizationRequestResolver defaultResolver;
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
//    public Siopv2AuthorizationRequestResolver(ClientRegistrationRepository repo, String authorizationRequestBaseUri) {
//        defaultResolver = new DefaultOAuth2AuthorizationRequestResolver(repo, authorizationRequestBaseUri);
//    }
//
//    @Override
//    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
//        OAuth2AuthorizationRequest req = defaultResolver.resolve(request);
//        if (req != null) {
//            req = customizeAuthorizationRequest(req);
//        }
//        return req;
//    }
//
//    @Override
//    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
//        OAuth2AuthorizationRequest req = defaultResolver.resolve(request, clientRegistrationId);
//        if (req != null) {
//            req = customizeAuthorizationRequest(req);
//        }
//        return req;
//    }
//
//    private OAuth2AuthorizationRequest customizeAuthorizationRequest(OAuth2AuthorizationRequest req) {
//        Map<String, Object> extraParams = new HashMap<String, Object>();
//        extraParams.putAll(req.getAdditionalParameters());
//        extraParams.put("test", "extra");
//
//        return OAuth2AuthorizationRequest.from(req).additionalParameters(extraParams).build();
//    }
//
//}