import XCTest
@testable import WalletRelyingParty

final class AuthenticationResultTests: XCTestCase {
    func testDecodesDisplayedCredentialData() throws {
        let data = Data(#"""
        {
          "imageDataBase64": "data:image;base64,SGVsbG8",
          "timestamp": 1234,
          "idToken": {"ignored": true},
          "idTokenError": null,
          "presentationError": null,
          "credentials": [{
            "jwtCredential": null,
            "allFields": {
              "family_name": "Doe",
              "age_over_18": true,
              "portrait": "not displayed"
            },
            "credentialType": "eu.europa.ec.eudi.pid.1",
            "error": null
          }]
        }
        """#.utf8)

        let result = try JSONDecoder().decode(AuthenticationResult.self, from: data)

        XCTAssertEqual(result.portraitData, Data("Hello".utf8))
        XCTAssertEqual(result.credentials[0].claims, [
            .init(name: "age_over_18", value: "true"),
            .init(name: "family_name", value: "Doe"),
        ])
    }
}
