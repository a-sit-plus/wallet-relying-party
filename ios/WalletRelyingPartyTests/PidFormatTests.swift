import XCTest
@testable import WalletRelyingParty

final class PidFormatTests: XCTestCase {
    private let state = UUID(uuidString: "123e4567-e89b-12d3-a456-426614174000")!

    func testRequestsThePidAsMdocWithItsRemoteCase() throws {
        let url = try XCTUnwrap(PidFormat.mdoc.loginURL(state: state))

        XCTAssertEqual(
            url.absoluteString,
            "https://wallet-rp.a-sit.plus/remote-case.html?id=6&client=ios&state=\(state.uuidString)"
        )
    }

    func testRequestsThePidAsSdJwtWithItsRemoteCase() throws {
        let url = try XCTUnwrap(PidFormat.sdJwt.loginURL(state: state))

        XCTAssertEqual(
            url.absoluteString,
            "https://wallet-rp.a-sit.plus/remote-case.html?id=5&client=ios&state=\(state.uuidString)"
        )
    }
}
