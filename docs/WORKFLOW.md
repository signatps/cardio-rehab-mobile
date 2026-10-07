# Workflow — Cardio Rehab Mobile

Jak prowadzimy rozwój aplikacji Android współpracującej z **EHO-Mini (Plus)** przez Bluetooth.

## Gałęzie

| Gałąź | Rola |
|-------|------|
| `main` | Stabilna linia; chroniona przez CI |
| `develop` | Integracja bieżącej pracy (opcjonalnie) |
| `cursor/*` / feature branches | Praca nad issue |

Nazwy feature: `feat/<issue>-short-slug`, `fix/<issue>-short-slug`, `protocol/<issue>-short-slug`.

## Issues

1. Każda zmiana funkcjonalna startuje od issue (bug / feature / protocol).
2. Etykiety: `bug`, `enhancement`, `bluetooth`, `eho-mini`, `android`, `ci`, `docs`, `blocked`.
3. Milestone'y odpowiadają fazom produktu (patrz poniżej).
4. Issue `blocked` gdy czekamy na wsad / docs od Plus.

## Pull requesty

- Małe PR-y, jeden cel.
- Wymagane zielone **Android CI** (assemble + lint + unit tests).
- Link do issue: `Closes #N`.
- Opis wg szablonu w `.github/PULL_REQUEST_TEMPLATE.md`.

## Fazy (milestones)

1. **M0 — Bootstrap** — repo, CI, szkielet UI, uprawnienia BT
2. **M1 — Protocol ingest** — dokumentacja wsadu EHO-Mini, mapa UUID / komend
3. **M2 — Connect & control** — skan, para, sterowanie rejestracją
4. **M3 — Session UX** — przebieg sesji rehabilitacji na telefonie / tablecie
5. **M4 — Hardening** — tablety, regresje Android 10–16, release candidate

## Protokół EHO-Mini

Pliki protokołu lądują w `docs/protocol/` (po otrzymaniu wsadu).  
Warstwa transportowa w kodzie: `app/.../bluetooth/` (`EhoMiniDeviceClient`).

Dopóki nie ma wsadu:

- klient jest stubem,
- UI pokazuje stan „oczekiwanie na dokumentację protokołu”,
- issues protokołowe mają label `blocked` + `eho-mini`.

## CI

Workflow: `.github/workflows/android-ci.yml`

- trigger: `push` / `PR` na `main` i `develop`
- JDK 17, Gradle
- `assembleDebug`, `lintDebug`, `testDebugUnitTest`
- artefakty: APK debug, raport lint, wyniki testów

## Lokalny development

Zobacz [README.md](../README.md).

Target: **Android 10 (API 29) → Android 16 (API 36)**, telefony primarily, tablety secondary.
