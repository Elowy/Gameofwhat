# Game of What — középkori coop aréna

Android játék (Kotlin + Jetpack Compose), amiben **többen játszhattok együtt online**:
fentről nézett középkori túlélő-aréna, ahol lovagként közösen mozogtok, automatán
**nyílvesszőkkel** lőtök, és hullámokban érkező **szörnyek** (koboldok, farkasok,
ogrék) ellen harcoltok. A játékosok **szobakóddal** csatlakoznak egymáshoz Firebase
Realtime Database-en keresztül, és **több pálya** közül választhattok.

> **Offline próba:** A *Gyakorlás* mód Firebase beállítás **nélkül** is azonnal
> játszható (egyjátékos), így rögtön ki tudod próbálni a játékmenetet.

## Játékmenet

- **Mozgás:** bal alsó virtuális joystick.
- **Lövés:** automata íjászat — a legközelebbi szörnyre céloz és nyilaz.
- **Szörnyek:** kobold (alap), farkas (gyors), ogre (erős, lassú, sok életerő).
- **Akadályok:** a pályán oszlopok / fák / ládák / sziklák blokkolják a mozgást és a
  nyilakat — bújj fedezékbe!
- **Cél:** közösen túlélni minél több hullámot. Érintkezésnél sebződsz; ha az életerőd
  elfogy, kiesel. A meccs akkor ér véget, ha **minden játékos** kiesett.
- **Coop:** közös pontszám és hullámszámláló mindenkinek.

## Pályák

A host (online) vagy te (offline) a kezdés előtt választhattok pályát:

| Pálya | Hangulat |
|-------|----------|
| **Várudvar** | kőpadló, négy sarokoszlop |
| **Sötét erdő** | füves talaj, szétszórt fák |
| **Tömlöc** | sötét kő, blokkokból álló sávok |
| **Lávabarlang** | izzó sziklák, lávaszín kiemelések |

Új pályát a `Maps.kt`-ben tudsz hozzáadni (név, színek, akadályok listája).

## Kasztok

A kezdés előtt mindenki választ egy kasztot (offline a menüben, online a lobbyban):

| Kaszt | Stílus |
|-------|--------|
| **Vadász** | gyors, hosszú hatótávú íjász, kevesebb életerő |
| **Harcos** | közelharci suhintás (több ellenfelet talál), sok életerő |
| **Paládin** | páncélos tank, lassú öngyógyulással |
| **Pap** | gyógyító aura a közeli társaknak, gyengébb sebzés |
| **Boszorkány** | átütő, nagy sebzésű mágia, törékeny |

A kasztokat a `Classes.kt`-ben lehet hangolni / bővíteni.

### Aktív képességek

Minden kaszthoz tartozik egy **aktív képesség** (a játékban jobb alul lévő gombbal,
cooldownnal):

| Kaszt | Képesség | Hatás |
|-------|----------|-------|
| **Vadász** | Nyílzápor | nyílvesszők minden irányba (átütő) |
| **Harcos** | Forgószél | nagy sebzésű AoE suhintás maga körül |
| **Paládin** | Pajzs | pár másodperc sebezhetetlenség |
| **Pap** | Szentfény | azonnali köris gyógyítás magának + a közeli társaknak |
| **Boszorkány** | Robbanás | mágikus nóva, AoE sebzés maga körül |

A gomb a hátralévő cooldownt is mutatja; a pap gyógyítását a `PlayerState.healPulse`
mező szinkronizálja (a hatókörben lévők saját magukat gyógyítják).

## Power-upok és szintek

- **Power-upok:** a szörnyek eséllyel dobnak felvehető tárgyat — **gyógyítás**,
  **sebzésnövelő**, **gyorsaság** és **gyorstűz** (az utóbbi három időleges buff).
- **XP és szintek:** a szörnyek XP-t adnak; a csapat **közös szintet** lép (max **10**).
  Minden szint több életerőt és sebzést ad mindenkinek. A szintet és XP-t a host számolja,
  és a `RoomMeta`-n keresztül szinkronizálja.

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
  meta            -> { phase, hostId, wave, score, mapId, xp, level }
  players/{id}    -> { id, name, x, y, angle, hp, maxHp, alive, colorIndex, score, classId, healPulse }
  enemies/{id}    -> { id, x, y, hp, maxHp, type }   # a host írja
  powerups/{id}   -> { id, x, y, type }              # a host írja
  hits/{pushId}   -> { enemyId, damage }             # kliensek pusholják, a host feldolgozza
  pickups/{pushId}-> { id }                          # felvett power-up, a host eltávolítja
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
