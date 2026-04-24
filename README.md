# Wallet Relying Party

Reference relying party for EUDI Wallet presentations, built with Spring Boot and
[VC-K](https://github.com/a-sit-plus/vc-k).

This project shows how a service provider can request credentials from a wallet,
receive a presentation response, validate the returned credentials, and expose the
result to a simple web UI. It is intended as a practical integration example for
OpenID4VP, ISO mDoc, SD-JWT VC, DCQL, Presentation Exchange, and the browser
Digital Credentials API.

## What This Demonstrates

- **OpenID4VP relying party flows** using VC-K's verifier APIs.
- **Cross-device and same-device wallet handover** with QR codes and wallet deep links.
- **Digital Credentials API support** for OpenID4VP and ISO mDoc requests.
- **DCQL and Presentation Exchange** request generation from selected credential attributes.
- **Signed authorization requests** using X.509 based client identifier schemes.
- **Response validation** for OpenID4VP, SD-JWT VC, and ISO mDoc presentations.
- **Status list resolution** for token status checks.
- **Verifier key configuration** with ephemeral, PEM file, or Java KeyStore backed keys.
- **Credential profile examples** for PID, mDL, age verification, EHIC, tax ID, certificate of residence, and power of representation.

## How VC-K Is Used

The service delegates the protocol-heavy work to VC-K and related A-SIT Plus wallet
libraries:

- `OpenId4VpVerifier` creates OpenID4VP authorization requests and validates wallet responses.
- `Iso180137AnnexCVerifier` creates and validates ISO 18013-7 Annex C mDoc requests for DC API flows.
- `CredentialPresentationRequestBuilder` converts selected credentials and attributes into Presentation Exchange, DCQL, and ISO mDoc device requests.
- `VerifierAgent`, `ValidatorSdJwt`, and `ValidatorMdoc` validate credential presentations and their cryptographic material.
- `ClientIdScheme.CertificateHash`, `ClientIdScheme.CertificateSanDns`, and `ClientIdScheme.RedirectUri` model the different verifier identification profiles.
- Credential scheme modules register EUDI data models with VC-K via `Initializer.initWithVCK()`.

The main integration points are:

- [`RelyingPartyApplication.kt`](service/src/main/kotlin/at/asit/wallet/relyingparty/RelyingPartyApplication.kt) registers the credential schemes with VC-K.
- [`VerifierProfiles.kt`](service/src/main/kotlin/at/asit/wallet/relyingparty/VerifierProfiles.kt) defines the supported verifier profiles and builds OpenID4VP, DC API, and ISO mDoc requests.
- [`ApiController.kt`](service/src/main/kotlin/at/asit/wallet/relyingparty/ApiController.kt) creates transactions, returns request objects, receives wallet responses, and invokes VC-K validation.
- [`TransactionRequest.kt`](service/src/main/kotlin/at/asit/wallet/relyingparty/TransactionRequest.kt) maps UI selections to VC-K credential request options.
- [`RelyingPartyConfiguration.kt`](service/src/main/kotlin/at/asit/wallet/relyingparty/RelyingPartyConfiguration.kt) loads verifier signing keys.

## Supported Presentation Profiles

The demo currently creates requests for these verifier profiles:

| Profile | Purpose | Supported modes |
| --- | --- | --- |
| `HAIPd05` | HAIP-style OpenID4VP with `x509_hash` and `direct_post.jwt` | Cross-device, same-device, OpenID4VP DC API |
| `AV` | Age verification profile with `redirect_uri` and `direct_post` | Cross-device, same-device, ISO mDoc DC API |
| `UOID4VP` | Unencrypted OpenID4VP over DC API | OpenID4VP DC API |
| `MDOCd23` | ISO 18013-7 draft 23 style OpenID4VP | Cross-device, same-device |
| `MDOCISO` | ISO 18013-7 Annex C | ISO mDoc DC API |
| `EUDIW` | EUDI Wallet reference profile | Cross-device, same-device |
| `DC_API_COMBINED` | Combined unencrypted OpenID4VP and ISO mDoc DC API request | OpenID4VP DC API, ISO mDoc DC API |
| `DC_API_COMBINED_ENCRYPTED` | Combined encrypted OpenID4VP and ISO mDoc DC API request | OpenID4VP DC API, ISO mDoc DC API |

Supported credential examples include:

- Personal Identification Data in ISO mDoc and SD-JWT VC form
- Mobile Driving Licence
- Age Verification
- European Health Insurance Card
- Tax ID
- Certificate of Residence
- Power of Representation

## Architecture

```text
Browser UI
   |
   | POST /transaction/create
   v
Spring Boot relying party
   |
   | builds Presentation Exchange / DCQL / ISO mDoc request with VC-K
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
   | validated credentials and validation summaries
   v
Result API and demo UI
```

Important endpoints:

| Endpoint | Description |
| --- | --- |
| `POST /transaction/create` | Creates transactions for all supported profiles and returns QR codes, wallet URLs, and DC API URLs. |
| `GET /transaction/get/{id}` | Returns the signed or unsigned authorization request for device handover flows. |
| `GET /transaction/get/dcapi/{id}` | Returns Digital Credentials API request options. |
| `POST /transaction/result/{id}` | Receives wallet responses and validates them with VC-K. |
| `POST /utilities/buildCredentialQueries` | Builds Presentation Exchange, DCQL, and device request payloads from credential selections. |
| `GET /api/items` | Returns validated presentation results stored by the demo. |
| `GET /api/single/{id}` | Returns one validated presentation result. |
| `POST /api/remove` | Removes a stored demo result. |

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

The runnable Spring Boot JAR is produced in:

```text
service/build/libs/
```

## Configuration

Custom application properties live under `app` and are defined in
[`AppConfigurationProperties.kt`](service/src/main/kotlin/at/asit/wallet/relyingparty/AppConfigurationProperties.kt).

Minimal local configuration:

```yaml
app:
  public-context: "http://localhost:8080/"
  verifier-key:
    type: MEMORY
```

`app.public-context` must be the externally reachable base URL of this relying
party. It is embedded in request objects, wallet links, callback URLs, metadata,
and expected origins.

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
- **Transactions are kept in memory**: active transactions and demo results are not persisted across restarts and are not shared across multiple service instances.
- **The demo stores presentation results**: validated credential data is exposed through `/api/items` and related demo endpoints. Do not run this unchanged with real personal data unless you have reviewed retention, access control, and logging behavior.
- **Logging is verbose for integration work**: request and response details can be useful while debugging, but may contain personal or protocol-sensitive data.
- **Supported profiles are hard-coded**: verifier profiles are defined in `VerifierProfiles.kt`. Adjust that file if your wallet requires a different client identifier scheme, response mode, protocol draft, or DC API variant.
- **The static UI is a demo client**: the frontend is intentionally simple and mirrors some backend constants. Treat it as an example, not as a finished application UI.
- **Browser DC API support depends on the runtime environment**: Digital Credentials API behavior is browser, platform, flag, and wallet dependent.
- **Remote issuer metadata and status list resolution are used during validation**: make sure the service can reach issuer metadata and status list endpoints required by the presented credentials.

## Related Projects

- [VC-K](https://github.com/a-sit-plus/vc-k) provides the core credential, OpenID4VP, SD-JWT, and mDoc implementation used here.
- [Valera](https://github.com/a-sit-plus/valera) is an A-SIT Plus wallet app useful for interoperability testing.
