# Epic Kütüphanesi — v1.0.4

Kotlin ve Jetpack Compose ile Epic Games hesabındaki kütüphaneyi görüntüleyen Android uygulaması.

## Özellikler

- Mağaza, Kütüphane ve Ayarlar arasında animasyonlu geçiş.
- Üst başlıklar Android durum çubuğunun altına yerleşir ve saat/bildirim alanıyla çakışmaz.
- Uygulama simgesi farklı launcher biçimlerine uyumlu olacak şekilde uyarlanabilir simge olarak tanımlanmıştır.
- Mağaza sekmesine yeniden dokunmak mağazanın ana sayfasını en üste açar; başka sekmeye geçiş mağaza sayfasını yenilemez.
- Kütüphane Tümü, Oyunlar ve Araçlar olarak ayrılır. Araçlar bölümünde sadece A–Z ve Z–A sıralaması gösterilir.
- Kütüphane içindeki tür filtreleri kaldırıldı; Tümü/Oyunlar/Araçlar ve Sırala aynı satırda gösterilir. Oyun ve araç ayrımı yalnızca açık ürün türü bilgisiyle yapılır.
- Tekrarlanan Epic kayıtları kütüphane ızgarasında tekilleştirilir; kütüphanedeyken Geri tuşu mağaza sekmesine döner.
- Kütüphane A–Z, Z–A, oynanma süresi ve mevcut oyuncu modu bilgilerine göre sıralanabilir.
- Bildirimler açılırsa uygulama GitHub güncelleme kaynağını yaklaşık günde bir kontrol eder.
- Epic Games Store uygulama içi WebView'da açılır; ziyaret edilen ürün sayfalarında Android geri tuşu önceki sayfaya döner.
- Sekmeler arası geçişte sayfalar yenilenmez. Aynı kütüphane sekmesine tekrar dokunmak kütüphaneyi yeniler; aynı mağaza sekmesine dokunmak ana sayfayı yeniler veya ürün sayfasındaysa ana mağazaya döner.
- Mağazada Android geri tuşu önce WebView geçmişinde ilerler; geçmiş yoksa ürün sayfasından mağaza ana sayfasına döner.
- Ayarlarda Türkçe, İngilizce, Almanca, İspanyolca ve Rusça dil seçenekleri; Sistem, Açık ve Koyu tema tercihleri.
- Oyun kataloğu isteği seçilen dile göre yerelleştirilir. Türler dilden bağımsız kategori kimlikleriyle filtrelenip beş dilde çevrilir.
- Uygulama oturumu Android Keystore ile şifrelenir. Gerekli olursa Epic access token yenilenir ve refresh token değişimi saklanır.
- Ayarlardan güncelleme kontrol edilebilir. Uygulama, herkese açık GitHub deposundaki `updates.json` manifestini okur ve GitHub Release APK'sının SHA-256 özetini doğrular. Yayımlanan Release sonrasında GitHub Actions manifesti otomatik yeniler. Yayın kurulumu: `UPDATE_SERVER_SETUP.md`.

## İlk giriş ve mağaza hesabı

İlk bağlantıda kullanıcı Epic'in kendi giriş sayfasında oturum açar, authorization code değerini uygulamaya verir. Uygulama Epic hesap parolasını istemez veya kaydetmez. Sonraki açılışlarda saklanan oturumu kullanır; belirteç Epic tarafından iptal edilirse tekrar giriş gerekebilir.

Uygulamanın kütüphane oturumu ile mağaza sayfasının web oturumu ayrıdır. Mağaza WebView'ında ilk kez ayrıca giriş gerekebilir; çerezler cihazda saklanır. Ödeme sayfası Epic'in WebView davranışına bağlıdır ve gerçek cihazda doğrulanmalıdır.

## Veri sınırları

Epic'in resmi kullanıcı kitaplığı API'si her oyun için oynanma süresi veya istek listesi fiyatlarını garanti etmez. Uygulama kitaplık kaydında süre bilgisi varsa gösterir; yoksa bunu açıkça belirtir. Oynanma süresine ve oyuncu moduna göre sıralamalar yalnızca ilgili bilgi Epic yanıtında mevcutsa kullanılabilir. İstek listesi indirim bildirimi için uygulamaların kullanabileceği resmi bir Epic API'si bulunmadığından bu özellik şu anda etkin değildir.

## iOS kaynak projesi

`ios/EpicLibraryIOS.xcodeproj` içinde Keychain oturum depolaması, Epic mağazası WebView'ı ve kütüphane kategorileri olan SwiftUI iOS uygulaması bulunur. Derleme, imzalama ve yükleme için macOS ve Xcode gerekir; bu Windows çalışma alanında iOS derlemesi doğrulanamadı. Kurulum ve dağıtım için [ios/README.md](ios/README.md) dosyasına bak. iOS sürümleri TestFlight veya App Store üzerinden dağıtılır; Android APK güncelleyicisi iOS'ta kullanılmaz.
