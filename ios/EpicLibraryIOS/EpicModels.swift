import Foundation

enum LibraryItemKind: String, Codable, Sendable, Equatable {
    case game
    case tool
}

struct EpicGame: Identifiable, Hashable, Sendable {
    let id: String
    let title: String
    let imageURL: URL?
    let kind: LibraryItemKind
    let playtimeMinutes: Int?
    let playerModes: Set<String>
    let genre: String
    let namespace: String
    let catalogItemID: String
    let appName: String
}

struct EpicCollection: Identifiable, Hashable, Sendable {
    let id: String
    let name: String
    let gameIDs: Set<String>
}

struct EpicAccount: Sendable {
    let accountID: String
    let displayName: String
}

struct EpicSession: Codable, Sendable {
    let accessToken: String
    let refreshToken: String?
    let expiresAt: Date
}

struct LibraryRecord: Sendable {
    let namespace: String
    let catalogItemID: String
    let appName: String
    let playtimeMinutes: Int?
    let kind: LibraryItemKind
    let categoryPaths: [String]
}

enum EpicLibraryError: LocalizedError {
    case missingCredentials
    case invalidCode
    case unauthorized
    case requestFailed
    case invalidResponse

    var errorDescription: String? {
        switch self {
        case .missingCredentials: return "Epic login is not configured in this iOS build."
        case .invalidCode: return "The Epic authorization code could not be used. Please sign in again."
        case .unauthorized: return "The Epic session expired. Please sign in again."
        case .requestFailed: return "Epic could not load the library. Check the connection and try again."
        case .invalidResponse: return "Epic returned an unexpected response. Please try again later."
        }
    }
}
