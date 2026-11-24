# Clipfang

Android-App (ab Android 10), die Videos und Fotos aus **öffentlichen** Instagram-Beiträgen speichert.
Sie öffnet Instagrams Einbettungsseite in einer unsichtbaren WebView und liest daraus die Medienadresse.
Kein Konto, keine Werbung, kein eigener Server.

Nur für eigene Inhalte oder mit Erlaubnis der Urheber. Automatisiertes Abrufen kann gegen Instagrams
Nutzungsbedingungen verstoßen. Nicht mit Instagram oder Meta verbunden.

## Bauen

    gradlew :app:assembleRelease

Das Ergebnis liegt in `app/build/outputs/apk/release/`. Bei zu langem Pfad unter Windows `-PbuildRoot=C:/tmp/clipfang` anhängen.
Die APK ist mit dem lokalen Debug-Schlüssel signiert (nicht für den Play Store geeignet).

## Testen

    gradlew :app:testDebugUnitTest
    adb reverse tcp:8099 tcp:8099
    node tools/mock-server.js       # dann die Variante assembleMock im Emulator benutzen

Ändert Instagram seine Einbettungsseite, muss meist nur `app/src/main/assets/extract.js` angepasst werden.
