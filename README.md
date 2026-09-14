# Windows Learning Desktop

Офлайн Android-застосунок для Chromebook: навчальна імітація робочого столу Windows для учнів 1–5 класів. Kotlin + Jetpack Compose, Android 8.0+ (API 26). Файловий домен і сховище Room/приватних файлів підключені до першої інтерактивної версії Провідника та Блокнота.

## Локальна збірка у Windows

Потрібні JDK 17, Android SDK Platform 35, Build Tools 34.0.0 та Platform Tools. Gradle 8.9 завантажує включений Wrapper із перевіркою SHA-256. Перша збірка потребує інтернету; робота самого застосунку запланована повністю офлайн.

Для вже підготовленої локальної копії відкрийте PowerShell у каталозі репозиторію:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
. ./scripts/Use-LocalToolchain.ps1
./gradlew.bat --no-daemon assembleDebug testDebugUnitTest assembleDebugAndroidTest
```

Скрипт використовує інструменти в `../work/toolchain`, налаштовує змінні лише для поточного PowerShell і не змінює системний PATH. Ці завантаження не входять у Git. Повторюйте dot-source після відкриття нового термінала.

На іншому комп'ютері встановіть JDK 17 та Android SDK через Android Studio або офіційний sdkmanager, задайте `JAVA_HOME` і `ANDROID_HOME`, додайте `JAVA_HOME/bin` і `ANDROID_HOME/platform-tools` до PATH поточного термінала. Замість ANDROID_HOME можна вказати `sdk.dir=C:/path/to/Android/Sdk` у локальному, ігнорованому Git файлі `local.properties`. У Android Studio також виберіть JDK 17 у Gradle settings. Локальний допоміжний скрипт тоді не потрібний.

Команди вище збирають застосунок та APK UI-тестів і запускають тести файлового домену та збереження. Деталі: [етап 2 — файлові операції](docs/stage-2-testing.md), [етап 3 — Room і збереження](docs/stage-3-testing.md), [етап 4 — інтерактивний сценарій](docs/stage-4-testing.md), [етап 5 — файлові операції та Кошик](docs/stage-5-testing.md).

Результати:

- Застосунок: `app/build/outputs/apk/debug/app-debug.apk`.
- UI-тести: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.

## Як тестувати етап 1

1. Підключіть Android-пристрій API 26+ із USB debugging та підтвердьте авторизацію на пристрої, або запустіть емулятор із Android Studio. Для Chromebook використовуйте доступний на ньому спосіб ADB debugging; на керованому шкільному пристрої його доступність залежить від адміністратора.
2. Перевірте `adb devices -l`: один цільовий пристрій має бути у стані `device`, не `unauthorized` чи `offline`. Якщо їх кілька, для ручних команд використайте `adb -s SERIAL ...`.
3. Встановіть і запустіть APK:

```powershell
adb install -r ./app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n ua.school.windowsdesktop/.MainActivity
```

4. Очікуйте синій екран, написи «Цей ПК», «Мої файли», «Блокнот», Paint, «Кошик» та темну панель унизу. Закрийте та відкрийте застосунок — він має запускатися без помилки. Значки поки не натискаються, годинник показує статичні 10:30.
5. Запустіть автоматичний launch-тест на підключеному пристрої:

```powershell
./gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=ua.school.windowsdesktop.AppLaunchTest'
```

Очікувано: BUILD SUCCESSFUL, 1 тест пройдено. HTML-звіт: `app/build/reports/androidTests/connected/debug/index.html`. Цей тест перевіряє запуск Activity та семантичну мітку робочого столу, а не майбутні файлові дії.

## План

Після перевіреної збірки: домен файлів і збереження → Провідник та Блокнот → повний shell і UA/EN → Paint → пілот на Chromebook. Початкова специфікація та план містяться у `docs/superpowers/`.

Сумісність інструментів: [Android Gradle Plugin 8.7](https://developer.android.com/build/releases/agp-8-7-0-release-notes). Завантаження SDK: [Android Developers](https://developer.android.com/studio#command-line-tools-only).
