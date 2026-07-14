import AuthenticationServices
import SwiftUI

@MainActor
final class AuthenticationModel: NSObject, ObservableObject, ASWebAuthenticationPresentationContextProviding {
    @Published private(set) var isAuthenticating = false
    @Published private(set) var result: AuthenticationResult?
    @Published private(set) var errorMessage: String?

    private var session: ASWebAuthenticationSession?

    func login() {
        let state = UUID()
        var components = URLComponents(string: "https://wallet-rp.a-sit.plus/pidmdoc.html")!
        components.queryItems = [
            URLQueryItem(name: "client", value: "ios"),
            URLQueryItem(name: "state", value: state.uuidString),
        ]

        guard let loginURL = components.url else {
            errorMessage = AuthenticationError.invalidLoginURL.localizedDescription
            return
        }

        errorMessage = nil
        result = nil
        isAuthenticating = true

        let session = ASWebAuthenticationSession(url: loginURL, callback: .customScheme("wallet-rp")) {
            [weak self] callbackURL, error in
            Task { @MainActor in
                self?.complete(callbackURL: callbackURL, error: error, expectedState: state)
            }
        }
        session.presentationContextProvider = self
        self.session = session

        if !session.start() {
            finish(with: AuthenticationError.couldNotStart)
        }
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        guard let windowScene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .first else {
            preconditionFailure("Authentication requires an active window scene.")
        }
        return windowScene.windows.first(where: \.isKeyWindow) ?? ASPresentationAnchor(windowScene: windowScene)
    }

    private func complete(callbackURL: URL?, error: Error?, expectedState: UUID) {
        if let sessionError = error as? ASWebAuthenticationSessionError,
           sessionError.code == .canceledLogin {
            finish()
            return
        }

        guard error == nil, let callbackURL else {
            finish(with: error ?? AuthenticationError.missingCallback)
            return
        }

        do {
            let transactionID = try AuthenticationCallback(url: callbackURL, expectedState: expectedState).transactionID
            session = nil
            Task { await loadResult(transactionID: transactionID) }
        } catch {
            finish(with: error)
        }
    }

    private func loadResult(transactionID: String) async {
        do {
            let url = URL(string: "https://wallet-rp.a-sit.plus/api/single/")!.appending(path: transactionID)
            let (data, response) = try await URLSession.shared.data(from: url)
            guard (response as? HTTPURLResponse)?.statusCode == 200 else {
                throw AuthenticationError.resultUnavailable
            }
            result = try JSONDecoder().decode(AuthenticationResult.self, from: data)
            finish()
        } catch {
            finish(with: error)
        }
    }

    private func finish(with error: Error? = nil) {
        errorMessage = error?.localizedDescription
        isAuthenticating = false
        session = nil
    }
}

struct AuthenticationCallback: Equatable {
    let transactionID: String

    init(url: URL, expectedState: UUID) throws {
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              components.scheme?.lowercased() == "wallet-rp",
              components.host?.lowercased() == "auth",
              components.path == "/callback" else {
            throw AuthenticationError.invalidCallback
        }

        let queryItems = components.queryItems ?? []
        let transactionIDs = queryItems.filter { $0.name == "transaction_id" }.compactMap(\.value)
        let states = queryItems.filter { $0.name == "state" }.compactMap(\.value)

        guard transactionIDs.count == 1,
              !transactionIDs[0].isEmpty,
              states.count == 1,
              UUID(uuidString: states[0]) == expectedState else {
            throw AuthenticationError.invalidCallback
        }

        transactionID = transactionIDs[0]
    }
}

enum AuthenticationError: LocalizedError {
    case couldNotStart
    case invalidCallback
    case invalidLoginURL
    case missingCallback
    case resultUnavailable

    var errorDescription: String? {
        switch self {
        case .couldNotStart: "Could not start authentication."
        case .invalidCallback: "The authentication response was invalid."
        case .invalidLoginURL: "The authentication URL was invalid."
        case .missingCallback: "Authentication did not return a response."
        case .resultUnavailable: "The authentication result is unavailable."
        }
    }
}
