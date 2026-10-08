# EHO-Mini / Silvermedia — mapa protokołu Bluetooth

Źródła (wsad techniczny, **bez GUI**):

| Plik | Rola |
|------|------|
| [`raw/Protokol_komunikacji_Silvermedia_EKG_v0.2.docx`](raw/Protokol_komunikacji_Silvermedia_EKG_v0.2.docx) | Specyfikacja ramek (v0.2, 15.12.2017) — **źródło prawdy dla kodowania** |
| [`raw/Podsumowanie_ustalen_ProPlus_2017-07-12.docx`](raw/Podsumowanie_ustalen_ProPlus_2017-07-12.docx) | Ustalenia Pro-Plus (12.07.2017) — flow sesji, starsze nazwy komend |
| [`diagrams/bpmn-session-flow.png`](diagrams/bpmn-session-flow.png) | BPMN: Init → równolegle ECG Offline + Pulse → End |
| [`raw/*.scp`](raw/) | Przykładowe pliki SCP-ECG (ISO 11073-91064 / EN 1064) |

Transport: **Bluetooth Classic SPP** (strumień bajtów RFCOMM). Parowanie w **menu Bluetooth Androida**; aplikacja łączy się z urządzeniem już sparowanym (bonded), filtrując po nazwie. Socket: well-known SPP UUID `00001101-0000-1000-8000-00805F9B34FB` — bez osobnego UUID od producenta. Model **Master–Slave**: aplikacja steruje, urządzenie nie inicjuje akcji biznesowych (poza błędami asynchronicznymi `0x03` i danymi po komendzie).

Kod: `app/src/main/java/pl/cardioscp/rehab/bluetooth/protocol/`.

---

## Budowa ramki

Wszystkie wartości wielobajtowe: **little-endian**.

| Offset | Pole | Opis |
|--------|------|------|
| 0 | `SOF` | zawsze `0x80` |
| 1 | `type` | typ ramki |
| 2–3 | `seq` | numer paczki (`ushort` LE) |
| 4–5 | `msgLen` | długość payloadu (`ushort` LE) |
| 6 … 6+msgLen−1 | `msg` | treść |
| n−1 … n | `crc` | CRC-16-CCITT init `0xFFFF`, poly `0x1021`, LE |

- CRC obejmuje bajty od `type` (offset 1) do końca `msg` (bez SOF i bez samego CRC).
- Ramka ze złą sumą jest **ignorowana**.
- `msgLen` ≤ **1492** (cała ramka ≤ 1499 B).
- Numery paczek rosną do wrapa `ushort`.

Minimalna ramka (pusty payload): 8 bajtów.

---

## Typy ramek

| Type | Nazwa | Kierunek | ACK? | Payload |
|------|-------|----------|------|---------|
| `0x01` | ACK | obie | — | pusty; `seq` = numer potwierdzanej paczki |
| `0x02` | CommandError | device → app | — | kod błędu + dane |
| `0x03` | DeviceError | device → app | — | kod błędu + dane (asynchronicznie) |
| `0x04` | Init | app → device | ACK | patrz niżej |
| `0x05` | EcgOffline | app → device | ACK | wstecz, czas, UID |
| `0x06` | EcgOfflineDone | device → app | — | pusty |
| `0x07` | GetScpInfo | app → device | → SCP Info | pusty |
| `0x08` | ScpInfo | device → app | — | rozmiar pliku SCP |
| `0x09` | GetScp | app → device | ACK, potem fragmenty | pusty |
| `0x0A` | ScpFragment | device → app | **ACK każdego fragmentu** | bajty SCP |
| `0x0B` | ScpDone | app → device | ACK; device kasuje SCP | pusty |
| `0x0C` | GetPulse | app → device | ACK | interwał `ushort` ×0.1 s (`0` = jeden pomiar) |
| `0x0D` | PulseValue | device → app | ProPlus: ACK; Silvermedia: „nie wymaga” — **implementujemy ACK** (ustalenie ProPlus) | 1 B puls |
| `0x0E` | EcgOnline | app → device | → Online Info | pusty |
| `0x0F` | EcgOnlineInfo | device → app | — | AVM, kanały, kody odprowadzeń |
| `0x10` | EcgOnlineData | device → app | — | nr próbki + int16 samples |
| `0x11` | EcgOnlineStop | app → device | ACK | pusty |
| `0x12` | End | app → device | ACK; koniec sesji | pusty |
| `0x13` | Get | app → device | → GetAns | ID informacji |
| `0x14` | GetAns | device → app | — | ID + wartość |
| `0x15` | EcgOfflineStop | app → device | ACK; brak pliku SCP | pusty |

### Init `0x04` payload

| Offset w msg | Pole |
|--------------|------|
| 0–3 | Unix timestamp (s, UTC) |
| 4–5 | sampling Hz (`ushort`) |
| 6 | okno średniej pulsu [s] |
| 7 | clear buffer (`0x01` / `0x00`) |
| 8 | liczba odprowadzeń dodatkowych |
| 9… | kody odprowadzeń (SCP / EN 1064) |

Init startuje ciągły zapis EKG+puls w pamięci urządzenia. Ponowny Init = nowy plik.

### ECG Offline `0x05` payload

| Offset | Pole |
|--------|------|
| 0 | czas wstecz [s] |
| 1 | całkowity czas pomiaru [s] (≥ wstecz) |
| 2 | długość UID |
| 3… | UID UTF-8 |

Po ACK urządzenie buduje SCP i wysyła `EcgOfflineDone`.

### SCP Info `0x08`

Rozmiar pliku SCP: **`uint32` little-endian — dokładnie 4 bajty** (`PayloadCodec.parseScpInfoSize`).  
Dokument źródłowy pisał „6 do 10”; przyjmujemy 4 B i zweryfikujemy na żywym EHO-Mini.

Ostatni fragment SCP (brama BPMN): gdy `receivedBytes >= scpSize` z `ScpInfo` (brak flagi w payloadzie fragmentu).

### Get / GetAns

| ID | Znaczenie | GetAns |
|----|-----------|--------|
| `0x01` | bateria % | 1 B |
| `0x02` | odpięte elektrody | count + kody |
| `0x03` | DeviceID UTF-8 | len + bytes |
| `0x04` | czas urządzenia | unix `uint32` |

### Błędy `0x02`

| Kod | Nazwa |
|-----|-------|
| `0x03` | EKG w trakcie (+ czas do końca [s]) |
| `0x04` | brak pliku SCP |
| `0x05` | bufor wsteczny niedostępny (+ dostępne sekundy) |

### Błędy `0x03`

| Kod | Nazwa |
|-----|-------|
| `0x01` | odpięcie elektrod |
| `0x02` | błąd urządzenia (bytes producenta) |

---

## Flow sesji (BPMN)

```
Start → Init
     → parallel
          ├─ EcgOffline → EcgOfflineDone
          │     → parallel
          │          ├─ GetScpInfo → ScpInfo
          │          └─ GetScp → (ScpFragment + ACK)* → join
          │     → ScpDone
          │     → jeszcze ECG? TAK → EcgOffline / NIE → join końcowy
          └─ GetPulse → PulseValue*(+ACK)
     → End
```

Zasady z ustaleń Pro-Plus (mapowanie starych nazw → Silvermedia):

| Pro-Plus (2017-07) | Silvermedia (2017-12) |
|--------------------|------------------------|
| PARAMS CONFIG / PARAMS INFO | Init (+ Get/GetAns na stan) |
| MEASURE | EcgOffline |
| potwierdzenie odbioru badania | ScpDone |
| puls — ACK każdej ramki; 3 braki = drop link | PulseValue + ACK |

Priorytet: dokończyć pierwsze zlecone badanie offline przed kolejnym.

---

## Pliki SCP

Próbki w `raw/` to **Biosig/SCP-ECG** (`SCPECG` @ offset 16). Aplikacja składa bajty z `ScpFragment` w kolejności i zapisuje jako `.scp` bez dodatkowej transformacji na poziomie transportu.

---

## Discovery Bluetooth

Nazwa reklamowa (zawsze):

```text
PRO_PLUS_ECG_<NNNNNN>
```

- stały prefiks `PRO_PLUS_ECG_`
- `<NNNNNN>` — **dokładnie 6 cyfr**, końcówka numeru seryjnego (np. `740579` → `PRO_PLUS_ECG_740579`)

Implementacja: `BluetoothDeviceFilter` (regex `^PRO_PLUS_ECG_\d{6}$`).

Flow połączenia:

1. Użytkownik paruje `PRO_PLUS_ECG_******` w ustawieniach Androida.
2. Aplikacja czyta bonded devices i wybiera pasujące po nazwie.
3. `createRfcommSocketToServiceRecord(SppConstants.SPP_UUID)` → framing Silvermedia.

## Otwarte / do potwierdzenia na EHO-Mini

- [x] Parowanie z poziomu systemu Android (bez custom UUID)
- [x] `ScpInfo` = 4 bajty (uint32 LE) — do potwierdzenia empirycznie
- [x] Nazwa reklamowa BT: `PRO_PLUS_ECG_` + 6 cyfr SN
- [ ] Czy produkcyjny firmware wymaga ACK na `PulseValue` (przyjmujemy TAK wg ProPlus)
- [ ] Komenda odzysku badań po reconnect (w ProPlus oznaczona „X”)
- [ ] Mapowanie brandingu EHO-Mini ↔ ten protokół Silvermedia (założenie: ten sam framing)
