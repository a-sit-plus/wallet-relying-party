# Wallet Relying Party

This service implements the Relying Party for a Wallet system, requesting credentials from Wallets using OpenID for Verifiable Presentations.

See <https://github.com/a-sit-plus/valera> for the Wallet App Valera.

## Transactions

The web page calls `/transaction/create` to create a new transaction, by specifying which credential to request in which representation. The result is the QR Code to display (to scan by wallets on a different device), a link for wallets on the same device, and the transaction id.

The client can then call the contained URL in the format `/transaction/get/{id}` to get the authentication request. Contained in it is the URL to POST the result to, in format `/transaction/result/{id}`.

All user data (that is all users extracted from authentication responses) can be read by calling `/api/items`, modified by `/api/remove` and read one-by-one at `/api/single/{id}`.

## Certificates

The certificate for the key used to sign authorization requests (stored in `verifier.p12`) needs to have the following extensions:
- Authority Key Identifier (AKI)
- CRL Distribution Points (CDP): At least one entry with an URI, and an existing, but empty CRL there
- Extended Key Usage (EKU): TLS Web Server Authentication (1.3.6.1.5.5.7.3.1), 1.0.18013.5.1.6 (for mDoc)
- Key Usage (KU): Digital Signature, Key Encipherment
- Subject Alternative Name (SAN):
    - DNS Name: `apps.egiz.gv.at`
    - DNS Name: `x509_san_dns:apps.egiz.gv.at`
- Subject Key Identifier (SKI)

The root certificate (signing the verifier certificate) needs to have the following extensions:
- Authority Key Identifier (AKI)
- Basic Constraints: CA
- Key Usage (KU): Certificate Signing, CRL Signing
- Subject Key Identifier (SKI)

The root certificate may be added to custom builds of the EUDIW Reference Wallet to enable trust in authorization requests.
