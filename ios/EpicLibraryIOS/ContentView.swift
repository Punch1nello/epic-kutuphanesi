import SafariServices
import SwiftUI
import UIKit
import WebKit

private enum AppLanguage: String, CaseIterable, Identifiable {
    case tr, en, de, es, ru
    var id: String { rawValue }
    var name: String {
        switch self {
        case .tr: return "Türkçe"
        case .en: return "English"
        case .de: return "Deutsch"
        case .es: return "Español"
        case .ru: return "Русский"
        }
    }
    var locale: String {
        switch self {
        case .tr: return "tr-TR"
        case .en: return "en-US"
        case .de: return "de-DE"
        case .es: return "es-ES"
        case .ru: return "ru-RU"
        }
    }
}

private enum IOSStrings {
    static func text(_ key: String, _ language: AppLanguage) -> String {
        let common: [String: [String: String]] = [
            "store": ["tr":"Mağaza", "en":"Store", "de":"Shop", "es":"Tienda", "ru":"Магазин"],
            "library": ["tr":"Kütüphane", "en":"Library", "de":"Bibliothek", "es":"Biblioteca", "ru":"Библиотека"],
            "settings": ["tr":"Ayarlar", "en":"Settings", "de":"Einstellungen", "es":"Ajustes", "ru":"Настройки"],
            "libraryContent": ["tr":"KÜTÜPHANE İÇERİĞİ", "en":"LIBRARY CONTENT", "de":"BIBLIOTHEKSINHALT", "es":"CONTENIDO DE LA BIBLIOTECA", "ru":"СОДЕРЖИМОЕ БИБЛИОТЕКИ"],
            "all": ["tr":"Tümü", "en":"All", "de":"Alle", "es":"Todo", "ru":"Все"],
            "games": ["tr":"Oyunlar", "en":"Games", "de":"Spiele", "es":"Juegos", "ru":"Игры"],
            "tools": ["tr":"Araçlar", "en":"Tools", "de":"Werkzeuge", "es":"Herramientas", "ru":"Инструменты"],
            "sort": ["tr":"Sırala", "en":"Sort", "de":"Sortieren", "es":"Ordenar", "ru":"Сортировка"],
            "search": ["tr":"Kütüphanede ara", "en":"Search library", "de":"Bibliothek durchsuchen", "es":"Buscar en la biblioteca", "ru":"Поиск в библиотеке"],
            "collections": ["tr":"KOLEKSİYONLAR", "en":"COLLECTIONS", "de":"SAMMLUNGEN", "es":"COLECCIONES", "ru":"КОЛЛЕКЦИИ"],
            "empty": ["tr":"Bu filtrelerle oyun bulunamadı.", "en":"No games match these filters.", "de":"Keine Spiele für diese Filter.", "es":"Ningún juego coincide con estos filtros.", "ru":"Нет игр по выбранным фильтрам."],
            "playtimeMissing": ["tr":"Oynanma süresi Epic'ten alınamıyor", "en":"Playtime not provided by Epic", "de":"Spielzeit von Epic nicht verfügbar", "es":"Epic no proporciona el tiempo de juego", "ru":"Epic не передаёт время игры"],
            "playtime": ["tr":"Oynanma süresi: %d dk", "en":"Playtime: %d min", "de":"Spielzeit: %d Min.", "es":"Tiempo de juego: %d min", "ru":"Время игры: %d мин"],
            "sortAZ": ["tr":"A–Z", "en":"A–Z", "de":"A–Z", "es":"A–Z", "ru":"А–Я"],
            "sortZA": ["tr":"Z–A", "en":"Z–A", "de":"Z–A", "es":"Z–A", "ru":"Я–А"],
            "mostPlayed": ["tr":"En çok oynanan", "en":"Most played", "de":"Am häufigsten gespielt", "es":"Más jugados", "ru":"Чаще всего играли"],
            "leastPlayed": ["tr":"En az oynanan", "en":"Least played", "de":"Am seltensten gespielt", "es":"Menos jugados", "ru":"Реже всего играли"],
            "singlePlayer": ["tr":"Tek oyunculu", "en":"Single player", "de":"Einzelspieler", "es":"Un jugador", "ru":"Одиночная игра"],
            "multiplayer": ["tr":"Çok oyunculu", "en":"Multiplayer", "de":"Mehrspieler", "es":"Multijugador", "ru":"Многопользовательская"],
            "loginHelp": ["tr":"Epic şifren uygulamaya girilmez. Girişi Epic'in kendi sayfasında yap; oturum anahtarı iOS Keychain'de saklanır.", "en":"Your Epic password is not entered here. Sign in on Epic's page; the session is stored in iOS Keychain.", "de":"Dein Epic-Passwort wird hier nicht eingegeben. Melde dich bei Epic an; die Sitzung wird im iOS-Schlüsselbund gespeichert.", "es":"No escribas tu contraseña de Epic aquí. Inicia sesión en Epic; la sesión se guarda en el llavero de iOS.", "ru":"Не вводите пароль Epic здесь. Войдите на странице Epic; сеанс сохранится в Связке ключей iOS."],
            "settingsNote": ["tr":"Oturum iOS Keychain'de saklanır. Mağaza WebView çerezleri bu oturumdan ayrıdır.", "en":"The session is stored in iOS Keychain. Store WebView cookies are separate.", "de":"Die Sitzung wird im iOS-Schlüsselbund gespeichert. Shop-Cookies sind getrennt.", "es":"La sesión se guarda en el llavero de iOS. Las cookies de la tienda están separadas.", "ru":"Сеанс хранится в Связке ключей iOS. Файлы cookie магазина отдельны."],
            "login": ["tr":"Epic hesabını bağla", "en":"Connect Epic account", "de":"Epic-Konto verbinden", "es":"Conectar cuenta de Epic", "ru":"Подключить Epic"],
            "sessionRestoreFailed": ["tr":"Kayıtlı Epic oturumu geri yüklenemedi. Bağlantını kontrol edip yeniden dene.", "en":"The saved Epic session could not be restored. Check your connection and try again.", "de":"Die gespeicherte Epic-Sitzung konnte nicht wiederhergestellt werden. Prüfe die Verbindung und versuche es erneut.", "es":"No se pudo restaurar la sesión guardada de Epic. Comprueba la conexión e inténtalo de nuevo.", "ru":"Не удалось восстановить сохранённый сеанс Epic. Проверьте подключение и повторите попытку."],
            "retrySession": ["tr":"Oturumu yeniden dene", "en":"Retry session", "de":"Sitzung erneut versuchen", "es":"Reintentar sesión", "ru":"Повторить вход"],
            "signInAgain": ["tr":"Başka hesapla giriş yap", "en":"Sign in with another account", "de":"Mit einem anderen Konto anmelden", "es":"Iniciar sesión con otra cuenta", "ru":"Войти в другой аккаунт"],
            "openLogin": ["tr":"Epic girişini aç", "en":"Open Epic sign-in", "de":"Epic-Anmeldung öffnen", "es":"Abrir inicio de sesión de Epic", "ru":"Открыть вход Epic"],
            "pasteCode": ["tr":"Epic'ten aldığın kodu yapıştır", "en":"Paste the code from Epic", "de":"Epic-Code einfügen", "es":"Pega el código de Epic", "ru":"Вставьте код Epic"],
            "connect": ["tr":"Hesabı bağla", "en":"Connect account", "de":"Konto verbinden", "es":"Conectar cuenta", "ru":"Подключить аккаунт"],
            "account": ["tr":"Bağlı hesap", "en":"Connected account", "de":"Verbundenes Konto", "es":"Cuenta conectada", "ru":"Подключённый аккаунт"],
            "language": ["tr":"Dil", "en":"Language", "de":"Sprache", "es":"Idioma", "ru":"Язык"],
            "theme": ["tr":"Tema", "en":"Theme", "de":"Design", "es":"Tema", "ru":"Тема"],
            "system": ["tr":"Sistem", "en":"System", "de":"System", "es":"Sistema", "ru":"Системная"],
            "light": ["tr":"Açık", "en":"Light", "de":"Hell", "es":"Claro", "ru":"Светлая"],
            "dark": ["tr":"Koyu", "en":"Dark", "de":"Dunkel", "es":"Oscuro", "ru":"Тёмная"],
            "logout": ["tr":"Çıkış yap", "en":"Sign out", "de":"Abmelden", "es":"Cerrar sesión", "ru":"Выйти"],
            "retry": ["tr":"Yeniden dene", "en":"Retry", "de":"Erneut versuchen", "es":"Reintentar", "ru":"Повторить"],
            "back": ["tr":"Geri", "en":"Back", "de":"Zurück", "es":"Atrás", "ru":"Назад"],
            "home": ["tr":"Ana sayfa", "en":"Home", "de":"Startseite", "es":"Inicio", "ru":"Главная"]
        ]
        return common[key]?[language.rawValue] ?? key
    }
}

private enum LibraryCategory: String, CaseIterable, Identifiable, Equatable {
    case all, games, tools
    var id: String { rawValue }
}

private enum LibrarySort: String, CaseIterable, Identifiable, Equatable {
    case az, za, mostPlayed, leastPlayed, singlePlayer, multiplayer
    var id: String { rawValue }
}

struct ContentView: View {
    @EnvironmentObject private var model: EpicAppModel
    @AppStorage("epic_language") private var languageID = AppLanguage.tr.rawValue
    @AppStorage("epic_theme") private var themeID = "system"
    @State private var selectedTab = 0

    private var language: AppLanguage { AppLanguage(rawValue: languageID) ?? .tr }
    private var preferredScheme: ColorScheme? {
        switch themeID {
        case "light": .light
        case "dark": .dark
        default: nil
        }
    }

    var body: some View {
        Group {
            switch model.phase {
            case .restoring:
                ProgressView(IOSStrings.text("library", language))
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            case .sessionError:
                SessionRecoveryView(language: language) {
                    await model.retrySessionRestore(locale: language.locale)
                } onSignInAgain: {
                    model.signOut()
                }
            case .signedOut:
                LoginView(language: language) { code in
                    await model.signIn(authorizationCode: code, locale: language.locale)
                }
            case .ready:
                TabView(selection: $selectedTab) {
                    StoreScreen(language: language)
                        .tabItem { Label(IOSStrings.text("store", language), systemImage: "storefront") }
                        .tag(0)
                    LibraryScreen(language: language)
                        .tabItem { Label(IOSStrings.text("library", language), systemImage: "books.vertical") }
                        .tag(1)
                    SettingsScreen(language: language, onLanguageChange: { newLanguage in
                        languageID = newLanguage.rawValue
                        Task { await model.refreshLibrary(locale: newLanguage.locale) }
                    })
                    .tabItem { Label(IOSStrings.text("settings", language), systemImage: "gearshape") }
                    .tag(2)
                }
                .tint(Color(red: 0.40, green: 0.31, blue: 0.82))
            }
        }
        .preferredColorScheme(preferredScheme)
        .task { await model.restoreSession(locale: language.locale) }
        .alert("Epic Kütüphanesi", isPresented: Binding(
            get: { model.errorMessage != nil },
            set: { if !$0 { model.errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) { model.errorMessage = nil }
        } message: {
            Text(model.errorMessage ?? "")
        }
    }
}

private struct SessionRecoveryView: View {
    let language: AppLanguage
    let onRetry: () async -> Void
    let onSignInAgain: () -> Void
    @State private var isRetrying = false

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "key.horizontal.fill")
                .font(.system(size: 40))
                .foregroundStyle(.purple)
            Text(IOSStrings.text("sessionRestoreFailed", language))
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
            Button {
                isRetrying = true
                Task {
                    await onRetry()
                    isRetrying = false
                }
            } label: {
                if isRetrying { ProgressView().frame(maxWidth: .infinity) }
                else { Text(IOSStrings.text("retrySession", language)).frame(maxWidth: .infinity) }
            }
            .buttonStyle(.borderedProminent)
            .disabled(isRetrying)
            Button(IOSStrings.text("signInAgain", language), role: .destructive, action: onSignInAgain)
                .disabled(isRetrying)
        }
        .padding(24)
        .frame(maxWidth: 440)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(uiColor: .systemGroupedBackground))
    }
}

private struct LoginView: View {
    let language: AppLanguage
    let onConnect: (String) async -> Void
    @State private var code = ""
    @State private var isConnecting = false

    var body: some View {
        VStack(spacing: 18) {
            Image(systemName: "gamecontroller.fill")
                .font(.system(size: 42, weight: .semibold))
                .foregroundStyle(Color.purple)
            Text(IOSStrings.text("login", language))
                .font(.title2.bold())
            Text(IOSStrings.text("loginHelp", language))
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            Button {
                if let url = EpicService.loginURL() { UIApplication.shared.open(url) }
            } label: {
                Label(IOSStrings.text("openLogin", language), systemImage: "safari")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            TextField(IOSStrings.text("pasteCode", language), text: $code, axis: .vertical)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .textFieldStyle(.roundedBorder)
                .lineLimit(1...3)
            Button {
                isConnecting = true
                Task {
                    await onConnect(code)
                    isConnecting = false
                    code = ""
                }
            } label: {
                if isConnecting { ProgressView().frame(maxWidth: .infinity) }
                else { Text(IOSStrings.text("connect", language)).frame(maxWidth: .infinity) }
            }
            .buttonStyle(.borderedProminent)
            .disabled(code.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isConnecting)
        }
        .padding(24)
        .frame(maxWidth: 440)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(uiColor: .systemGroupedBackground))
    }
}

private struct StoreScreen: View {
    let language: AppLanguage
    @StateObject private var state = StoreWebState()
    @State private var homeRequest = 0

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if state.isLoading { ProgressView().frame(maxWidth: .infinity).padding(.vertical, 4) }
                StoreWebView(state: state, homeRequest: homeRequest)
            }
            .navigationTitle(IOSStrings.text("store", language))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItemGroup(placement: .navigationBarTrailing) {
                    Button { state.webView?.goBack() } label: { Image(systemName: "chevron.left") }
                        .disabled(!state.canGoBack)
                    Button { homeRequest += 1 } label: { Image(systemName: "house") }
                        .accessibilityLabel(IOSStrings.text("home", language))
                }
            }
        }
    }
}

@MainActor
private final class StoreWebState: ObservableObject {
    weak var webView: WKWebView?
    @Published var canGoBack = false
    @Published var isLoading = true
}

private struct StoreWebView: UIViewRepresentable {
    @ObservedObject var state: StoreWebState
    let homeRequest: Int

    func makeCoordinator() -> Coordinator { Coordinator(state: state) }

    func makeUIView(context: Context) -> WKWebView {
        let view = WKWebView(frame: .zero)
        view.allowsBackForwardNavigationGestures = true
        view.navigationDelegate = context.coordinator
        state.webView = view
        view.load(URLRequest(url: URL(string: "https://store.epicgames.com/")!))
        return view
    }

    func updateUIView(_ view: WKWebView, context: Context) {
        if context.coordinator.lastHomeRequest != homeRequest {
            context.coordinator.lastHomeRequest = homeRequest
            view.load(URLRequest(url: URL(string: "https://store.epicgames.com/")!))
        }
    }

    final class Coordinator: NSObject, WKNavigationDelegate {
        let state: StoreWebState
        var lastHomeRequest = 0
        init(state: StoreWebState) { self.state = state }
        func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
            state.isLoading = true
        }
        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            state.isLoading = false
            state.canGoBack = webView.canGoBack
        }
        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            state.isLoading = false
            state.canGoBack = webView.canGoBack
        }
    }
}

private struct LibraryScreen: View {
    @EnvironmentObject private var model: EpicAppModel
    let language: AppLanguage
    @State private var search = ""
    @State private var category: LibraryCategory = .all
    @State private var sort: LibrarySort = .az
    @State private var collectionID: String?

    private var matchingGames: [EpicGame] {
        var result = model.games.filter { game in
            let categoryMatches = category == .all ||
                (category == .games && game.kind == .game) ||
                (category == .tools && game.kind == .tool)
            let collectionMatches = collectionID == nil || model.collections.first(where: { $0.id == collectionID })?.gameIDs.contains(game.id) == true ||
                model.collections.first(where: { $0.id == collectionID })?.gameIDs.contains(game.catalogItemID) == true ||
                model.collections.first(where: { $0.id == collectionID })?.gameIDs.contains(game.appName) == true
            let modeMatches = sort == .singlePlayer ? game.playerModes.contains("single") :
                sort == .multiplayer ? game.playerModes.contains("multi") : true
            return categoryMatches && collectionMatches && modeMatches && game.title.localizedCaseInsensitiveContains(search)
        }
        switch sort {
        case .az, .singlePlayer, .multiplayer:
            result.sort { $0.title.localizedStandardCompare($1.title) == .orderedAscending }
        case .za:
            result.sort { $0.title.localizedStandardCompare($1.title) == .orderedDescending }
        case .mostPlayed:
            result.sort { ($0.playtimeMinutes ?? -1) > ($1.playtimeMinutes ?? -1) }
        case .leastPlayed:
            result.sort { ($0.playtimeMinutes ?? Int.max) < ($1.playtimeMinutes ?? Int.max) }
        }
        return result
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                TextField(IOSStrings.text("search", language), text: $search)
                    .textFieldStyle(.roundedBorder)
                if !model.collections.isEmpty {
                    Text(IOSStrings.text("collections", language))
                        .font(.caption.bold()).foregroundStyle(.secondary)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack {
                            collectionChip(nil, title: IOSStrings.text("all", language))
                            ForEach(model.collections) { collection in collectionChip(collection.id, title: collection.name) }
                        }
                    }
                }
                Text(IOSStrings.text("libraryContent", language))
                    .font(.caption.bold()).tracking(1).foregroundStyle(.secondary)
                HStack(spacing: 8) {
                    Picker(IOSStrings.text("libraryContent", language), selection: $category) {
                        Text(IOSStrings.text("all", language)).tag(LibraryCategory.all)
                        Text(IOSStrings.text("games", language)).tag(LibraryCategory.games)
                        Text(IOSStrings.text("tools", language)).tag(LibraryCategory.tools)
                    }
                    .pickerStyle(.segmented)
                    .onChange(of: category) { newValue in
                        if newValue == .tools && ![LibrarySort.az, .za].contains(sort) { sort = .az }
                    }
                    Menu {
                            Button(IOSStrings.text("sortAZ", language)) { sort = .az }
                            Button(IOSStrings.text("sortZA", language)) { sort = .za }
                        if category != .tools {
                            Divider()
                                Button(IOSStrings.text("mostPlayed", language)) { sort = .mostPlayed }
                                .disabled(!model.games.contains { $0.playtimeMinutes != nil })
                                Button(IOSStrings.text("leastPlayed", language)) { sort = .leastPlayed }
                                .disabled(!model.games.contains { $0.playtimeMinutes != nil })
                                Button(IOSStrings.text("singlePlayer", language)) { sort = .singlePlayer }
                                .disabled(!model.games.contains { !$0.playerModes.isEmpty })
                                Button(IOSStrings.text("multiplayer", language)) { sort = .multiplayer }
                                .disabled(!model.games.contains { !$0.playerModes.isEmpty })
                        }
                    } label: {
                        Label(IOSStrings.text("sort", language), systemImage: "arrow.up.arrow.down")
                            .labelStyle(.titleAndIcon)
                            .font(.subheadline.weight(.semibold))
                            .fixedSize()
                    }
                }
                if model.isRefreshing { ProgressView().frame(maxWidth: .infinity) }
                if matchingGames.isEmpty {
                    VStack(spacing: 10) {
                        Image(systemName: "books.vertical").font(.largeTitle).foregroundStyle(.secondary)
                        Text(IOSStrings.text("empty", language)).foregroundStyle(.secondary).multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity, minHeight: 240)
                } else {
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                        ForEach(matchingGames) { game in GameCard(game: game, language: language) }
                    }
                }
            }
                .padding(16)
            }
            .refreshable { await model.refreshLibrary(locale: language.locale) }
            .navigationTitle(IOSStrings.text("library", language))
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private func collectionChip(_ id: String?, title: String) -> some View {
        Button { collectionID = id } label: {
            Text(title).font(.subheadline).padding(.horizontal, 12).padding(.vertical, 8)
                .background(collectionID == id ? Color.purple.opacity(0.18) : Color(uiColor: .secondarySystemGroupedBackground))
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

private struct GameCard: View {
    let game: EpicGame
    let language: AppLanguage

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            AsyncImage(url: game.imageURL) { image in
                image.resizable().scaledToFill()
            } placeholder: {
                RoundedRectangle(cornerRadius: 14).fill(Color(uiColor: .tertiarySystemFill))
                    .overlay(Image(systemName: game.kind == .tool ? "wrench.and.screwdriver" : "gamecontroller.fill").font(.largeTitle).foregroundStyle(.secondary))
            }
            .frame(height: 175)
            .frame(maxWidth: .infinity)
            .clipped()
            .clipShape(RoundedRectangle(cornerRadius: 14))
            Text(game.title).font(.headline).lineLimit(2).frame(height: 44, alignment: .topLeading)
            Text(IOSStrings.text(game.kind == .tool ? "tools" : "games", language))
                .font(.caption).foregroundStyle(.secondary)
            if game.kind == .game {
                if let minutes = game.playtimeMinutes {
                    Text(String(format: IOSStrings.text("playtime", language), minutes))
                        .font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                } else {
                    Text(IOSStrings.text("playtimeMissing", language))
                        .font(.caption2).foregroundStyle(.secondary).lineLimit(2)
                }
            }
        }
        .padding(10)
        .background(Color(uiColor: .secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 18))
    }
}

private struct SettingsScreen: View {
    @EnvironmentObject private var model: EpicAppModel
    @AppStorage("epic_language") private var languageID = AppLanguage.tr.rawValue
    @AppStorage("epic_theme") private var themeID = "system"
    let language: AppLanguage
    let onLanguageChange: (AppLanguage) -> Void

    var body: some View {
        NavigationStack {
            Form {
                Section(IOSStrings.text("account", language)) {
                    LabeledContent(IOSStrings.text("account", language), value: model.displayName)
                    Button(IOSStrings.text("logout", language), role: .destructive) { model.signOut() }
                }
                Section(IOSStrings.text("settings", language)) {
                    Picker(IOSStrings.text("language", language), selection: Binding(
                        get: { AppLanguage(rawValue: languageID) ?? .tr },
                        set: onLanguageChange
                    )) {
                        ForEach(AppLanguage.allCases) { Text($0.name).tag($0) }
                    }
                    Picker(IOSStrings.text("theme", language), selection: $themeID) {
                        Text(IOSStrings.text("system", language)).tag("system")
                        Text(IOSStrings.text("light", language)).tag("light")
                        Text(IOSStrings.text("dark", language)).tag("dark")
                    }
                }
                Section {
                    Text(IOSStrings.text("settingsNote", language))
                        .font(.footnote).foregroundStyle(.secondary)
                }
            }
            .navigationTitle(IOSStrings.text("settings", language))
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}
