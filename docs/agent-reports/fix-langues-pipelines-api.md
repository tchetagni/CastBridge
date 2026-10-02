# Correctifs d'audit des pipelines Langues + garde « niveau d'API Android »

Branche `claude/fix-langues-pipelines-api` (depuis `integration/agents` 7e2ca0d), non poussée.

## Constats de l'audit Opus

| # | Constat | Corrigé | Comment |
|---|---|---|---|
| 1 | TLS (heure fausse, autorité racine absente) : message technique anglais | oui | `TvLotFetcher.isCertificateProblem` (parcourt la chaîne des causes : `CertificateException` dont NotYetValid / Expired, `SSLHandshakeException`, `SSLPeerUnverifiedException`) ; `Blocked.CERTIFICATE_REFUSED` ; message « Certificat du serveur refusé : vérifiez la date et l'heure de la TV » aussi pour un échec au téléchargement (sans nouvelle tentative). Test avec doublure (`FakeRemote.failWith`). |
| 2 | `readNBytes` (API 33) dans `TvLotFetcher` et `LangLotConsumer` | oui | `castbridge.core.util.BoundedRead` (`readAll` borné qui lève `TooLarge`, `readUpTo` qui tronque) ; 7 tests dont limites exactes, flux infini, lectures courtes. |
| 3 | « Libre seulement » garanti par procédure | oui (validateur) / non (`families`) | Le constructeur n'écrivait aucun champ de licence dans `langue.json` (la licence n'était que dans le catalogue embarqué) : `LangLotBuilder.zip(dir, free=true)` ajoute `"license": "CC-BY-SA-4.0"` en tête de `langue.json` des lots `free` ; `LangLotValidator` (serveur) refuse un lot sans ce champ. Tests Java mis à jour (`LotLangValidatorTest`, `LotsLanguesApiTest`) + test Kotlin. `families` du fetcher **non relié** : le registre embarqué (`EmbeddedFreeSource.families()`) ne couvre que le démarrage embarqué et rejetterait tous les autres lots ; documenté dans `docs/LANGUES.md` § 15. |
| 4 | Éviction possible entre contrôle de place et installation | oui | `TvLotStore.installReceived(name, proof, evict = true)` ; le fetcher passe `evict = false` (refus « rien n'a été supprimé »). Test : un lot poussé par le téléphone pendant le téléchargement n'est pas évincé. |
| 5 | Activité tenue par le fil de téléchargement | oui | `LanguesActivity` : annulation dans `onStop`, fetcher construit avec `applicationContext`, fil = classe imbriquée `UpdateTask` (références faibles, drapeau d'annulation `AtomicBoolean` indépendant de l'Activity). |
| 6 | `startswith("http://localhost")` | oui | `urlparse` : hôte exactement `localhost`, `127.0.0.1` ou `::1`, sans identifiants ; test (localhost.example.com, 127.0.0.1.evil.com, userinfo, ftp, 0.0.0.0). |
| 7 | Quota téléphone 100 Mo / 500 Mo décidé | oui (documenté) | `docs/LANGUES.md` § 15 et `docs/HANDOFF.md`. Le code reste à 100 Mo (à faire). |

## Garde `ApiLevelGuardTest` (core, `:core:test`)

Analyse ligne à ligne (commentaires et chaînes retirés) de `src/main` de core, receiver, sender, owner, ownerlib, sshd, devbridge. 22 règles (readNBytes / readAllBytes / transferTo à un argument, fabriques `List/Set/Map.of|copyOf`, `Map.entry`, `Files.readString|writeString`, `Path.of`, `Optional` JDK 9-11, `Stream.toList`, `Collectors.toUnmodifiable*`, `requireNonNullElse`, `Objects.checkIndex`, `BigInteger.TWO`, parties de `Duration`, `strip*`, `indent/formatted/...`, `isBlank/lines/repeat` en `.java` seulement, `null*Stream`, `HexFormat`, `ProcessHandle/StackWalker`, `java.net.http`, `Math` JDK 9). Précision : ce que fournit la bibliothèque Kotlin (`isBlank()`, `lines()`, `repeat()`, `toList()`, `readBytes()`, `copyTo`, `listOf`) n'est jamais signalé (test des faux positifs). Échec avec `fichier:ligne [règle]` et le remplacement. Exceptions : `android/core/src/test/resources/api-level-allowlist.txt` (`chemin|RÈGLE|justification`, justification obligatoire, entrée périmée = échec). Non couverts (ambigus à l'écriture) : `ByteArrayOutputStream.toString(Charset)`, `Character.toString(int)`.

Occurrences réelles trouvées dans le code partagé / Android : 
1. `core/.../langues/LangLotConsumer.kt` `readPack` : `readNBytes` (déjà dans main) -> `BoundedRead.readAll`.
2. `core/.../lots/TvLotFetcher.kt` catalogue : `readNBytes` -> `BoundedRead.readAll`.
3. `core/.../remote/smart/Net.kt:94` : `readNBytes(MAX_BODY)` (réponses HTTP de la télécommande intelligente, non listé dans la demande) -> `BoundedRead.readUpTo` (même sémantique : tronque).
Faux positif écarté : `FileChannel.transferTo(pos, count, out)` (Lane.kt, Lanes.kt, ancien et valide) : la règle ne vise que la forme à un argument. Aucune autre occurrence ; aucune exception d'allowlist nécessaire. Hors périmètre : backend et outils de bureau (le backend utilise `readNBytes` / `Set.of` / `Path.of`, c'est du Java 17 serveur).

## Vérifications

:core:test complet : 2302 tests, 0 échec (dont ApiLevelGuardTest 2, BoundedReadTest 5, TvLotFetcherTest +2, LangLotBuildTest +1) ; :sender et :receiver compileDebugKotlin OK ; test_publish_lots 12 OK. Non vérifié ici : exécution sur TV, backend Maven (tests Java modifiés mais non lancés).
