# Tasarım dili — Nubia / MyOS

Bu belge nubiaSMS'te oturttuğumuz tasarım dilini anlatır. Aynı dil **nubiaPhone**
(arama uygulaması) ve sonraki Nubia uygulamaları için de geçerlidir.

Değerlerin kaynağı:
- Nubia Z80 Ultra (NX741J, Global MyOS, 1216×2688, 520 dpi → **3,25 px/dp**)
  üzerinde sistem uygulamalarından (Ayarlar, Ses Kaydedici, Dosya Yöneticisi)
  piksel ölçümü.
- Telefondan çekilen APK'lardaki MFVC token'ları (`design-refs/myos-tokens/`).
- Kullanıcının uygulama üzerindeki geri bildirimleri.

> Kural: Token ile ekran çelişirse **ekran kazanır**. Örneğin `search_bar_*`
> token'ı 52 dp / 28 dp derken Ayarlar'da gerçekte 38 dp / ~12 dp ölçüldü.

---

## 1. Renk

Renkler **sabit yazılmaz**, sistem paletinden gelir (Android 12+ dinamik renk).
Kullanıcının MyOS tema rengi neyse uygulama da odur. Örneğin şu an duvar
kağıdından "Expressive" stilde gelen palet `system_accent1_600` = `#455C99`.

| Rol | Açık tema | Koyu tema | Not |
|---|---|---|---|
| Sayfa zemini | `#F5F5F5` | `#000000` | Ayarlar'da ölçüldü |
| Kart / grup | `#FFFFFF` | `#1B1B1B` | `mfv_common_card_bg_color` |
| Basılı satır | `#0F000000` | `#282828` | |
| Arama kutusu | `system_accent1_50` (ör. `#EEF0FF`) | `#1E1E1E` | Vurgu tonuna boyanır |
| Vurgu (primary) | dinamik | dinamik | Buton, nokta, giden balon, FAB |
| Ana metin | `#111111` | `#FFFFFF` | |
| İkincil metin | `#5C5C5C` | `#D0D0D0` | |
| Yer tutucu metin | `#9A9A9A` | `#888888` | |
| Ayırıcı çizgi | onSurface %8 | onSurface %8 | 0,5 dp |
| Tehlikeli eylem | `#E5393B` | `#FF6B6B` | Sil, engelle, kalıcı sil |
| Açılır menü zemini | `#FFFFFF` | `#232323` | `mfvc_popup_bg_color` |
| Gelen balon | `#FFFFFF` | `#262626` | |
| Yazma çubuğu | `#F2F2F2` | `#1B1B1B` | |
| Yıldız | `#F5B400` | `#F5B400` | |
| Arama vurgusu | `#FFE59A` (aktif: `#FFC94D`) | aynı | Sohbet içi arama |

Android 12 altı için yedek vurgu: `#0F87F6` (Çin ROM Mesajlar'dan ölçüldü).

## 2. Yazı

Sistem yazı tipi (Roboto / MyOS Sans). Kalın (Bold) başlık yok; ana başlık **Normal**, alt başlıklar **Medium**.

| Stil | Boyut / satır | Ağırlık | Kullanım |
|---|---|---|---|
| headlineLarge | **21 / 26 sp** | **Normal** | Ana sayfa başlığı ("Mesajlar"). Ayarlar ile yan yana ölçüldü: glif yüksekliği 19,7 dp, ince çizgi |
| titleLarge | 18 / 24 sp | Medium | Alt sayfa başlığı, seçim başlığı |
| bodyLarge | 16 / 24 sp | Normal | Liste başlığı, menü satırı, ayar satırı |
| bodyMedium | 14 / 20 sp | Normal | Önizleme, alt yazı, grup etiketi |
| labelMedium | 12 sp | Normal | Saat, sayaç, eylem etiketi |
| Mesaj balonu | 15 / 21 sp | Normal | |

Okunmamış öğe: başlık **SemiBold**, saat vurgu renginde, sağda 8 dp vurgu noktası.

## 3. Şekil ve ölçü

| Öğe | Değer |
|---|---|
| Kart, liste grubu, menü paneli, diyalog | **16 dp** köşe (`mfvc_radius`) |
| İç öğe, balon, basılı satır | **12 dp** köşe |
| Arama kutusu | **38 dp** yükseklik, **12 dp** köşe, ekran kenarından **20 dp** (listeden 4 dp içeride) |
| Etiket (chip) | tam yuvarlak (%50), 14 × 6 dp iç boşluk |
| Ekran yan boşluğu | **16 dp** |
| Kart içi yatay boşluk | 16 dp |
| Başlık sol girintisi | 24 dp (16 + 8) |
| Tek satırlı liste satırı | 52–56 dp |
| İki satırlı liste satırı | **64 dp** |
| Avatar | 36 dp daire, vurgu kabı (primaryContainer) |
| Üst çubuk ikonları | 22–24 dp, dokunma alanı 40–48 dp |
| Gruplar arası boşluk | 12–20 dp |
| Menü satırı | 52 dp, 16 sp, 20 dp ikon, ikon–yazı 14 dp |

**Gölge yok.** Kartlar ve bildirim kartı (snackbar) düz durur. İstisnalar: açık
temadaki açılır menü (sistemdeki gibi hafif, geniş, düşük kontrastlı gölge) ve FAB'ın
vurgu tonlu parlaması (bkz. "Telefon uygulaması bileşenleri").

## 4. Bileşenler

### Liste
- Satırlar **tek kart grubu** içinde durur (Ayarlar gibi). Her satır ayrı kart değildir.
- Sadece ilk ve son satırın köşeleri yuvarlaktır.
- Ayırıcı çizgi avatarın sağından başlar (64 dp girinti).
- Sağda saat: bugün ise "17:41", değilse kısa tarih.

### Başlık ve kaydırma
- Ana sayfada büyük başlık ("Mesajlar" + ⋮) ve arama çubuğu **sabit kalır**, sadece liste kayar
  (Ayarlar gibi). Liste ve yaylanması sabit başlığın altından kayar.
- Alt sayfalarda "←" üst çubuğu ve seçim modundaki "N seçildi" başlığı da sabittir.
- Uyarı kartları ve alt sayfa açıklama yazıları (ör. Arşiv/Çöp ipuçları) listenin ilk öğesidir, listeyle kayar.

### Ana sayfa başlık bloğu (Ayarlar ile yan yana ölçüldü)
| Ölçü | Değer |
|---|---|
| Başlık glifinin ekran üstünden uzaklığı | 54,5 dp (durum çubuğu altından ~6 dp boşluk) |
| Başlık sol kenarı | 24 dp |
| Başlık → arama kutusu | 23,7 dp |
| Arama kutusu | 38 dp yükseklik, kenarlardan 20 dp, 12 dp köşe |
| Büyüteç ikonu | 13,8 dp glif (18,5 dp kutu), kutunun solundan 15,7 dp |
| İkon → yer tutucu yazı | 3,7 dp |
| Yer tutucu yazı | 15 sp; koyu temada `#5C5C5C`, açık temada `#9A9A9A` |
| Arama kutusu → ilk kart | 21 dp |

### Arama
- Ana sayfada arama kutusu **dokunulabilir bir çubuktur**, yazı alanı değildir.
  Dokununca ayrı arama sayfası açılır.
- Arama sayfası: ince "<" geri oku + vurgu tonlu kutu, altında
  **"Arama geçmişi"** etiketleri ve ortalanmış **"Kaydı temizle"**.
- Sonuçlar tek kart grubunda, aranan kelime vurgu renginde.
- "Yeni mesaj" gibi kişi seçme ekranları da **aynı üst düzeni** kullanır.
- İkon: ince çizgili büyüteç (`ic_myos_search_line`), 20 dp.

### Üst çubuk
- Alt sayfa: sol "←" + sola dayalı başlık (18 sp Medium), gerekirse altında gri alt satır.
- Sağda en fazla bir eylem ikonu + "⋮".

### Açılır menü (⋮ ve uzun basma)
- Beyaz / `#232323` panel, 16 dp köşe, min 196 dp genişlik.
- 52 dp satır, 20 dp çizgi ikon + 16 sp yazı. Grup ayırıcısı yok.
- Tehlikeli satırlar kırmızı (ikon dahil).
- Açılış: hafif büyüyerek belirme (~150–180 ms).

### Seçim modu (uzun basma)
- Başlığın yerine: "×" · "N seçildi" · sağda "Tümünü seç / Hiçbirini seçme".
- Avatar onay işaretine döner, satır vurgu rengi %10–12 ile boyanır.
- Altta kart renginde eylem çubuğu: ikon (22 dp) üstte, etiket altta, eşit genişlikte.
- Geri tuşu seçimden çıkar.

### Kaydırma (swipe)
- **Sağa: Arşivle** (vurgu rengi). **Sola: Çöp kutusu** (kırmızı).
- Eşik: satır genişliğinin %35'i. Eşik geçilince hafif titreşim ve renk koyulaşır.
- Etiket kaydırmayla birlikte ortaya kayar, **asla birden zıplamaz**.
- Bırakınca panel dolar, kısa süre bekler, satır kapanır, alttakiler yukarı kayar.
- Geri dönüşü olmayan işlemlerde (kalıcı sil) satır geri oturur ve **onay sorulur**.

### Bildirim kartı (snackbar)
- Material'ın koyu kutusu **kullanılmaz**.
- Açık temada beyaz, koyu temada `#2A2A2A`. 16 dp köşe, **gölgesiz**.
- Yazı onSurface, eylem ("Geri al") vurgu renginde, Medium.
- Taşıma işlemlerinden (arşiv, çöp) sonra **her zaman "Geri al"** sunulur.
- Sistem Toast'u (siyah) sadece gerçekten kısa bilgi için kullanılır. Başka ekrandan dönülen
  işlemler `ListNotice` ile listeye taşınır ve orada snackbar olarak gösterilir.

### Diyalog
- Kart renginde, 16 dp köşe. Başlık + metin + sağda yazı düğmeleri.
- Tehlikeli onay düğmesi kırmızı ("Sil"), iptal nötr.
- Bilgi diyalogları: solda gri etiket (112 dp), sağda değer, aralarda 0,5 dp çizgi.

### Mesaj balonu
- 12 dp eşit köşe, **kuyruk yok**, en fazla 280 dp genişlik, 12 × 7 dp iç boşluk.
- Giden: vurgu rengi + beyaz yazı. Gelen: kart rengi.
- Saat balonun altında küçük ve gri, sadece gönderen değişince ya da 5 dakika arayla.
- Gün değişince ortalanmış tarih ayırıcı.

### Yazma çubuğu
- `#F2F2F2` / `#1B1B1B` şerit, beyaz hap şeklinde alan (20 dp köşe, min 40 dp).
- 40 dp yuvarlak vurgu renkli gönder düğmesi. Boşken %35 opak.

### Bildirim
- Bildirim başına en fazla **2 düğme**: kodlu SMS'te "Kodu kopyala · 123456" + "Okundu",
  numaradan gelen SMS'te "Yanıtla" (bildirim içinden yazma) + "Okundu",
  yanıt kabul etmeyen gönderende sadece "Okundu".
- `setNumber(okunmamış)`: MyOS ikondaki rozeti bildirimden okur.

### Telefon uygulaması bileşenleri (nubiaPhone)

Global MyOS'ta Telefon ve Kişiler Google uygulamalarıdır (Material). MyOS değerleri bu yüzden
aynı bileşeni kullanan ZTE uygulamalarından ölçüldü (koyu tema, 2026-11-14). Kaynak görseller:
`nubiaPhone/design-refs/`. Açık tema renkleri token'dan alındı. "Türetildi" yazanlar ölçülmedi.

**Alt sekme çubuğu** (Saat; token `mfvc_bottom_tab_*`, `mfvc_bottom_height`)
- **60 dp** yükseklik, yanlarda 8 dp, sekmeler eşit genişlikte.
- Zemin sayfanın kendisi: `mfvc_bottom_tab_bg_color` açıkta `#EBF5F5F5`, koyuda `#E6000000`
  (yarı saydam sayfa rengi). **Ayırıcı çizgi yok**, kart rengi yok.
- İkon **24 dp**, ekranda üstten ≈ 9–10 dp (token 6 dp der, ekran kazanır). İkon–etiket ≈ 3 dp (token 1 dp), etiket satırı 15 dp, altında 10 dp.
- Etiket **11 sp** Normal.
- Seçili sekme: **dolu** ikon ve etiket, koyuda `system_accent1_500` (açıkta `_600`, türetildi).
- Seçili olmayan (koyu): ikon `#8F8F8F` (beyaz ≈ %56), etiket `#B9B9B9` (beyaz ≈ %72).
  Açık temada aynı oranlar `#111111` üstünden alınır (türetildi).
- Basınca dalga (ripple) yok.

**FAB** (Saat; token `mfvc_floating_button_radius` = 24 dp)
- **48 dp daire**, **yatayda ortada**, sekme çubuğunun 28 dp üstünde. Dolgu koyuda
  `system_accent1_400` (açıkta `_600`, türetildi). Material'ın soluk koyu tema primary'si kullanılmaz.
- İkon her iki temada **beyaz** ("+" 16 dp).
- Gölge yerine **vurgu tonlu hafif parlama** (`mfvc_fab_bg_shadow_color`, `mfvc_fab_translation_z` 2 dp).
  "Gölge yok" kuralının tek istisnası budur.

**Tuş takımı** (kilit ekranı PIN'i, SystemUI_MFV token'ları)
- Rakam **32 dp**, Normal, gövde yazı tipi. Satır yüksekliği **66,5 dp**.
- Yan boşluk 30 dp, tuş alanı en fazla 298 dp genişlik (`mfv_kg_pin_container_width`).
- Harfler (ABC…) MyOS'ta ölçülemedi: rakamın altında 11 sp, ikincil renk (türetildi).
- Silme tuşu ikincil renkte.

**Alt panel** (Saat › Zamanlayıcı ilk açılış paneli)
- Üst köşeler **24 dp** (`mfvc_*Radius_dialog`). Zemin koyuda `#232323` (açılır menüyle aynı).
- Arkadaki sayfa karartılır (`#141414` → `#0E0E0E`).
- Ana düğme: **40 dp** yükseklik, hap şeklinde (20 dp köşe), vurgu renginde, 240 dp genişlik, ortada.

**Harf dizini (A–Z)** (başlatıcı › tüm uygulamalar)
- Sağ kenarda, sütun merkezi kenardan **10 dp** içeride.
- Harfler ≈ **12 sp** (büyük harf yüksekliği 8,6 dp), **19 dp** aralıkla.
- Türkçe alfabe: `#`, A, B, C, Ç, D … Ş, T, U, Ü, V, Y, Z, `…`.

**Liste token'ları (karşılaştırma)**
- `mfvc_2line_list_height` = 68 dp, `mfvc_avatar` = 40 dp, `mfvc_subheader_height` = 32 dp
  (Medium 18 sp). Ayarlar'da ölçülen 64 dp satır ve 36 dp avatar geçerli kalır (ekran kazanır).
  Kişi listesindeki harf başlıkları grup etiketi gibi çizilir (14 sp, ikincil renk).

**Ölçülemeyenler**
- Gelen arama ve arama sırasındaki ekran: MyOS'ta karşılığı yok. Tasarım bu belgeden
  türetilir ve telefonda onaylanır.

## 5. Hareket

Sistem geçişleri `framework-res` içinden okundu. **Solma (crossfade) kullanılmaz.**

| Geçiş | Değer |
|---|---|
| Sayfa açılış | Yeni sayfa sağdan **96 dp** kayar, 450 ms; 50 ms sonra 83 ms belirme. Eski sayfa sola 96 dp kayar, solmaz |
| Geri | Önceki sayfa soldan 96 dp gelir. Kapanan sağa kayar, 35 ms sonra 83 ms'de kaybolur |
| Eğri | `fast_out_extra_slow_in` (iki parçalı cubic: 0.05,0 0.133,0.06 0.167,0.4 · 0.208,0.82 0.25,1 1,1) |
| Liste uçları | Android'in esneme (stretch) efekti kapalı (`LocalOverscrollConfiguration = null`). Yerine Ayarlar'daki gibi **MyOS bounce** (`Modifier.myOsBounce()`, `ui/components/MyOsBounce.kt`) |
| Bounce: fırlatma | Kalan hızla `spring(dampingRatio = 1.0, stiffness = 60)`, en fazla **66 dp** taşar; tepe ≈130 ms, oturma ≈600 ms (Ayarlar › Tüm uygulamalar ekran kaydıyla ölçüldü) |
| Bounce: sürükleme | Parmağın **0,28×**'i kadar lastik etkisi, 110 dp'de doğrusal olarak 0'a iner |
| Bounce: bırakma | `spring(dampingRatio = 1.0, stiffness = 180)` ile 0'a döner. Kap `clipToBounds`, içerik sabit çubukların altından kayar |

Kod: `ui/navigation/MyOsTransitions.kt`.

## 6. İkon

- Adaptive icon: zemin **arka plan katmanında, tam kenara kadar** çizilir; köşeyi launcher verir.
- Ön plan nesnesi 108 dp tuvalin ortasındaki **66 dp güvenli alanda** kalır
  (onaylı tasarım 0,72 ölçek + 18 dp kaydırma ile yerleştirilir).
- **Tek renk (monochrome) katmanı** her zaman eklenir (temalı ikonlar).
- Dil: hafif dikey gradyanlı zemin + yarı 3B, katmanlı, tozlu tonlu nesne (Ayarlar / Dosya Yöneticisi gibi).
- Çin ROM'daki ikonlar **referanstır, kopyalanmaz**.

## 7. Metin ve dil

- Arayüz Türkçe, kısa ve sade: "Arşivle", "Çöp kutusu", "Geri al", "Okundu".
- Boş durumlar ne yapılacağını söyler: "Yıldızlı mesaj yok. Bir mesaja uzun basıp \"Yıldız ekle\"yi seçin."
- Tarih: bugün → saat, değilse kısa tarih. Bilgi ekranında uzun tarih + saniye.

## 8. Yapılmayacaklar

- Sabit vurgu rengi (mavi/turkuaz) yazmak. Renk sistemden gelir.
- Material varsayılanlarını olduğu gibi bırakmak (koyu snackbar, crossfade, kalın başlık, 52 dp arama kutusu).
- Gölgeli kartlar.
- Nubia/ZTE APK'larından kod, görsel ya da ikon kopyalamak. Sadece ölçü ve renk değeri alınır.
- Kullanıcının sistem ayarlarını (varsayılan uygulama, otomatik doldurma, ekran döndürme) uygulamadan değiştirmek.
  Uygulama sadece sistemin seçim ekranını açar.
