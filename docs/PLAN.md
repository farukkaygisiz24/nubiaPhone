# nubiaPhone — Plan

Tasarım kuralları için önce [../TASARIM.md](../TASARIM.md). Bu belge **ne sırayla** yapılacağını söyler.

## Uygulama yapısı

Tek aktivite, Compose + Navigation. Ana sayfada altta 3 sekme:

| Sekme | İçerik |
|---|---|
| **Son aramalar** | Arama geçmişi, gün gruplu, tek kart grubu. Satırda tür ikonu (gelen/giden/cevapsız), sayı "(3)", saat. Cevapsız kırmızı. |
| **Kişiler** | A–Z harf başlıklı liste, sağda hızlı harf kaydırıcı, üstte "Benim kartım" + gruplar. |
| **Favoriler** | Yıldızlı kişiler (`STARRED`), ızgara ya da liste. |

- Her sekmede büyük başlık + ⋮ + arama çubuğu **sabit** (nubiaSMS ana sayfası gibi), liste altından kayar, MyOS bounce.
- Sağ altta vurgu renkli **tuş takımı FAB**'ı (16 dp köşe, gölgesiz). Dokununca tuş takımı alttan açılır.
- Ortak arama sayfası (nubiaSMS'teki gibi): kişi + numara + arama geçmişinde arar.

## Aşamalar

### 0. İskelet ✅ (bu oturum)
- Gradle (AGP 8.7.3, Kotlin 2.0.21, compileSdk 35, minSdk 26) — nubiaSMS ile aynı sürümler.
- Paket `com.nubiaphone.dialer`, uygulama adı **Telefon**.
- nubiaSMS'ten taşınan: `ui/theme` (renk, yazı, şekil), `ui/components` (kart, arama çubuğu,
  ⋮ menü, açılır menü, snackbar, bounce, seçim, üst çubuk), `ui/navigation/MyOsTransitions`.
- Ana sayfa: 3 sekme + boş durum kartları + tuş takımı FAB'ı (henüz yer tutucu).

### 1. Arama geçmişi ✅ (2026-11-14)
- `CallLog.Calls` okuma (`READ_CALL_LOG`), kişi adıyla eşleme, ardışık aynı numarayı gruplama.
- Satıra dokununca **ara**, sağdaki (i) ile arama detayı (tarihli liste, süre, SIM).
- Uzun basma: kopyala, mesaj gönder (nubiaSMS'e `smsto:`), kişi ekle/aç, ayrıntılar, sil.
  Engelleme 4. aşamaya kaldı (engel listesi sadece varsayılan telefon uygulamasına açık).

### 2. Kişiler ✅ (2026-11-14)
- `ContactsContract` listesi, harf başlıkları, hızlı kaydırıcı, foto/harf avatar.
- Kişi detayı: büyük avatar, numaralar (ara / mesaj), e-posta, yıldız, ⋮ (düzenle, paylaş, sil).
- Kişi ekle / düzenle (`WRITE_CONTACTS`). Silme her zaman onay sorar.
- Favoriler sekmesi `STARRED` üzerinden.
- Test edilmedi (telefondaki gerçek veriye yazar): kaydet, yıldız, kişi silme, arama geçmişi silme.

### 3. Tuş takımı ✅ (2026-11-14)
- 3×4 tuş (kilit ekranı PIN ölçüleri), harfler altta. Uzun basma: 0 → "+", 1 → sesli mesaj (numara boşken).
- Yazarken panelin üstünde en fazla 4 öneri: T9 ad (Türkçe harfler kendi tuşunda: Ç→2, Ğ/İ→4, Ö→6,
  Ş→7, Ü→8) ve numara eşleşmesi. Sıra: adın başı, kelime başı, numara, kelime içi; eşitlikte son aranan.
- Numara Türkiye biçiminde gruplanır, uzayınca küçülür. Uzun basma: Yapıştır / Kopyala.
- Altında "Kişilere ekle · Mesaj gönder". Tuş sesi sistemin "tuşlama sesleri" ayarına ve sessiz moda,
  titreşim sistem dokunma geri bildirimine uyar.
- Test edilmedi (gerçek arama başlatır): ara düğmesi, öneriye dokunma, sesli mesaj.

### 4. Varsayılan telefon uygulaması + arama ekranları ✅ (2026-11-15)
- `telecom/CallService` (InCallService) + `CallRegistry`: cevapla, reddet (otomatik SMS yok), kapat,
  beklet, iki arama arasında değiştir, arama içi tuşlama (DTMF), sessiz, ses yolu (telefon/hoparlör/Bluetooth),
  çift SIM seçimi. Çalma sesi Telecom'da kalır (IN_CALL_SERVICE_RINGING yok): uygulama çökse de telefon çalar.
- `InCallActivity`: kilit ekranında açılır, ekranı yakar, kulağa yaklaşınca ekranı kapatır, ses tuşu
  çalmayı susturur. Gelen: Reddet / Cevapla / "Mesajla yanıtla" (reddeder, nubiaSMS'i açar).
  Süren: Sessiz, Tuş takımı, Hoparlör, Beklet, Arama ekle, Mesaj + kırmızı kapat; ikinci arama şeridi.
- Bildirimler: gelen = CallStyle + tam ekran (Cevapla aktiviteyi doğrudan açar, Reddet receiver);
  süren = sayaç + "Kapat". Bildirim/tam ekran izni yoksa gelen arama ekranı doğrudan açılır.
- "Varsayılan yap" bandı (sistemin rol penceresi), DIAL / tel: intent'leri tuş takımını numarayla açar.
- Gelen arama: iOS tarzı "Cevaplamak için kaydır", Mesaj (hazır cümleler → reddet + SMS; "Özel mesaj yaz…"
  → nubiaSMS), Sessize al, kırmızı "Meşgul" (reddet).
- Konferans: yalnızca bağlı bir görüşmedeyken "Arama ekle" → nubiaSMS "yeni mesaj" gibi tam boy modal
  (arama kutusu, "Ara: <numara>", boşken son aranan 6 kişi, yazarken kişiler). Seçince mevcut görüşme önce
  beklemeye alınır, sonra yeni kişi aranır. Üstte "Beklemede · Değiştir" şeridi; ikinci görüşme bağlanınca
  "Birleştir". Kırmızı düğme yalnız o anki görüşmeyi kapatır, bekleyen kalır ("Devam"). Konferansta
  katılımcılar üstte kartta: "Özel" (ayrı görüşme, diğerleri bekler) ve "Çıkar". Operatör desteklemiyorsa
  "Birleştir"/"Özel"/"Çıkar" görünmez ya da soluk kalır.
- Önizleme: `adb shell am start -n com.nubiaphone.dialer/.MainActivity --es incall_preview incoming|active|dialing|two|conference`
- Gerçek arama testi kullanıcıyla birlikte yapılır.

### 5. Cila ✅ (2026-11-15)
- Ayarlar: varsayılan uygulama, engellenen numaralar, arama hesapları/SIM, operatör ayarları, ses, hakkında.
- Engellenen numaralar: sistem listesi; listeden ve uzun basma / arama ayrıntısı menüsünden engelle,
  engeli kaldır ("Geri al" ile). Sadece varsayılan telefon uygulamasıyken açık.
- Son aramalarda seçim modu (⋮ › Seç): "N seçildi", Tümünü seç, toplu sil (onaylı).
- İkon: tozlu mavi-gri gradyan zemin, katmanlı ahize + ses dalgaları, monochrome katmanı.
- Bildirim kartı (snackbar) nubiaSMS ile aynı yerde: alt çubuğun 16 dp üstünde, görünürken FAB yukarı kayar.

## Ölçümler (2026-11-14 ✅)

Global MyOS'ta Telefon/Kişiler Google uygulaması olduğu için değerler ZTE uygulamalarından
(Saat, başlatıcı, SystemUI kilit ekranı token'ları) alındı ve TASARIM.md → "Telefon uygulaması
bileşenleri"ne yazıldı. İskelet bunlara göre düzeltildi: alt sekme (Saat'le ±0,4 dp), ortadaki
48 dp FAB, tuş takımı paneli.

Hâlâ açık:
- Tuş üstündeki harflerin (ABC) boyutu: MyOS'ta karşılığı yok, 11 sp türetildi.
- Açık tema: telefon koyu temada, sistem ayarına dokunmadan ölçülemedi. Token'dan türetildi;
  kullanıcı açık temaya geçtiğinde ekranda doğrulanacak.
- Gelen arama / arama sırası ekranı: MyOS'ta karşılığı yok, 4. aşamada tasarlanıp onaylatılacak.
