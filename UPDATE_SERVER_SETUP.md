# Uygulama içi güncelleme kaynağı

Bu proje doğrudan APK dağıtımı için bir güncelleme denetleyicisi içerir. Uygulama yalnızca herkese açık HTTPS adresinden güncelleme bilgisi alır, APK'nın SHA-256 özetini doğrular ve Android'in kendi yükleyicisini açar. Android son kurulum onayını kullanıcıdan ister.

## GitHub deposu

Bu proje şu adreslere göre yapılandırıldı:

- Manifest: `https://raw.githubusercontent.com/Punch1nello/epic-kutuphanesi/main/updates.json`
- En son APK: `https://github.com/Punch1nello/epic-kutuphanesi/releases/latest/download/EpicLibraryMobile-v1.0.1.apk`

`updates.json` dosyasını deponun `main` dalına ekle ve her sürümün APK dosyasını bir GitHub Release'e ekle. Depo public olmalı ki uygulama giriş yapmadan bu dosyalara ulaşabilsin. Yeni sürümlerde APK dosya adını, URL'yi, `versionCode` değerini ve SHA-256 özetini birlikte güncelle.

## Gerekenler

Herkese açık bir HTTPS sunucusuna şunları koy:

1. En yeni APK dosyası.
2. Aynı klasörde veya başka bir HTTPS adresinde aşağıdaki alanlara sahip `updates.json`.

```json
{
  "versionCode": 11,
  "versionName": "1.0.1",
  "apkUrl": "https://github.com/Punch1nello/epic-kutuphanesi/releases/latest/download/EpicLibraryMobile-v1.0.1.apk",
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

Mevcut v0.9.0 APK'sı debug imzalıdır. Uygulama içi güncelleme kaynağı kurulana kadar v0.9.0'ı bir kez elle kurmak gerekir. Sonraki APK aynı paket kimliği ve imza anahtarıyla derlenmeli; başka kullanıcılara kalıcı dağıtım yaparken sabit release anahtarı kullan ve anahtarı proje dosyalarının içine koyma. Anahtar değişirse Android güncellemenin üzerine kurulmasına izin vermez.
