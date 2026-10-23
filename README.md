<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="rp-light.png">
  <source media="(prefers-color-scheme: light)" srcset="rp-dark.png">
  <img alt="Wallet Relying Party for Kotlin Multiplatform" src="rp-dark.png">
</picture>
<br><br>

[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-brightgreen.svg?style=flat)](http://www.apache.org/licenses/LICENSE-2.0)
[![A-SIT Plus Official](https://img.shields.io/badge/A--SIT_Plus-official-005b79?logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAxNDMuNzYyODYgMTg0LjgxOTk5Ij48ZGVmcz48Y2xpcFBhdGggaWQ9ImEiIGNsaXBQYXRoVW5pdHM9InVzZXJTcGFjZU9uVXNlIj48cGF0aCBkPSJNMCA1OTUuMjhoODQxLjg5VjBIMFoiLz48L2NsaXBQYXRoPjwvZGVmcz48ZyBjbGlwLXBhdGg9InVybCgjYSkiIHRyYW5zZm9ybT0ibWF0cml4KDEuMzMzMzMzMyAwIDAgLTEuMzMzMzMzMyAtNDgyLjI1IDUxNy41MykiPjxwYXRoIGZpbGw9IiMwMDViNzkiIGQ9Ik00MTUuNjcgMjQ5LjUzYy03LjE1LjA4LTEzLjk0IDEtMjAuMTcgMi43NWE1Mi4zMyA1Mi4zMyAwIDAgMC0xNy40OCA4LjQ2IDQwLjQzIDQwLjQzIDAgMCAwLTExLjk2IDE0LjU2Yy0yLjY4IDUuNDEtNC4xNCAxMS44NC00LjM1IDE5LjA5bC0uMDIgNi4xMnYyLjE3YS43MS43MSAwIDAgMCAuNy43M2gxNi41MmMuMzkgMCAuNy0uMzIuNzEtLjdsLjAxLTIuMmMwLTIuNi4wMi01LjgyLjAzLTYuMDcuMi00LjYgMS4yNC04LjY2IDMuMDgtMTIuMDZhMjguNTIgMjguNTIgMCAwIDEgOC4yMy05LjU4IDM1LjI1IDM1LjI1IDAgMCAxIDExLjk2LTUuNTggNTUuMzggNTUuMzggMCAwIDEgMTIuNTgtMS43NmM0LjMyLjEgOC42LjcgMTIuNzQgMS44YTM1LjA3IDM1LjA3IDAgMCAxIDExLjk2IDUuNTcgMjguNTQgMjguNTQgMCAwIDEgOC4yNCA5LjU3YzEuOTYgMy42NCAzIDguMDIgMy4xMiAxMy4wMnYyNC4wOUgzNjIuNGEuNy43IDAgMCAwLS43MS43VjMzNWMwIDguNDMuMDEgOC4wNS4wMSA4LjE0LjIgNy4zIDEuNjcgMTMuNzcgNC4zNiAxOS4yMmE0MC40MyA0MC40MyAwIDAgMCAxMS45NiAxNC41N2M1IDMuNzYgMTAuODcgNi42MSAxNy40OCA4LjQ2YTc3LjUgNzcuNSAwIDAgMCAyMC4wMiAyLjc3YzcuMTUtLjA3IDEzLjk0LTEgMjAuMTctMi43NGE1Mi4zIDUyLjMgMCAwIDAgMTcuNDgtOC40NiA0MC40IDQwLjQgMCAwIDAgMTEuOTUtMTQuNTdjMS42Mi0zLjI2IDMuNzctMTAuMDQgMy43Ny0xNC42OCAwLS4zOC0uMTctLjc0LS41NC0uODJsLTE2Ljg5LS40Yy0uMi0uMDQtLjM0LjM0LS4zNC41NCAwIC4yNy0uMDMuNC0uMDYuNi0uNSAyLjgyLTEuMzggNS40LTIuNjEgNy42OWEyOC41MyAyOC41MyAwIDAgMS04LjI0IDkuNTggMzUuMDEgMzUuMDEgMCAwIDEtMTEuOTYgNS41NyA1NS4yNSA1NS4yNSAwIDAgMS0xMi41NyAxLjc3Yy00LjMyLS4xLTguNjEtLjcxLTEyLjc1LTEuOGEzNS4wNSAzNS4wNSAwIDAgMS0xMS45Ni01LjU3IDI4LjUyIDI4LjUyIDAgMCAxLTguMjMtOS41OGMtMS44Ni0zLjQ0LTIuOS03LjU1LTMuMDktMTIuMmwtLjAxLTcuNDdoODkuMTZhLjcuNyAwIDAgMCAuNy0uNzJ2LTM5LjVjLS4xLTcuNjUtMS41OC0xNC40LTQuMzgtMjAuMDZhNDAuNCA0MC40IDAgMCAwLTExLjk1LTE0LjU2IDUyLjM3IDUyLjM3IDAgMCAwLTE3LjQ4LTguNDcgNzcuNTYgNzcuNTYgMCAwIDAtMjAuMDEtMi43N1oiLz48cGF0aCBmaWxsPSIjY2U0OTJlIiBkPSJNNDE5LjM4IDI4MC42M2gtNy41N2EuNy43IDAgMCAwLS43MS43MXYxNS40MmE4LjE3IDguMTcgMCAwIDAtMy43OCA2LjkgOC4yOCA4LjI4IDAgMCAwIDE2LjU0IDAgOC4yOSA4LjI5IDAgMCAwLTMuNzYtNi45di0xNS40MmEuNy43IDAgMCAwLS43Mi0uNzEiLz48L2c%2BPC9zdmc%2B&logoColor=white&labelColor=white)](https://a-sit-plus.github.io)
[![Powered by VC-K](https://img.shields.io/badge/VC--K-powered-8A2BE2?logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCA4LjAzIDkuNSI+PGcgZmlsbD0iIzhhMmJlMiIgZm9udC1mYW1pbHk9IlZBTE9SQU5UIiBmb250LXNpemU9IjEyLjciIHRleHQtYW5jaG9yPSJtaWRkbGUiPjxwYXRoIGQ9Ik01OS42NCAyMjIuMTNxMC0uOTguMzYtMS44Mi4zNy0uODQuOTgtMS40Ni42Mi0uNjIgMS40Ni0uOTYuODMtLjM2IDEuOC0uMzUgMS4wMy4wMiAxLjkuNDIuODcuNCAxLjUgMS4xMi4wNC4wNS4wMy4xMSAwIC4wNy0uMDUuMWwtMSAuODZxLS4wNi4wMy0uMTIuMDN0LS4xLS4wNnEtLjQyLS40OC0xLS43Ni0uNTYtLjMtMS4yMi0uMjgtLjYuMDEtMS4xMy4yNy0uNTQuMjQtLjkzLjY3LS40LjQyLS42Mi45OC0uMjMuNTYtLjIzIDEuMiAwIC42My4yNCAxLjE4LjI0LjU2LjY1Ljk4LjQuNDIuOTQuNjYuNTMuMjMgMS4xNC4yMy42My0uMDEgMS4yLS4zLjU1LS4yNy45Ni0uNzUuMDQtLjA1LjEtLjA1LjA2LS4wMi4xMS4wM2wxIC44NnEuMDYuMDMuMDYuMS4wMS4wNi0uMDMuMTEtLjY0LjczLTEuNTMgMS4xNC0uOS40MS0xLjk1LjQtLjk1IDAtMS43OS0uMzYtLjgyLS4zNy0xLjQzLS45OS0uNjEtLjYzLS45NS0xLjQ4LS4zNS0uODUtLjM1LTEuODN6IiBzdHlsZT0iLWlua3NjYXBlLWZvbnQtc3BlY2lmaWNhdGlvbjpWQUxPUkFOVDt0ZXh0LWFsaWduOmNlbnRlciIgdHJhbnNmb3JtPSJ0cmFuc2xhdGUoLTU5LjY0IC0yMTcuNDIpIi8+PHBhdGggZD0iTTY2LjIxIDIyMS4zNWgxLjNjLjEgMCAuMTYuMDYuMTYuMTd2MS4zOGMwIC4xMS0uMDUuMTctLjE2LjE3aC0xLjNjLS4xIDAtLjE2LS4wNi0uMTYtLjE3di0xLjM4YzAtLjExLjA1LS4xNy4xNi0uMTd6IiBsZXR0ZXItc3BhY2luZz0iLTMuMTIiIHN0eWxlPSItaW5rc2NhcGUtZm9udC1zcGVjaWZpY2F0aW9uOlZBTE9SQU5UO3RleHQtYWxpZ246Y2VudGVyIiB0cmFuc2Zvcm09InRyYW5zbGF0ZSgtNTkuNjQgLTIxNy40MikiLz48L2c+PC9zdmc+&logoColor=white&labelColor=white)](https://github.com/a-sit-plus/vck)
[![Kotlin](https://img.shields.io/badge/kotlin-2.3.20-blue.svg?logo=kotlin)](http://kotlinlang.org)


</div>

Reference relying party for EUDI Wallet presentations, built with Spring Boot and
[VC-K](https://github.com/a-sit-plus/vc-k).

This project shows how a service provider can request credentials from a wallet,
receive a presentation response, validate the returned credentials, and expose the
result to a simple web UI. It is intended as a practical integration example for
OpenID4VP, ISO mDoc, SD-JWT VC, DCQL, and the browser
Digital Credentials API.

| ⚠️ Warning                                             |
|:-------------------------------------------------------|
| This service is intended as a Technology Demonstrator! |

## What This Demonstrates

- **OpenID4VP relying party flows** using VC-K's verifier APIs.
- **Cross-device and same-device wallet handover** with QR codes and wallet deep links.
- **Digital Credentials API support** for OpenID4VP and ISO mDoc requests.
- **DCQL request generation** from selected credential attributes.
- **Signed authorization requests** using X.509 based client identifier schemes.
- **Response validation** for OpenID4VP, SD-JWT VC, and ISO mDoc presentations, with a **validation report** listing every check of the response, its presentations, credentials and status lists on the result page.
- **Trust from the EU Lists of Trusted Entities (LoTE)**: issuers and status list signers are trusted per credential type through the lists of the configured stages, next to the A-SIT root.
- **Status list resolution** for token status checks (JWT and CWT status lists, CWT identifier lists), cached and with a timeout.
- **Wallet-relying party certificates**: requests signed with a selectable access certificate (WRPAC), carrying a registration certificate (WRPRC) as JWT in OpenID4VP `verifier_info` and as CWT in ISO 18013-7 Annex C `euWrprc`.
- **EUDI Wallet Launchpad 2026 test cases** for remote presentation, see `remote-case.html`.
- **Native DC API demonstrators** for iOS and Android.
- **Verifier key configuration** with ephemeral, PEM file, or Java KeyStore backed keys.
- **Credential profile examples** for PID, mDL, age verification, EHIC, tax ID, certificate of residence, and power of representation.

## How VC-K Is Used

The service delegates the protocol-heavy work to VC-K and related A-SIT Plus wallet
libraries:

- `OpenId4VpVerifier` creates OpenID4VP authorization requests (as a `CreatedRequest` with the wallet URL and the request object to serve) and validates wallet responses.
- `DcApiVerifier` creates Digital Credentials API requests for OpenID4VP (signed or unsigned) and ISO 18013-7 Annex C, the latter with reader authentication, and validates their responses.
- `CredentialPresentationRequestBuilder` converts selected credentials and attributes into DCQL and ISO mDoc device requests.
- `CredentialValidator` validates every presented credential under a `ValidationPolicy`: issuer signature and trust, holder binding, validity period, and status with a `StatusValidator`. Both verifiers return a `ValidationReport` of the response, which the result page shows check by check.
- `LoTEFilterService` and `LoteProfile` select the trust anchors of each credential type from the Lists of Trusted Entities, whose signatures are verified with `VerifyJwsObjectJades`.
- `PreparedHttpResponse` carries the status, headers and encoded body VC-K prepares, e.g. request objects and response acknowledgements, which are passed to Spring unchanged.
- `ClientIdScheme.CertificateHash`, `ClientIdScheme.CertificateSanDns`, and `ClientIdScheme.RedirectUri` model the different verifier identification profiles.
- Credential schemes are no longer compiled in per credential. Instead a `RemoteCredentialMetadataRegistry` (from `vck-openid-ktor`) resolves [SD-JWT Type Metadata](https://github.com/a-sit-plus/credentials-collection) documents over HTTP at runtime; no `Initializer.initWithVCK()` calls are needed. The `eupid` and `mobiledrivinglicence` libraries are still on the classpath, but only to register the ISO mdoc value serializers for non-primitive claims (dates, portrait, driving privileges).

The main integration points are:

- [`CredentialCatalog.kt`](service/src/main/kotlin/at/asit/wallet/relyingparty/CredentialCatalog.kt) lists the known credentials and their hosted type-metadata URLs (the single source for both registration and the UI).
- [`RelyingPartyConfiguration.kt`](service/src/main/kotlin/at/asit/wallet/relyingparty/RelyingPartyConfiguration.kt) loads verifier signing keys and registers the remote metadata registry and ISO value serializers with VC-K.
- [`VerifierProfiles.kt`](service/src/main/kotlin/at/asit/wallet/relyingparty/VerifierProfiles.kt) defines the supported verifier profiles and builds OpenID4VP, DC API, and ISO mDoc requests.
- [`ApiController.kt`](service/src/main/kotlin/at/asit/wallet/relyingparty/ApiController.kt) creates transactions, returns request objects, receives wallet responses, and invokes VC-K validation.
- [`CredentialValidation.kt`](service/src/main/kotlin/at/asit/wallet/relyingparty/CredentialValidation.kt) configures the `CredentialValidator`: one trust scope per credential type from the Lists of Trusted Entities, fetched by [`TrustListService.kt`](service/src/main/kotlin/at/asit/wallet/relyingparty/TrustListService.kt), and status lists fetched by [`StatusListTokenFetcher.kt`](service/src/main/kotlin/at/asit/wallet/relyingparty/StatusListTokenFetcher.kt).
- [`ValidationReportView.kt`](service/src/main/kotlin/at/asit/wallet/relyingparty/ValidationReportView.kt) turns VC-K's validation report into the view of the result page.
- [`WrpCertificateStore.kt`](service/src/main/kotlin/at/asit/wallet/relyingparty/WrpCertificateStore.kt) loads the configured access and registration certificates.
- [`TransactionRequest.kt`](service/src/main/kotlin/at/asit/wallet/relyingparty/TransactionRequest.kt) maps UI selections to VC-K credential request options.
- [`LoginConfigController.kt`](service/src/main/kotlin/at/asit/wallet/relyingparty/LoginConfigController.kt) serves the UI's credential/attribute picker (`/js/login-config.js`), generated from the type-metadata documents instead of a hand-maintained static file.

## Supported Presentation Profiles

The service defines the following verifier profiles in `VerifierProfiles.kt`. The 2026 test-case pages select `EUDIW2026` explicitly; the custom request page uses the default profiles. Every profile supports cross-device QR, same-device links, and Digital Credentials API requests. For DC API, the request selects OpenID4VP, ISO mdoc, or a combination independently of the verifier profile.

| Profile | Request style | Offered by default |
| --- | --- | --- |
| `EUDIW2026` | EUDI.Wallet 2026: `eu-eaap://`, `x509_hash`, `direct_post.jwt` | No; selected by the 2026 test cases |
| `HAIPd05` | HAIP d05: `x509_hash`, `direct_post.jwt` | Yes |
| `AV` | Age verification: `redirect_uri`, inline request, `direct_post` | Yes |
| `MDOC10` | ISO 18013-7: OpenID4VP 1.0, `x509_san_dns`, `direct_post.jwt` | Yes |
| `EUDIW` | EUDI Wallet reference: OpenID4VP 1.0, `x509_san_dns`, `direct_post.jwt` | Yes |

`MDOC10` references the [final version 1.0 standard](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html).
Selecting a WRPAC uses its signing key and certificate chain with the `x509_hash` client identifier defined in OpenID4VP 1.0.
For requests by reference, the transaction retains VC-K's generated wallet URL and request-object loader alongside the verifier used to validate the response.

Supported credential examples include:

- Personal Identification Data in ISO mDoc and SD-JWT VC form
- Mobile Driving Licence
- Age Verification
- European Health Insurance Card
- Tax ID
- Certificate of Residence
- Power of Representation
- Health ID
- Company Registration

The list is driven entirely by the type-metadata documents in [`CredentialCatalog.kt`](service/src/main/kotlin/at/asit/wallet/relyingparty/CredentialCatalog.kt); add an entry there to surface a new credential, no code changes required.

## Architecture

```text
Browser UI
   |
   | POST /transaction/create
   v
Spring Boot relying party
   |
   | builds DCQL / ISO mDoc request with VC-K
   v
Wallet handover
   |
   | QR code, deep link, or Digital Credentials API
   v
Wallet
   |
   | POST /transaction/result/{id}
   v
VC-K validation
   |
   | validated credentials and validation report
   v
Result API and demo UI
```

Important endpoints:

| Endpoint | Description |
| --- | --- |
| `POST /transaction/create` | Creates transactions for all supported profiles and returns QR codes, wallet URLs, and DC API URLs. |
| `GET /transaction/get/{id}` | Returns the signed or unsigned authorization request for device handover flows. |
| `GET /transaction/get/dcapi/{id}` | Returns Digital Credentials API request options. Query parameters: `oid4vpMode` (`SIGNED`, `UNSIGNED`, or `NONE`; default `SIGNED`) selects the OpenID4VP request, `isoMdoc` (default `false`) adds an ISO 18013-7 Annex C request, `encrypt` (default `true`) requests an encrypted OpenID4VP response. At least one request type is required. |
| `POST /transaction/result/{id}` | Receives wallet responses and validates them with VC-K. |
| `POST /utilities/buildCredentialQueries` | Builds a DCQL query from credential selections. |
| `GET /api/items` | Returns validated presentation results stored by the demo. |
| `GET /api/single/{id}` | Returns one validated presentation result, with its validation report. |
| `GET /api/wrp/certs/access` | Lists the configured access certificates (WRPAC) to select from. |
| `GET /api/wrp/certs/registrations` | Lists the configured registration certificates (WRPRC) to select from. |
| `GET /api/wrp/availability` | Tells whether access and registration certificates are configured. |
| `GET /js/login-config.js` | The credential and attribute picker of the UI, generated from the type-metadata documents. |
| `GET /logs/{id}` | Returns log messages captured during processing of a specific transaction. Useful for debugging individual wallet presentation flows. |

## Quick Start

Prerequisites:

- JDK 21 for the build environment
- A wallet that supports one of the configured profiles
- A publicly reachable `app.public-context` URL for real device-to-server testing

Run locally:

```bash
./gradlew :service:bootRun
```

Open:

```text
http://localhost:8080
```

Build and test:

```bash
./gradlew clean assemble test build
```

The VC-K version is set in [`gradle/libs.versions.toml`](gradle/libs.versions.toml). If a VC-K checkout exists next to this repository (`../vck`), Gradle includes it as a composite build instead, so changes to VC-K can be tested here without publishing them.

### Native iOS demonstrator

The native SwiftUI app in [`ios/WalletRelyingParty.xcodeproj`](ios/WalletRelyingParty.xcodeproj) requires iOS 26 or later. It opens the PID mdoc Annex C test case in `ASWebAuthenticationSession`, lets an installed identity document provider such as Valera handle the Digital Credentials API request, and displays the validated credential data returned by this service.

The entire app code has been generated by ChatGPT and should be treated as a pure demonstrator for native DCAPI calls.

Requirements:

- Xcode 26 or later and an iOS 26+ device
- Valera installed and registered as an identity document provider for the PID mdoc
- The web callback bridge from this repository deployed at `https://wallet-rp.a-sit.plus/`
- An Apple development team selected for the `at.asitplus.wallet.rp` app target

The authentication session starts at:

```text
https://wallet-rp.a-sit.plus/remote-case.html?id=6&client=ios&state=<uuid>
```

After DCAPI validation, the page returns the transaction capability to the app through:

```text
wallet-rp://auth/callback?transaction_id=<id>&state=<uuid>
```

The app validates the state, loads `/api/single/<id>`, and renders the returned credential claims. Build and compile the unit tests from the command line with:

```bash
xcodebuild build-for-testing \
  -project ios/WalletRelyingParty.xcodeproj \
  -scheme WalletRelyingParty \
  -destination 'generic/platform=iOS' \
  CODE_SIGNING_ALLOWED=NO
```

### Native Android demonstrator

The native Android app in [`android`](android) offers two PID mdoc login paths. **Login with Android** calls Credential Manager's Digital Credentials API directly; **Login with browser** retains the AndroidX Browser Auth Tab flow. Both paths let Valera present the credential and display the validated data returned by this service.

The entire app code has been generated by ChatGPT and should be treated as a pure demonstrator for native DCAPI calls.

Requirements:

- Android 9 (API 28) or later
- Valera installed and registered as the credential provider for the PID mdoc
- The relying-party service deployed at `https://wallet-rp.a-sit.plus/`
- Android SDK 36 for building the app

The browser flow additionally requires Chrome 141 or later as the default browser. The direct flow creates a PID mdoc transaction, supplies the app's `android:apk-key-hash` origin, starts Credential Manager with the returned DC API options, and submits the `DigitalCredential` response directly to the relying party.

The Auth Tab starts at:

```text
https://wallet-rp.a-sit.plus/remote-case.html?id=6&client=android&state=<uuid>
```

The page returns the transaction capability directly to the Auth Tab result callback through:

```text
wallet-rp://auth/callback?transaction_id=<id>&state=<uuid>
```

The app validates the callback and state, loads `/api/single/<id>`, and renders the portrait and credential claims. Open the `android` directory in Android Studio, or build and test it from the repository root with:

```bash
./gradlew -p android :app:testDebugUnitTest :app:assembleDebug
```

The debug APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`.

The runnable Spring Boot JAR is produced in:

```text
service/build/libs/
```

### Docker

A multi-stage `Dockerfile` is provided in the project root. It builds the application using JDK 21 and produces a minimal JRE-based image.

Build the image:

```bash
docker build -t wallet-relying-party .
```

Run the container:

```bash
docker run -p 8080:8080 \
  -e JAVA_OPTS="-Xmx512m" \
  -e APP_PUBLIC_CONTEXT="https://my-relying-party.example.com/" \
  wallet-relying-party
```

Pass any `app.*` configuration properties as environment variables using Spring Boot's relaxed binding (e.g. `APP_PUBLIC_CONTEXT` maps to `app.public-context`).

## Configuration

Custom application properties live under `app` and are defined in
[`AppConfigurationProperties.kt`](service/src/main/kotlin/at/asit/wallet/relyingparty/AppConfigurationProperties.kt).

Minimal local configuration:

```yaml
app:
  public-context: "http://localhost:8080/"
  transaction-ttl: 30m
  result-ttl: 30m
  trust-list-stages: [DEVELOPMENT, ACCEPTANCE]
  verifier-key:
    type: MEMORY
```

### Optional Spring Cloud Config Client

The service can read configuration from a Spring Cloud Config Server, such as an
internal service endpoint. This remains disabled by default, so the application
still starts normally when no config server is available.

Set these environment variables to enable it:

- `SPRING_CLOUD_CONFIG_ENABLED=true`
- `SPRING_CLOUD_CONFIG_URI=http://<internal-config-service>:8888`

Optional override:

- `SPRING_CONFIG_IMPORT=optional:configserver:` if you want to override the default import value explicitly
- `SPRING_APPLICATION_NAME=wallet-relying-party` to control which config server application name is resolved
- `SPRING_PROFILES_ACTIVE=<profile>` if the remote config is profile-specific.

Example:

```bash
SPRING_CLOUD_CONFIG_ENABLED=true \
SPRING_CLOUD_CONFIG_URI=http://config.internal.svc.cluster.local:8888 \
./gradlew :service:bootRun
```

`app.public-context` must be the externally reachable base URL of this relying
party. It is embedded in request objects, wallet links, callback URLs, metadata,
and expected origins.

`app.transaction-ttl` controls how long pending wallet presentation transactions
remain valid. `app.result-ttl` controls how long validated demo results remain
available through `/api/items` and `/api/single/{id}`. Both default to 30
minutes, and expired entries are removed by a scheduled cleanup task.

`app.trust-list-stages` selects the stages of the European Commission's trust
infrastructure (`DEVELOPMENT`, `ACCEPTANCE`, `PRODUCTION`) to fetch the Lists
of Trusted Entities from. Defaults to `[DEVELOPMENT, ACCEPTANCE]`. The lists of
all configured stages are combined per credential type: issuers are trusted
through their issuance services, signers of status lists through their
revocation services, next to the A-SIT root. The development stage is included
by default, as most PID providers publish the revocation services that
authorize their status list signers only there. A list a stage does not
publish, or one that can not be verified, is skipped (and its cached version
kept), without affecting the other lists.

`app.validation` sets the policy presentations are validated under, see
`ValidationConfigurationProperties` in
[`AppConfigurationProperties.kt`](service/src/main/kotlin/at/asit/wallet/relyingparty/AppConfigurationProperties.kt).
It is strict by default; relax it for test setups, e.g. with
`require-timeliness: false` or `trust: INTEGRITY_ONLY`. Parsing, signatures and
holder binding are always checked.

```yaml
app:
  validation:
    trust: REQUIRE_AUTHORIZED_SIGNER          # or INTEGRITY_ONLY: issuers are reported, but need not be trusted
    status: VALIDATE_IF_PRESENT               # or SKIP, or REQUIRE_CLAIM: every credential needs a status
    status-signer-trust: REQUIRE_AUTHORIZED_SIGNER
    accepted-status: [0]                      # token statuses accepted, 0 being valid
    require-timeliness: true                  # reject expired or not yet valid credentials
    time-leeway: 5m
```

The result page shows the decision of every check under this policy; a
credential that is rejected is listed with the reason, but without its claims.
Status lists are fetched while the wallet waits for its response to be
acknowledged: a status list server that does not answer within 5 seconds leaves
the status undetermined. Fetched status lists are cached until their `ttl`
elapses or they expire, but at most for 5 minutes.

### Optional Spring Boot Admin Client

The service includes the [Spring Boot Admin](https://github.com/codecentric/spring-boot-admin) client. It is inactive by default — no registration attempt is made unless a server URL is configured.

To connect the service to a running Spring Boot Admin server, set:

```yaml
spring:
  boot:
    admin:
      client:
        url: http://<admin-server>:9090
        enabled: true
        instance:
          metadata:
            user.name: actuator-user
            user.password: secret
```

Or via environment variables:

```bash
SPRING_BOOT_ADMIN_CLIENT_URL=http://admin.internal.svc.cluster.local:9090 \
SPRING_BOOT_ADMIN_CLIENT_ENABLED=true \
SPRING_BOOT_ADMIN_CLIENT_INSTANCE_METADATA_USER_NAME=actuator-user \
SPRING_BOOT_ADMIN_CLIENT_INSTANCE_METADATA_USER_PASSWORD=secret \
./gradlew :service:bootRun
```

Once registered, the admin server provides a UI for health checks, log levels, metrics, and environment inspection. This is optional infrastructure — the relying party runs fully without it.

#### Actuator endpoint security

Access to `/actuator/**` is controlled based on whether the admin client credentials are configured:

- **Admin client not configured** (default): all `/actuator/**` requests are denied with `403 Forbidden`.
- **Admin client configured** with `instance.metadata.user.name` and `instance.metadata.user.password`: `/actuator/**` requires HTTP Basic authentication with those credentials. Requests without credentials receive `401 Unauthorized`. The admin server passes the credentials when polling the actuator endpoints automatically.

The actuator endpoints are never reachable without credentials, even when `spring.boot.admin.client.enabled=true` — both the client flag and the credentials must be set.

### Verifier Key

The verifier key signs authorization requests and provides verifier identity
material for X.509 based profiles.

Ephemeral development key:

```yaml
app:
  verifier-key:
    type: MEMORY
```

PEM files:

```yaml
app:
  verifier-key:
    type: FILE
    file:
      private-key: file:issuer-key-private.pem
      public-key: file:issuer-key-public.pem
      certificate: file:issuer-cert.pem
```

Java KeyStore:

```yaml
app:
  verifier-key:
    type: KEYSTORE
    keystore:
      path: file:/some/path/keystore.p12
      type: PKCS12
      provider: BC
      password: changeit
      alias: key1
      alias-password: changeit
```

Do not use the in-memory key for interoperable or production-like testing where
wallets need stable verifier trust material.

### WRPAC/WRPRC

To use access certificates and registration certificates you need a signed certificate from the registrar.
For that purpose we use our demo registrar at https://wrp-registrar.a-sit.plus.

Therefore, a wallet relying party creates a private key and a certificate signing request.

#### 1. Create keys:
```bash
openssl req -new -newkey rsa:2048 -nodes -keyout wrp.key -out wrp.csr
```
Next the certificate signing request gets transmitted to the registrar which in turn approves the request and issues a certificate chain.
We can store the now trusted private key as well as the associated certificate chain in a PKCS12 key store file
#### 2. Add to keystore
```bash
openssl pkcs12 -export -out wrp.p12 -inkey wrp.key -in wrp.pem -name changeit -passout pass:changeit
```

#### 3. Adjust the configuration to your needs
To add access and registration certificates to the relying party, add the `wrp` section to `app`
```yaml
app:
  wrp:
    ac:
      - label: Demo Service
        keystore:
          path: file:keystore.p12
          type: PKCS12
          provider: BC
          password: changeit
          alias: wrpac
          alias-password: changeit
    rc:
      - label: Entwicklungsübersicht
        path: file:wrprc-entwicklung.jws
      - label: Altersprüfung 18+
        path: file:wrprc-alterspruefung.jws
      - label: Identitätsprofil
        path: file:wrprc-identitaetsprofil.jws
        cwt-path: file:wrprc-identitaetsprofil.cwt
      - label: Adressübersicht
        path: file:wrprc-adressuebersicht.jws
```
#### Field explanation
Access certificates: List to load one or multiple access certificates:
- `ac.label`: Human-readable text shown when selecting the access certificate.
- `ac.keystore.path`: Path to the keystore file.
- `ac.keystore.password`: Password to unlock the keystore.
- `ac.keystore.type`: Type of the keystore, e.g. "PKCS12".
- `ac.keystore.provider`: Provider of the keystore.
- `ac.keystore.alias`: Alias of the key inside the keystore.
- `ac.keystore.alias-password`: Password to unlock the key inside the keystore.

Each keystore must contain the key as well as the certificate chain provided through the registrar.

Registration certificates: List to load one or multiple registration certificates:
- `rc.label`: Human readable text describing the registration certificate
- `rc.path`: Path to the registration certificate as JWS (see below)
- `rc.cwt-path`: Path to the same registration certificate as CWT (COSE_Sign1), binary or base64-encoded, optionally tagged as CWT (tag 61) and/or COSE_Sign1 (tag 18), always sent untagged

At least one of `rc.path` and `rc.cwt-path` is required. Selecting a registration certificate sends its JWS in OpenID4VP requests (`verifier_info`) and its CWT in ISO 18013-7 Annex C requests (`euWrprc` of each document request), so a DC API request offering both protocols carries both. Annex C requests carry `readerAuth`, signed with the selected access certificate. For compatibility, a CWT in `rc.path` is used as if it was set in `rc.cwt-path`.

#### Example jws file content

Example content of a registration certificate JWS file:

```json
eyJ4NWMiOlsiTUlJQ1NqQ0NBZStnQXdJQkFnSVZBTlRMWXAwMXQ2VWY5dVVWWU5yMmJLaXZsbC9JTUFvR0NDcUdTT[...]
```

```json
{
  "name": "Demo Services",
  "sub_ln": [ "Service" ],
  "sub": "WRP-5BF7F0FA3DBB",
  "country": "AT",
  "registry_uri": "https://wrp-registrar.a-sit.plus/wrp",
  "srv_description": [ { "lang": "en", "value": "Identity Check" } ],
  "entitlements": [ "https://uri.etsi.org/19475/Entitlement/Service_Provider" ],
  "privacy_policy": [ "https://services.example.at/privacy/identity-check" ],
  "support_uri": [ "https://wallet.a-sit.plus/support" ],
  "supervisory_authority": { "uri": [ "https://www.dsb.gv.at" ] },
  "policy_id": [ "0.4.0.19475.3.1" ],
  "certificate_policy": "https://wrp-registrar.a-sit.plus/certificate-policy",
  "iat": 1783589391,
  "status": {
    "status_list": { "idx": 0, "uri": "https://wrp-registrar.a-sit.plus/statuslists/1" }
  },
  "purpose": [
    { "lang": "en", "value": "Identity checks for digital onboarding processes" }
  ],
  "credentials": [
    {
      "format": "mso_mdoc",
      "meta": { "doctype_value": "eu.europa.ec.eudi.pid.1" },
      "claim": [ { "path": [ "eu.europa.ec.eudi.pid.1", "given_name" ] } ]
    }
  ],
  "intended_use_id": "urn:uuid:ba626804-d6a1-5147-b3c9-b888f58e8bb5",
  "public_body": false,
  "exp": 1815125391
}
```

Wallets validating WRPRCs with VC-K 9 apply the multiplicities of ETSI TS 119 475 V1.2.1, Annex B: `support_uri`, `supervisory_authority` and `policy_id` (with the WRPRC policy `0.4.0.19475.3.1`) are required, an entitlement of Annex A.2 is required, a service provider needs a `purpose`, and empty arrays, e.g. an empty `provides_attestations`, are rejected. `sub_ln`, `privacy_policy`, `info_uri` and `support_uri` may hold several values. `srv_description` holds the localized descriptions of the one service the WRPRC is issued for; the request page also reads it as an array of such arrays.

Reference: https://www.etsi.org/deliver/etsi_ts/119400_119499/119475/01.02.01_60/ts_119475v010201p.pdf


## Certificates

For ISO 18013-5 and ISO 18013-7 related flows, the verifier certificate needs the
extensions expected by the wallet profile being tested. In particular, the
certificate used for authorization request signing should include:

- Authority Key Identifier
- CRL Distribution Points with at least one URI
- Extended Key Usage for TLS Web Server Authentication and `1.0.18013.5.1.6`
- Key Usage for Digital Signature and Key Encipherment
- Subject Alternative Name matching the public relying party hostname
- Subject Key Identifier

The issuing root certificate should include:

- Authority Key Identifier
- Basic Constraints with CA enabled
- Key Usage for Certificate Signing and CRL Signing
- Subject Key Identifier

For custom wallet builds, the root certificate may need to be added as a trust
anchor so that the wallet trusts signed authorization requests.

## Limitations

This repository is ready to run as a relying party demo, but it is intentionally
not a drop-in production service. Keep these points in mind when using it:

- **Use HTTPS and a public URL for real wallet tests**: many wallet and browser flows require an externally reachable `app.public-context` with HTTPS. `localhost` is only suitable for local development and same-machine experiments.
- **The default verifier key is ephemeral**: `type: MEMORY` creates a new self-signed key on startup. Use a stable file or keystore-backed verifier certificate when testing trust, signed requests, or interoperability with real wallets.
- **Trust anchors are wallet-specific**: wallets may reject authorization requests unless the verifier certificate chain is trusted by that wallet or test environment.
- **Transactions are kept in memory**: active transactions and demo results expire after the configured TTL, but they are not persisted across restarts and are not shared across multiple service instances.
- **The demo stores presentation results**: validated credential data is exposed through `/api/items` and related demo endpoints. Do not run this unchanged with real personal data unless you have reviewed retention, access control, and logging behavior.
- **Logging is verbose for integration work**: request and response details can be useful while debugging, but may contain personal or protocol-sensitive data.
- **Supported profiles are hard-coded**: verifier profiles are defined in `VerifierProfiles.kt`. Adjust that file if your wallet requires a different client identifier scheme, response mode, protocol draft, or DC API variant.
- **The static UI is a demo client**: the frontend is intentionally simple and mirrors some backend constants. Treat it as an example, not as a finished application UI.
- **Browser DC API support depends on the runtime environment**: Digital Credentials API behavior is browser, platform, flag, and wallet dependent.
- **Trust lists and status lists are fetched over the network**: make sure the service can reach the Lists of Trusted Entities of the configured stages and the status list endpoints referenced by the presented credentials. Without the lists, only the A-SIT root is trusted.
- **Credential type metadata is fetched at runtime**: credential schemes and the UI picker are resolved from the documents at the `CredentialCatalog.BASE_URL` branch of [`credentials-collection`](https://github.com/a-sit-plus/credentials-collection). The service must be able to reach those raw URLs; a credential whose document is unreachable on cold start is omitted until the next request (successful documents are cached). The default branch is `main` — point it at a branch that actually hosts the documents.

## Contributing

External contributions are greatly appreciated! Be sure to observe the contribution guidelines (see [CONTRIBUTING.md](CONTRIBUTING.md)).
In particular, external contributions to this project are subject to the A-SIT Plus Contributor License Agreement (see also [CONTRIBUTING.md](CONTRIBUTING.md)).

<p align="center">
The Apache License does not apply to the logos, (including the A-SIT logo) and the project/module name(s), as these are the sole property of
A-SIT/A-SIT Plus GmbH and may not be used in derivative works without explicit permission!
</p>
