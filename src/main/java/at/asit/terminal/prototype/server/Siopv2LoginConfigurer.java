//package at.asit.terminal.prototype.server;
//
//import org.springframework.security.config.annotation.SecurityConfigurerAdapter;
//import org.springframework.security.config.annotation.web.HttpSecurityBuilder;
//import org.springframework.security.config.annotation.web.builders.HttpSecurity;
//import org.springframework.security.config.annotation.web.configurers.AbstractAuthenticationFilterConfigurer;
//import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2ClientConfigurerUtils;
//import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;
//import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
//import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationProvider;
//import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient;
//import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
//import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
//import org.springframework.security.oauth2.client.oidc.authentication.OidcAuthorizationCodeAuthenticationProvider;
//import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
//import org.springframework.security.oauth2.client.registration.ClientRegistration;
//import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
//import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
//import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
//import org.springframework.security.oauth2.core.oidc.user.OidcUser;
//import org.springframework.security.oauth2.core.user.OAuth2User;
//import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
//import org.springframework.security.web.DefaultSecurityFilterChain;
//import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
//import org.springframework.security.web.util.matcher.RequestMatcher;
//import org.springframework.util.Assert;
//import org.springframework.util.ClassUtils;
//
//import java.util.Map;
//
//
////public Siopv2LoginConfigurer<HttpSecurity> siopv2Login(HttpSecurity http) {
////    return new Siopv2LoginConfigurer<HttpSecurity>().getOrApply(http)
////}
//
//public final class Siopv2LoginConfigurer<B extends HttpSecurityBuilder<B>>
//        extends AbstractAuthenticationFilterConfigurer<B, Siopv2LoginConfigurer<B>, Siopv2LoginAuthenticationFilter> {
//
//    public Siopv2LoginConfigurer<HttpSecurity> getOrApply(HttpSecurity http) throws Exception {
//        Siopv2LoginConfigurer existingConfig = http.getConfigurer(this.getClass());
//        if (existingConfig != null) {
//            return existingConfig;
//        }
//        return http.apply(this);
//    }
//
//    @Override
//    public void init(B http) throws Exception {
//        Siopv2LoginAuthenticationFilter authenticationFilter = new Siopv2LoginAuthenticationFilter(this.loginProcessingUrl);
//        authenticationFilter.setSecurityContextHolderStrategy(getSecurityContextHolderStrategy());
//        this.setAuthenticationFilter(authenticationFilter);
//        super.init(http);
//
//        OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> accessTokenResponseClient = this.tokenEndpointConfig.accessTokenResponseClient;
//        if (accessTokenResponseClient == null) {
//            accessTokenResponseClient = new DefaultAuthorizationCodeTokenResponseClient();
//        }
//        OAuth2UserService<OAuth2UserRequest, OAuth2User> oauth2UserService = getOAuth2UserService();
//        OAuth2LoginAuthenticationProvider oauth2LoginAuthenticationProvider = new OAuth2LoginAuthenticationProvider(
//                accessTokenResponseClient, oauth2UserService);
//        GrantedAuthoritiesMapper userAuthoritiesMapper = this.getGrantedAuthoritiesMapper();
//        if (userAuthoritiesMapper != null) {
//            oauth2LoginAuthenticationProvider.setAuthoritiesMapper(userAuthoritiesMapper);
//        }
//        http.authenticationProvider(this.postProcess(oauth2LoginAuthenticationProvider));
//        boolean oidcAuthenticationProviderEnabled = ClassUtils
//                .isPresent("org.springframework.security.oauth2.jwt.JwtDecoder", this.getClass().getClassLoader());
//        if (oidcAuthenticationProviderEnabled) {
//            OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService = getOidcUserService();
//            OidcAuthorizationCodeAuthenticationProvider oidcAuthorizationCodeAuthenticationProvider = new OidcAuthorizationCodeAuthenticationProvider(
//                    accessTokenResponseClient, oidcUserService);
//            JwtDecoderFactory<ClientRegistration> jwtDecoderFactory = this.getJwtDecoderFactoryBean();
//            if (jwtDecoderFactory != null) {
//                oidcAuthorizationCodeAuthenticationProvider.setJwtDecoderFactory(jwtDecoderFactory);
//            }
//            if (userAuthoritiesMapper != null) {
//                oidcAuthorizationCodeAuthenticationProvider.setAuthoritiesMapper(userAuthoritiesMapper);
//            }
//            http.authenticationProvider(this.postProcess(oidcAuthorizationCodeAuthenticationProvider));
//        }
//        else {
//            http.authenticationProvider(new OAuth2LoginConfigurer.OidcAuthenticationRequestChecker());
//        }
//        this.initDefaultLoginFilter(http);
//    }
//
//
//
//
//    @Override
//    protected RequestMatcher createLoginProcessingUrlMatcher(String loginProcessingUrl) {
//        return new AntPathRequestMatcher(loginProcessingUrl);
//    }
//}