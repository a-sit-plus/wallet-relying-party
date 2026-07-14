import SwiftUI
import UIKit

struct ContentView: View {
    @StateObject private var authentication = AuthenticationModel()

    var body: some View {
        VStack(spacing: 16) {
            if authentication.isAuthenticating {
                ProgressView("Authenticating…")
            } else if let result = authentication.result {
                AuthenticationResultView(result: result)
                Button("Login again", action: authentication.login)
                    .buttonStyle(.bordered)
            } else {
                Button("Login", action: authentication.login)
                    .buttonStyle(.borderedProminent)
            }

            if let errorMessage = authentication.errorMessage {
                Text(errorMessage)
                    .foregroundStyle(.red)
                    .multilineTextAlignment(.center)
            }
        }
        .padding()
    }
}

private struct AuthenticationResultView: View {
    let result: AuthenticationResult

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Authenticated")
                    .font(.title.bold())

                if let data = result.portraitData, let image = UIImage(data: data) {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                        .frame(width: 120, height: 120)
                        .clipShape(.circle)
                        .accessibilityLabel("Credential portrait")
                }

                if let error = result.idTokenError ?? result.presentationError {
                    ErrorText(message: error)
                }

                ForEach(Array(result.credentials.enumerated()), id: \.offset) { _, credential in
                    VStack(alignment: .leading, spacing: 12) {
                        Text(credential.credentialType ?? "Credential")
                            .font(.headline)

                        if let error = credential.error {
                            ErrorText(message: error)
                        }

                        ForEach(credential.claims) { claim in
                            VStack(alignment: .leading, spacing: 4) {
                                Text(claim.name)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                Text(claim.value)
                                    .textSelection(.enabled)
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

private struct ErrorText: View {
    let message: String

    var body: some View {
        Text(message)
            .foregroundStyle(.red)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}
