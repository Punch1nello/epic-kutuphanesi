# Epic Kütüphanesi — Devir Notu

## Amaç

Epic Games hesabındaki oyun kütüphanesini, mağazayı ve ayarları Android'de sunmak; iOS'ta da native uygulama olarak çalıştırabilmek.

## Güncel kaynak durumu

- Android: Kotlin + Jetpack Compose, v1.0.5 (`versionCode 15`). Kütüphane Tümü/Oyunlar/Araçlar bölümlerinde ve sıralamada seçim sonrası listeyi başa alıyor. Oynanma süresi yoksa kartta gizleniyor. Bildirim ayarı, zamanlanmış servis ve izinler kaldırıldı.
- Oyun/araç ayrımı genel item/category metadata'sına göre yapılmıyor. `ue` namespace ve bilinen Epic araç ürün adları araç; kalan kitaplık öğeleri oyun sayılıyor. Kullanıcı hesabındaki gerçek API yanıtlarıyla doğrulanmalı.
- Mağaza WebView'ı kullanıcı tıklamasıyla açılan popup/ödeme sayfalarını uygulama içi pencerede gösteriyor ve HTTP hatalarında yeniden deneme sunuyor. Epic ödeme sağlayıcısı/hesapla gerçek cihaz testi yapılmadı; satın alma başarısı garanti edilmemeli.
- Oturum Android Keystore'da, iOS'ta Keychain'de tutuluyor; refresh token gerektiğinde deneniyor. İlk Epic bağlantısı authorization code ister. Kullanıcı kodları ve token'lar loglara veya repoya girmemeli.
- iOS kaynakları `ios/EpicLibraryIOS.xcodeproj` altındadır. Xcode/Mac gerektirir ve bu Windows ortamında derlenmemiştir. OAuth istemci anahtarı yerel, Git tarafından yok sayılan `ios/EpicLibraryIOS/Secrets.xcconfig` dosyasında sağlanır. iOS dağıtımı Xcode, TestFlight veya App Store ile yapılır.
- Koleksiyonlar belgelenmemiş Epic GraphQL uçlarına bağlıdır; Epic API'si değişirse koleksiyonlar görünmeyebilir. Oynanma süresi/oyuncu modu Epic'in ilgili veriyi döndürmesine bağlıdır.

## Yayın adımları

Android v1.0.5 kaynağı ve APK'sı hazırlandıktan sonra GitHub `main` dalına aktarılmalı; daha sonra `outputs/EpicLibraryMobile-v1.0.5.apk` dosyası tek APK olarak `v1.0.5` Release'ine eklenip yayımlanmalı. GitHub Actions yayımdan sonra `updates.json` manifestini günceller. Manifesti elle v1.0.5'e yükseltme; APK Release'te erişilebilir olmalı. APK imzası önceki yayımlı APK ile eşleşmelidir; Android son kurulum onayı ister.

Android kütüphanesinden atılma gerçek cihazda devam ederse crash log ile incele. Bu kaynakta duplicate liste anahtarları ve geri gezinmesi ele alındı; gerçek Epic hesabıyla uçtan uca doğrulama henüz yapılmadı.

