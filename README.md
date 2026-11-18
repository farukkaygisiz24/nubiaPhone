# nubiaPhone

Nubia / MyOS telefonlar için, sistemin tasarım diliyle yazılacak **arama (Telefon)
uygulaması**. Kardeş proje: **nubiaSMS** (`../Projects/aramalr`).

> Durum: **0–5. aşamalar tamam** (2026-11-15). Varsayılan telefon uygulaması olarak çalışır. Yol haritası: **[docs/PLAN.md](docs/PLAN.md)**.
> Tasarım dili: **[TASARIM.md](TASARIM.md)** (nubiaSMS'ten kopya; ikisinde de güncel tutulmalı).
>
> Derleme: `./gradlew :app:assembleRelease && adb install -r app/build/outputs/apk/release/app-release.apk` (JDK 17).

## Güncellemeler (GitHub Releases)

Uygulama public GitHub Release’lerden kendini günceller (`update.json` + `nubiaphone-release.apk`).

1. `local.properties` içine repo bilgisini yaz:
   ```
   update.github.owner=SENIN_GITHUB_KULLANICI
   update.github.repo=nubiaPhone
   update.apk.asset=nubiaphone-release.apk
   ```
2. Kalıcı imza için kökte `keystore.properties` (gitignore’lı):
   ```
   KEYSTORE_FILE=release.jks
   KEYSTORE_PASSWORD=...
   KEY_ALIAS=...
   KEY_PASSWORD=...
   ```
   İlk release-imzalı sürüm, eski debug-imzalı kurulumun üstüne yazılmaz; bir kez kaldırıp yeniden kur.
3. Her yayın: `versionCode` artır → tag `v0.0.2` push → Actions Release oluşturur.
4. Arkadaşın ilk kurulumu: Release sayfasındaki APK + “bilinmeyen kaynak / bu uygulamadan yükle”.

## Hedef

- Telefon (dialer) uygulamasının yerine **varsayılan telefon uygulaması** olarak çalışmak
  (`RoleManager.ROLE_DIALER`).
- Ekranlar: tuş takımı, arama geçmişi (son aramalar), kişiler / favoriler,
  gelen arama ekranı, arama sırasındaki ekran (InCallService).
- nubiaSMS ile bağlantı: arama geçmişinden ve gelen arama ekranından **"Mesaj gönder"**.

## Planlanan teknik yapı

- Paket önerisi: `com.nubiaphone.dialer`
- Kotlin + Jetpack Compose + Material 3, nubiaSMS'teki MyOS teması ve bileşenleri
  (`ui/theme`, `ui/components`) temel alınır.
- `CallLog`, `ContactsContract`, `TelecomManager` + `InCallService`.
- Derleme: JDK 17 (`JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home`),
  telefonda denemek için release derlemesi.

## Kurallar

nubiaSMS'teki kuralların aynısı geçerlidir (bkz. `../Projects/aramalr/README.md` → Kurallar). Özellikle:

1. Önce [TASARIM.md](TASARIM.md). Ölçüler telefondan ölçülür, renk sistem paletinden gelir.
2. Nubia/ZTE uygulamalarından kod, görsel ya da ikon kopyalanmaz; sadece referans.
3. Kullanıcının sistem ayarlarına uygulama dokunmaz; varsayılan uygulama seçimi sistem penceresiyle yapılır.
4. Telefonda gerçek arama başlatan ya da arama geçmişini silen işler test sırasında onaysız yapılmaz.
5. Her değişiklik telefonda ekranda görülerek doğrulanır.
