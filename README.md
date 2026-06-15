# Game of What — valós idejű coop aréna

Android játék (Kotlin + Jetpack Compose), amiben **többen játszhattok együtt online**:
fentről nézett túlélő-aréna, ahol közösen mozogtok, automatán lőtök, és hullámokban
érkező szörnyek ellen harcoltok. A játékosok **szobakóddal** csatlakoznak egymáshoz
Firebase Realtime Database-en keresztül.

> **Offline próba:** A *Gyakorlás* mód Firebase beállítás **nélkül** is azonnal
> játszható (egyjátékos), így rögtön ki tudod próbálni a játékmenetet.

## Játékmenet

- **Mozgás:** bal alsó virtuális joystick.
- **Lövés:** automata — a legközelebbi szörnyre céloz és tüzel.
- **Cél:** közösen túlélni minél több hullámot. Érintkezésnél sebződsz; ha az életerőd
  elfogy, kiesel. A meccs akkor ér véget, ha **minden játékos** kiesett.
- **Coop:** közös pontszám és hullámszámláló mindenkinek.

## Technikai felépítés

| Réteg | Megoldás |
|------|----------|
| UI / menük | Jetpack Compose (Material 3) |
| Renderelés | Compose `Canvas` + `withFrameNanos` game loop (~60 fps) |
| Hálózat | Firebase Realtime Database (online) / in-memory loopback (offline) |
| Hálózati modell | kliens-vezérelt játékosok, **host-vezérelt** szörnyek/hullámok |

A `GameNetwork` interfész mögött két implementáció van
(`FirebaseGameNetwork`, `LocalGameNetwork`), így a játékmenet kódja online és offline
módban teljesen azonos.

### Adatbázis-fa (Realtime Database)

```
rooms/{KÓD}/
  meta            -> { phase, hostId, wave, score }
  players/{id}    -> { id, name, x, y, angle, hp, maxHp, alive, colorIndex, score }
  enemies/{id}    -> { id, x, y, hp, maxHp, type }   # a host írja
  hits/{pushId}   -> { enemyId, damage }             # kliensek pusholják, a host feldolgozza
```

## Online mód bekapcsolása (Firebase)

1. Hozz létre egy projektet a [Firebase Console](https://console.firebase.google.com/)-ban.
2. Adj hozzá egy **Android appot** `com.gameofwhat.arena` csomagnévvel.
3. Töltsd le a `google-services.json` fájlt, és tedd az **`app/`** mappába.
   (A build automatikusan érzékeli, és bekapcsolja a Google Services plugint.)
4. A Firebase Console-ban kapcsold be a **Realtime Database**-t.
5. Fejlesztéshez használhatod a `database.rules.json` mintát (lásd a repóban).
   **Éles használatra szigorítsd a szabályokat!**

A `google-services.json` szándékosan **nincs** verziókezelve (lásd `.gitignore`), mert
projektenként egyedi.

## Build és futtatás

- Nyisd meg a projektet **Android Studio**-ban (Giraffe vagy újabb), vagy:

```bash
./gradlew assembleDebug          # APK build
./gradlew installDebug           # telepítés csatlakoztatott eszközre/emulátorra
```

Követelmények: Android Studio + Android SDK (compileSdk 35), JDK 17, minSdk 24.

## Hogyan játsszatok együtt

1. Az egyik játékos: **Szoba létrehozása** → megjelenik a 4 betűs kód.
2. A többiek: írják be a kódot → **Csatlakozás**.
3. A host megnyomja a **Játék indítása** gombot — mindenki egyszerre kezd.

## Tudnivalók / továbbfejlesztés

- A Realtime Database késleltetése miatt a szinkron „lazán konzisztens" (casual coop-ra
  szabva). Versenyszintű pontossághoz dedikált, hiteles szerver (pl. WebSocket + tickrate)
  kellene.
- Lehetséges bővítések: újraéledés hullámok között, power-upok, többféle pálya, hangok,
  ranglista (Firestore), barát-meghívó linkek.
