# Epic Kütüphanesi v0.7.0

- Kayıtlı Epic oturumu açılışta otomatik kullanılmaya devam eder. Geçici bağlantı hataları artık oturumu silmez; kullanıcı yeniden deneme yapabilir.
- Yeni **Mağaza** sekmesi Epic Games Store'u güvenli uygulama içi tarayıcı sekmesinde açar. Satın alma Epic'in resmî mağazasında tamamlanır.
- Yeni **Güncelle** sekmesi sürüm kontrolü yapar; yeni APK'yı indirir, SHA-256 özetini kontrol eder ve Android'in onaylı kurulum ekranını açar.
- Uygulama içi güncelleme için HTTPS güncelleme adresi henüz ayarlanmalıdır. Kurulum sunucusu biçimi `UPDATE_SERVER_SETUP.md` dosyasında anlatılıyor.
- Epic'e ilk girişte veya oturumun süresi dolup yenileme reddedilirse tekrar giriş gerekebilir.
