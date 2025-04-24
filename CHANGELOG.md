# Changelog

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
 - Disable attribute selection on some credential types, because they are not selectively discosable: PoR, HealthID

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
 - Update to vck 5.0.0

Version 4.2.0:
 - Major UI redesign

Version 4.1.0:
 - Remove unused code: locks, state-map

Version 4.0.1:
 - Update to VCK (renamed VcLib)
 - Update credentials
 - Add E-Prescription support
 - Add simple.html which is used as presentation demo

Version 4.0.0:
 - Update to vclib 4.0.0

Version 3.8.0:
 - Update to vclib 3.8.0

Version 2.4.0:
 - Replace mustache templates with vue.js frontend from FH
 - Remove notion of "codes" from API controller

Version 2.3.0:
 - Upgrade to vclib 3.3.0, idacredential 3.3.0
 - Discard separate MDL use case, is integrated in IDA credential
 - Support SIOPv2 authentication with POST and QUERY response modes
 - Display QR code for SIOPv2 cross-device authentication flow
 - Rework URLs to drop `/terminal` prefix
