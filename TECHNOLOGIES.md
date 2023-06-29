# Terminal Service Point

How to run:
1. execute `ServerApplication.main`
2. run app
3. setup app-localhost-connection: adb reverse tcp:8080 tcp:8080
4. create jwt with the following content:
   - payload:

[//]: # (     - siopv2: localhost:8080/HYUhYQpV5LsKxe7fQ5l84oOVyTDyHqMZy3PJGuVC9hc/terminal/test/login)
     - oidc: http://localhost:8080/HYUhYQpV5LsKxe7fQ5l84oOVyTDyHqMZy3PJGuVC9hc/terminal/test/login
     - friendlyName: Test name
   - header:
     - alg: ES256
     - typ: JWT
     - kid: use key id supported by app 
   - signature:
     - use private key related to key id
Example: eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6InZlcmlmaWNhdGlvbkNlcnRpZmljYXRlX29wZW5Tc2wifQ.eyJvaWRjIjoiaHR0cDovL2xvY2FsaG9zdDo4MDgwL0hZVWhZUXBWNUxzS3hlN2ZRNWw4NG9PVnlURHlIcU1aeTNQSkd1VkM5aGMvdGVybWluYWwvdGVzdC9sb2dpbiIsImZyaWVuZGx5TmFtZSI6IlRlc3QgbmFtZSJ9.eoz2CNBsVSPSIcR-bcf4T7QnalToSgserFgBnb8xYJwqbA7X-j4SfALkjqlRF8ixZInmjX_M6GnjXHAUKOrkCg
5. create qr code from jwt and scan it with app
6. Examine whether the data that is shown sounds reasonable 
7. Click on the "Open"-Button below the Online authentication flow url
8. Login with predefined test identities: https://eid.egiz.gv.at/anbindung/testidentitaeten/vordefinierte-testidentitaeten/
    - If connected using E-ID, then the test identity on the device is automatically used and only a fingerprint is necessary

Setup E-ID:
Private IDA credentials verwenden um eine Testidentität für das Testsystem zu erstellen: https://www.a-trust.at/testidentitaetenmanagement/Login/Default.aspx
EINSTELLUNGEN > URLs setzen: A-SIT DEV
EINSTELLUNGEN > VDA Component: A-Trust Abnahme
VDA > AKTIVIEREN (und anmelden über private **Test**-Credentials aus Test-identitätenmanager)
BINDUNG > ERZEUGEN 
https://www.a-trust.at/testidentitaetenmanagement/Login/Default.aspx  --> hier mit private IDA credentials anmelden, Test-identität erzeugen, App bindingen via VDA > AKTIVIEREN



Aktiv angemeldete/wartende user können dann über folgenden Link angesehen werden:
http://localhost:8080/HYUhYQpV5LsKxe7fQ5l84oOVyTDyHqMZy3PJGuVC9hc/terminal/test/customers



Deployment: ssh stefan.kreiner@asitapps-demo.iaik.tugraz.at
Build executable *.jar
copy to root@asitapps-demo:/opt/spring/terminal_sp/config

Start server
systemctl start terminal_sp
systemctl stop terminal_sp
systemctl status terminal_sp

debugging for non-application issues:
journalctl -u(f) terminal_sp.service


Puppet managed:
cat /etc/systemd/system/terminal_sp.service



Test:
asitapps-demo.iaik.tugraz.at:8093
https://apps.egiz.gv.at/terminal_sp

Home: SFTP: asitapps-demo.iaik.tugraz.at
/home/stefan.kreiner



Wallet app: eid.a-sit.at/wallet
wallet app implicit intent url: https://wallet.a-sit.at/mobile

implementation group: 'at.asitplus.wallet', name: 'vclib', version: '1.7.2'





MDOC -> Android library für 18013-5

MDOC-Credentials erstellen und validieren 

Android Referenz: Client: 
- Annehmen & in google wallet ablegen
- Presentation von document 

Android library für:
 - MDOC- Struktur (ISO/IEC 18013-5) verarbeiten/erstellen/validieren
 - 
