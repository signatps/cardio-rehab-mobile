# EHO-MINI firmware notes (MC60-CA / OpenCPU)

Źródło: archiwum kodu urządzenia (`custom/`, Quectel MC60-CA, firmware `01r03`).

## Walidacja ramek (`restor_packets.c`)

| CMD | Nazwa | Dozwolony `msgLen` |
|-----|-------|--------------------|
| `0x01` | ACK | 0 |
| `0x04` | Init | **10 lub 12** |
| `0x05` | ECG Offline | ≠ 0 |
| `0x07` | Get SCP Info | 0 |
| `0x09` | Get SCP | 0 |
| `0x0B` | SCP Done | 0 |
| `0x0C` | Get Pulse | **2** |
| `0x0E` | ECG Online | 0 |
| `0x11` | ECG Online Stop | 0 |
| `0x12` | End | 0 |
| `0x13` | Get | 1 |
| `0x15` | ECG Offline Stop | 0 |

CRC: `calc_block_crc_BT` od bajtu `type` (bez `0x80`), init `0xFFFF` — zgodne z CRC-CCITT w app.

## Init (`bt_task_protocol.c` → `app_init`)

Po ACK startuje ECG. Przy `count != 3` firmware ustawia I/II + Vx z pierwszego bajtu danych odprowadzeń.

## SCP Info

`msgLen=7`: size u32 + `0x00` + crcfile u16.

## BT name

Log: `IMEI 861359034740579` → nazwa `PRO_PLUS_ECG_740579` (ostatnie 6 cyfr).
