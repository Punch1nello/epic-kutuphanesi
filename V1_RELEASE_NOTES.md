# Epic Kütüphanesi — v1.0.0

## Android

- Mağaza ürün bağlantıları ve yeni pencereler uygulama içindeki gezinme alanında açılır; geri/ileri, ana sayfa ve yenile kontrolleri eklendi.
- Kütüphane başlığının altındaki sayaç Tümü/Oyunlar/Araçlar seçimine göre doğru toplamı gösterir.
- Oyunlar ve araçlar yalnızca bilinen Unreal Engine/Epic araç adlarıyla ayrılır; genel uygulama türü metaverisi oyunları Araçlar'a taşımaz.
- Süre bilgisi yoksa oyun kartında oynanma süresi yazısı gösterilmez.

## iOS

- iOS 16+ SwiftUI projesi güncellendi; WKWebView'da yeni pencere isteyen mağaza bağlantıları aynı uygulama içi sayfada açılır.
- Kütüphane başlığında seçili kategoriye ait toplam sayı gösterilir; oynanma süresi yoksa gizlenir.
- Release'e `EpicLibraryIOS-v1.0.0-source.zip` Xcode proje arşivi eklenmiştir. Bu Windows derlemesinde imzalı IPA üretilemedi; iOS kurulumu için Mac, Xcode ve Apple imzası gerekir.

## Bilinen sınır

Epic'in hesap doğrulaması ve checkout davranışı Epic'in sitesine ve ödeme sağlayıcısına bağlıdır. Bu değişiklikler gerçek Epic hesabı/cihazında tamamlanmış satın alma testi değildir; uygulama belgelenmemiş satın alma API'si kullanmaz.
