# Protokół EHO-Mini / Silvermedia

Warstwa techniczna komunikacji Bluetooth z rejestratorem EKG (Plus / Pro-Plus / Silvermedia framing).

**Status:** wsad przeanalizowany; codec + maszyna sesji offline w kodzie. GUI / flow ekranów — poza zakresem tej fazy.

## Wejście

1. Specyfikacja ramek → [`eho-mini.md`](eho-mini.md)
2. Surowce → [`raw/`](raw/)
3. Diagramy → [`diagrams/`](diagrams/)

## Kod

`pl.cardioscp.rehab.bluetooth.protocol`:

- `Crc16Ccitt`, `FrameCodec`, `FrameStreamParser`
- typy / payloady komend
- `OfflineSessionOrchestrator` — flow BPMN (Init, parallel ECG+Pulse, fragmenty SCP, End)

Transport BT (socket): skan na razie bez filtra nazwy; SPP UUID do ustalenia. `ScpInfo` = 4 B.
