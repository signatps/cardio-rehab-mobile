# Protokół EHO-Mini (Plus)

Ten katalog zbiera dokumentację komunikacji Bluetooth z rejestratorem EKG **EHO-Mini**.

## Status

**Oczekiwanie na wsad / dokumentację od producenta (Plus).**

Po otrzymaniu materiałów uzupełnimy m.in.:

- typ łącza (Classic SPP vs BLE GATT),
- UUID serwisów / charakterystyk (BLE) lub UUID kanału RFCOMM,
- framing pakietów, endianness, CRC,
- komendy start/stop rejestracji, status baterii, streaming próbek,
- wymagania parowania i bezpieczeństwa.

## Jak dodawać materiały

1. Wrzuć surowy wsad / PDF / logi do `docs/protocol/raw/` (lub załącznik w issue).
2. Otwórz issue z szablonem **Protocol / device task**.
3. Zaktualizuj `eho-mini.md` (mapa komend) i odblokuj issues z labelem `blocked`.

Kod implementujący transport: `pl.cardioscp.rehab.bluetooth`.
