Skip to content
 
Search Gists
Search...
All gists
Back to GitHub
Sign in
Sign up
Instantly share code, notes, and snippets.

@benve-meucci
benve-meucci/archivio-file-2.md Secret
Last active last week
Code
Revisions
3
Clone this repository at &lt;script src=&quot;https://gist.github.com/benve-meucci/84e18f10192a40407cfad245504d62eb.js&quot;&gt;&lt;/script&gt;
<script src="https://gist.github.com/benve-meucci/84e18f10192a40407cfad245504d62eb.js"></script>
Invio file client/server - v2
archivio-file-2.md
Archivio di file – Un server TCP in Java, una tappa alla volta
Obiettivo
Realizzare in Java un server che permetta di consultare a distanza dei file di testo. Chi si collega richiede un file indicando il suo nome; il server lo cerca sul proprio disco e restituisce il contenuto oppure uno stato di errore.

Il servizio sarà un piccolo archivio di classe: un utente potrà consultare avvisi e orari senza avere una copia dei file sul proprio computer. Il nome viaggia verso il server; il testo del file viaggia dal server verso chi lo ha chiesto.

Non scriverete un client: per collegarvi al server userete programmi già esistenti. All'inizio userete nc (netcat), che si limita a inviare ciò che scrivete e a mostrare ciò che riceve: così vedrete esattamente ogni carattere che viaggia sulla rete. Più avanti scoprirete che anche altri programmi sono in grado di parlare con il vostro server.

Le regole del dialogo, cioè il protocollo, le stabiliamo noi. Nella prima tappa costruirete una versione semplice; nelle tappe successive la farete crescere. Ogni tappa nasce da un limite della tappa precedente e introduce una sola novità.

Tappa	Limite da superare	Novità
1	–	Il primo dialogo: richiesta, stato, contenuto
2	Bisogna conoscere l'estensione; il server non può descrivere il contenuto	Righe di intestazione
3	TYPE md lo capiamo solo noi	Intestazioni e tipi standard
4	Un file troncato sembra completo	La lunghezza del contenuto
5	Ogni errore ha una parola diversa da conoscere	Codici di stato
6	Un cambio di regole renderebbe incompatibili programmi vecchi e nuovi	Risorsa e versione del protocollo
7	Chi chiede non può esprimere preferenze	Intestazioni nella richiesta
8	Per consultare l'archivio bisogna scrivere le richieste a mano	Un ospite inatteso
9	Tutti i file nella stessa cartella	Sottocartelle
10	Il server sa solo inviare file	Chiedere informazioni e scrivere file
11	Lo stesso file viene riscaricato anche se non è cambiato	Date di modifica
12	Per consultare l'archivio bisogna conoscere i nomi dei file	Un archivio da sfogliare
Completate e verificate ogni tappa prima di passare alla successiva, e conservate una copia dei sorgenti funzionanti di ciascuna (per esempio in una cartella tappe/01, tappe/02, …).

Organizzazione del progetto
I file dell'archivio stanno in una sottocartella documenti del progetto:

archivio-server/
├── src/
│   └── ServerArchivio.java
└── documenti/
    ├── saluto.txt
    ├── orario.txt
    └── ...
Il server cerca i file con il percorso relativo documenti, che Java risolve a partire dalla directory di avvio del programma. Avviate quindi il server dalla cartella principale del progetto, archivio-server: è ciò che fanno per impostazione predefinita IntelliJ, Eclipse e VS Code. Da terminale:

cd archivio-server
java -cp out ServerArchivio     # oppure il percorso in cui compilate le classi
Regole che valgono per tutta la traccia
Il server ascolta sulla porta 3000.
Tutto il testo viaggia ed è salvato in UTF-8.
Il server legge e scrive file esclusivamente nella cartella documenti del progetto.
Ogni connessione serve per una sola richiesta e una sola risposta. Per un'altra richiesta ci si ricollega.
Un errore relativo a una connessione non deve mai terminare tutto il server.
Trasferiamo solo testo, non immagini, PDF o altri file binari.
Ripartite dalle istruzioni usate nella verifica: ServerSocket, Socket, BufferedReader, PrintWriter, readLine(), println(), split(), if e switch.

Usare nc
Avviate prima il server, poi in un altro terminale:

nc localhost 3000
Scrivete la richiesta e premete Invio: sotto compare la risposta del server. Quando il server chiude la connessione, nc termina. Per ogni nuova richiesta avviate di nuovo nc.

Per ripetere una prova senza riscriverla, potete preparare la richiesta con printf e passarla a nc; \n indica la fine di una riga:

printf 'LEGGI saluto.txt\n' | nc localhost 3000
Se nc non termina dopo la risposta, il server non ha chiuso il socket. Se termina subito senza mostrare nulla, controllate che il server sia avviato e in ascolto sulla porta giusta. Su alcuni sistemi la versione di nc installata richiede l'opzione -q 1 per terminare dopo aver inviato i dati di printf.

Tappa 1 – Il primo dialogo
Preparazione
Nella cartella documenti del progetto create questi piccoli file, salvati in UTF-8:

File	Contenuto da preparare
saluto.txt	Due righe di saluto
orario.txt	Alcune righe con giorni e orari, una riga vuota, una parola accentata e una riga contenente solo FINE
vuoto.txt	Nessun carattere
Non create assente.txt: servirà per provare il caso di errore.

La versione base serve una connessione alla volta, ma deve restare attiva per accettarne di nuove. La gestione contemporanea di più connessioni con i thread è facoltativa.

Procedete per passi, verificando ogni risultato con nc prima di continuare:

Collegamento: il server stampa sulla propria console la riga ricevuta. Controllate che sia esattamente quella che avete scritto.
Risposta di prova: il server restituisce OK FILE, una riga vuota e due righe di testo scritte nel codice, poi chiude il socket. nc deve mostrare tutte le righe e terminare.
Accesso al disco: sostituite il testo di prova con le righe del file richiesto e gestite il file assente.
Completamento: aggiungete i controlli sulle richieste e provate file vuoti e righe vuote.
Regole del dialogo
Chi chiede si collega.
Invia una riga di richiesta.
Il server invia la risposta.
Il server chiude il socket.
Richiesta
LEGGI nomefile.txt
Il comando LEGGI deve essere maiuscolo. Ignorate gli spazi iniziali e finali e accettate più spazi tra comando e nome. Non modificate maiuscole e minuscole del nome del file.

Sono ammessi soltanto nomi semplici, senza spazi, /, \ o la sequenza .., e con estensione .txt. Non sono previste sottocartelle.

Risposta
Ogni risposta è composta da:

Una riga di stato, che indica l'esito.
Una riga vuota, che separa lo stato dal contenuto.
Il contenuto, presente soltanto in caso di successo.
La riga di stato serve a un programma per decidere che cosa fare; il contenuto è il testo da mostrare all'utente. La riga vuota segna l'inizio del contenuto, non la sua fine.

Situazione	Riga di stato
File trovato e aperto correttamente	OK FILE
Richiesta vuota, argomenti mancanti o in eccesso, nome non ammesso	ERR SYNTAX
Comando diverso da LEGGI	ERR UNKNOWNCMD
Nome valido, ma file assente o percorso che non indica un file	ERR NOTFOUND
File presente, ma impossibile da aprire in lettura	ERR READ
Per decidere quale errore inviare, controllate nell'ordine: richiesta vuota, comando riconosciuto, numero di argomenti e nome, presenza del file, apertura in lettura.

Anche in caso di errore inviate la riga vuota, poi chiudete la connessione. Non inviate una parola speciale come FINE per terminare il contenuto: anche quella parola potrebbe essere una riga del file. La fine della risposta è segnalata dalla chiusura del socket: è per questo che nc termina.

Esempi di scambio
Le etichette → SERVER e SERVER → sono illustrative e non devono essere trasmesse.

→ SERVER  LEGGI saluto.txt
SERVER →  OK FILE
SERVER →
SERVER →  Benvenuti in laboratorio!
SERVER →  Oggi condividiamo un file.
Il server chiude quindi il socket. Per un file assente:

→ SERVER  LEGGI inesistente.txt
SERVER →  ERR NOTFOUND
SERVER →
Un file vuoto produce OK FILE, una riga vuota e la chiusura: non è un errore.

Il server
Realizzate un programma che:

crei un ServerSocket sulla porta 3000 e usi accept() dentro un ciclo;
legga la richiesta dal socket e la analizzi;
controlli la richiesta prima di accedere agli elementi dell'array prodotto da split();
cerchi il file nella cartella documenti e lo apra prima di comunicare OK FILE;
invii stato, separatore e, se previsto, tutte le righe del file;
chiuda file e socket della connessione, lasciando aperto il ServerSocket per la richiesta successiva.
Se chi si è collegato chiude senza inviare una richiesta (provate a premere Ctrl+C in nc prima di scrivere), readLine() restituisce null: terminate quella gestione senza analizzare la stringa. Collocate la gestione degli errori della singola connessione all'interno del ciclo che accetta le connessioni.

Tenete distinti i due oggetti: chiudere il Socket termina il dialogo corrente; chiudere il ServerSocket impedisce di accettare nuove connessioni.

Se la lettura del file fallisce dopo l'invio di OK FILE, registrate l'errore sulla console del server e chiudete il socket: non inserite un secondo stato nel contenuto. In questa versione chi riceve la risposta non può distinguere un trasferimento interrotto da uno completo: è un limite che supereremo più avanti.

Promemoria e suggerimenti
I frammenti seguenti sono aiuti da integrare nel vostro programma, non una soluzione completa.

Stream testuali sul socket
Per leggere e scrivere correttamente anche le lettere accentate, impostate UTF-8:

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

BufferedReader in = new BufferedReader(
    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
PrintWriter out = new PrintWriter(
    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
out.println(testo) invia una riga sulla rete; System.out.println(testo) scrive soltanto sulla console del server. Il parametro true fa inviare subito i dati dopo ogni println().

Analizzare una richiesta
String richiesta = in.readLine();
// Se richiesta è null, terminate la gestione di questa connessione.
// Altrimenti, se richiesta.trim().isEmpty(), rispondete ERR SYNTAX.
// Solo dopo questi controlli:
String[] parti = richiesta.trim().split("\\s+");
split("\\s+") separa usando uno o più spazi o altri caratteri di spaziatura. Verificate parti.length prima di leggere parti[1]. Per controllare il nome potete usare contains(), endsWith() e equals(); per indicare una barra inversa in una stringa Java si scrive "\\".

Cercare il file sul disco del server
File cartella = new File("documenti");
File file = new File(cartella, nomeFile);

System.out.println(file.getAbsolutePath()); // Utile durante le prove
boolean presente = file.isFile();
isFile() restituisce true se il percorso indica un file esistente, non una cartella. Il controllo va eseguito dopo aver validato il nome.

Attenzione alla cartella di avvio: se il server non trova file che esistono, probabilmente non è stato avviato dalla cartella principale del progetto. Il percorso assoluto stampato vi mostra dove sta cercando davvero.

Aprire, leggere e inviare un file
Un file può essere letto riga per riga con un altro BufferedReader, proprio come un socket. Per chiuderlo anche in caso di errore usate try-with-resources: la risorsa dichiarata tra parentesi viene chiusa automaticamente all'uscita dal blocco.

try (BufferedReader lettoreFile = new BufferedReader(
        new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
    out.println("OK FILE");
    out.println(); // Riga vuota

    String riga;
    while ((riga = lettoreFile.readLine()) != null) {
        out.println(riga);
    }
} catch (IOException e) {
    // Gestite l'errore distinguendo se lo stato è già stato inviato.
}
Potete usare una variabile boolean per ricordare se avete già inviato OK FILE. Alla fine della risposta chiudete il socket.

Se qualcosa non funziona
Sintomo	Controllo da fare
nc termina subito senza mostrare nulla	Il server è avviato? Ascolta sulla porta 3000?
Il server riceve la richiesta, ma nc non mostra nulla	State usando out.println() sul socket, e non System.out.println()? Il PrintWriter ha il parametro true?
nc mostra la risposta ma non termina	Il server ha chiuso il socket dopo l'ultima riga?
Un file presente risulta assente	Stampate file.getAbsolutePath() e controllate directory di avvio e maiuscole.
La seconda richiesta non riceve risposta	Il server torna su accept() dopo aver chiuso il socket?
Il server si è fermato dopo una richiesta sbagliata	La gestione degli errori è dentro il ciclo di accept()?
Prove
Prova	Risultato atteso
File esistente con più righe	OK FILE e tutto il testo
orario.txt, con righe vuote, accenti e la riga FINE	Contenuto completo, accenti corretti
File esistente ma vuoto	OK FILE, riga vuota, nessun contenuto
Nome valido di un file assente	ERR NOTFOUND
LEGGI senza nome oppure con due nomi	ERR SYNTAX
CIAO saluto.txt	ERR UNKNOWNCMD
Richiesta vuota (solo Invio)	ERR SYNTAX
LEGGI ../saluto.txt	ERR SYNTAX
LEGGI orario.pdf	ERR SYNTAX
 LEGGI   saluto.txt 	OK FILE e contenuto
Collegamento chiuso senza scrivere nulla	Il server continua a funzionare
Nuova richiesta dopo un errore	Il server risponde ancora
Scambiatevi i server con un compagno: collaudate il suo server con nc e lui il vostro, usando le stesse regole. Se usate due computer, sostituite localhost con l'indirizzo IP del computer che esegue il server. Ripetete questo scambio alla fine di ogni tappa.

Domande
Perché conviene separare lo stato dal contenuto? Come distinguiamo un file vuoto da un file inesistente?
Collegatevi con nc e non scrivete nulla: che cosa fa il server? Che cosa succederebbe se anche il server aspettasse di ricevere qualcosa da voi prima di scrivere, e voi aspettaste lui?
Perché una riga vuota non può indicare la fine del file?
Tappa 2 – Informazioni sul contenuto
Limite: chi chiede un file deve conoscerne l'estensione. Inoltre, se il server volesse comunicare il tipo di file, non avrebbe un posto dove scriverlo: la riga di stato parla dell'esito, il contenuto è il file stesso.

Aggiungete in documenti:

File	Contenuto
regole.md	Un titolo # Regole e un elenco puntato in Markdown
voti.csv	Tre o quattro righe nome;materia;voto
avviso.txt e avviso.md	Due versioni diverse dello stesso avviso
note.log	Una riga qualsiasi: estensione non ammessa, non deve mai essere inviata
Richiesta
LEGGI nome
Il nome non contiene alcun punto (quindi niente estensione e niente ..), né spazi, / o \. LEGGI saluto.txt ora produce ERR SYNTAX.

Il server conosce un elenco ordinato di estensioni ammesse: txt, md, csv. Per LEGGI avviso prova nell'ordine avviso.txt, avviso.md, avviso.csv e si ferma al primo file esistente. Se nessuno esiste risponde ERR NOTFOUND. L'elenco va scritto una sola volta nel codice, per esempio in un array.

Risposta
Tra la riga di stato e la riga vuota compare una nuova parte:

La riga di stato.
Zero o più righe di intestazione, nella forma NOME valore, che descrivono il contenuto.
Una riga vuota, che chiude l'intestazione.
Il contenuto, solo in caso di successo.
Con OK FILE il server invia l'intestazione obbligatoria TYPE, con l'estensione del file trovato. Le risposte di errore non hanno intestazioni.

→ SERVER  LEGGI regole
SERVER →  OK FILE
SERVER →  TYPE md
SERVER →
SERVER →  # Regole
SERVER →  ...
Ora la riga vuota ha un ruolo più chiaro: chiude la parte che parla del contenuto e apre il contenuto.

Prove
Prova	Risultato atteso
LEGGI saluto	OK FILE, TYPE txt, contenuto
LEGGI regole / LEGGI voti	TYPE md / TYPE csv
LEGGI avviso	Contenuto di avviso.txt
LEGGI note	ERR NOTFOUND
LEGGI saluto.txt, LEGGI ../saluto	ERR SYNTAX
Domande
Un programma che riceve questa risposta come fa a sapere quante righe di intestazione ci sono?
Domani il server potrebbe aggiungere una nuova intestazione, per esempio AUTORE Rossi. Come dovrebbe comportarsi un programma scritto oggi, che non la conosce, perché continui a funzionare? Annotate la vostra risposta: questa regola diventerà importante.
Tappa 3 – Intestazioni e tipi standard
Limite: TYPE md è una convenzione tra noi. Un programma scritto da altri non saprebbe che cosa significa.

Non serve inventare nomi nuovi: per descrivere un contenuto esistono già nomi standard, nati per gli allegati della posta elettronica e oggi conosciuti da moltissimi programmi. Da questa tappa in poi, quando esiste un nome standard, usiamo quello.

Formato delle intestazioni
Ogni intestazione ha ora la forma:

Nome: valore
Il nome termina al primo carattere :; il valore è ciò che segue, senza spazi iniziali e finali. Il confronto dei nomi non distingue maiuscole e minuscole: Content-Type, content-type e CONTENT-TYPE sono la stessa intestazione. Chi scrive un programma non deve preoccuparsi di come l'altro programma ha scritto il nome. (Questa regola vi servirà quando sarà il server a leggere intestazioni.)

Tipi standard
L'intestazione TYPE viene sostituita da Content-Type, il cui valore è un tipo MIME: un nome registrato a livello internazionale. Il parametro charset indica la codifica del testo.

Estensione	Content-Type
txt	text/plain; charset=utf-8
md	text/markdown; charset=utf-8
csv	text/csv; charset=utf-8
Estensioni e tipi stanno ora in un'unica tabella nel codice del server, che sostituisce l'elenco della tappa 2:

String[][] TIPI = {
    {"txt", "text/plain; charset=utf-8"},
    {"md",  "text/markdown; charset=utf-8"},
    {"csv", "text/csv; charset=utf-8"},
};
→ SERVER  LEGGI regole
SERVER →  OK FILE
SERVER →  Content-Type: text/markdown; charset=utf-8
SERVER →
SERVER →  # Regole
Domanda: cercate in rete l'elenco dei tipi MIME. Quale tipo usereste per un file .json? E per un .html?

Tappa 4 – La lunghezza del contenuto
Limite: se la connessione si interrompe a metà, chi riceve non può distinguere un file troncato da un file completo.

Nuova intestazione
Con OK FILE il server invia anche l'intestazione standard:

Content-Length: 58
Il valore è il numero di byte del contenuto, non di caratteri né di righe. In UTF-8 una lettera accentata occupa due byte: perché è lungo 6 caratteri ma 7 byte.

Il server
Per conoscere la lunghezza prima di inviare il contenuto, il server legge l'intero file in memoria prima di inviare lo stato, e poi lo invia così com'è, terminatori di riga compresi:

byte[] dati = Files.readAllBytes(file.toPath());
String contenuto = new String(dati, StandardCharsets.UTF_8);

out.println("OK FILE");
out.println("Content-Type: " + tipoMime);
out.println("Content-Length: " + dati.length);
out.println();
out.print(contenuto);
out.flush();   // print() non svuota il buffer da solo
Attenzione: l'invio automatico del PrintWriter avviene solo con println(). Dopo print() serve flush(), altrimenti chiudendo il socket gli ultimi dati vanno persi.

Leggendo il file prima di inviare lo stato si risolve anche un limite della tappa 1: un errore di lettura viene scoperto prima di aver comunicato OK FILE, quindi la variabile boolean non serve più.

Prove
Confrontate il valore di Content-Length con la dimensione reale del file, misurata dal sistema operativo:

wc -c documenti/orario.txt     # numero di byte
wc -m documenti/orario.txt     # numero di caratteri
Per orario.txt, che contiene una lettera accentata, i due numeri devono essere diversi e Content-Length deve coincidere con il primo.

Fate poi inviare al server una lunghezza volutamente sbagliata (per esempio dati.length + 10). nc se ne accorge? Annotate il risultato: ripeterete la prova nella tappa 8 con un altro programma.

Domanda: grazie a questa intestazione, chi riceve saprebbe dove finisce il contenuto anche senza la chiusura del socket. Che cosa permetterebbe di fare?

Tappa 5 – Codici di stato
Limite: per capire una risposta, un programma deve conoscere ogni singola parola di errore. Un errore nuovo, aggiunto in futuro, non sarebbe interpretabile.

Nuova riga di stato
La riga di stato diventa: codice numerico di tre cifre, spazio, frase descrittiva. Anche qui usiamo codici e frasi standard, in inglese.

Prima	Ora	Significato
OK FILE	200 OK	Successo
ERR SYNTAX	400 Bad Request	Richiesta non valida
ERR NOTFOUND	404 Not Found	Risorsa inesistente
ERR UNKNOWNCMD	501 Not Implemented	Comando non supportato
ERR READ	500 Internal Server Error	Problema del server
La prima cifra indica la classe dell'esito:

Classe	Significato
2xx	Successo
3xx	Chi ha chiesto deve fare qualcos'altro (lo vedremo nella tappa 11)
4xx	Errore di chi chiede: la richiesta è sbagliata
5xx	Errore del server: la richiesta era corretta, ma il server non è riuscito a soddisfarla
Un programma che legge la risposta decide in base al codice, non alla frase: la frase serve solo alle persone. Se riceve un codice che non conosce, per esempio 418, gli basta la prima cifra per capire di che tipo di esito si tratta.

Un contenuto anche per gli errori
Anche le risposte di errore hanno ora Content-Type, Content-Length e un breve testo per l'utente:

→ SERVER  LEGGI inesistente
SERVER →  404 Not Found
SERVER →  Content-Type: text/plain; charset=utf-8
SERVER →  Content-Length: 34
SERVER →
SERVER →  Il file "inesistente" non esiste.
Conviene scrivere nel server un metodo che invia una risposta completa ricevendo codice, frase, tipo e contenuto: lo userete sia per i successi sia per gli errori.

Domanda: avete già visto qualcuno di questi codici da qualche altra parte? Dove?

Tappa 6 – Risorsa e versione
Limite: se un giorno cambiassimo il protocollo, il server non saprebbe se sta parlando con un programma che segue le regole vecchie o quelle nuove. Inoltre il nome del file non ha la forma di un percorso.

Nuova richiesta
LEGGI /saluto ARCH/1.0
La riga di richiesta ha esattamente tre parti: comando, risorsa (che inizia con /), versione del protocollo.

Nuova risposta
Anche la riga di stato inizia con la versione:

ARCH/1.0 200 OK
Regole
Controllate nell'ordine:

Situazione	Risposta
Parti diverse da tre	400 Bad Request
Risorsa che non inizia con /	400 Bad Request
Versione che non inizia con ARCH/	400 Bad Request
Versione ARCH/ diversa da ARCH/1.0	505 Version Not Supported
Comando diverso da LEGGI	501 Not Implemented
Il controllo del nome della tappa 2 si applica ora alla risorsa senza la / iniziale.

Domanda: perché è utile che la versione sia presente anche nella risposta?

Tappa 7 – Intestazioni nella richiesta
Limite: esistono avviso.txt e avviso.md, ma chi chiede non ha modo di dire quale preferisce. Il server, inoltre, non sa nulla del programma che lo sta interrogando.

Nuova richiesta
Anche la richiesta ha ora una parte di intestazione, chiusa da una riga vuota. Richiesta e risposta hanno così la stessa forma. Anche qui usiamo nomi standard.

LEGGI /avviso ARCH/1.0
User-Agent: nc (Rossi)
Accept: text/markdown

Il server legge le intestazioni fino alla riga vuota, con le regole della tappa 3, e ignora quelle che non conosce: è la regola che avete scoperto rispondendo alla seconda domanda della tappa 2. Una riga di intestazione senza : produce 400. Se la connessione si chiude prima della riga vuota, il server abbandona la richiesta senza rispondere.

Attenzione: con nc dovete ora premere Invio due volte alla fine della richiesta. Se il server sembra bloccato, sta aspettando la riga vuota. Con printf la richiesta termina con \n\n:

printf 'LEGGI /avviso ARCH/1.0\nAccept: text/markdown\n\n' | nc localhost 3000
Conviene memorizzare le intestazioni ricevute in una HashMap<String, String> con il nome convertito in minuscolo: così il confronto non distingue maiuscole e minuscole.

Le intestazioni da gestire
Intestazione	Comportamento del server
User-Agent	Il nome del programma che si è collegato: il server lo scrive sulla console
Accept	Elenco di tipi MIME separati da virgole, in ordine di preferenza
Gestione di Accept:

se manca, o se contiene */* («qualsiasi tipo»), il server usa il proprio ordine delle estensioni, come prima;
altrimenti il server prova solo i tipi elencati, nell'ordine richiesto, e si ferma al primo file esistente;
se nessuno dei tipi accettati è disponibile, ma il file esiste con un altro tipo, risponde 406 Not Acceptable; se il file non esiste affatto, risponde 404.
Di ogni tipo elencato considerate solo la parte prima di ;, e togliete gli spazi.

Richiesta di /avviso	Risposta
Senza Accept	avviso.txt
Accept: text/markdown	avviso.md
Accept: text/csv, text/markdown	avviso.md
Accept: text/csv	406 Not Acceptable
Accept: text/csv, */*	avviso.txt
accept: TEXT/MARKDOWN	Decidete voi se il tipo va confrontato senza distinguere maiuscole, e motivate
Autore: Rossi (intestazione sconosciuta)	Ignorata, risposta normale
Riga Accept text/markdown (senza :)	400 Bad Request
Il registro del server
Da questa tappa il server stampa sulla propria console ogni riga ricevuta, dalla riga di richiesta fino alla riga vuota, seguita dal codice di stato della risposta. Vi servirà nella prossima tappa.

Tappa 8 – Un ospite inatteso
Limite: l'archivio funziona, ma per consultarlo bisogna scrivere a mano ogni richiesta. Sui computer della scuola, però, c'è già un programma capace di collegarsi a un server e mostrare testo: il browser.

In questa tappa non riceverete regole nuove: dovrete scoprirle osservando.

A. Che cosa dice il browser?
Prima di collegare il browser al vostro server, collegatelo a un programma che si limita a mostrare ciò che riceve: nc in modalità di ascolto, cioè nel ruolo del server. Fermate il vostro server e avviate:

nc -l 3000        # su alcune versioni: nc -l -p 3000
Nella barra degli indirizzi del browser scrivete localhost:3000/saluto e premete Invio. Il browser rimarrà in attesa: guardate il terminale.

Che forma ha la prima riga? Riconoscete le tre parti della vostra riga di richiesta?
Quali righe seguono? Che forma hanno? Ce ne sono di già note? Come finisce la richiesta?
Che valore ha l'intestazione Accept? Contiene */*?
Provate a rispondere voi al browser, scrivendo a mano nel terminale di nc una risposta secondo le vostre regole (riga di stato, intestazioni, riga vuota, un po' di testo), poi premete Ctrl+C. Che cosa mostra il browser?

Ripetete l'ascolto facendo passare l'uscita di nc attraverso od -c, che mostra ogni singolo carattere:

nc -l 3000 | od -c
Come termina ogni riga? È diverso da ciò che invia il vostro println()?

B. Un altro ospite
Lo strumento a riga di comando curl parla la stessa lingua del browser. Con nc -l 3000 in ascolto, eseguite in un altro terminale:

curl localhost:3000/saluto
Che differenze ci sono tra la richiesta di curl e quella del browser? Che cosa hanno in comune?

C. Le differenze
Confrontate la richiesta del browser con quelle che avete scritto finora con nc e annotate tutte le differenze. Dovreste trovarne poche:

Le nostre richieste	Il browser e curl
Comando	LEGGI	
Versione	ARCH/1.0	
Fine riga	\n	
Intestazioni	User-Agent, Accept	
D. Adattare il server
Comando: sostituite LEGGI con il nome usato dal browser, che da ora diventa il comando del nostro protocollo.
Versione: sostituite ARCH con la sigla usata dal browser. Accettate le versioni 1.0 e 1.1; per tutte le altre rispondete 505. Rispondete sempre con la versione 1.0.
Connessione: il browser vorrebbe tenere aperta la connessione per altre richieste (cercate l'intestazione che lo chiede). Il nostro server continua a chiuderla dopo ogni risposta, e lo dichiara aggiungendo alla risposta l'intestazione standard Connection: close.
Fine riga: terminate riga di stato e intestazioni con \r\n, come fanno browser e curl, invece di usare println(), che su Linux invia solo \n. Il contenuto resta invariato. Scrivete un piccolo metodo che invia una riga seguita da \r\n. readLine() riconosce già entrambi i terminatori, quindi la lettura non cambia e potete continuare a provare anche con nc.
E. Provate
Riavviate il vostro server e aprite nel browser localhost:3000/saluto.

Il testo compare? Osservate il registro sulla console del server: quante intestazioni ha inviato il browser che il vostro server non conosce? Perché funziona lo stesso?
Aprite /orario: le lettere accentate sono corrette? Quale parte della vostra risposta lo garantisce?
Aprite /regole: il browser lo visualizza o lo scarica? Cambiate temporaneamente il tipo dei file md in text/plain e riprovate. Che cosa avete scoperto sul ruolo di Content-Type?
Aprite /avviso e /inesistente. Che cosa mostra il browser per un 404?
Aprite gli strumenti per sviluppatori del browser (F12, scheda Rete), ricaricate e selezionate la richiesta: ritrovate la vostra riga di stato e le vostre intestazioni?
Il registro mostra che il browser chiede di sua iniziativa anche un'altra risorsa: quale? Perché il vostro server risponde 400?
Provate anche con curl. L'opzione -v mostra tutto ciò che invia (righe che iniziano con >) e riceve (righe che iniziano con <):

curl -v localhost:3000/saluto
curl -v -H "Accept: text/markdown" localhost:3000/avviso
curl -v localhost:3000/inesistente
Ripetete la prova della tappa 4: fate inviare al server un Content-Length volutamente sbagliato. curl se ne accorge? E il browser?

Se il vostro computer e quello di un compagno sono nella stessa rete, aprite il suo archivio dal vostro browser usando il suo indirizzo IP. Funziona anche da uno smartphone collegato alla stessa rete?

Domanda: che cosa avete dovuto cambiare nella struttura dei messaggi per farvi capire dal browser? E nei nomi?

Tappa 9 – Sottocartelle
Limite: tutti i file devono stare nella stessa cartella.

Create in documenti una cartella avvisi con i file gita.txt e assemblea.md, e dentro di essa una cartella vecchi con un file natale.txt.

Regole
Ora la risorsa può contenere /: per esempio /avvisi/gita. La risorsa è valida se:

inizia con /;
divisa in segmenti con split("/"), ogni segmento dopo il primo (che è vuoto) è non vuoto e non contiene . né \.
Sono quindi rifiutate con 400 risorse come /avvisi//gita, /avvisi/, /../saluto, /avvisi/../../segreto.

L'estensione viene aggiunta all'ultimo segmento: /avvisi/gita → documenti/avvisi/gita.txt. Se il percorso indica una cartella, per esempio /avvisi, il server risponde 404.

Provate dal browser localhost:3000/avvisi/gita e localhost:3000/avvisi/vecchi/natale.

Sicurezza
Il controllo sui segmenti è ciò che impedisce di leggere file fuori dalla cartella documenti, per esempio il codice sorgente del server, che si trova nella cartella accanto. Provate a disattivarlo temporaneamente e verificate che cosa riuscite a leggere: questo tipo di attacco si chiama path traversal.

Il server aggiunge sempre un'estensione, quindi per la prova create temporaneamente un file segreto.txt nella cartella principale del progetto, accanto a documenti. Il browser e curl "ripuliscono" i percorsi con .. prima di inviarli: usate nc, scrivendo la richiesta a mano, oppure:

curl -v --path-as-is localhost:3000/../segreto
Tappa 10 – Chiedere informazioni e scrivere file
Limite: il server sa solo inviare file. Per conoscere tipo e dimensione di un file bisogna scaricarlo tutto, e non c'è modo di aggiungere un avviso senza accedere al computer del server.

Ci servono due nuovi comandi: uno che chiede solo le informazioni su un file, senza il contenuto, e uno che invia un file al server. Anche questi hanno nomi standard: scopriteli con curl, che sa usarli entrambi. Fermate il server, mettete in ascolto nc -l 3000 e osservate che cosa ricevete con:

curl -I localhost:3000/regole
Poi, sempre con nc -l 3000 in ascolto, create un file compiti.txt fuori dalla cartella documenti e inviatelo:

curl -T compiti.txt -H "Content-Type: text/plain; charset=utf-8" localhost:3000/avvisi/compiti
Quali comandi usa curl? Quali intestazioni invia? Dove si trova il testo del file?

Il comando informativo
Funziona come il comando di lettura, ma la risposta non ha contenuto: solo riga di stato e intestazioni. Content-Length indica comunque la lunghezza che il contenuto avrebbe.

Il comando di scrittura
Crea o sostituisce un file sul server. Il testo arriva dopo la riga vuota della richiesta: la richiesta ha ora un contenuto, come le risposte.

Qui chi invia non può chiudere la connessione per segnalare la fine del contenuto, perché deve ancora ricevere la risposta. Per questo Content-Length diventa indispensabile: il server legge esattamente quel numero di byte e poi risponde.

Situazione	Risposta
File creato	201 Created
File già esistente, sostituito	200 OK
Content-Length mancante	411 Length Required
Content-Length non numerico o negativo, Content-Type mancante, contenuto più corto del previsto	400 Bad Request
Content-Type non presente nella tabella dei tipi	415 Unsupported Media Type
Cartella di destinazione inesistente	404 Not Found
L'estensione del nuovo file si ricava da Content-Type usando la tabella della tappa 3. Il file viene salvato in UTF-8 nella cartella documenti, con le stesse regole sui percorsi della tappa 9.

Leggere il contenuto della richiesta
StringBuilder corpo = new StringBuilder();
int byteLetti = 0;
while (byteLetti < lunghezza) {
    int c = in.read();
    if (c == -1) break;   // contenuto incompleto
    corpo.append((char) c);
    byteLetti += String.valueOf((char) c).getBytes(StandardCharsets.UTF_8).length;
}
Questo frammento funziona con lettere accentate, ma non con le emoji: non usatele nei file di prova.

Prove
Riavviate il vostro server e ripetete i due comandi curl di sopra aggiungendo -v. Verificate con il browser che /avvisi/compiti sia ora consultabile. Inviate di nuovo lo stesso file: che codice ottenete?

Provate anche a mano:

printf 'COMANDO /prova VERSIONE\r\nContent-Type: text/plain\r\nContent-Length: 6\r\n\r\nCiao\r\n' | nc localhost 3000
sostituendo COMANDO e VERSIONE con quelli che avete scoperto. Ciao più \r\n fa 6 byte. Provate poi con Content-Length: 50: che cosa succede? Perché? Provate infine senza Content-Length e con un Content-Type non previsto.

Domande:

Si può inviare un file al server scrivendo un indirizzo nella barra del browser? Perché?
In questa versione chiunque può modificare i file del server. Quali problemi può causare? Come potrebbe il server sapere chi sta scrivendo?
Tappa 11 – Non riscaricare ciò che non è cambiato
Limite: chi richiede più volte lo stesso file lo riceve ogni volta per intero, anche se non è cambiato.

Last-Modified
Nelle risposte 200 dei comandi di lettura e di informazione il server aggiunge l'intestazione standard con la data dell'ultima modifica del file, nel formato standard per le date usato in Internet e nel fuso orario GMT:

Last-Modified: Tue, 10 Nov 2026 08:30:00 GMT
DateTimeFormatter formato = DateTimeFormatter.RFC_1123_DATE_TIME;
String data = formato.format(
    Instant.ofEpochMilli(file.lastModified()).atZone(ZoneOffset.UTC));
If-Modified-Since
Chi ha già una copia del file, e ne conosce la data Last-Modified, può aggiungere alla richiesta:

If-Modified-Since: Tue, 10 Nov 2026 08:30:00 GMT
Il server confronta le date al secondo:

se il file non è stato modificato dopo quella data, risponde 304 Not Modified, senza contenuto: chi ha chiesto userà la copia che possiede;
altrimenti risponde normalmente con 200 e il nuovo contenuto.
Instant richiesta = ZonedDateTime.parse(valore, formato).toInstant();
boolean nonModificato = file.lastModified() / 1000 <= richiesta.getEpochSecond();
Se la data non è nel formato corretto, il server ignora l'intestazione.

Prove
Con curl -I leggete la data Last-Modified di /saluto.
Con nc inviate una richiesta di /saluto con If-Modified-Since uguale a quella data: dovete ottenere 304. Con una data di un anno prima: 200.
Lo stesso con curl: curl -v -z "<data>" localhost:3000/saluto.
Modificate saluto.txt con un editor, oppure inviandolo di nuovo con il comando di scrittura, e ripetete la richiesta con la vecchia data: dovete ottenere 200 con il nuovo testo.
Aprite /saluto nel browser con gli strumenti per sviluppatori aperti (scheda Rete) e ricaricate la pagina. Guardate il registro del server: il browser invia If-Modified-Since? Che codice ottiene?
Domanda: il browser ha fatto questo da solo, senza che nessuno glielo chiedesse. Perché è vantaggioso per il server, per la rete e per l'utente?

Tappa 12 – Un archivio da sfogliare
Limite: dal browser si può leggere qualsiasi file, ma bisogna conoscerne il nome esatto. Un utente vorrebbe vedere l'elenco dei documenti e aprirli con un clic.

La pagina indice
Quando la risorsa indica una cartella (/, /avvisi, /avvisi/vecchi), il server non risponde più 404: costruisce una pagina con l'elenco del contenuto della cartella.

Il browser sa mostrare con impaginazione e collegamenti un documento di tipo text/html. Aggiungete alla tabella dei tipi la riga:

{"html", "text/html; charset=utf-8"},
e fate costruire al server, in uno StringBuilder, un documento HTML come questo:

<!DOCTYPE html>
<html>
<head><meta charset="utf-8"><title>Archivio /avvisi</title></head>
<body>
  <h1>Archivio /avvisi</h1>
  <ul>
    <li><a href="/avvisi/vecchi">vecchi/</a></li>
    <li><a href="/avvisi/assemblea">assemblea</a> (md, 120 byte)</li>
    <li><a href="/avvisi/gita">gita</a> (txt, 85 byte)</li>
  </ul>
  <p><a href="/">Torna all'inizio</a></p>
</body>
</html>
Regole:

elencate prima le sottocartelle, poi i file, in ordine alfabetico;
mostrate solo i file con un'estensione presente nella tabella dei tipi (quindi non note.log), senza estensione nel collegamento;
se due file hanno lo stesso nome con estensioni diverse, come avviso.txt e avviso.md, elencateli entrambi con il proprio tipo;
la pagina è una risposta come le altre: 200 OK, Content-Type: text/html; charset=utf-8, Content-Length corretto.
File[] elementi = cartella.listFiles();   // può restituire null: controllatelo
Arrays.sort(elementi);
Prove
Richiedete / con nc o con curl: che cosa ricevete? Poi aprite localhost:3000/ nel browser e navigate l'archivio solo con i clic. Che cosa fa il browser con lo stesso testo?
Aggiungete un file con il comando di scrittura e ricaricate l'indice: compare?
Mettete in documenti un file benvenuto.html scritto da voi e apritelo nel browser.
Facoltativo: se in una cartella esiste un file index.html, il server invia quello al posto dell'elenco generato.

Prova finale
Avviate il server e lasciatelo in esecuzione. Un compagno, dal proprio computer e usando solo il browser, deve riuscire a sfogliare il vostro archivio e leggere tutti i file. Poi, con curl, deve riuscire ad aggiungere un avviso, che dovete poter vedere ricaricando l'indice.

Consegna e riflessione
Consegnate il codice del server di ogni tappa completata, la cartella documenti con i file di prova, brevi istruzioni per avviare il server e, per ciascuna tappa, l'elenco delle prove eseguite (le richieste inviate con nc, curl o browser) con il loro esito. Indicate le tappe in cui avete incontrato difficoltà.

Rispondete inoltre a queste domande:

Disegnate la struttura di una richiesta e di una risposta nella versione finale, indicando le quattro parti e che cosa le separa.
Quale regola, scoperta nella tappa 2, ha permesso al vostro server di funzionare con un browser che non conoscevate? Perché è così importante nei protocolli di rete?
Fate un esempio di informazione che viaggia nell'intestazione e non nel contenuto, e spiegate perché.
Perché per il comando di scrittura Content-Length è indispensabile, mentre per le risposte del nostro server era solo utile?
Che differenza c'è tra un errore 4xx e uno 5xx? Chi deve correggere il problema in ciascun caso?
Il vostro server chiude la connessione dopo ogni risposta: una pagina con 30 immagini richiederebbe 31 connessioni. Il browser vorrebbe invece tenerla aperta. Che cosa dovrebbe cambiare nel server per usare una sola connessione per più richieste? Quale intestazione rende possibile riconoscere la fine di ogni risposta?
I messaggi della traccia viaggiavano in chiaro: lo avete visto con nc. Chi si trova sulla stessa rete può leggerli o modificarli? Che cosa servirebbe per proteggerli?
Nella prima riga di ogni richiesta e di ogni risposta compare ora la sigla del protocollo che avete adottato. Cercate in rete che cosa significa, in che anno è nata la versione 1.0 e quali differenze ci sono con la versione 1.1. Quali delle sue regole avete già implementato senza saperlo?
Oltre questa traccia
Restano fuori, e possono diventare approfondimenti: le connessioni persistenti (più richieste sulla stessa connessione, domanda 6), il trasferimento di file binari come le immagini, che richiede di leggere e inviare byte anziché righe, la sicurezza dei messaggi e degli utenti (domanda 7 e domanda 2 della tappa 10) e, per chi lo desidera, la scrittura di un proprio client in Java che segua le stesse regole.

 to join this conversation on GitHub. Already have an account? Sign in to comment
Footer
© 2026 GitHub, Inc.
Footer navigation
Terms
Privacy
Security
Status
Community
Docs
Contact
Manage cookies
Do not share my personal information
