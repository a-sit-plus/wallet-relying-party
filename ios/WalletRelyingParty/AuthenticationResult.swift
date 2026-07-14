import Foundation

struct AuthenticationResult: Decodable, Equatable {
    let imageDataBase64: String?
    let timestamp: Int64
    let idTokenError: String?
    let presentationError: String?
    let credentials: [Credential]

    var portraitData: Data? {
        guard let encoded = imageDataBase64?.split(separator: ",", maxSplits: 1).last else {
            return nil
        }
        let normalized = String(encoded).replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        let padded = normalized.padding(toLength: ((normalized.count + 3) / 4) * 4, withPad: "=", startingAt: 0)
        return Data(base64Encoded: padded)
    }

    struct Credential: Decodable, Equatable {
        let jwtCredential: JSONValue?
        let allFields: [String: JSONValue]?
        let credentialType: String?
        let error: String?

        var claims: [Claim] {
            let fields = allFields ?? jwtCredential?.objectValue ?? [:]
            return fields
                .filter { $0.key.caseInsensitiveCompare("portrait") != .orderedSame }
                .map { Claim(name: $0.key, value: $0.value.displayValue) }
                .sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
        }
    }

    struct Claim: Identifiable, Equatable {
        var id: String { name }
        let name: String
        let value: String
    }
}

enum JSONValue: Codable, Equatable {
    case array([JSONValue])
    case bool(Bool)
    case null
    case number(Double)
    case object([String: JSONValue])
    case string(String)

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if container.decodeNil() {
            self = .null
        } else if let value = try? container.decode(Bool.self) {
            self = .bool(value)
        } else if let value = try? container.decode(Double.self) {
            self = .number(value)
        } else if let value = try? container.decode(String.self) {
            self = .string(value)
        } else if let value = try? container.decode([JSONValue].self) {
            self = .array(value)
        } else {
            self = .object(try container.decode([String: JSONValue].self))
        }
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case .array(let value): try container.encode(value)
        case .bool(let value): try container.encode(value)
        case .null: try container.encodeNil()
        case .number(let value): try container.encode(value)
        case .object(let value): try container.encode(value)
        case .string(let value): try container.encode(value)
        }
    }

    var objectValue: [String: JSONValue]? {
        guard case .object(let value) = self else { return nil }
        return value
    }

    var displayValue: String {
        switch self {
        case .bool(let value): value ? "true" : "false"
        case .null: "null"
        case .number(let value): value.formatted(.number.grouping(.never))
        case .string(let value): value
        case .array, .object:
            (try? String(data: JSONEncoder.pretty.encode(self), encoding: .utf8)) ?? ""
        }
    }
}

private extension JSONEncoder {
    static let pretty: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
        return encoder
    }()
}
