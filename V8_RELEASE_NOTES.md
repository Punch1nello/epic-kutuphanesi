# Epic Kütüphanesi v0.5.0

## Bu sürümde

- Oyun adları kartlarda daima beyaz renkte gösterilir.
- Epic katalog sorguları Türkçe ülke/dil parametreleriyle yapılır; tür/kategori etiketleri için Türkçe karşılıklar eklendi.
- Epic hesabındaki kullanıcı koleksiyonları (varsa) kütüphanede ayrı bir koleksiyon filtresi olarak gösterilir.
- Koleksiyon seçildiğinde ilgili oyunlar filtrelenebilir.
- Kayıtlı Epic oturumu korunur: refresh token Android Keystore tabanlı şifreli depolamada tutulur ve uygulama açıldığında otomatik yenilenir. Tekrar giriş çoğu açılışta gerekmez; token geçersiz hale gelirse yeniden giriş gerekir.

## Teknik not

Koleksiyonlar, Epic web mağazasının GraphQL Library yüzeyi üzerinden en iyi çaba (best-effort) şeklinde okunur. Epic bu dahili/yarı-özel yüzeyi değiştirebilir; koleksiyon isteği başarısız olsa bile ana oyun kütüphanesi normal şekilde yüklenmeye devam eder.
