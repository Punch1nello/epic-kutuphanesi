# Epic Kütüphanesi v0.8.0

- Oturum AES-GCM anahtarı Android Keystore'da doğru kullanım izinleriyle oluşturuluyor.
- Access token, süresi ve refresh token şifreli şekilde kalıcı olarak kaydediliyor; kayıt başarısızlığı artık sessizce yok sayılmıyor.
- Epic refresh token'ı yenileme sonrasında hemen saklanıyor. Aynı oturum için eşzamanlı yenileme istekleri sıraya alınıyor.
- Mağaza ilk sekme oldu; kütüphane ikinci sekmeye taşındı.
- Epic Games Store mağaza sayfası harici tarayıcı yerine uygulama içindeki WebView'da açılıyor. WebView çerezleri cihazda kalıcı tutuluyor.

İlk v0.8.0 açılışında eski kurulumda oturum anahtarı oluşturulamamışsa bir kez Epic girişi gerekebilir. Başarılı girişten sonra oturumun uygulama yeniden açılışlarında yenilenmesi beklenir.

Mağazanın WebView oturumu, uygulamanın kütüphane API oturumundan ayrıdır. Mağaza içinde bir kez Epic hesabına giriş yapmak gerekebilir; bazı ödeme sağlayıcıları veya Epic tarafındaki WebView kısıtları satın alma akışını etkileyebilir.
