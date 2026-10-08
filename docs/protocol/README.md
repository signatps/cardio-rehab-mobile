# Protokół EHO-Mini / Silvermedia

Warstwa techniczna komunikacji Bluetooth z rejestratorem EKG (Plus / Pro-Plus / Silvermedia framing).

**Status:** wsad przeanalizowany; codec + maszyna sesji offline w kodzie. GUI / flow ekranów — poza zakresem tej fazy.

## Wejście

1. Specyfikacja ramek → [`eho-mini.md`](eho-mini.md)
2. Scenariusze 2/3 → [`scenarios.md`](scenarios.md)
3. Surowce → [`raw/`](raw/)
4. Diagramy → [`diagrams/`](diagrams/)

## Kod

`pl.cardioscp.rehab.bluetooth.protocol`:

- `Crc16Ccitt`, `FrameCodec`, `FrameStreamParser`
- typy / payloady komend (`Init` domyślnie **8 B** — kompatybilność Pro-PLUS)
- `PulseScenarioOrchestrator` — scenariusz 2
- `EcgOfflineCreateOrchestrator` — scenariusz 3
- `OfflineSessionOrchestrator` — BPMN z transferem SCP

Transport BT: bonded `PRO_PLUS_ECG_`****** + SPP. `Init` msgLen 10/12; `ScpInfo` 7 B — wg firmware.
