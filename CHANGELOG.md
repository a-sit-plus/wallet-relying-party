# Changelog

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
 - Add support for european heath insurance card (EHIC) credential (1.1.0)
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
 - Update to VC-K 5.0.0
