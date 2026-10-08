# Cardio Rehab Mobile

Aplikacja Android do **domowej rehabilitacji kardiologicznej (CardioSCP)** współpracująca z rejestratorem EKG **EHO-Mini (Plus)** przez Bluetooth.

**Zakres urządzeń:** Android **10–16** (API 29–36), głównie telefony, tablety wspierane drugorzędnie.

Repozytorium: [signatps/cardio-rehab-mobile](https://github.com/signatps/cardio-rehab-mobile)

## Status

Działa połączenie **Bluetooth Classic SPP** z `PRO_PLUS_ECG_******` oraz **BLE** do ciśnienia/wagi (port z mobile-DSD).

**Sesja rehabilitacji** (po SPP): EKG spoczynkowe → ciśnienie BLE → waga BLE (gdy HF) → ankieta kwalifikacyjna → trening sekwencyjny interwałowy (domyślnie 3×1 min wysiłek / 1 min odpoczynek, akwizycje EKG wg schematu + 2 min po) → podsumowanie z przeglądem EKG z sesji.

Scenariusze ręczne:

1. **Puls** (`Get Pulse` → `Pulse Value`) — BPM na ekranie (wymaga podłączonych elektrod; przy odpięciu urządzenie raportuje 0)
2. **ECG Offline** — zapis pliku SCP na urządzeniu
3. **Pobierz cały plik SCP** — złożenie wszystkich fragmentów `0x0A` w jeden `.scp`, zapis lokalny i podgląd przebiegu
4. **Przeglądarka EKG** — papier milimetrowy z CardioSCP-mobile (`EcgPaper`: cecha 1 mV, 25/50 mm/s, 5/10/20 mm/mV, kolory odprowadzeń, PQRST) oraz analiza (`EcgAnalysisEngine`: QRS, HR, PQ/QRS/QT/QTc, ST, oś)

## Stack

- Kotlin, Jetpack Compose, Material 3
- Min SDK 29 / Target & Compile SDK 36
- Gradle Version Catalog + Android Gradle Plugin 8.10
- GitHub Actions: build, lint, unit tests

## Uruchomienie lokalne

Wymagania:

- JDK 17+
- Android SDK (platform 36, build-tools)
- Android Studio Ladybug+ (zalecane) lub CLI

```bash
# skonfiguruj SDK
cp local.properties.example local.properties
# edytuj sdk.dir=

chmod +x gradlew
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew installDebug   # z podłączonym telefonem / emulatorem
```

APK debug: `app/build/outputs/apk/debug/`.

## Struktura

```
app/                 # aplikacja Android
docs/WORKFLOW.md     # branche, issues, milestones, CI
docs/protocol/       # mapa protokołu EHO-Mini (do wypełnienia)
.github/workflows/   # Android CI
.github/ISSUE_TEMPLATE/
```

Warstwa urządzenia: `bluetooth/` (EHO-Mini SPP), `ble/` (ciśnienie/waga z mobile-DSD), `session/` (przepływ sesji rehabilitacji).

## Workflow

Zobacz [docs/WORKFLOW.md](docs/WORKFLOW.md). Issues i milestone'y prowadzimy na bieżąco na GitHubie.

## Licencja / medycyna

Oprogramowanie wspomagające rehabilitację — **nie zastępuje** oceny klinicznej. Integracja z wyrobem medycznym wymaga zgodności z dokumentacją producenta EHO-Mini.
