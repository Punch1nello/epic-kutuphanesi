import Foundation

enum EpicService {
    private static let clientID = "34a02cf8f4414e29b15921876da36f9a"
    // Supply the OAuth client secret through the ignored local Secrets.xcconfig.
    // User session tokens are never stored here; they live in the iOS Keychain.
    private static var clientSecret: String {
        Bundle.main.object(forInfoDictionaryKey: "EPIC_CLIENT_SECRET") as? String ?? ""
    }
    private static let oauthHost = "https://account-public-service-prod03.ol.epicgames.com"
    private static let libraryURL = "https://library-service.live.use1a.on.epicgames.com/library/api/public/items"
    private static let catalogHost = "https://catalog-public-service-prod06.ol.epicgames.com"
    private static let graphQLURLs = [
        "https://launcher.store.epicgames.com/graphql",
        "https://store.epicgames.com/graphql",
        "https://graphql.epicgames.com/graphql"
    ]

    static func loginURL() -> URL? {
        guard var redirect = URLComponents(string: "https://www.epicgames.com/id/api/redirect") else { return nil }
        redirect.queryItems = [
            URLQueryItem(name: "clientId", value: clientID),
            URLQueryItem(name: "responseType", value: "code")
        ]
        guard var login = URLComponents(string: "https://www.epicgames.com/id/login") else { return nil }
        login.queryItems = [URLQueryItem(name: "redirectUrl", value: redirect.url?.absoluteString)]
        return login.url
    }

    static func exchange(code: String) async throws -> EpicSession {
        try await requestToken(fields: [
            "grant_type": "authorization_code",
            "code": code,
            "token_type": "eg1"
        ])
    }

    static func refresh(_ refreshToken: String) async throws -> EpicSession {
        let refreshed = try await requestToken(fields: [
            "grant_type": "refresh_token",
            "refresh_token": refreshToken,
            "token_type": "eg1"
        ])
        // Preserve the existing refresh token if Epic omits a rotated one.
        guard refreshed.refreshToken == nil else { return refreshed }
        return EpicSession(
            accessToken: refreshed.accessToken,
            refreshToken: refreshToken,
            expiresAt: refreshed.expiresAt
        )
    }

    static func verify(_ accessToken: String) async throws -> EpicAccount {
        guard let url = URL(string: "\(oauthHost)/account/api/oauth/verify") else { throw EpicLibraryError.invalidResponse }
        var request = URLRequest(url: url)
        request.setValue("bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue("EpicLibraryIOS/1.0", forHTTPHeaderField: "User-Agent")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw EpicLibraryError.invalidResponse }
        guard http.statusCode == 200 else {
            throw (http.statusCode == 401 || http.statusCode == 403) ? EpicLibraryError.unauthorized : EpicLibraryError.requestFailed
        }
        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        guard let json else { throw EpicLibraryError.invalidResponse }
        return EpicAccount(
            accountID: json["account_id"] as? String ?? "",
            displayName: json["displayName"] as? String ?? "Epic account"
        )
    }

    static func loadLibrary(accessToken: String, locale: String) async throws -> [EpicGame] {
        var records: [LibraryRecord] = []
        var cursor: String?
        repeat {
            var components = URLComponents(string: libraryURL)!
            var query = [URLQueryItem(name: "includeMetadata", value: "true")]
            if let cursor { query.append(URLQueryItem(name: "cursor", value: cursor)) }
            components.queryItems = query
            let json = try await getJSON(url: components.url!, token: accessToken)
            let page = json["records"] as? [[String: Any]] ?? []
            records.append(contentsOf: page.compactMap(makeRecord))
            let next = (json["responseMetadata"] as? [String: Any])?["nextCursor"] as? String
            let normalizedNext = next.flatMap { $0.isEmpty || $0 == "null" ? nil : $0 }
            if normalizedNext == cursor { break }
            cursor = normalizedNext
        } while cursor != nil

        var iterator = Array(records.uniqued { "\($0.namespace):\($0.catalogItemID)" }).makeIterator()
        return try await withThrowingTaskGroup(of: EpicGame.self) { group in
            for _ in 0..<min(6, records.count) {
                if let record = iterator.next() {
                    group.addTask { await catalogGameOrFallback(record, token: accessToken, locale: locale) }
                }
            }
            var games: [EpicGame] = []
            while let game = try await group.next() {
                games.append(game)
                if let record = iterator.next() {
                    group.addTask { await catalogGameOrFallback(record, token: accessToken, locale: locale) }
                }
            }
            return games.uniqued { $0.id }
        }
    }

    static func loadCollections(accessToken: String, accountID: String) async -> [EpicCollection] {
        let query = """
        query getCollections($accountId: String!) {
          Library { collection { collections(accountId: $accountId) { name collectionId } } }
        }
        """
        guard let json = try? await graphQL(query, variables: ["accountId": accountID], token: accessToken),
              let data = json["data"] as? [String: Any],
              let library = data["Library"] as? [String: Any],
              let collection = library["collection"] as? [String: Any],
              let values = collection["collections"] as? [[String: Any]] else { return [] }

        var result: [EpicCollection] = []
        for value in values {
            guard let id = value["collectionId"] as? String,
                  let name = value["name"] as? String, !id.isEmpty, !name.isEmpty else { continue }
            let itemQuery = """
            query getCollectionItems($accountId: String!, $collectionId: String!, $cursor: String) {
              Library { collection { items(accountId: $accountId, collectionId: $collectionId, cursor: $cursor) {
                records { relationshipId artifactId catalogId sandbox }
                responseMetadata { nextCursor }
              } } }
            }
            """
            var ids = Set<String>()
            var cursor: String?
            repeat {
                let response = try? await graphQL(itemQuery, variables: [
                    "accountId": accountID, "collectionId": id, "cursor": cursor
                ], token: accessToken)
                let items = (((response?["data"] as? [String: Any])?["Library"] as? [String: Any])?["collection"] as? [String: Any])?["items"] as? [String: Any]
                for record in items?["records"] as? [[String: Any]] ?? [] {
                    for key in ["relationshipId", "artifactId", "catalogId"] {
                        if let value = record[key] as? String, !value.isEmpty { ids.insert(value) }
                    }
                    if let sandbox = record["sandbox"] as? String,
                       let catalogID = record["catalogId"] as? String { ids.insert("\(sandbox):\(catalogID)") }
                }
                let next = (items?["responseMetadata"] as? [String: Any])?["nextCursor"] as? String
                if next == cursor { break }
                cursor = next.flatMap { $0.isEmpty || $0 == "null" ? nil : $0 }
            } while cursor != nil
            result.append(EpicCollection(id: id, name: name, gameIDs: ids))
        }
        return result.uniqued { $0.id }
    }

    private static func requestToken(fields: [String: String]) async throws -> EpicSession {
        guard !clientID.isEmpty, !clientSecret.isEmpty else { throw EpicLibraryError.missingCredentials }
        let url = URL(string: "\(oauthHost)/account/api/oauth/token")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        let credentials = Data("\(clientID):\(clientSecret)".utf8).base64EncodedString()
        request.setValue("Basic \(credentials)", forHTTPHeaderField: "Authorization")
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.httpBody = formBody(fields)
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw EpicLibraryError.invalidResponse }
        guard http.statusCode == 200,
              let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let access = json["access_token"] as? String else {
            if fields["grant_type"] == "refresh_token" && (http.statusCode == 400 || http.statusCode == 401 || http.statusCode == 403) {
                throw EpicLibraryError.unauthorized
            }
            throw EpicLibraryError.invalidCode
        }
        return EpicSession(
            accessToken: access,
            refreshToken: json["refresh_token"] as? String,
            expiresAt: Date().addingTimeInterval((json["expires_in"] as? Double ?? 7200) - 60)
        )
    }

    private static func makeRecord(_ value: [String: Any]) -> LibraryRecord? {
        let namespace = value["namespace"] as? String ?? ""
        let catalogID = value["catalogItemId"] as? String ?? ""
        let appName = value["appName"] as? String ?? ""
        guard !namespace.isEmpty, !appName.isEmpty, appName != "1" else { return nil }
        if namespace == "89efe5924d3d467c839449ab6ab52e7f" { return nil }
        let platforms = value["platform"] as? [String] ?? []
        if !platforms.isEmpty && !platforms.contains(where: { ["windows", "win32"].contains($0.lowercased()) }) { return nil }
        let categories = value["categories"] as? [[String: Any]] ?? []
        let paths = categories.compactMap { $0["path"] as? String }
        let metadata = value["metadata"] as? [String: Any] ?? [:]
        let typeKeys = ["itemType", "productType", "applicationType", "artifactType", "type", "kind"]
        let types = typeKeys.compactMap { value[$0] as? String } + typeKeys.compactMap { metadata[$0] as? String }
        let toolTypes: Set<String> = ["application", "applications", "editor", "tool", "tools", "utility", "utilities", "launcher", "software"]
        let toolCategories: Set<String> = ["applications", "developer tools", "development tools", "utilities", "editor", "tools"]
        let isTool = namespace.lowercased() == "ue" || types.contains { toolTypes.contains($0.lowercased()) } || paths.contains { path in
            path.lowercased().split(separator: "/").contains { toolCategories.contains(String($0).replacingOccurrences(of: "_", with: " ")) }
        }
        let playtime = value["playtimeMinutes"] as? Int ?? metadata["playtimeMinutes"] as? Int
        return LibraryRecord(namespace: namespace, catalogItemID: catalogID, appName: appName,
                             playtimeMinutes: playtime, kind: isTool ? .tool : .game, categoryPaths: paths)
    }

    private static func catalogGame(_ record: LibraryRecord, token: String, locale: String) async throws -> EpicGame {
        var components = URLComponents(string: "\(catalogHost)/catalog/api/shared/namespace/\(record.namespace)/bulk/items")!
        components.queryItems = [
            URLQueryItem(name: "id", value: record.catalogItemID),
            URLQueryItem(name: "includeDLCDetails", value: "true"),
            URLQueryItem(name: "includeMainGameDetails", value: "true"),
            URLQueryItem(name: "country", value: "TR"),
            URLQueryItem(name: "locale", value: locale)
        ]
        let json = try await getJSON(url: components.url!, token: token)
        let item = json[record.catalogItemID] as? [String: Any] ?? [:]
        let title = item["title"] as? String ?? record.appName
        let images = item["keyImages"] as? [[String: Any]] ?? []
        let priorities = ["DieselGameBoxTall", "DieselGameBox", "DieselStoreFrontWide", "Thumbnail"]
        let imageURLString = priorities.lazy.compactMap { type in
            images.first(where: { $0["type"] as? String == type })?["url"] as? String
        }.first
        let imageURL = imageURLString.flatMap { URL(string: $0) }
        let categories = item["categories"] as? [[String: Any]] ?? []
        let paths = categories.compactMap { $0["path"] as? String }
        let modes = Set(paths.compactMap { path -> String? in
            let normalized = path.lowercased().replacingOccurrences(of: "_", with: " ").replacingOccurrences(of: "-", with: " ")
            if normalized.contains("single player") || normalized.contains("singleplayer") { return "single" }
            if normalized.contains("multiplayer") || normalized.contains("co op") || normalized.contains("online pvp") { return "multi" }
            return nil
        })
        return EpicGame(id: "\(record.namespace):\(record.catalogItemID)", title: title, imageURL: imageURL,
                        kind: record.kind, playtimeMinutes: record.playtimeMinutes, playerModes: modes,
                        genre: paths.first ?? "", namespace: record.namespace,
                        catalogItemID: record.catalogItemID, appName: record.appName)
    }

    private static func catalogGameOrFallback(_ record: LibraryRecord, token: String, locale: String) async -> EpicGame {
        do { return try await catalogGame(record, token: token, locale: locale) }
        catch {
            return EpicGame(id: "\(record.namespace):\(record.catalogItemID)", title: record.appName,
                            imageURL: nil, kind: record.kind, playtimeMinutes: record.playtimeMinutes,
                            playerModes: [], genre: "", namespace: record.namespace,
                            catalogItemID: record.catalogItemID, appName: record.appName)
        }
    }

    private static func getJSON(url: URL, token: String) async throws -> [String: Any] {
        var request = URLRequest(url: url)
        request.setValue("bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("EpicLibraryIOS/1.0", forHTTPHeaderField: "User-Agent")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw EpicLibraryError.invalidResponse }
        guard http.statusCode == 200 else {
            throw (http.statusCode == 401 || http.statusCode == 403) ? EpicLibraryError.unauthorized : EpicLibraryError.requestFailed
        }
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw EpicLibraryError.invalidResponse }
        return json
    }

    private static func graphQL(_ query: String, variables: [String: String?], token: String) async throws -> [String: Any] {
        var bodyVariables: [String: Any] = [:]
        for (key, value) in variables {
            if let value { bodyVariables[key] = value }
            else { bodyVariables[key] = NSNull() }
        }
        let body = try JSONSerialization.data(withJSONObject: ["query": query, "variables": bodyVariables])
        var lastError: Error = EpicLibraryError.requestFailed
        for address in graphQLURLs {
            guard let url = URL(string: address) else { continue }
            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.httpBody = body
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.setValue("https://store.epicgames.com", forHTTPHeaderField: "Origin")
            request.setValue("https://store.epicgames.com/", forHTTPHeaderField: "Referer")
            do {
                let (data, response) = try await URLSession.shared.data(for: request)
                guard let http = response as? HTTPURLResponse else { throw EpicLibraryError.invalidResponse }
                guard http.statusCode == 200 else { throw EpicLibraryError.requestFailed }
                guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any], json["errors"] == nil else {
                    throw EpicLibraryError.requestFailed
                }
                return json
            } catch { lastError = error }
        }
        throw lastError
    }

    private static func formBody(_ fields: [String: String]) -> Data? {
        var components = URLComponents()
        components.queryItems = fields.map { URLQueryItem(name: $0.key, value: $0.value) }
        return components.percentEncodedQuery?.data(using: .utf8)
    }
}

private extension Array {
    func uniqued<Key: Hashable>(by key: (Element) -> Key) -> [Element] {
        var seen = Set<Key>()
        return filter { seen.insert(key($0)).inserted }
    }
}
