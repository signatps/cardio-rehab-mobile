# Macierz komend vs firmware EHO-MINI

Źródło: `restor_packets.c` → `is_valid_packet_len` + handlery w `bt_task_protocol.c`.

| CMD | Kierunek | msgLen | Payload | Uwagi z logów / firmware |
|-----|----------|--------|---------|---------------------------|
| `0x01` ACK | obie | **0** | — | ACK echo `seq` drugiej strony |
| `0x02` CmdError | device | ≥1 | kod + dane | `0x05` = za mało bufora wstecznego (+ dostępne sekundy) |
| `0x03` DevError | device | ≥1 | kod + dane | m.in. odpięte elektrody `0x01` |
| `0x04` Init | app | **10 lub 12** | ts4+Hz2+pulse1+clear1+count+leads | `app_init_flag`; drugi Init bez End → błąd |
| `0x05` ECG Offline | app | **>0** | back1+total1+uidLen+uid | Wymaga `total > back` oraz `rng_msgs >= back` |
| `0x06` Offline Done | device | 0 | — | po zbudowaniu SCP |
| `0x07` Get SCP Info | app | 0 | — | |
| `0x08` SCP Info | device | **7** | size4+pad1+crc2 | |
| `0x09` Get SCP | app | 0 | — | |
| `0x0A` SCP Fragment | device | >0 | chunk | ACK każdego fragmentu |
| `0x0B` SCP Done | app | 0 | — | kasuje SCP na urządzeniu |
| `0x0C` Get Pulse | app | **2** | interval u16 LE ×0.1s | `0` = stop + ostatni puls |
| `0x0D` Pulse Value | device | 1 | bpm | ACK (ustalenie ProPlus); timer `interval*100` ms |
| `0x0E/0x11` Online | — | — | — | **nieobsługiwane** na tym FW |
| `0x12` End | app | 0 | — | czyści `app_init_flag` (gdy ECG nie w toku) |
| `0x13` Get | app | 1 | id | bat/elektrody/IMEI/czas |
| `0x15` Offline Stop | app | 0 | — | przerywa offline |

## Lekcje z logu tabletu

1. Init 10 B → OK (`ACK COMMAND APP_INIT`).
2. Get Pulse 2 B → OK; stop `00 00` → OK; End → OK.
3. ECG Offline `back=5` zaraz po Init → `0x05 NOT ENOUGH … BUFFER` (bufor < 5 s).
4. Init ponownie bez End → `APP INIT ALREADY SET`.
