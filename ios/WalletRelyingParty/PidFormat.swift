import Foundation

/// The format the EU PID is requested in over ISO/IEC 18013-7 Annex C: as ISO mdoc, or as SD-JWT VC in the ISO device
/// request and response as proposed by WG10 N2933, which the wallet has to support.
enum PidFormat: String, CaseIterable, Identifiable {
    case mdoc
    case sdJwt

    var id: Self { self }

    var label: String {
        switch self {
        case .mdoc: "mdoc"
        case .sdJwt: "SD-JWT"
        }
    }

    /// The remote presentation case of the relying party requesting the PID in this format over Annex C.
    var remoteCaseID: String {
        switch self {
        case .mdoc: "6"
        case .sdJwt: "5"
        }
    }

    /// The page of the relying party presenting the PID in this format, which calls back with `state`.
    func loginURL(state: UUID) -> URL? {
        var components = URLComponents(string: "https://wallet-rp.a-sit.plus/remote-case.html")
        components?.queryItems = [
            URLQueryItem(name: "id", value: remoteCaseID),
            URLQueryItem(name: "client", value: "ios"),
            URLQueryItem(name: "state", value: state.uuidString),
        ]
        return components?.url
    }
}
