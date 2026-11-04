# Launcher Leggero (Android TV)

Launcher minimale: una griglia di icone, nessuna libreria, nessuna rete, nessun servizio in background.

## Compilare

1. Apri la cartella in Android Studio (richiede JDK 17).
2. Attendi la sincronizzazione Gradle.
3. Menu Build > Build APK(s), oppure da terminale: `./gradlew assembleRelease`
   L'APK si trova in `app/build/outputs/apk/release/`.

## Installare sulla TV

Attiva il debug USB / di rete sulla TV (Impostazioni > Preferenze dispositivo > Informazioni,
tocca 7 volte "Build", poi Opzioni sviluppatore), poi dal PC:

    adb connect IP_DELLA_TV:5555
    adb install -r app-release.apk

Premi il tasto Home sul telecomando e scegli "Launcher Leggero" (Sempre).

## Disattivare il launcher originale (facoltativo)

Trova il pacchetto del launcher attuale:

    adb shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME

Disattivalo solo per l'utente (reversibile):

    adb shell pm disable-user --user 0 NOME.PACCHETTO

Per ripristinarlo:

    adb shell pm enable NOME.PACCHETTO

Tieni sempre installato questo launcher prima di disattivare l'altro, altrimenti la TV resta senza Home.

## Uso

- OK: avvia l'app
- MENU / INFO / tasto *: informazioni dell'app (cancella cache, disinstalla)
- Indietro: ignorato

## Compilare con GitHub (senza installare nulla)

1. Crea un nuovo repository su GitHub e carica tutto il contenuto di questa cartella
   (compresa la cartella nascosta `.github`).
2. Apri la scheda Actions, scegli "Build APK" e premi "Run workflow"
   (parte da solo anche a ogni push).
3. A fine lavoro (circa 3-5 minuti) apri l'esecuzione e scarica l'artefatto
   `launcher-leggero-apk`: dentro c'e' l'APK.
