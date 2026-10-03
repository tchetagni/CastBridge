STATUT: TERMINÉ, correctifs de l'audit Opus intégrés (voir « CORRECTIFS DE L'AUDIT » ci-dessous : ils remplacent les lignes contraires plus bas)

CORRECTIFS DE L'AUDIT (commit amendé, un seul)
1. Prolongation en heures : la fin FUSIONNÉE peut atteindre le 16/11 23:59:59.999 (15/11 + 1 jour de tolérance, UNE fois par location : PilotRules.extend, checkEnd(slackMs = DAY)) ; l'émission initiale reste bornée au 15/11. Après une prolongation, bandeau et phrases diront « avant le 16/11 » (refus : « …avant le 16/11 »). Testé : émissions 16/10, 20/10, 31/10, 01/11 puis prolongation ; bords exacts (15/11 23:59:59.999 accepté, +1 ms refusé) ; seconde prolongation refusée ; série de prolongations bornée. Une prolongation reste refusée après le 01/11 (fenêtre).
2. Réémission : exige une clé d'installation différente (ContractSummary.installPub d'origine, newInstallPub de la TV ; sinon refus « Même installation… » / origine inconnue / clé absente) ; jours = arrondi INFÉRIEUR, refus s'il reste moins d'un jour ; heures = min(accordées, 96 h) − usage, comptées au quota ; autorisée après le 01/11 jusqu'à la fin d'origine ; refus pour bouquet libre/Langues/inconnu. Signature : reissue(existing, usedMinutes, issuedAt, newInstallPub, state, params, catalog, families). La fin fusionnée d'une extension est contrôlée (checkEnd sur newStart + jours) ; pour une réémission la fusion est exclue par l'exigence d'installation différente.
3. Langues : refus par type « langues », préfixe d'identifiant « langues » ou PilotParams.freeBundles, quel que soit LotFamilies (spec, extend, reissue, userChosen=0). PilotParams.parse lit `freeBundles` (liste ou texte) ; toute clé inconnue est REFUSÉE (« clé inconnue »). Testé avec des familles volontairement fausses (reserved = tout).
4. Mineurs : quota en 168 h glissantes (PilotRegistry.hoursIssuedLast168h, dates Douala UTC+1 explicites, date seule = jusqu'à la fin du jour, horodaté AAAA-MM-JJTHH:MM accepté) ; la réémission compte ; Entry accepte quantité 0 pour une réémission ; code TV masqué imposé (PilotRegistry.mask, motif 2…2 vérifié par Entry et parse) ; écriture atomique SafeFile + verrou (PilotRegistry.update/load, test de 24 émissions simultanées, repli sur .bak, registre illisible = refus d'émettre) ; checkChosen vérifie produit = loc-<bouquet> (le contrôle des familles reste RentalPolicy.refusals, à appeler en plus) ; tests des bords de fenêtre (12/10 00:00, 01/11 23:59:59.999, 02/11 00:00).
COMPTES : PilotRulesTest 38, PilotRegistryTest 9, RentalDurationsTest 7 ; :core:test complet 2690 tests, 0 échec ; sender/receiver compileDebugKotlin OK ; verify_vectors.py 204/0. Rouge avant correctifs : 54 tests lancés, 13 en échec par assertion/exception, puis vert.
CAHIER w16-05 : en-tête amendé (relevé d'usage FRAIS avant prolongation/réémission, installPub, mask, update atomique).
RISQUES RESTANTS : le registre CSV ne stocke pas installPub (w16-05 doit le fournir depuis la demande d'appareil / GET /api/rental) ; LicenseState.hoursIssuedLast7d garde son nom (c'est maintenant 168 h glissantes) ; checkChosen non branché sur rentalCheck tant que w16-05 n'existe pas ; ancienne ligne ci-dessous « réémission refusée après le 01/11 » est caduque.

STATUT INITIAL: TERMINÉ (audit Opus obligatoire)
CAHIER: w16-04 · MODÈLE: sonnet · BRANCHE: claude/w16-04-pilot-rules · COMMIT: voir git log
PORTE: :core:test --tests '*PilotRules*' '*PilotRegistry*' '*RentalDurations*' '*LicensedIssuer*' → VERT (43 tests : 30 + 6 + 7)
SUITE COMPLÈTE: :core:test + :sender:compileDebugKotlin + :receiver:compileDebugKotlin → VERT (2679 tests, 0 échec) ; verify_vectors.py → 204 contrôles, 0 échec
ROUGE avant code : 40 tests lancés, 36 en échec (stubs : refus « TODO », résultats neutres), puis VERT.
FICHIERS: core/lots/PilotRules.kt, PilotRegistry.kt (neufs) ; RentalDurations.kt (+checkChosen, check inchangé) ; owner/LicensedIssuer.kt (+RentalChoice, RightsSyntax.rentalChoice ; RentalIssuing.right et IssueSpec intacts) ; tests PilotRulesTest, PilotRegistryTest, RentalDurationsTest ; test/resources/pilot.example.json. Rien dans sender/receiver. Aucune dépendance.
FUMÉE: sans objet (cœur seul).

TABLE DE REFUS (PilotRules.kt, messages français ; toutes les décisions passent par `rules{}` => Result.failure(PilotRefusal))
- bouquet : inconnu, sans lot, lot FREE ou famille inconnue (fail closed) => refus, dans spec, extend, y compris userChosen=0 (bundleProduct).
- fenêtre 12/10..01/11 (Douala, UTC+1) : avant/après => refus (spec, extend, reissue).
- spec : bouquet déjà loué actif ; relocation avant cooldownMin ; 4e contrat actif ; heures 1..96 ; jours 1..min(30, rentalDays) ; quota 192 h/7 j (heures seulement).
- extend : produit différent ; contrat terminé/futur/essai/incohérent ; unité différente (Default et Days = jours) => « On ne mélange pas… » ; heures : total accordé + h > 96 h => « a déjà X h… » ; quota ; fin +1 jour au-delà de 15/11 23:59 => refus ; jours : restant + ajout > 30 j ; fin > 01/12.
- reissue : > 3 fois, heures épuisées, contrat terminé, fenêtre fermée (pas de réémission après le 01/11).
CLAMPS : PilotParams.HARD_CAP_HOURS = RentalConfig().maxUseMinutesPerContract/60 = 96 (hourly.maxUseHours borné 1..96, test d'égalité) ; sûreté heures = min(30, jours entiers jusqu'au 15/11) (12/10:30, 25/10:21, 01/11:14) ; jours/défaut honorés jusqu'au 01/12 (checkEnd) ; défaut = min(maxDays, rentalDays ou defaultDays).
LIGNES PRODUITES (épinglées dans producedLinesKeepTenFieldsAndAreInBoundsForEveryTv, 10 champs, RentalLines.bounds = null) :
 12 h : rental|loc-classe-cm2|classe-cm2|T|T|30|0|720|3|  ; 7 j : …|7|0|0|3| ; défaut : …|30|0|0|3| ;
 prolongation 36 h : …|T2|T|1|0|2160|3|  (période conservée, 1 jour car le moteur ajoute les jours de la ligne à la fin ; durée de sûreté plus longue dépasserait le 15/11).
Test moteur : 60 h + 36 h => 5760, aucune note ; 37 h refusé alors que le moteur clamperait ; jours 7 + 14 => fin T+21 j.

CHOIX
- userChosen=0 : spec n'accepte que Default (durée exacte de daysOf, maxConcurrent = params), pas de fenêtre pilote ; extend et reissue refusés.
- Days(n) accepte tout n dans les bornes (pas seulement les paliers) ; paliers validés dans PilotParams pour l'interface.
- Prolongation en heures : durationDays = 1 (au lieu de « la borne de sûreté » du design, qui repousserait la fin au-delà du 15/11).
- Réémission : jours = ceil (date d'origine, jamais plus courte, sauf dépassement du 01/12), heures = floor (jamais plus tard). Réponse à la question du cahier : oui, même fin.
- Dates pilot.start/end : ms ou « AAAA-MM-JJ » (fin = dernière ms du jour). Registre : unites heures|jours|defaut, types nouvelle|prolongation|reemission, quota 7 jours calendaires, réémissions exclues.
- LicenseState.active peut contenir des contrats finis (endedAt) pour le délai de relocation ; l'appelant (w16-05) le construit depuis le registre / GET /api/rental.
NON FAIT : aucun branchement dans DK/louer.py (w16-05) ; checkChosen non branché dans rentalCheck.
QUESTION : une réémission après le 01/11 (réinstallation tardive) doit-elle rester refusée (design : « aucune émission après pilot.end ») ? Appliqué strictement.
AUDIT OPUS : table de refus ci-dessus, 96 h/+1 jour/15/11, mélange d'unités, FREE fail closed, userChosen=0, parse des bornes, registre (period en cours).
