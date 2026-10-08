# Scenariusze protokołu (Pro-PLUS / Silvermedia)

Źródło: dokument testowy scenariuszy SPP (zrzut w `diagrams/scenarios-2-3.png`).

Wspólne: połączenie SPP jest już zestawione zanim lecą komendy.

## Scenariusz 2 — pomiar i transmisja pulsu

1. App → `Init 0x04` (sampling + czas średniej pulsu)
2. Device → `ACK 0x01` (start zapisu pulsu/EKG w pamięci)
3. App → `Get Pulse 0x0C` z interwałem **> 0** (×0.1 s)
4. Device → `ACK 0x01`, potem cykliczne `Pulse Value 0x0D` (średnia z okna Init)
5. App → `Get Pulse 0x0C` z interwałem **0** (stop ciągłego)
6. Device → `ACK 0x01` + ostatnia wartość pulsu, koniec transmisji
7. App → `End 0x12`
8. Device → `ACK 0x01`

Kod: `PulseScenarioOrchestrator`.

## Scenariusz 3 — pomiar EKG Offline + plik SCP (utworzenie)

Opis: wykonanie pomiaru EKG i utworzenie pliku SCP; **wysłanie SCP** następuje po ponownym zestawieniu połączenia (osobny flow Get SCP).

1. App → `Init 0x04`
2. Device → `ACK 0x01` (start zapisu)
3. App → `ECG Offline 0x05` — firmware: **`total > lookback`** oraz **`rng_msgs >= lookback`**
   - zaraz po Init bufor ≈ 0 → używamy **`lookback=0`, `total=10`**
   - `lookback=5` zaraz po Init → CmdError `0x05 NOT ENOUGH … BUFFER` (log tabletu)
4. Device → `ACK 0x01`, zbiera dane na SCP
5. Device → `ECG Offline Done 0x06` (po ~`total-lookback` s)
6. App → `End 0x12` (czyści `app_init_flag`)
7. Device → `ACK 0x01`
8. App → `Init 0x04` ponownie

Przy błędzie Offline app **musi** wysłać `End`, inaczej kolejne Init → `APP INIT ALREADY SET`.

Kod: `EcgOfflineCreateOrchestrator`.

Pobieranie SCP — osobny flow po reconnect (`OfflineSessionOrchestrator`).
