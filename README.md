# Clipfang

Android-App (ab Android 10), die Videos und Fotos aus **öffentlichen** Instagram-Beiträgen speichert.
Sie öffnet Instagrams Einbettungsseite in einer unsichtbaren WebView und liest daraus die Medienadresse.
Kein Konto, keine Werbung, kein eigener Server.

Nur für eigene Inhalte oder mit Erlaubnis der Urheber. Automatisiertes Abrufen kann gegen Instagrams
Nutzungsbedingungen verstoßen. Nicht mit Instagram oder Meta verbunden.

## Bauen

    gradlew :app:assembleRelease

Das Ergebnis liegt in `app/build/outputs/apk/release/`. Bei zu langem Pfad unter Windows `-PbuildRoot=C:/tmp/clipfang` anhängen.
Lokal gebaute APKs tragen die Version „dev“ und den Debug-Schlüssel, sind also nur zum Ausprobieren gedacht.

## Testen

    gradlew :app:testDebugUnitTest
    adb reverse tcp:8099 tcp:8099
    node tools/mock-server.js       # dann die Variante assembleMock im Emulator benutzen

Ändert Instagram seine Einbettungsseite, muss meist nur `app/src/main/assets/extract.js` angepasst werden.

## Release

Jeder Pull Request und jeder Push auf `main` wird automatisch getestet und gebaut, aber nicht veröffentlicht.
Ein Release entsteht nur durch einen Tag auf `main`:

    git switch main
    git pull
    git tag v1.0.0
    git push origin v1.0.0

GitHub baut daraus die signierte APK und legt ein Release mit APK und Prüfsumme an. Versionsname und
`versionCode` kommen aus dem Tag (`v1.2.3` wird zu 10203). Einen Tag nie verschieben, sondern einen neuen setzen:
Fehler behoben → letzte Zahl, neue Funktion → mittlere Zahl, großer Umbau → erste Zahl.

Einmalig vorher den Signaturschlüssel anlegen (fragt nach einem neuen Passwort und hinterlegt beides als GitHub-Secret):

    powershell -File tools/setup-signing.ps1

Die Schlüsseldatei und das Passwort danach sichern. Ohne sie sind keine Updates der installierten App mehr möglich.
