package at.asit.terminal.prototype.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

//@RestController
//public class ApiController2 {
//
//    final private static SecureRandom secureRandomGenerator = new SecureRandom();
//    final private Lock lock = new ReentrantLock();
//
//    // maybe having a database for this might be valuable as there are multiple keys to search for
//    // terminal id, subject id, code
//    final Map<String, Map<String, OidcUser>> terminal2subject2user = new HashMap<>();
//    final Map<String, OneToOneMapping<String, String>> terminal2codeSubjectMapping = new HashMap<>();
//
//    private <KeyType, ValueType> ValueType getExistingOrEmplaced(Map<KeyType, ValueType> map, KeyType key, ValueType defaultValue) {
//        {
//            ValueType existingValue = map.putIfAbsent(key, defaultValue);
//            if(existingValue != null) {
//                defaultValue = existingValue;
//            }
//        }
//        return defaultValue;
//    }
//
//    // path may also be a more structured identifier like /{locationId}/{terminalId}
//    // if authentication is performed on a dedicated server, then this might just be some wildcard path
//    @GetMapping(path = "/login")
//    public String customerLogin(@AuthenticationPrincipal OidcUser user) {
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
//
////    // path may also be a more structured identifier like /{locationId}/{terminalId}
////    // if authentication is performed on a dedicated server, then this might just be some wildcard path
////    @GetMapping(path = "/login")
////    public String customerLogin(@AuthenticationPrincipal OidcUser user, @PathVariable String terminalId) {
////        lock.lock();
////        Map<String, OidcUser> terminalUserMap = getExistingOrEmplaced(terminal2subject2user, terminalId, new HashMap<>());
////        OneToOneMapping<String, String> terminalCodeSubjectMapping = getExistingOrEmplaced(terminal2codeSubjectMapping, terminalId, new OneToOneMapping<>());
////
////        // refresh user info
////        terminalUserMap.put(user.getSubject(), user);
////        String code = null;
////        {
////            // find code if user exists
////            // TODO: optimize lookup, probably using database instead of a map
////            code = terminalCodeSubjectMapping.getBySecond(user.getSubject());
////            if(code == null) {
////                // new user - issue authentication code
////                byte[] randomBytes = new byte[256 / 8];
////                do {
////                    secureRandomGenerator.nextBytes(randomBytes);
////                    code = Base64.getEncoder().encodeToString(randomBytes);
////                } while(terminalCodeSubjectMapping.getByFirst(code) != null);
////                terminalCodeSubjectMapping.put(code, user.getSubject());
////            }
////        }
////
//////        this.brokerMessagingTemplate.convertAndSend("/topic/greetings", code);
//////        this.brokerMessagingTemplate.convertAndSendToUser("toUser", "/queue/messages", message)
////
////        String body = String.format("<p>Hello, your code is: %s</p>", code);
////        lock.unlock();
////        return formatHtml(body);
////    }
//
//    @GetMapping(path = "/")
//    public String listCustomers(@AuthenticationPrincipal OidcUser user, @PathVariable String terminalId) {
//        lock.lock();
//        Map<String, OidcUser> terminalUserMap = getExistingOrEmplaced(terminal2subject2user, terminalId, new HashMap<>());
//        OneToOneMapping<String, String> terminalCodeSubjectMapping = getExistingOrEmplaced(terminal2codeSubjectMapping, terminalId, new OneToOneMapping<>());
//
//        String body = String.format("<p>Hello, here is a list of users waiting for checkin:</p>");
//        for(Map.Entry<String, String> code2subject : terminalCodeSubjectMapping.byFirst.entrySet()) {
//            body += String.format("<p>%s: %s</p>", code2subject.getKey(), code2subject.getValue());
//        }
//        lock.unlock();
//
//        return formatHtml(body);
//    }
//
//    @Autowired
//    private ApplicationContext context;
//
//    private final AuthorizationRequestRepository<OAuth2AuthorizationRequest> authorizationRequestRepository = new HttpSessionOAuth2AuthorizationRequestRepository();
//
//    private String formatHtml(String body) {
//        final String baseUrl =
//                ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
//        String head = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
//                + "<base href=\"" + baseUrl + "/\" />";
//        return String.format("<html><head>%s</head><body>%s<form action=\"logout\"><button>Logout</button></form></body></html>", head, body);
//    }
//
//}
