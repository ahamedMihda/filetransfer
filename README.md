# Archivio di file – server TCP in Java

Server didattico che serve file di testo dalla cartella `documenti` sulla porta **3000**.
Tutto il testo è in UTF-8; ogni connessione serve una sola richiesta e una sola risposta.
Il servizio funziona con `nc`, `curl` e con un normale browser.

## Avvio

Dalla cartella principale del progetto (perché `documenti` è un percorso relativo):

```bash
mvn -DskipTests compile
java -cp target/classes it.meucci.FileServer
```

Poi, in un altro terminale:

```bash
curl localhost:3000/                 # indice dell'archivio
curl localhost:3000/saluto
printf 'GET /saluto HTTP/1.0\r\n\r\n' | nc localhost 3000
```

Oppure aprire `http://localhost:3000/` nel browser.

Il server usa un pool di *virtual thread*, quindi serve più client in contemporanea;
un errore di una singola connessione non ferma il server.

## Tappe

Ogni tappa è stata salvata in un ramo separato, con un commit per ramo:

| Ramo | Novità |
|------|--------|
| `tappa-1` … `tappa-7` | Dal primo dialogo alle intestazioni nella richiesta (lavoro precedente) |
| `tappa-8` |Comando `GET`, versione `HTTP/1.0`/`1.1`, righe CRLF, `Connection: close` |
| `tappa-9` |Sottocartelle e controllo anti *path traversal* |
| `tappa-10` |Comandi `HEAD` e `PUT` (informazioni e scrittura) |
| `tappa-11` |`Last-Modified` e `If-Modified-Since`, risposta `304` |
| `tappa-12` |Indice HTML delle cartelle e file `index.html` |

Il codice finale è quello del ramo `tappa-12`.

## Protocollo finale

Richiesta (quattro parti):

```text
GET /avviso HTTP/1.1       ① riga di richiesta: metodo, risorsa, versione
User-Agent: curl/8.21.0    ② intestazioni Nome: valore
Accept: text/markdown      … (sconosciute: ignorate)
                           ③ riga vuota: chiude le intestazioni
Ciao\r\n                   ④ corpo, solo per PUT (Content-Length byte)
```

Risposta (la stessa forma):

```text
HTTP/1.0 200 OK            ① versione, codice a tre cifre, frase
Content-Type: text/plain; charset=utf-8
Content-Length: 29         ② intestazioni
Last-Modified: Tue, 24 Nov 2026 07:57:15 GMT
Connection: close
                           ③ riga vuota: chiude le intestazioni
buongiorno a tutti…        ④ contenuto (assente per HEAD e 304)
```

Riga di richiesta, intestazioni e riga vuota terminano con `\r\n`; il contenuto viene
inviato così com'è, senza modifiche. La riga vuota è il separatore tra le parti
"di controllo" e il corpo; la lunghezza del corpo è dichiarata da `Content-Length`.

- Metodi: `GET` (lettura), `HEAD` (solo informazioni), `PUT` (scrittura).
- Versioni accettate: `HTTP/1.0` e `HTTP/1.1`; la risposta usa sempre `HTTP/1.0`.
- Codici usati: `200 OK`, `201 Created`, `304 Not Modified`, `400 Bad Request`,
  `404 Not Found`, `406 Not Acceptable`, `411 Length Required`,
  `415 Unsupported Media Type`, `500 Internal Server Error`,
  `501 Not Implemented`, `505 Version Not Supported`.
- `Accept` vuoto o con `*/*` → ordine del server (`txt`, `md`, `csv`, `html`);
  altrimenti prova solo i tipi elencati, in ordine di preferenza.

## Prove eseguite

### Tappa 8 – browser e curl

| Richiesta | Esito |
|-----------|-------|
| `GET /saluto HTTP/1.1` (con `nc`) | `200 OK`, righe con `\r\n` (verificato con `od -c`) e `Connection: close` |
| `curl localhost:3000/orario` | `200`, testo con accenti corretto, `Content-Length: 73` = `wc -c` |
| `GET /avviso` con `Accept: text/markdown` | `200`, contenuto di `avviso.md` |
| `GET /saluto` con `Accept: text/markdown` | `406 Not Acceptable` (esiste solo `.txt`) |
| `GET /favicon.ico` | `400 Bad Request` (il nome contiene `.`) |
| `GET /saluto HTTP/2.0` | `505 Version Not Supported` |
| `POST /saluto HTTP/1.0` | `501 Not Implemented` |

### Tappa 9 – sottocartelle

| Richiesta | Esito |
|-----------|-------|
| `GET /avvisi/gita`, `GET /avvisi/vecchi/natale` | `200`, file nelle sottocartelle |
| `GET /avvisi` | `404` (a questa tappa una cartella non ha contenuto) |
| `GET /avvisi//gita`, `GET /avvisi/`, `GET /../saluto`, `GET /avvisi/../../segreto` | `400` |
| `GET /../segreto` con `segreto.txt` nella cartella del progetto | `400`, nessun *path traversal* |

### Tappa 10 – informazioni e scrittura

| Richiesta | Esito |
|-----------|-------|
| `curl -I localhost:3000/regole` | `200`, solo intestazioni, `Content-Length: 155` |
| `curl -T compiti.txt -H 'Content-Type: text/plain; charset=utf-8' …/avvisi/compiti` | `201 Created`; ripetendola, `200 OK` |
| `GET /avvisi/compiti` | contenuto scritto, letto dal browser/curl |
| `PUT /prova` con `Content-Length: 6` e corpo `Ciao\r\n` | `201`, file `documenti/prova.txt` creato |
| `PUT` senza `Content-Length` | `411 Length Required` |
| `PUT` con lunghezza non numerica, negativa, `Content-Type` mancante o corpo corto | `400 Bad Request` |
| `PUT` con `Content-Type: application/json` | `415 Unsupported Media Type` |
| `PUT /nonc-e/compiti` | `404 Not Found` (cartella inesistente) |

### Tappa 11 – date di modifica

| Richiesta | Esito |
|-----------|-------|
| `curl -I localhost:3000/saluto` | `200`, `Last-Modified: Tue, 24 Nov 2026 07:57:15 GMT` |
| `curl -z "<quella data>" localhost:3000/saluto` | `304 Not Modified`, senza contenuto |
| `curl -z "Tue, 10 Nov 2025 08:30:00 GMT" …` | `200` con contenuto |
| `nc` con `If-Modified-Since` esatto / non valido | `304` / `200` (intestazione ignorata) |
| `PUT` del file e richiesta con la vecchia data | `200` con il nuovo contenuto |

### Tappa 12 – archivio da sfogliare

| Richiesta | Esito |
|-----------|-------|
| `curl localhost:3000/` | `200`, `text/html`, `Content-Length: 794` = byte ricevuti (`wc -c`) |
| `curl localhost:3000/avvisi`, `/avvisi/vecchi` | cartelle prima dei file; `note.log` non compare; `avviso` compare due volte (`md` e `txt`) |
| `curl localhost:3000/benvenuto` | pagina `benvenuto.html` scritta a mano |
| `PUT /avvisi/nuovo` e ricarica dell'indice | `201`, nuovo file elencato |
| `index.html` temporaneo in `/avvisi` | server invia la pagina scritta a mano al posto dell'elenco |

## Domande

### Tappa 1

- **Perché separare stato e contenuto?** Un programma decide in base allo stato (prima riga),
  le persone leggono il testo. Così un file vuoto (`200`, zero byte) si distingue da uno
  inesistente (`404`): entrambi hanno il separatore, ma solo il primo ha `200`.
- **Se non scriviamo nulla con nc?** `readLine()` restituisce `null`: il server chiude solo
  quella connessione e torna ad `accept()`. Se entrambi aspettassero l'altro, si creerebbe
  un *deadlock*: la richiesta non arriverebbe mai e la risposta non verrebbe mai inviata.
- **Perché una riga vuota non indica la fine del file?** Perché un file può contenere righe
  vuote (per esempio `orario.txt`): la riga vuota chiude le intestazioni, non il contenuto.

### Tappa 2

- Come si sa quante intestazioni ci sono? Si leggono righe fino alla riga vuota.
- Con una nuova intestazione sconosciuta? La si ignora e si usano solo quelle note:
  è la regola che rende il protocollo estensibile.

### Tappa 3

`.json` → `application/json`; `.html` → `text/html` (con `charset=utf-8` per il testo).

### Tappa 4

Con `Content-Length` chi riceve sa dove finisce il corpo senza attendere la chiusura del
socket: può quindi riutilizzare la connessione per altre richieste e accorgersi di un
trasferimento troncato.

### Tappa 5

Sono gli stessi codici che si vedono nel browser (per esempio 404 pagina non trovata).

### Tappa 6

La versione nella risposta dice al client quale regole sta usando il server: se non sono
quelle attese, il client può adattarsi o segnalare l'incompatibilità.

### Tappa 8

Sono cambiati i nomi (`LEGGI` → `GET`, `ARCH/1.0` → `HTTP/1.0|1.1`) e la forma dei messaggi
(righe con `\r\n`, intestazione `Connection`, righe di stato e intestazioni standard).
I nomi standard permettono a programmi scritti da altri di capirsi.

### Tappa 10

- Dalla barra del browser si può solo fare una richiesta di lettura (`GET`): non si può
  indicare un corpo, quindi non si può caricare un file.
- Chiunque può sovrascrivere i file: servono autenticazione/autorizzazione (per esempio
  credenziali o token) e, per la riservatezza, TLS.

### Tappa 11

Il browser usa la copia in cache e risparmia banda, tempo di attesa e lavoro del server.

### Consegna e riflessione

1. **Struttura finale:** riga iniziale, intestazioni, riga vuota, corpo, in richiesta e
   risposta; il `\r\n` termina riga e intestazioni, la riga vuota separa le intestazioni
   dal corpo, il corpo ha la lunghezza dichiarata da `Content-Length`.
2. **Regola della tappa 2:** ignorare le intestazioni sconosciute. Il browser invia
   `Host`, `Accept-Encoding`, `Accept-Language`, ecc.; senza questa regola il server non
   funzionerebbe. Nei protocolli di rete l'estensibilità è essenziale: nuove intestazioni
   non devono rompere i programmi vecchi.
3. **Informazione nell'intestazione:** `Content-Type` (o `Last-Modified`) descrive il
   contenuto senza essere il contenuto: il client può scegliere come presentarlo, o se
   riscaricarlo, prima di leggere i bytes.
4. **`Content-Length` per `PUT`:** il client deve restare in attesa per ricevere la
   risposta, quindi non può chiudere il socket per segnalare la fine del corpo; nelle
   risposte la chiusura del socket bastava, e `Content-Length` era solo un'utility.
5. **4xx contro 5xx:** 4xx è un errore di chi chiede (va corretta la richiesta); 5xx è un
   errore del server (va corretto il server o la sua configurazione).
6. **Connessioni persistenti:** il server dovrebbe leggere più richieste sullo stesso
   socket, dichiarare `Connection: keep-alive` e usare `Content-Length` (o il *chunked
   transfer encoding*) per far riconoscere la fine di ogni risposta senza chiudere.
7. **Messaggi in chiaro:** sì, chi è sulla stessa rete può leggerli e modificarli
   (*man in the middle*). Servono HTTPS/TLS e un meccanismo di autenticazione.
8. **HTTP:** *Hypertext Transfer Protocol*, nato nel 1991; HTTP/1.0 è del 1996
   (RFC 1945), HTTP/1.1 del 1999 (RFC 2616, aggiornato nel 2014). Il 1.1 introduce
   connessioni persistenti di default, `Host` obbligatorio, *chunked encoding*,
   *pipelining* e regole di cache più precise. Abbiamo già implementato senza saperlo
   codici di stato, intestazioni, `Content-Length`, `Connection`, metodi `GET`/`HEAD`/`PUT`,
   tipi MIME e richieste condizionali (`If-Modified-Since`).

## Difficoltà incontrate

- La lettura del corpo di `PUT`: `Content-Length` conta byte, non caratteri, e il corpo
  non è delimitato dalla chiusura del socket (gestito anche `Expect: 100-continue` di curl).
- La validazione dei segmenti della risorsa con `split("/", -1)`, necessaria per non
  perdere i segmenti vuoti finali e per bloccare `/..` e le barre inverse.
