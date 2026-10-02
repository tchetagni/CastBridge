STATUT: TERMINÉ (partie cœur ; partie téléphone listée plus bas)
CAHIER: sonnet-w15-05 · MODÈLE: sonnet · BRANCHE: claude/sonnet-w15-05
PORTE: :core:test --tests '*InFlight*' '*MultiVolume*' '*TvHardening*' '*Receiver*' MoveRequestsTest (+ MoverUnitTest, MoveTest, TvDedupe) → VERT
ROUGE avant correctif : InFlightTest.aFinishedHomonymOfAnotherSizeIsNotDone et aPartOfAnotherContentIsNeverResumed (AssertionError) ; MoverUnitTest.resumedPartWithCorruptMiddleIsNeverCommitted (ComparisonFailure : copie corrompue acceptée, source supprimée) et aCorruptionOutsideTheCheckedWindowStillKeepsTheSource. VERT après : mêmes tests.
FICHIERS (+ TvHardeningTest et UxTest ajustés hors liste, assertions seules): core tv/TvClient.kt, tv/ReceiverServer.kt, tv/Mover.kt, tv/MoveRequests.kt (neuf : MoveProof + MoveRequests), tests InFlightTest, MultiVolumeTest, tv/MoveRequestsTest (neuf), docs/agent-reports/sonnet-w15-05.md
Tableau situation => suppression de l'original/source :
1 octets envoyés par ce travail == taille + TV a tenu compte de la taille (sizeChecked) + done => oui
2 « done » sans octet envoyé (homonyme déjà là) => non
3 ancienne TV (pas de sizeChecked) => non (OLD_TV_TEXT)
4 « déjà là » taille égale seule => non ; taille + 64 Ko tête + 64 Ko queue égaux => oui (MoveProof.alreadyThere)
5 transfert rapide Done + racine vérifiée => oui, sinon non
6 Mover, copie reprise (quelconque taille) ou >= 100 Mo : SHA-256 complet égal => oui, sinon copie supprimée, source gardée
7 Mover, copie neuve < 100 Mo : taille + bords 1 Mo => oui
8 Mover annulé pendant le SHA / source modifiée pendant la preuve => non (copie à nous supprimée)
CHOIX: 409 NAME_TAKEN (code + message FR) et 409 PART_OTHER (Meta.total différent) ; /api/part?size= répond sizeChecked:true ; sans size la TV répond comme avant (compat, pas une preuve) ; findFinalOrOrigin(name,size) n'accepte plus un homonyme de taille différente ; PART_OTHER => le téléphone fait reset du .part puis renvoie tout ; Mover : fsync/64 Mo + « safe » en 6e ligne additive du marqueur .castbridge-move (au lieu de Meta, hors zone Progressive.kt), relecture de 8 Mo de queue par blocs de 1 Mo, troncature au dernier bloc identique (dossier réel seulement, sinon repart de 0), phase/checked dans MoveJob.json.
CHANGEMENT DE COMPORTEMENT : MultiVolumeServerTest.duplicatesAreReported... asserte désormais 409 NAME_TAKEN au lieu du remplacement silencieux d'un homonyme de taille différente (décision D-W15-05a).
NON FAIT (zone téléphone, UploadService.kt confié à un autre exécutant) : S/UploadService.kt (_moveReady/checkMoved à remplacer par MoveProof.byUpload avec octets réellement envoyés), S/MoveToTv.kt (MoveHandler lit MoveRequests persistée dans filesDir, une à une), S/TransferQueue.kt (« déjà là » + move : MoveProof.alreadyThere via /stream Range ; Bluetooth ignore move => ne pas supprimer), textes OLD_TV_TEXT / ALREADY_THERE_TEXT prêts dans MoveProof ; TvDedupe.alreadyThere exigeait déjà la taille (inchangé).
FUMÉE: à lancer par le coordinateur (tools/smoke/smoke.py --tv fake)
REGRESSIONS.md (ligne proposée) : W15-05 · homonyme fini de taille différente pris pour « done » / .part d'un autre contenu complété / Mover supprimant sur copie reprise corrompue · InFlightTest + MultiVolumeTest(MoverUnitTest)
QUESTION d'audit : 409 NAME_TAKEN casse-t-il une reprise après renommage TV ? Non : seul findFinal(name) strict déclenche, et seulement sans .part ; l'origine reste retrouvée par origin + taille égale.
RISQUES/AUDIT : un .part présent à côté d'un final de taille différente passe encore (commit remplace) ; Meta absent = .part repris comme avant ; recover() n'a que sameEdges (marqueur verified écrit après preuve complète) ; vérifier check(proven) et les 2 deleteFinal de source (run, recover).
SUITE COMPLÈTE: :core:test → 2390 tests, 2 rouges (MoverFaultTest.aFullDrive..., UxTest.aCopyInProgress...) dus au changement voulu ; tests ajustés (TvHardeningTest : offset de reprise = 1er open > 0 ; UxTest : renommé, asserte 409 NAME_TAKEN) puis relancés VERT avec journey/lint ; pas de rerun complet après ajustement ; aucun blocage ByteRelayTest. :receiver:compileDebugKotlin et :sender:compileDebugKotlin VERT.
