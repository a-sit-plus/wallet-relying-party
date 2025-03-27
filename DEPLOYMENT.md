# Terminal Service Point

This service acts a relying party (or verifier) for wallets presenting verifiable credentials.


This sevrice is deployed at <https://apps.egiz.gv.at/customverifier/>.

1. build
   1. `./gradlew clean bootJar`
1. push jar to server
   1. `scp build/libs/service*.jar asitapps-demo.iaik.tugraz.at:`
1. login to server
   1. `ssh asitapps-demo.iaik.tugraz.at`
1. deploy
```
sudo cp ~/service*.jar /opt/spring/customverifier/customverifier.jar
sudo systemctl restart customverifier
sudo journalctl -u customverifier -f
```




The certificate for the key used to sign authorization requests (stored in `verifier.p12` in `/opt/spring/customverifier`) needs to have the following extensions:
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
