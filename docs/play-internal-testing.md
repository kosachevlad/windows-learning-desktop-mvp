# Google Play Internal Testing

Після підтвердження облікового запису Google Play:

1. У корені проєкту активувати локальний toolchain:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
. .\scripts\Use-LocalToolchain.ps1
```

2. Перевірити код і зібрати release AAB:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug bundleRelease
```

Файл буде створено тут:

```text
app\build\outputs\bundle\release\app-release.aab
```

Для завантаження у внутрішній тест потрібен підписаний release AAB. Ключ підпису не зберігаємо в Git. Його створюємо один раз локально через Android Studio або `keytool`, після чого налаштовуємо signing config у локальному, некомітованому файлі.

У Play Console відкрийте `Test and release` → `Internal testing`, створіть тестовий реліз, завантажте AAB і додайте Google-акаунт тестувальника. Після цього відкрийте посилання тестування на Chromebook.

Якщо потрібно лише швидко перевірити файл через Play без release-підпису, використовуйте `Test and release` → `Internal app sharing` і завантажте `app-debug.aab` після команди `bundleDebug`.
