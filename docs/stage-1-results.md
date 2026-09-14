# Етап 1 — локальна збірка

Перевірено 2026-09-13 на Windows, гілка `feature/windows-learning-desktop-mvp`.

Додано Gradle Wrapper 8.9 з перевіркою SHA-256, AndroidX-конфігурацію, узгоджені Java/Kotlin targets 17, явні залежності instrumentation runner та AndroidX JUnit. Із launch-тесту прибрано некоректний import assertExists і невикористаний import ActivityScenarioRule. Додано README і скрипт підключення локального інструментарію до поточного PowerShell.

Локально у `../work/toolchain` підготовлено Temurin JDK 17, SDK Platform 35, Build Tools 34.0.0 та Platform Tools. Завантаження JDK, command-line tools і wrapper JAR перевірені за SHA-256. Інструментарій не входить до репозиторію.

Результат `./gradlew.bat --no-daemon assembleDebug testDebugUnitTest assembleDebugAndroidTest`: **BUILD SUCCESSFUL**, 68 виконаних задач. Зібрано APK застосунку та APK UI-тестів. `apksigner verify --verbose` підтвердив debug-підпис APK (v2).

На момент етапу 1 `testDebugUnitTest` — **NO-SOURCE**. UI-тест скомпільований, але **не запускався**: `adb devices -l` не показав жодного підключеного пристрою. Після передачі APK користувач підтвердив успішний ручний запуск на Android. Перевірка саме на Chromebook залишається відкритою.

Неблокувальні повідомлення першої збірки: Android analytics не зміг записати налаштування в домашній каталог; бібліотеку `libandroidx.graphics.path.so` включено без видалення debug symbols. Доданий наприкінці етапу ANDROID_SDK_HOME конфліктував із ANDROID_USER_HOME; під час етапу 2 скрипт виправлено, він використовує ANDROID_USER_HOME та очищає застарілий ANDROID_SDK_HOME лише в поточному терміналі.

Наступний етап: домен навчальної файлової системи та тести створення, копіювання, перейменування, Кошика і відновлення.
