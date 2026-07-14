import SwiftUI

struct ContentView: View {
    @StateObject private var authentication = AuthenticationModel()

    var body: some View {
        VStack(spacing: 16) {
            if authentication.isAuthenticating {
                ProgressView("Authenticating…")
            } else if let transactionID = authentication.transactionID {
                Text("Authenticated")
                    .font(.headline)
                Text(transactionID)
                    .font(.caption)
                    .textSelection(.enabled)
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
