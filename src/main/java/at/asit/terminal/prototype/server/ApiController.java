package at.asit.terminal.prototype.server;

import at.asitplus.wallet.lib.agent.CryptoService;
import at.asitplus.wallet.lib.agent.DefaultCryptoService;
import at.asitplus.wallet.lib.agent.VerifierAgent;
import at.asitplus.wallet.lib.oidc.OidcSiopProtocol;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.xml.stream.events.StartDocument;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@RestController
public class ApiController {
    final private static SecureRandom secureRandomGenerator = new SecureRandom();
    final private Lock lock = new ReentrantLock();

    // maybe having a database for this might be valuable as there are multiple keys to search for
    // terminal id, subject id, code
    Map<String, OidcUser> terminalUserMap = new HashMap<>();
    final OneToOneMapping<String, String> terminalCodeSubjectMapping = new OneToOneMapping<>();

    private <KeyType, ValueType> ValueType getExistingOrEmplaced(Map<KeyType, ValueType> map, KeyType key, ValueType defaultValue) {
        {
            ValueType existingValue = map.putIfAbsent(key, defaultValue);
            if(existingValue != null) {
                defaultValue = existingValue;
            }
        }
        return defaultValue;
    }

    // path may also be a more structured identifier like /{locationId}/{terminalId}
    // if authentication is performed on a dedicated server, then this might just be some wildcard path
    @GetMapping(path = "/terminal")
    public String customerLogin(@AuthenticationPrincipal OidcUser user) {
        lock.lock();
        // refresh user info
        terminalUserMap.put(user.getSubject(), user);
        String code = null;
        {
            // find code if user exists
            // TODO: optimize lookup, probably using database instead of a map
            code = terminalCodeSubjectMapping.getBySecond(user.getSubject());
            if(code == null) {
                // new user - issue authentication code
                byte[] randomBytes = new byte[256 / 8];
                do {
                    secureRandomGenerator.nextBytes(randomBytes);
                    code = Base64.getEncoder().encodeToString(randomBytes);
                } while(terminalCodeSubjectMapping.getByFirst(code) != null);
                terminalCodeSubjectMapping.put(code, user.getSubject());
            }
        }

        String body = String.format("<p>Hallo, dein Anmeldecode ist: %s</p>", code);
        body += "<a href=\"logout\"><button type=\"button\">Logout</button></a>";
        lock.unlock();
        return formatHtml(body);
    }


    // path may also be a more structured identifier like /{locationId}/{terminalId}
    // if authentication is performed on a dedicated server, then this might just be some wildcard path
    @GetMapping(path = "/terminal/siopv2")
    public ResponseEntity<String> customerLogin2(@AuthenticationPrincipal OidcUser user) {
        CryptoService cryptoService = new DefaultCryptoService();
        VerifierAgent verifier = VerifierAgent.Companion.newDefaultInstance(cryptoService.getIdentifier());
//        new OidcSiopProtocol(
//
//        )

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setLocation(URI.create(ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()));
        return new ResponseEntity<>(new HttpHeaders(), HttpStatus.FOUND);
    }

//    // path may also be a more structured identifier like /{locationId}/{terminalId}
//    // if authentication is performed on a dedicated server, then this might just be some wildcard path
//    @GetMapping(path = "/remove/{codeEncoded}")
//    public String customerRemove(HttpServletResponse response, @AuthenticationPrincipal OidcUser user, @PathVariable String codeEncoded) throws IOException {
//        lock.lock();
//        // refresh user info
//        String code = URLDecoder.decode(codeEncoded, StandardCharsets.UTF_8);
//        System.out.println("Code: "+code);
//        String subject = terminalCodeSubjectMapping.getByFirst(code);
//        terminalUserMap.remove(subject);
//        terminalCodeSubjectMapping.removeByFirst(code);
//        lock.unlock();
//        response.sendRedirect("./");
//        return null;
//    }

//    // path may also be a more structured identifier like /{locationId}/{terminalId}
//    // if authentication is performed on a dedicated server, then this might just be some wildcard path
//    @GetMapping(path = "/login")
//    public String customerLogin(@AuthenticationPrincipal OidcUser user, @PathVariable String terminalId) {
//        lock.lock();
//        Map<String, OidcUser> terminalUserMap = getExistingOrEmplaced(terminal2subject2user, terminalId, new HashMap<>());
//        OneToOneMapping<String, String> terminalCodeSubjectMapping = getExistingOrEmplaced(terminal2codeSubjectMapping, terminalId, new OneToOneMapping<>());
//
//        // refresh user info
//        terminalUserMap.put(user.getSubject(), user);
//        String code = null;
//        {
//            // find code if user exists
//            // TODO: optimize lookup, probably using database instead of a map
//            code = terminalCodeSubjectMapping.getBySecond(user.getSubject());
//            if(code == null) {
//                // new user - issue authentication code
//                byte[] randomBytes = new byte[256 / 8];
//                do {
//                    secureRandomGenerator.nextBytes(randomBytes);
//                    code = Base64.getEncoder().encodeToString(randomBytes);
//                } while(terminalCodeSubjectMapping.getByFirst(code) != null);
//                terminalCodeSubjectMapping.put(code, user.getSubject());
//            }
//        }
//
////        this.brokerMessagingTemplate.convertAndSend("/topic/greetings", code);
////        this.brokerMessagingTemplate.convertAndSendToUser("toUser", "/queue/messages", message)
//
//        String body = String.format("<p>Hello, your code is: %s</p>", code);
//        lock.unlock();
//        return formatHtml(body);
//    }

    private String customerCard(OidcUser user, String code) {
//
//        body += String.format("<p>%s: %s</p>", "user()", user);
//        body += String.format("<p>%s: %s</p>", "user.getClaims()", user.getClaims());
//        body += String.format("<p>%s: <ul>%s</ul></p>", "user.getClaims() iterated",
//                user.getClaims().entrySet().stream().map(entry -> String.format("<li>%s: %s</li>", entry.getKey(), entry.getValue())).collect(Collectors.joining())
//        );
//        body += String.format("<p>%s: %s</p>", "user.getFamilyName()", user.getFamilyName());
//        body += String.format("<p>%s: %s</p>", "user.getFullName(()", user.getFullName());
//        body += String.format("<p>%s: %s</p>", "user.getUserInfo().getClaims()", user.getUserInfo().getClaims());
//        body += String.format("<p>%s: <ul>%s</ul></p>", "user.getUserInfo().getClaims() iterated",
//                user.getUserInfo().getClaims().entrySet().stream().map(entry -> String.format("<li>%s: %s</li>", entry.getKey(), entry.getValue())).collect(Collectors.joining())
//        );
//        body += String.format("<p>%s: %s</p>", "user.getSubject()", user.getSubject());

        return String.format("<div class=\"card\">%s</div>",
                String.format("<div class=\"container\">%s %s %s %s</div>",
                        String.format("""
                            <h4><b>Kunde: %s %s, %s</b></h4>
                            """, user.getFamilyName(), user.getGivenName(), user.getBirthdate()),
                        String.format("""
                            <p>Code: %s</p>
                            """, code),
                        String.format("""
                            <p>Sonstige Daten: %s</p>
                            """, String.format("<ul>%s</ul>",
                                    user.getClaims().entrySet().stream().map(entry -> {
                                        return String.format("<li>%s: %s</li>", entry.getKey(), entry.getValue());
                                    }).collect(Collectors.joining())
                                )
                        ),
                        String.format(
                            "<p><a href=\"?removedCode=%s\"><button type=\"button\">Entfernen</button></a></p>",
                            URLEncoder.encode(code, StandardCharsets.UTF_8)
                        )
                )
        );
    }

    @GetMapping(path = "/")
    public String listCustomers(@RequestParam(required = false) String removedCode) {
        lock.lock();

        if(removedCode != null) {
            String subject = terminalCodeSubjectMapping.getByFirst(removedCode);
            terminalUserMap.remove(subject);
            terminalCodeSubjectMapping.removeByFirst(removedCode);
        }

        StringBuilder body = new StringBuilder(String.format("<p>Hallo! Hier ist eine Liste von Kunden die auf die Anmeldung warten:</p>"));
        body.append(String.format("<div class=\"column\">%s</div>", terminalCodeSubjectMapping.byFirst.entrySet().stream().map(code2subject -> {
            OidcUser user = terminalUserMap.get(code2subject.getValue());
            return customerCard(user, code2subject.getKey());
        }).collect(Collectors.joining())));
//        for(Map.Entry<String, String> code2subject : terminalCodeSubjectMapping.byFirst.entrySet()) {
//            OidcUser user = terminalUserMap.get(code2subject.getValue());
//            body.append(customerCard(user, code2subject.getKey()));
////        String.format(
////                    "<p><a href=\"?removedCode=%s\"><button type=\"button\">Remove</button></a> %s: %s</p>",
////                    URLEncoder.encode(code2subject.getKey(), StandardCharsets.UTF_8),
////                    code2subject.getKey(),
////                    user
////            );
//        }
//        body.append("</div>");
        body.append("<p><a href=\"terminal\"><button type=\"button\">Login</button></a></p>");
        lock.unlock();

        return formatHtml(body.toString());
    }

    @Autowired
    private ApplicationContext context;

    private final AuthorizationRequestRepository<OAuth2AuthorizationRequest> authorizationRequestRepository = new HttpSessionOAuth2AuthorizationRequestRepository();

    private String style() {
        return """
<style>
    .card {
      box-shadow: 0 4px 8px 0 rgba(0,0,0,0.2);
      transition: 0.3s;
      border-radius: 5px;
    }
    
    .container {
      padding: 2px 16px;
    }
    
    .column {
        display: flex;
        flex-direction: column;
    }
    .row {
        display: flex;
        flex-direction: row;
    }
</style>""";
    }

    private String formatHtml(String body) {
        String head = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                + "<base href=\"" + ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString() + "/\" />"
                + style();
        return String.format("<html><head>%s</head><body>%s</body></html>", head, body);
    }

}
