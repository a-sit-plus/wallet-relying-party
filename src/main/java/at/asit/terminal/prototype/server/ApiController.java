package at.asit.terminal.prototype.server;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
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
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@RestController("/HYUhYQpV5LsKxe7fQ5l84oOVyTDyHqMZy3PJGuVC9hc")
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

//        this.brokerMessagingTemplate.convertAndSend("/topic/greetings", code);
//        this.brokerMessagingTemplate.convertAndSendToUser("toUser", "/queue/messages", message)

        String body = String.format("<p>Hello, your code is: %s</p>", code);
        body += "<a href=\"logout\"><button type=\"button\">Logout</button></a>";
        lock.unlock();
        return formatHtml(body);
    }

    // path may also be a more structured identifier like /{locationId}/{terminalId}
    // if authentication is performed on a dedicated server, then this might just be some wildcard path
    @GetMapping(path = "/remove/{codeEncoded}")
    public String customerRemove(HttpServletResponse response, @AuthenticationPrincipal OidcUser user, @PathVariable String codeEncoded) throws IOException {
        lock.lock();
        // refresh user info
        String code = URLDecoder.decode(codeEncoded, StandardCharsets.UTF_8);
        System.out.println("Code: "+code);
        String subject = terminalCodeSubjectMapping.getByFirst(code);
        terminalUserMap.remove(subject);
        terminalCodeSubjectMapping.removeByFirst(code);
        lock.unlock();
        response.sendRedirect("./");
        return null;
    }

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

    @GetMapping(path = "/")
    public String listCustomers(@RequestParam(required = false) String removedCode) {
        lock.lock();

        if(removedCode != null) {
            String subject = terminalCodeSubjectMapping.getByFirst(removedCode);
            terminalUserMap.remove(subject);
            terminalCodeSubjectMapping.removeByFirst(removedCode);
        }

        String body = String.format("<p>Hello, here is a list of users waiting for checkin:</p>");
        for(Map.Entry<String, String> code2subject : terminalCodeSubjectMapping.byFirst.entrySet()) {
            body += String.format("<p><a href=\"?removedCode=%s\"><button type=\"button\">Remove</button></a> %s: %s</p>", URLEncoder.encode(code2subject.getKey(), StandardCharsets.UTF_8), code2subject.getKey(), code2subject.getValue());
        }
        body += "<a href=\"terminal\"><button type=\"button\">Login</button></a>";
        lock.unlock();

        return formatHtml(body);
    }

    @Autowired
    private ApplicationContext context;

    private final AuthorizationRequestRepository<OAuth2AuthorizationRequest> authorizationRequestRepository = new HttpSessionOAuth2AuthorizationRequestRepository();

    private String formatHtml(String body) {
        final String baseUrl =
                ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        String head = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                + "<base href=\"" + baseUrl + "/\" />";
        return String.format("<html><head>%s</head><body>%s</body></html>", head, body);
    }

}
