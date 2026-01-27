# Wallet Relying Party

This service implements the Relying Party for a Wallet system, requesting credentials from Wallets using OpenID for Verifiable Presentations.

See <https://github.com/a-sit-plus/valera> for the Wallet App Valera.

## Transactions

The web page calls `/transaction/create` to create a new transaction, by specifying which credential to request in which representation. The result is the QR Code to display (to scan by wallets on a different device), a link for wallets on the same device, and the transaction id.

The client can then call the contained URL in the format `/transaction/get/{id}` to get the authentication request. Contained in it is the URL to POST the result to, in format `/transaction/result/{id}`.

All user data (that is all users extracted from authentication responses) can be read by calling `/api/items`, modified by `/api/remove` and read one-by-one at `/api/single/{id}`.



## Configuration

There are several custom configuration properties, all under the key `app`, defined in
`service/src/main/kotlin/at/asit/wallet/relyingparty/AppConfigurationProperties.kt`.

```yaml
app:
  public-context: "http://localhost:8080/"
  verifier-key:
    type: MEMORY
```

Options for the public URL:
- `public-context` is the externally reachable base URL of this service (used in metadata and links sent to wallets).

There are several options to configure the verifier key under `app.verifier-key`:

Key type `memory`:

```yaml
type: MEMORY
```

will create an ephemeral key pair with a self-signed certificate.


Key type `file`:

```yaml
type: FILE
file:
  private-key: file:issuer-key-private.pem
  public-key: file:issuer-key-public.pem
  certificate: file:issuer-cert.pem
```

will load the private key, public key and certificate from `PEM` encoded files.

Key type `keystore`:

```yaml
type: KEYSTORE
keystore:
  path: file:/some/path/keystore.p12
  type: PKCS12
  provider: BC                     # may be null
  password: changeit               # may be null
  alias: key1
  alias-password: changeit         # may be null
```

will load a Java KeyStore object and use key and certificate from there.


## Certificates

The certificate for the key used to sign authorization requests (configured with `app.verifier-key`) needs to have the following extensions to comply with requirements from ISO 18013-5:
- Authority Key Identifier (AKI)
- CRL Distribution Points (CDP): At least one entry with a URI, and an existing, but empty CRL there
- Extended Key Usage (EKU): TLS Web Server Authentication (1.3.6.1.5.5.7.3.1), 1.0.18013.5.1.6 (for mDoc)
- Key Usage (KU): Digital Signature, Key Encipherment
- Subject Alternative Name (SAN):
    - DNS Name: `example.com` (your host name)
    - DNS Name: `x509_san_dns:example.com` (your host name)
- Subject Key Identifier (SKI)

The root certificate (signing the verifier certificate) needs to have the following extensions:
- Authority Key Identifier (AKI)
- Basic Constraints: CA
- Key Usage (KU): Certificate Signing, CRL Signing
- Subject Key Identifier (SKI)

The root certificate may be added to custom builds of the EUDIW Reference Wallet to establish trust in authorization requests.
