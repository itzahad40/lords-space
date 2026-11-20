# Lord's-Space: APK banane ke steps (Windows)

Ye project purana toolchain use karta hai (Gradle 7.0.2, Android Gradle Plugin 7.0.2, Kotlin 1.5.21).
Isliye **JDK 11** chahiye. JDK 17/21 par build fail hoga ("Unsupported class file major version").

## 1. Ek baar ka setup

1. Android Studio install karo (koi bhi naya version chalega).
2. Android Studio > Settings > Languages & Frameworks > Android SDK > SDK Platforms:
   **Android 13 (API 33)** tick karo.
3. Wahi screen > SDK Tools tab, "Show Package Details" tick karke ye install karo:
   - Android SDK Build-Tools **31.0.0**
   - NDK (Side by side) **21.4.7075529**
   - CMake **3.10.2**
   - Android SDK Platform-Tools (adb)
4. JDK 11: Android Studio > Settings > Build, Execution, Deployment > Build Tools > Gradle >
   "Gradle JDK" > "Download JDK..." > Version 11 (Temurin) chuno.

## 2. Project kholna

1. ZIP ko extract karo (path chhota rakho, jaise `C:\LordsSpace`, spaces ke bina).
2. Android Studio > Open > `LordsSpace` folder.
3. Agar Studio "Update Android Gradle Plugin" puchhe to **"Remind me never"** dabao (update mat karo).
4. Gradle sync chalne do. Pehli baar internet se libraries download hongi (kuch minute).

## 3. APK build

Android Studio se: Build > Build Bundle(s) / APK(s) > Build APK(s).

Ya command line se (project folder mein, Command Prompt):

    gradlew.bat :app:assembleDebug

APK yahan milega:

    app\build\outputs\apk\debug\app-debug.apk

(Release APK chahiye to `gradlew.bat :app:assembleRelease`, ye bhi debug key se sign hota hai.)

## 4. Phone par install

1. Phone mein Developer options > USB debugging on karo, USB se jodo.
2. Command Prompt:

       adb install -r app\build\outputs\apk\debug\app-debug.apk

   Ya APK file phone mein copy karke khol lo.
3. Agar "package conflicts / signatures do not match" aaye, to phone se purani Waxmoon/Lord's-Space app
   uninstall karo aur dobara install karo.
4. "Add app" ya clone karte waqt app 32-bit helper (assist) install karne ko bol sakti hai. Confirm kar do.

## 5. Agar build fail ho

- **"Could not find top.canyie.pine:core:0.3.0"**: Maven Central par "top.canyie.pine core" search karo,
  jo naya 0.3.x version mile wo `app/build.gradle` mein badal do, phir sync karo.
- **"NDK not configured" ya "CMake not found"**: step 1.3 ke NDK 21.4.7075529 aur CMake 3.10.2 install karo.
- **"Unsupported class file major version"**: Gradle JDK 11 nahi hai (step 1.4).
- **Sync mein jcenter ka error**: dobara sync karo, ya thodi der baad try karo (jcenter read-only hai).
- Koi aur error aaye to poora error text (pehli 'error:' wali lines) mujhe bhej do.

## 6. Pehli baar test kaise kare

1. App kholo: Loader, phir Home dikhega.
2. "+ Add app" > Telegram (ya koi app) select karo > CLONE.
3. Home se Telegram "Open" karo, voice chat mein jao.
4. Fight Mode (mic button) kholo, ON karo, Gain badhao. Level meter hilna chahiye.
5. `adb logcat -s FightMode` chalao. "AudioRecord.read hooks installed" dikhna chahiye.
   Agar nahi dikhe ya hook error aaye, wo log mujhe bhej do.

## 7. Android 14, 15, 16 par test karna

Kya pakka hai aur kya nahi:
- Lord's-Space ka apna code (Loader, Home, Add Apps, Fight Mode) Android 6 se upar sab par chalne ke liye bana hai,
  aur apni native library 16 KB memory pages ke liye align ki gayi hai (Android 15/16 ke naye phones).
- App ka asli engine (`moon.jar`, `libl.so`, `libpl.so`) band source hai. Uske changelog mein sirf **Android 14 tak**
  ka support likha hai, aur uski libraries 4 KB aligned hain. Android 15/16 par ye chalega ya nahi, ye
  sirf phone par test karke pata chalega, mere paas isse badalne ka tareeka nahi.
- Fight Mode ka mic hook Pine library par chalta hai. Pine naye Android par kaam karta hai ya nahi, wo bhi
  phone par hi pata chalega.

Har phone par ye test karo:
1. App install karo aur kholo. Loader aur Home aana chahiye. (Nahi aaya = engine us Android par nahi chal raha.)
2. "+ Add app", koi app select karo, CLONE. Phir Home se "Open". App space ke andar khulni chahiye.
3. Mic button dabao (Fight Mode screen). Sabse upar ek line dikhegi:
   - hari line "Mic hook: active in ... (API xx)" = hook lag gaya.
   - laal line "Mic hook: FAILED ... on API xx" = us Android par hook nahi laga (app phir bhi chalegi,
     bas Fight Mode asar nahi karega).
   - "not seen yet" = abhi koi app Home se nahi kholi.
4. Voice chat mein jao, Fight Mode ON karo. Level meter hilna chahiye.
5. Agar kuch na chale: `adb logcat -d > log.txt` chalao aur log.txt bhej do, saath mein phone ka model aur Android version.

Phone ka Android version: Settings > About phone > Android version.
