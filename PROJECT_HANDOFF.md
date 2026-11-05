# Epic Kütüphanesi — Devir Notu

## Amaç

Epic Games hesabındaki oyun kütüphanesini, mağazayı ve ayarları Android'de sunmak; iOS'ta da native uygulama olarak çalıştırabilmek.

## Güncel kaynak durumu

- Android: Kotlin + Jetpack Compose, temiz sürüm adı v1.0.0 (`versionCode 16`; uygulama içi yükseltmeleri bozmayacak şekilde artan kod). Kütüphane sayaçları seçili Tümü/Oyunlar/Araçlar kategorisine göre güncelleniyor. Oynanma süresi yoksa karttan gizleniyor.
- iOS: SwiftUI, marketing version 1.0.0 / build 15. Mağaza `WKWebView` yeni pencere isteyen ürün bağlantılarını mevcut sayfada açıyor; kütüphane başlığında kategori toplamı var. iOS kaynak arşivi Release'e konabilir, imzalı IPA Windows'ta üretilemez.
- Oyun/araç ayrımı genel item/category metadata'sına göre yapılmıyor. `ue` namespace ve bilinen Epic araç adları araç; kalan kayıtlar oyun. Gerçek hesap yanıtıyla doğrulanmalı.
- Mağaza WebView'larında iç gezinme, popup ve kontrol düğmeleri iyileştirildi. Checkout Epic hesabına/ödeme sağlayıcısına bağlı; gerçek cihazda satın alma testi henüz yapılmadı.
- Oturum Android Keystore'da, iOS'ta Keychain'de tutuluyor; refresh token gerektiğinde deneniyor. İlk Epic bağlantısı authorization code ister. Kullanıcı kodları ve token'lar loglara veya repoya girmemeli.
- iOS kaynakları `ios/EpicLibraryIOS.xcodeproj` altındadır. Xcode/Mac gerektirir ve bu Windows ortamında derlenmemiştir. OAuth istemci anahtarı yerel, Git tarafından yok sayılan `ios/EpicLibraryIOS/Secrets.xcconfig` dosyasında sağlanır. iOS dağıtımı Xcode, TestFlight veya App Store ile yapılır.
- Koleksiyonlar belgelenmemiş Epic GraphQL uçlarına bağlıdır; Epic API'si değişirse koleksiyonlar görünmeyebilir. Oynanma süresi/oyuncu modu Epic'in ilgili veriyi döndürmesine bağlıdır.

## Yayın adımları

Yeni temiz başlangıç Release'i `v1.0.0` tag'iyle yayımlanır. Release'e tam bir Android APK ve iOS Xcode proje ZIP'i eklenir; `.github/workflows/update-manifest.yml` APK'dan Android manifestini otomatik üretir. iOS için signed IPA bu Windows ortamında üretilemez; source ZIP'i Xcode/Apple imzası gerektirir.

Android kütüphanesinden atılma gerçek cihazda devam ederse crash log ile incele. Bu kaynakta duplicate liste anahtarları ve geri gezinmesi ele alındı; gerçek Epic hesabıyla uçtan uca doğrulama henüz yapılmadı.
