# News Frame Studio – Android App (WebView)

Yeh project aapke webapp ko ek asli Android app mein badalta hai. Isme browser mein jo cheezein
nahi ho paati thi wo ho jaati hain:

* **Foreground service + notification:** export ke dauraan notification mein progress aur Cancel button.
  Screen band ho ya dusra app khule, app process ko Android zinda rakhta hai.
* **Gallery mein seedha save:** video `Movies/NewsFrameStudio`, photo `Pictures/NewsFrameStudio` mein.
* **Share button:** seedha Facebook / WhatsApp wagairah mein bhejein.
* Back button se app band nahi hota (export beech mein na ruke).

> Dhyan: yeh project maine yahan compile ya phone par test nahi kiya (mere paas Android SDK nahi tha).
> Web wala hissa (bridge, save, share, cancel) ek nakli bridge ke saath test hua hai.
> Pehli baar build mein agar koi error aaye to error ka text bhej dein, main theek kar dunga.

## APK banane ke 2 tareeke

### Tareeka 1: GitHub se (kuch install karne ki zaroorat nahi)
1. github.com par free account banayein, **New repository** banayein (naam: NewsFrameStudio).
2. Is zip ko computer par extract karein. Repository ke page par **Add file > Upload files** dabayein
   aur extract kiye hue folder ke **andar ki saari files aur folders** (app, gradle, .github, build.gradle ...)
   drag-and-drop kar dein. Commit karein.
3. **Actions** tab kholein. "Build APK" chalega (5-10 minute). Khatam hone par us run par click karein,
   neeche **Artifacts > NewsFrameStudio-APK** download karein. Zip ke andar `app-debug.apk` hai.
4. APK ko phone par bhejein aur install karein (Settings mein "Install unknown apps" allow karna padega).

### Tareeka 2: Windows par Android Studio
1. Android Studio install karein, **Open** se is folder ko kholein, Gradle sync hone dein.
2. **Build > Build Bundle(s) / APK(s) > Build APK(s)**. APK `app/build/outputs/apk/debug/` mein milega.

## Pehli baar chalane par
* Notification permission **Allow** karein (Android 13+ mein poochta hai).
* Battery: Settings > Apps > News Frame Studio > Battery > **Unrestricted** rakhein.
* Fonts internet se aate hain (Google Fonts). Bina internet ke system font dikhega.

## Folder
* `app/src/main/assets/index.html` : webapp (isi file ko badal kar app update hota hai)
* `MainActivity.java` : WebView, file chooser, gallery save, share
* `ExportService.java` : export ke dauraan foreground service aur notification
