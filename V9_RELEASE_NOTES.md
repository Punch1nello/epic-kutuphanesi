# Epic Kütüphanesi v0.6.0

- Tür/kategori ayrıştırması genişletildi ve Türkçe etiket eşlemesi güçlendirildi.
- Katalog isteklerinde `tr-TR` locale kullanılıyor.
- Koleksiyon sorgusu artık Epic'in aktif olabilecek GraphQL uç noktalarını sırayla deniyor ve kimlik eşleştirmesini genişletiyor.
- Oturum hem erişim tokenı hem refresh token ile Android Keystore üzerinden kalıcı saklanıyor; uygulama yeniden açıldığında önce geçerli erişim tokenı, sonra refresh token deneniyor.
- Sadece uygulamayı kapatıp açmak veya normal bir APK güncellemesi yapmak yeniden Epic kodu istememeli. Uygulama verisini silmek veya uygulamayı kaldırmak oturumu doğal olarak temizler.
