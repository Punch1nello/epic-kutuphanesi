# Epic Kütüphanesi — Devir Notu

## Amaç

Epic Games hesabındaki oyun kütüphanesini, mağazayı ve ayarları Android'de sunmak; iOS'ta da native uygulama olarak çalıştırabilmek.

## Güncel kaynak durumu

- Android: Kotlin + Jetpack Compose, v1.0.4 (`versionCode 14`). Kütüphane içeriği başlığı korunuyor; tür şeridi kaldırıldı; Tümü/Oyunlar/Araçlar ve sağdaki Sırala menüsü var. Yinelenen kartlar tekilleştiriliyor, kütüphanedeki Geri mağazaya dönüyor.
- Oyun/araç ayrımı oyun adından çıkarım yapmıyor. `ue` namespace ve açık Epic item/category metadata'sı araç; geri kalan kayıtlar oyun sayılıyor. Bu sınıflandırma gerçek kullanıcı hesabında doğrulanmalı.
- Oturum Android Keystore'da, iOS'ta Keychain'de tutuluyor; refresh token gerektiğinde deneniyor. İlk Epic bağlantısı authorization code ister. Kullanıcı kodları ve token'lar loglara veya repoya girmemeli.
- iOS kaynakları `ios/EpicLibraryIOS.xcodeproj` altındadır. Xcode/Mac gerektirir ve bu Windows ortamında derlenmemiştir. OAuth istemci anahtarı yerel, Git tarafından yok sayılan `ios/EpicLibraryIOS/Secrets.xcconfig` dosyasında sağlanır. iOS dağıtımı Xcode, TestFlight veya App Store ile yapılır.
- Koleksiyonlar belgelenmemiş Epic GraphQL uçlarına bağlıdır; Epic API'si değişirse koleksiyonlar görünmeyebilir. Oynanma süresi/oyuncu modu Epic'in ilgili veriyi döndürmesine bağlıdır.

## Yayın adımları

GitHub `main` dalında v1.0.4 Android/iOS kaynakları bulunuyor. En yeni Android Release ve `updates.json` manifesti hâlâ v1.0.3. Uygulama içi güncellemenin çalışması için `outputs/EpicLibraryMobile-v1.0.4.apk` dosyasını tek APK olarak `v1.0.4` Release'ine yükleyip yayımla. GitHub Actions manifesti yayımdan sonra günceller. APK imzası yayımlı v1.0.3 APK'sıyla eşleşiyor; Android yine kurulum onayı ister.

Android kütüphanesinden atılma gerçek cihazda devam ederse crash log ile incele. Bu kaynakta duplicate liste anahtarları ve geri gezinmesi ele alındı; gerçek Epic hesabıyla uçtan uca doğrulama henüz yapılmadı.

