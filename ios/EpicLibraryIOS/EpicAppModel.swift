import Combine
import Foundation

@MainActor
final class EpicAppModel: ObservableObject {
    enum Phase: Equatable { case restoring, sessionError, signedOut, ready }

    @Published private(set) var phase: Phase = .restoring
    @Published private(set) var games: [EpicGame] = []
    @Published private(set) var collections: [EpicCollection] = []
    @Published private(set) var displayName = ""
    @Published private(set) var isRefreshing = false
    @Published var errorMessage: String?

    private let sessionStore = KeychainSessionStore()
    private var session: EpicSession?
    private var accountID = ""

    func restoreSession(locale: String) async {
        guard phase == .restoring || phase == .sessionError else { return }
        guard let saved = sessionStore.load() else {
            phase = .signedOut
            return
        }
        do {
            let usable = try await validSession(from: saved)
            try await loadAccount(using: usable, locale: locale)
        } catch {
            if isUnauthorized(error) {
                clearExpiredSession()
            } else {
                phase = .sessionError
            }
        }
    }

    func retrySessionRestore(locale: String) async {
        phase = .restoring
        await restoreSession(locale: locale)
    }

    func signIn(authorizationCode: String, locale: String) async {
        let code = authorizationCode.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !code.isEmpty else {
            errorMessage = "Enter the Epic authorization code first."
            return
        }
        do {
            let newSession = try await EpicService.exchange(code: code)
            sessionStore.save(newSession)
            try await loadAccount(using: newSession, locale: locale)
        } catch {
            errorMessage = message(for: error)
        }
    }

    func refreshLibrary(locale: String) async {
        guard !isRefreshing, let current = session ?? sessionStore.load() else { return }
        isRefreshing = true
        defer { isRefreshing = false }
        do {
            let (usable, loaded) = try await withAuthorizedSession(from: current) { token in
                try await EpicService.loadLibrary(accessToken: token.accessToken, locale: locale)
            }
            session = usable
            sessionStore.save(usable)
            games = loaded
            collections = await EpicService.loadCollections(accessToken: usable.accessToken, accountID: accountID)
        } catch {
            if isUnauthorized(error) { clearExpiredSession() }
            errorMessage = message(for: error)
        }
    }

    func signOut() {
        sessionStore.delete()
        session = nil
        games = []
        collections = []
        displayName = ""
        accountID = ""
        phase = .signedOut
    }

    private func loadAccount(using candidate: EpicSession, locale: String) async throws {
        sessionStore.save(candidate)
        let (usable, accountAndGames) = try await withAuthorizedSession(from: candidate) { token in
            let account = try await EpicService.verify(token.accessToken)
            let games = try await EpicService.loadLibrary(accessToken: token.accessToken, locale: locale)
            return (account, games)
        }
        let (account, loadedGames) = accountAndGames
        session = usable
        sessionStore.save(usable)
        let loadedCollections = await EpicService.loadCollections(accessToken: usable.accessToken, accountID: account.accountID)
        displayName = account.displayName
        accountID = account.accountID
        games = loadedGames
        collections = loadedCollections
        phase = .ready
        errorMessage = nil
    }

    private func validSession(from candidate: EpicSession) async throws -> EpicSession {
        if candidate.expiresAt > Date().addingTimeInterval(60) { return candidate }
        guard let refresh = candidate.refreshToken, !refresh.isEmpty else { throw EpicLibraryError.unauthorized }
        let newSession = try await EpicService.refresh(refresh)
        session = newSession
        sessionStore.save(newSession)
        return newSession
    }

    private func withAuthorizedSession<Value>(
        from candidate: EpicSession,
        operation: (EpicSession) async throws -> Value
    ) async throws -> (EpicSession, Value) {
        let usable = try await validSession(from: candidate)
        do {
            return (usable, try await operation(usable))
        } catch {
            guard isUnauthorized(error),
                  let refreshToken = usable.refreshToken,
                  !refreshToken.isEmpty else { throw error }
            let refreshed = try await EpicService.refresh(refreshToken)
            session = refreshed
            sessionStore.save(refreshed)
            return (refreshed, try await operation(refreshed))
        }
    }

    private func isUnauthorized(_ error: Error) -> Bool {
        guard let epicError = error as? EpicLibraryError else { return false }
        if case .unauthorized = epicError { return true }
        return false
    }

    private func clearExpiredSession() {
        sessionStore.delete()
        session = nil
        games = []
        collections = []
        displayName = ""
        accountID = ""
        phase = .signedOut
    }

    private func message(for error: Error) -> String {
        (error as? LocalizedError)?.errorDescription ?? "Something went wrong. Please try again."
    }
}
