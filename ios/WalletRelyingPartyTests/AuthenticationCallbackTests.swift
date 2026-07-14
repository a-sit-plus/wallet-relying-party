import XCTest
@testable import WalletRelyingParty

final class AuthenticationCallbackTests: XCTestCase {
    private let state = UUID(uuidString: "123e4567-e89b-12d3-a456-426614174000")!

    func testAcceptsExpectedCallback() throws {
        let url = URL(string: "wallet-rp://auth/callback?transaction_id=transaction-id&state=\(state.uuidString)")!

        XCTAssertEqual(
            try AuthenticationCallback(url: url, expectedState: state).transactionID,
            "transaction-id"
        )
    }

    func testRejectsWrongState() {
        let url = URL(string: "wallet-rp://auth/callback?transaction_id=transaction-id&state=00000000-0000-4000-8000-000000000000")!

        XCTAssertThrowsError(try AuthenticationCallback(url: url, expectedState: state))
    }

    func testRejectsUnexpectedCallbackLocation() {
        let url = URL(string: "wallet-rp://other/callback?transaction_id=transaction-id&state=\(state.uuidString)")!

        XCTAssertThrowsError(try AuthenticationCallback(url: url, expectedState: state))
    }
}
