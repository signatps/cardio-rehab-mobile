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
3. App → `ECG Offline 0x05` (czas bufora wstecznego + czas pomiaru; bufor ≤ czas pomiaru; bufor krótszy niż czas od startu zapisu do Init)
4. Device → `ACK 0x01`, zbiera dane na SCP
5. Device → `ECG Offline Done 0x06`
6. App → `End 0x12`
7. Device → `ACK 0x01`
8. App → `Init 0x04` ponownie (restart zapisu)

Kod: `EcgOfflineCreateOrchestrator`.

Pobieranie SCP (`Get SCP Info` / `Get SCP` / fragmenty / `SCP Done`) — osobny scenariusz po reconnect; nadal wspierany w `OfflineSessionOrchestrator` (BPMN).
