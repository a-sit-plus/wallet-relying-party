# Changelog

Version 5.0.3:
 - Update to vck 5.0.1

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
