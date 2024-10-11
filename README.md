# Wallet Service Provider

This service implements the SP for a Wallet system, requesting credentials from Wallets using OpenID 4 Verifiable Presentations.

See <https://github.com/a-sit-plus/compose-wallet-app/> for the Compose Wallet App.

## Transactions

The web page calls `/transaction/create` to create a new transaction, by specifying which credential to request in which representation. The result is the QR Code to display (to scan by wallets on a different device), a link for wallets on the same device, and the transaction id.

The client can then call the contained URL in the format `/transaction/get/{id}` to get the authentication request. Contained in it is the URL to POST the result to, in format `/transaction/result/{id}`.

All user data (that is all users extracted from authentication responses) can be read by calling `/api/items`, modified by `/api/remove` and read one-by-one at `/api/single/{id}`.
