# Epic Kütüphanesi — v1.0.2

Kotlin ve Jetpack Compose ile Epic Games hesabındaki kütüphaneyi görüntüleyen Android uygulaması.

## Özellikler

- Mağaza, Kütüphane ve Ayarlar arasında animasyonlu geçiş.
- Üst başlıklar Android durum çubuğunun altına yerleşir ve saat/bildirim alanıyla çakışmaz.
- Uygulama simgesi farklı launcher biçimlerine uyumlu olacak şekilde uyarlanabilir simge olarak tanımlanmıştır.
- Epic Games Store uygulama içi WebView'da açılır; ziyaret edilen ürün sayfalarında Android geri tuşu önceki sayfaya döner.
- Sekmeler arası geçişte sayfalar yenilenmez. Aynı kütüphane sekmesine tekrar dokunmak kütüphaneyi yeniler; aynı mağaza sekmesine dokunmak ana sayfayı yeniler veya ürün sayfasındaysa ana mağazaya döner.
- Mağazada Android geri tuşu önce WebView geçmişinde ilerler; geçmiş yoksa ürün sayfasından mağaza ana sayfasına döner.
- Ayarlarda Türkçe, İngilizce, Almanca, İspanyolca ve Rusça dil seçenekleri; Sistem, Açık ve Koyu tema tercihleri.
- Oyun kataloğu isteği seçilen dile göre yerelleştirilir. Türler dilden bağımsız kategori kimlikleriyle filtrelenip beş dilde çevrilir.
- Uygulama oturumu Android Keystore ile şifrelenir. Gerekli olursa Epic access token yenilenir ve refresh token değişimi saklanır.
- Ayarlardan güncelleme kontrol edilebilir. Uygulama, herkese açık GitHub deposundaki `updates.json` manifestini okur ve GitHub Release APK'sının SHA-256 özetini doğrular. Yayın kurulumu: `UPDATE_SERVER_SETUP.md`.

## İlk giriş ve mağaza hesabı

İlk bağlantıda kullanıcı Epic'in kendi giriş sayfasında oturum açar, authorization code değerini uygulamaya verir. Uygulama Epic hesap parolasını istemez veya kaydetmez. Sonraki açılışlarda saklanan oturumu kullanır; belirteç Epic tarafından iptal edilirse tekrar giriş gerekebilir.

Uygulamanın kütüphane oturumu ile mağaza sayfasının web oturumu ayrıdır. Mağaza WebView'ında ilk kez ayrıca giriş gerekebilir; çerezler cihazda saklanır. Ödeme sayfası Epic'in WebView davranışına bağlıdır ve gerçek cihazda doğrulanmalıdır.
