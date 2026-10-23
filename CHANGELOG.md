# Changelog

Version 9.0.0-SNAPSHOT (unreleased):
 - Update to VC-K 9.0.0-RC1
 - Use VC-K prepared HTTP responses for signed OpenID4VP request objects and response acknowledgements
 - Preserve prepared response status, headers and encoded body through the Spring adapter, including `Cache-Control: no-store` on acknowledgements
 - Validate wallet responses with VC-K validation reports: trust per credential type (LoTEs and A-SIT root), status lists, and a policy configurable in `app.validation` (strict by default); show every check on the result page, and claims only of accepted credentials
 - Trust credential types without a list of their own through the Pub-EAA providers list, and status list signers through the revocation services of the lists; fetch the lists of the development and acceptance stages by default, skipping lists a stage does not publish
 - Cache status list tokens until their `ttl` elapses or they expire, at most 5 minutes, sharing concurrent fetches; time out fetching them after 5 seconds
 - Status lists: Accept CWTs tagged as COSE_Sign1 (tag 18), and report a token that cannot be parsed with the content type it has been served with
 - Configure a WRPRC as JWT (`path`) and CWT (`cwt-path`) in one `app.wrp.rc` entry, sending the JWT in OpenID4VP `verifier_info` and the CWT in ISO 18013-7 Annex C `euWrprc`; load CWTs binary, base64-encoded, or tagged as CWT (tag 61) and/or COSE_Sign1 (tag 18), and always send them untagged; reject a WRPRC entry that has neither
 - Show the summary of a WRPRC given only as CWT, and the service description of WRPRCs that encode `srv_description` as one array of localized strings
 - Create signed requests by reference with VC-K: the QR code carries the wallet URL VC-K generates, and the request object is served by the request VC-K created for it, with the selected WRPAC
 - BREAKING: Rename the verifier profile `MDOCd23` to `MDOC10`, as it uses OpenID4VP 1.0; clients selecting it by `profileName` have to use the new name
 - Fetch the WRPRC providers lists of the configured stages, too, as VC-K 9.0.0-RC1 knows them

Version 8.0.0 (unreleased):
 - Add WRPAC and WRPRC support, see Readme
 - Allow configuring and selecting multiple WRP access certificates per transaction
 - DC API: Authenticate the reader in ISO 18013-7 Annex C requests (`readerAuth`) with the selected WRPAC
 - Fetch the Lists of Trusted Entities with `LoteProfile` from the stages configured in `app.trust-list-stages`
 - Status lists: Detect CWT status lists and identifier lists from the body, also when served with another media type, accept the CWT tag, and reject unsuccessful HTTP responses
 - Remove support for Presentation Exchange
 - Remove ID tokens from the result
 - Remove the `emptyIssuerTrustList` flag of transactions: test an untrusted issuer with a credential whose issuer is not on the LoTE
 - Remove the ITB remote verifier test cases (PRE-01 to PRE-14), and the standalone test pages `av.html`, `ehic.html`, `mdl.html`, `pidsdjwt.html` and `pidmdoc.html`; the native browser demos moved to the remote presentation case
 - Update the Gradle conventions plugin to 20260828 and the Gradle wrapper to 9.7.1
 - Update to Spring Boot 4.1.1
 - Implement test cases for EUDI Launchpad 2026
 - Update to VC-K 8.0.0-RC1

Version 7.0.0:
 - Update to VC-K 7.0.0
 - Rework resolving credential schemes
 - Update to Spring Boot 4.1.0
 - Display trust evaluation of received credentials
 - Show option to use DCAPI for every profile
 - Add native apps to demonstrate DCAPI usage (see repository) 

Version 6.0.0:
- Update to VC-K 6.0.0
- Update to EU PID 3.5.0
- Remove `jvm` suffix from `vck` in `lib.versions` to restore composite build functionality
- Use `JwsCompactTyped` instead of `JwsSigned`
- Register credential schemes from remote SD-JWT Type Metadata documents (`RemoteCredentialMetadataRegistry`) instead of compiling in a library per credential; drop the `ageverification`, `eupidcredential`, `eupidcredential-sdjwt`, `mobiledrivinglicence`, `powerofrepresentation`, `certificateofresidence`, `taxid`, and `ehic` dependencies
- Generate the UI credential/attribute picker (`/js/login-config.js`) from those type-metadata documents instead of a hand-maintained static file

Version 5.13.0:
 - Update to VC-K 5.13.0
 - Migrate to Spring Boot 4.0.6 (Spring Framework 7, Spring Security 7, Jakarta EE 11)
 - Update Spring Cloud to 2025.1.1 (Oakwood)
 - Update Spring Boot Admin client to 4.0.4
 - Upgrade JVM toolchain to Java 21
 - Enable virtual threads (`spring.threads.virtual.enabled`)
 - Switch primary HTTP message converter to kotlinx.serialization backed by `joseCompliantSerializer`
 - Coroutine migration: controller handler methods are now `suspend`; `ReentrantLock` replaced by `Mutex` in `ApiController` and `TransactionStore`; `TransactionStore` public API is now suspending

Version 5.12.0:
 - Update to VC-K 5.12.0
 - Treat all attributes selected by the user as required
 - DC API:
    - Support verification of DC API responses
    - Support combined requests
    - Support OID4VP unsigned, signed (encrypted and unencrypted) and ISO 18013-7 requests
 - Raw Requests
 - Add presentation submission validation

Version 5.11.0:
 - Update to VC-K 5.11.0
 - First public release

Version 5.10.0:
 - Update to VC-K 5.10.0
 - Add Age Verification credential
 - Offer test cases from EUDI Launchpad event December 2025

Version 5.9.0:
 - Update to VC-K 5.9.0

Version 5.8.0:
 - Update to VC-K 5.8.0
 - Update credentials
 - Prepare for open source

Version 5.7.1:
 - Update to VC-K 5.7.1
 - Add profile ISO 18013-7 with Draft23

Version 5.7.0:
 - Update to VC-K 5.7.0

Version 5.6.1:
 - Add support for European Health Insurance Card (EHIC) credential (1.1.0)
 - Update to VC-K 5.6.6

Version 5.6.0:
 - Update to VC-K 5.6.0

Version 5.5.3:
 - Add basic Digital Credentials API support
 - Add PID with SD-JWT claim names and new `vct` `urn:eudi:pid:1`

Version 5.5.2:
 - Change profile "Potential v2" to request encryption
 - Update to VC-K 5.5.2

Version 5.5.1:
 - Add more profiles, also with deprecated `client_id_scheme`
 - Update to VC-K 5.5.1

Version 5.5.0:
 - Update to Spring Boot 3.4.3
 - Update to VC-K 5.5.0

Version 5.4.5:
 - Add support for selecting DCQL in the web interface

Version 5.4.4:
 - Update Power of Representation credential to 1.2.0, fixing the `sdJwtType`
 - Update Company Registration credential to 1.1.0, fixing the `sdJwtType`

Version 5.4.3:
 - Disable attribute selection on some credential types, because they are not selectively disclosable: PoR, HealthID

Version 5.4.0:
 - Update to VC-K 5.4.0
 - Update to EU PID 3.0.0, conforming to ARF 1.5.0
 - Update ePrescription to HealthID

Version 5.3.3:
 - Update to VC-K 5.3.3
 - Fixes ISO 18013-7 implementation

Version 5.3.2:
 - Update to VC-K 5.3.2
 - Fixes ISO value digests

Version 5.3.1:
- Update to VC-K 5.3.1
- Provide `jar-issuer` metadata file, set public HTTPS URL as `iss` for signed authorization requests

Version 5.3.0:
 - Update to VC-K 5.3.0

Version 5.2.3:
 - Update to VC-K 5.2.3

Version 5.2.1:
 - Update to VC-K 5.2.1

Version 5.2.0:
 - Update to VC-K 5.2.0
 - Remove URL prefix from request details
 - Allow selecting URL prefix when displaying QR Code

Version 5.1.0:
 - Update to VC-K 5.1.0

Version 5.0.3:
 - Update to VC-K 5.0.1
 - Update UI to allow for combined presentation of two or more credentials

Version 5.0.2:
 - Refactor to use transactions for QR code generation and receiving credentials

Version 5.0.1:
 - Remove storing user in session, but pass reference by query param
 - Implement OpenID4VP correctly by returning `redirect_uri` on receiving authentication response from wallet

Version 5.0.0:
 - Update to VC-K 5.0.0
