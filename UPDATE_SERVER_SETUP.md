# Uygulama içi güncelleme kaynağı

Bu proje doğrudan APK dağıtımı için bir güncelleme denetleyicisi içerir. Uygulama yalnızca herkese açık HTTPS adresinden güncelleme bilgisi alır, APK'nın SHA-256 özetini doğrular ve Android'in kendi yükleyicisini açar. Android son kurulum onayını kullanıcıdan ister.

## GitHub deposu

Bu proje şu adreslere göre yapılandırıldı:

- Manifest: `https://raw.githubusercontent.com/Punch1nello/epic-kutuphanesi/main/updates.json`
- Sürüme özel APK URL'si workflow tarafından oluşturulur: `https://github.com/Punch1nello/epic-kutuphanesi/releases/download/vX.Y.Z/EpicLibraryMobile-vX.Y.Z.apk`

Depo public olmalı ki uygulama giriş yapmadan manifesti ve APK'yı okuyabilsin. Kaynak deposunda bulunan `.github/workflows/update-manifest.yml` iş akışı, yayınlanmış GitHub Release içindeki tek APK'yı bulur; SHA-256 özetini ve Gradle'daki sürüm numaralarını okuyarak `updates.json` dosyasını `main` dalında otomatik günceller.

Yeni bir sürüm yayımlarken uygulama kaynak kodunu ve `versionCode`/`versionName` değerlerini `main` dalına aktar, aynı kaynak sürümünden APK oluştur, sonra APK'yı ekleyerek `vX.Y.Z` etiketli bir GitHub Release yayımla. Release'in yayımlanmasından sonra GitHub Actions manifesti günceller. Workflow, aynı Release'te tam olarak bir APK bulamazsa hata verir; bu durumda manifest eski çalışan sürümde kalır.

## Gerekenler

Herkese açık bir HTTPS sunucusuna şunları koy:

1. En yeni APK dosyası.
2. Aynı klasörde veya başka bir HTTPS adresinde aşağıdaki alanlara sahip `updates.json`.

```json
{
  "versionCode": 14,
  "versionName": "1.0.4",
  "apkUrl": "https://github.com/Punch1nello/epic-kutuphanesi/releases/download/v1.0.4/EpicLibraryMobile-v1.0.4.apk",
  "sha256": "APK_DOSYASININ_64_HANELI_SHA256_OZETI",
  "releaseNotes": "Arayüz, dil ve mağaza gezintisi iyileştirmeleri"
}
```

`sha256`, gerçek APK dosyasının 64 karakterli SHA-256 özetidir. `versionCode`, telefondaki uygulamanın değerinden büyük olmalıdır. Güncelleme APK'sı aynı `applicationId` ve aynı imza anahtarıyla hazırlanmalıdır.

## Manifest adresini ayarlama

`gradle.properties` içindeki `epicUpdateManifestUrl` satırı bu proje için yukarıdaki manifest adresine ayarlandı. Başka bir adres kullanılırsa bu satırı değiştirip uygulamayı yeniden derle. Adres boş bırakılırsa Ayarlar içindeki **Güncelle** bölümü kaynak ayarlanmadığını gösterir.

```properties
epicUpdateManifestUrl=https://raw.githubusercontent.com/Punch1nello/epic-kutuphanesi/main/updates.json
```

Ardından uygulamayı yeniden derle.

## İlk kurulum ve imza

GitHub'daki mevcut v1.0.2 APK'sı debug imzalıdır. Uygulama içi güncelleme için yeni APK aynı paket kimliği ve aynı imza anahtarıyla derlenmelidir. Anahtar değişirse Android güncellemenin üzerine kurulmasına izin vermez. Kalıcı dağıtım için sabit bir release anahtarı kullan; anahtarı veya parolasını depoya ekleme.
