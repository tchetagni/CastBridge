#!/usr/bin/env python3
"""Les dix processus BPMN de l'architecture de CastBridge, décrits de façon compacte (bibliothèque standard seulement).

Chaque processus est un dictionnaire :
    id, title, summary (3 à 6 phrases, affichées sur la page ; `code` entre accents graves),
    pools    : (id, nom, famille, [(id de lane, nom de lane), …]) ; famille = user | phone | tv | server | usb | mac | other
    nodes    : une ligne par nœud « id | lane | type | libellé | options | documentation » (options et documentation facultatives)
    flows    : « source -> cible : libellé » (flux de séquence ; un libellé qui finit par « * » marque le flux par défaut)
    messages : « source => cible : libellé » (flux de message, d'un pool à un autre)
    sources  : documents lus ; code : (étape ou règle, repère dans le dépôt)

Types de nœud : start, start:message, start:timer, end, end:message, end:error, end:terminate, task, user, service,
send, receive, xor, and, or, evgw, catch:message|timer|condition|signal, throw:message, boundary:timer|error|message|condition.
Options : attach=<tâche> (événement limite), dx=<px>, row=<n> (rangée voulue dans la lane), rank=<n> (colonnes de décalage vers la droite),
lbl=N|S (côté du libellé), dur=<durée ISO 8601> (minuteur).

Le chargeur vérifie les références ; le générateur (bpmn_gen.py) calcule la mise en page et écrit les fichiers .bpmn.
"""
import re

KINDS = {"user", "phone", "tv", "server", "usb", "mac", "other"}
NODE_TYPES = {"start", "start:message", "start:timer", "end", "end:message", "end:error", "end:terminate", "task", "user", "service", "send",
              "receive", "xor", "and", "or", "evgw", "catch:message", "catch:timer", "catch:condition", "catch:signal", "throw:message",
              "boundary:timer", "boundary:error", "boundary:message", "boundary:condition"}
TASK_TYPES = {"task", "user", "service", "send", "receive"}
_FLOW = re.compile(r"^([A-Za-z0-9_]+)\s*(->|=>)\s*([A-Za-z0-9_]+)\s*(?::\s*(.*))?$")


def _lines(text):
    for raw in text.strip().splitlines():
        line = raw.strip()
        if line and not line.startswith("#"):
            yield line


def load(raw):
    pid = raw["id"]

    def bad(msg):
        raise ValueError("processus %s : %s" % (pid, msg))

    pools, lane_pool = [], {}
    for p_id, name, kind, lanes in raw["pools"]:
        if kind not in KINDS:
            bad("famille inconnue %r" % kind)
        pool = {"id": p_id, "name": name, "kind": kind, "lanes": []}
        for l_id, l_name in lanes:
            if l_id in lane_pool:
                bad("lane en double %r" % l_id)
            pool["lanes"].append({"id": l_id, "name": l_name})
            lane_pool[l_id] = pool
        pools.append(pool)
    nodes, ids = [], set()
    for line in _lines(raw["nodes"]):
        f = [x.strip() for x in line.split("|")]
        if len(f) < 4:
            bad("nœud mal formé : " + line)
        nid, lane, typ, label = f[:4]
        opts_s = f[4] if len(f) > 4 else ""
        doc = f[5] if len(f) > 5 else ""
        if nid in ids:
            bad("nœud en double %r" % nid)
        if typ not in NODE_TYPES:
            bad("type inconnu %r pour %s" % (typ, nid))
        if lane not in lane_pool:
            bad("lane inconnue %r pour %s" % (lane, nid))
        opts = {}
        for tok in opts_s.split():
            k, _, v = tok.partition("=")
            opts[k] = v
        node = {"id": nid, "lane": lane, "pool": lane_pool[lane]["id"], "type": typ, "label": label, "doc": doc,
                "attach": opts.get("attach"), "dx": int(opts["dx"]) if "dx" in opts else None,
                "row_hint": int(opts["row"]) if "row" in opts else None, "rank_pad": int(opts["rank"]) if "rank" in opts else 0, "lbl_side": opts.get("lbl"), "dur": opts.get("dur")}
        if typ.startswith("boundary") != bool(node["attach"]):
            bad("événement limite et attach= vont ensemble (%s)" % nid)
        nodes.append(node)
        ids.add(nid)
    by_id = {n["id"]: n for n in nodes}
    for n in nodes:
        if n["attach"]:
            host = by_id.get(n["attach"])
            if host is None or host["type"] not in TASK_TYPES:
                bad("%s : l'événement limite doit s'attacher à une tâche" % n["id"])
            if host["lane"] != n["lane"]:
                bad("%s : l'événement limite doit être dans la lane de sa tâche" % n["id"])
    flows, messages, seen = [], [], {}
    for kind, key in (("flows", "->"), ("messages", "=>")):
        for line in _lines(raw.get(kind, "")):
            m = _FLOW.match(line)
            if not m or m.group(2) != key:
                bad("flux mal formé (%s) : %s" % (kind, line))
            src, _, dst, label = m.groups()
            if src not in by_id or dst not in by_id:
                bad("flux vers un nœud inconnu : " + line)
            label = (label or "").strip()
            default = False
            if kind == "flows" and label.endswith("*"):
                default, label = True, label[:-1].strip()
            base = "%s__%s" % (src, dst)
            seen[base] = seen.get(base, 0) + 1
            fid = base if seen[base] == 1 else "%s_%d" % (base, seen[base])
            e = {"id": fid if kind == "flows" else "m_" + fid, "src": src, "dst": dst, "label": label, "default": default}
            if kind == "flows":
                if by_id[src]["pool"] != by_id[dst]["pool"]:
                    bad("un flux de séquence ne traverse pas les pools : " + line)
                flows.append(e)
            else:
                if by_id[src]["pool"] == by_id[dst]["pool"]:
                    bad("un flux de message relie deux pools différents : " + line)
                messages.append(e)
    out_seq = {f["src"] for f in flows}
    in_seq = {f["dst"] for f in flows}
    in_msg = {m["dst"] for m in messages}
    for n in nodes:
        t = n["type"]
        if not t.startswith("end") and n["id"] not in out_seq:
            bad("%s n'a aucun flux sortant" % n["id"])
        if t.startswith("end") and n["id"] in out_seq:
            bad("%s est un événement de fin avec un flux sortant" % n["id"])
        if not t.startswith(("start", "boundary")) and n["id"] not in in_seq:
            bad("%s n'a aucun flux entrant" % n["id"])
        if t.startswith("start") and n["id"] in in_seq:
            bad("%s est un début avec un flux entrant" % n["id"])
    for m in messages:
        s, d = by_id[m["src"]], by_id[m["dst"]]
        if s["type"] in GATE_TYPES or d["type"] in GATE_TYPES:
            bad("un flux de message ne touche pas une passerelle : %s => %s" % (m["src"], m["dst"]))
        if d["type"].startswith(("start", "end")) and not d["type"].startswith("start:message"):
            bad("%s reçoit un message mais n'est pas un début de message" % d["id"])
    return {"id": pid, "title": raw["title"], "summary": " ".join(raw["summary"].split()), "sources": raw.get("sources", []),
            "code": raw.get("code", []), "pools": pools, "nodes": nodes, "flows": flows, "messages": messages}


GATE_TYPES = {"xor", "and", "or", "evgw"}
RAW = []

# ========================================================================================================== 1. copie-fichier
RAW.append(dict(
    id="copie-fichier",
    title="Copier un fichier du téléphone vers la TV",
    summary="""
    Un partage ou une sélection entre dans la file d'envoi du téléphone, qui n'envoie qu'un fichier à la fois (R-09). La file attend au plus une minute
    le lien de confiance, puis choisit la route : réseau local, sinon Wi-Fi Direct automatique (groupe CBTN) à partir de 5 Mo, sinon elle attend le Wi-Fi
    45 secondes (15 si aucune route Wi-Fi n'a été vue depuis dix minutes) avant de se rabattre sur le Bluetooth. La TV écrit `<nom>.part` et `<nom>.meta`.
    Une coupure reprend à l'octet confirmé après 1, 2, 5, 10 secondes puis toutes les 15 secondes ; un envoi Bluetooth repasse en Wi-Fi quand la route est
    stable depuis 3 secondes, s'il reste au moins 1 Mo et au plus deux fois (R-54). Une copie interrompue s'annule depuis la TV : son nom est refusé dix
    minutes (400, 409, TV_CANCELLED, R-51).
    """,
    pools=[
        ("user", "Utilisateur / Propriétaire", "user", [("u1", "Sur le téléphone")]),
        ("phone", "CastBridge (téléphone)", "phone", [("q", "File d'envoi"), ("s", "Envoi (UploadService, BtUploadService)")]),
        ("tv", "CastBridge-TV", "tv", [("r", "Réception (ReceiverServer, BtServer)"), ("b", "Écran de la TV")]),
    ],
    nodes="""
        u_start | u1 | start           | Copier un fichier vers la TV
        u_send  | u1 | send            | Partager ou sélectionner, puis « Copier » | | Ouvrir avec, Partager ou sélection dans CastBridge ; tout passe par la file (R-09).
        u_notif | u1 | catch:message   | Notification de fin
        u_end   | u1 | end             | Fichier prêt sur la TV
        q_in    | q  | start:message   | Copie demandée : mise en file | | Un seul envoi à la fois (UploadSlot) ; la file survit à la mort du processus.
        q_link  | q  | task            | Attendre le lien de confiance (1 min au plus) | | TransferQueue.waitForTv ; code 8 (je ne vous reconnais plus) : échec immédiat avec le code à saisir.
        g_route | q  | xor             | Quelle route ? | | Ordre : réseau commun, Wi-Fi Direct automatique, attente du Wi-Fi, Bluetooth (BulkRoute).
        evgw    | s  | evgw            | Attendre le Wi-Fi (45 s ou 15 s) | | BulkRoute.waitForWifiMs : 0 sous 5 Mo ; 45 s si une route Wi-Fi était vivante il y a moins de 10 min, sinon 15 s.
        c_wifi  | s  | catch:condition | Route Wi-Fi vue
        c_timer | s  | catch:timer     | Délai écoulé | dur=PT45S
        s_http  | s  | send            | Envoyer en HTTP (multivoie ou PUT /upload) | | Multivoie /api/transfer si la TV le sait, sinon PUT /upload reprenable (GET /api/part).
        s_bt    | s  | send            | Envoyer en Bluetooth (RFCOMM) | rank=1 | BtUploadService ; la TV écrit nom.part et nom.meta depuis la 0.14.54.
        b_cut   | s  | boundary:error  | Coupure | attach=s_http
        b_ref   | s  | boundary:message | Refus de la TV | attach=s_http
        b_wifi  | s  | boundary:condition | Wi-Fi stable 3 s | attach=s_bt
        s_fail  | s  | end:error       | Échec : copie annulée sur la TV
        s_done  | s  | end:message     | Notifier : copie terminée
        r_recv  | r  | start:message   | Réception d'un fichier (HTTP ou RFCOMM)
        r_gate  | r  | xor             | Nom refusé (10 min) ? | | ReceiverServer : marque de refus posée par cancelReception, fenêtre TV_CANCEL_REFUSE_MS = 10 min.
        r_refuse| r  | end:message     | Répondre 400 ou 409 TV_CANCELLED
        r_write | r  | task            | Écrire nom.part et nom.meta
        r_or    | r  | or              | Copier et lire ?
        r_final | r  | task            | Vérifier, renommer, ranger en bibliothèque | | Relecture et SHA-256 des blocs (multivoie), renommage atomique, vignette.
        l_play  | r  | task            | Lire le .part pendant la copie | | Lecture progressive du fichier qui grandit (Progressive, PlaybackPriority).
        r_end   | r  | end             | Fichier dans la bibliothèque
        c_start | b | start | Copie interrompue (puce d'accueil)
        c_task  | b | user | MENU › Copie : annuler
        c_do    | b  | service         | cancelReception : refus 10 min, effacer le .part
        c_end   | b  | end             | Copie annulée
    """,
    flows="""
        u_start -> u_send
        u_send -> u_notif
        u_notif -> u_end
        q_in -> q_link
        q_link -> g_route
        g_route -> s_http : Réseau local ou Wi-Fi Direct (≥ 5 Mo)
        g_route -> s_bt : Aucune route, moins de 5 Mo
        g_route -> evgw : Aucune route, 5 Mo ou plus
        evgw -> c_wifi
        evgw -> c_timer
        c_wifi -> s_http
        c_timer -> s_bt
        s_http -> s_done
        s_bt -> s_done
        b_cut -> g_route : Attendre 1, 2, 5, 10 s puis 15 s
        b_ref -> s_fail
        b_wifi -> g_route : Arrêter le Bluetooth, reprendre en Wi-Fi
        r_recv -> r_gate
        r_gate -> r_refuse : Oui
        r_gate -> r_write : Non *
        r_write -> r_or
        r_or -> r_final : Toujours *
        r_or -> l_play : « Copier et lire »
        r_final -> r_end
        l_play -> r_end
        c_start -> c_task
        c_task -> c_do
        c_do -> c_end
    """,
    messages="""
        u_send => q_in : Copie demandée
        s_http => r_recv : PUT /upload ou /api/transfer
        s_bt => r_recv : RFCOMM
        r_refuse => b_ref : 400 / 409
        s_done => u_notif : Notification
    """,
    sources=["docs/TRANSFER.md § 1-3, 12-13", "docs/REGRESSIONS.md R-19, R-51, R-54"],
    code=[
        ("File d'envoi, un seul créneau, issues d'un envoi", "`android/sender/.../TransferQueue.kt`, `android/core/.../tv/UploadGuards.kt` (UploadSlot, QueueOutcome)"),
        ("Choix de la route, attente du Wi-Fi, bascule Bluetooth vers Wi-Fi", "`android/core/.../link/BulkRoute.kt`, `android/sender/.../AutoWifiDirect.kt`"),
        ("Reprise 1, 2, 5, 10 s puis 15 s", "`android/core/.../tv/TvReach.kt` (RetryBackoff, ResumeWait)"),
        ("Envoi reprenable et multivoie", "`android/core/.../tv/TvClient.kt` (ResumableUpload), `android/core/.../xfer/` (TransferClient, PartAssembler)"),
        ("Réception, .part et .meta, refus de 10 minutes", "`android/core/.../tv/ReceiverServer.kt` (cancelReception), `android/core/.../tv/BtProtocol.kt`"),
        ("Lecture pendant la copie", "`android/core/.../xfer/PlaybackPriority.kt`, `android/core/.../tv/Progressive.kt`"),
    ],
))

# ========================================================================================================== 2. lien-confiance
RAW.append(dict(
    id="lien-confiance",
    title="Établir et garder le lien de confiance",
    summary="""
    Le téléphone retrouve la TV par mDNS (`_castbridge._tcp`) ou par Bluetooth, puis obtient un jeton par un HELLO sur le Bluetooth appairé : la TV ne répond
    qu'à un pair appairé et de confiance, et ne lui livre le jeton (256 bits, 12 heures, gardé haché côté TV) qu'à lui. Sans Bluetooth, le PIN à 6 chiffres est
    saisi une seule fois par TV (une fiche par TV, jamais redemandée en changeant de TV ou de session : règle du 2026-10-06). Un garde-vivant de deux sondes
    vérifie le jeton toutes les 15 secondes (5 minutes en arrière-plan), et le jeton est renouvelé à mi-vie. Si la route Wi-Fi tombe, le lien se replie sur
    Wi-Fi Direct puis sur le Bluetooth avec le même jeton, et reprend la route la plus rapide dès qu'elle répond. Un jeton refusé (TV réinstallée, téléphone
    retiré) provoque un nouveau HELLO ; la réponse de code 8 (`ERR_UNTRUSTED`) demande de réassocier.
    """,
    pools=[
        ("phone", "CastBridge (téléphone)", "phone", [("p1", "Boucle du lien (LinkDriver)"), ("p2", "Association (PairFlow, PinBook)")]),
        ("tv", "CastBridge-TV", "tv", [("t1", "Annonce mDNS"), ("t2", "HELLO et registre"), ("t3", "API HTTP (ReceiverServer)")]),
    ],
    nodes="""
        p_start | p1 | start            | Ouverture, réseau ou tâche périodique | | Déclencheurs : ouverture de l'app, écran allumé, réseau, diffusions Bluetooth, job périodique de 15 min.
        p_known | p1 | xor              | TV déjà ajoutée ?
        a_bond  | p2 | service          | Appairer en Bluetooth, demander l'ajout | | Comparaison du code sur les deux écrans, puis demande d'ajout à la TV (PairFlow).
        a_bt    | p2 | xor              | Bluetooth utilisable ?
        a_pin   | p2 | user | Saisir le PIN à 6 chiffres |  | Une seule fois par TV. PinBook : une fiche par TV (bt:<adresse>, name:, host:), jamais effacée sur refus.
        p_find  | p1 | task             | Chercher la TV : mDNS, sinon Bluetooth
        p_tok   | p1 | xor              | Jeton valide (marge de 60 s) ?
        p_hello | p1 | service          | HELLO Bluetooth (RFCOMM, TV appairée) | | Un HELLO ne porte jamais le PIN ; 10 HELLO par minute au plus par pair côté TV (ERR_BUSY).
        b_deny  | p1 | boundary:message | Refus : code 8 ou 9 | attach=p_hello
        p_conn  | p1 | task             | Connecté : route la plus rapide qui répond | | Priorité : réseau local, Wi-Fi Direct, Bluetooth seul.
        p_ka    | p1 | catch:timer      | Garde-vivant : 15 s (5 min en arrière-plan) | dur=PT15S
        p_probe | p1 | service          | Deux sondes GET /api/info avec le jeton | | Jamais un seul délai : deux essais.
        p_resp  | p1 | xor              | Réponse de la TV ?
        p_fall  | p1 | task             | Repli : Wi-Fi Direct, puis Bluetooth (même jeton)
        p_err   | p1 | end:error        | TV non reconnue : réassocier
        n_ann   | t1 | start            | TV allumée
        n_pub   | t1 | end:message      | Annoncer _castbridge._tcp (role=receiver)
        h_recv  | t2 | start:message    | HELLO reçu
        h_peer  | t2 | xor              | Pair appairé et de confiance ?
        h_deny  | t2 | end:message      | Erreur d'un octet (8, 9, 12 ou 14)
        h_ans   | t2 | end:message      | Répondre : nom, IP:port, jeton de 12 h | | Identifiant d'installation de la TV ; jeton de 256 bits gardé haché, renouvelé à mi-vie.
        t_win   | t2 | start:message    | Demande d'ajout reçue
        t_auth  | t2 | user             | Autoriser ce téléphone ? (fenêtre de 2 min) | | « Refuser » présélectionné ; trois refus bloquent le téléphone 10 minutes.
        t_ok    | t2 | end:message      | Téléphone inscrit (8 au plus)
        i_recv  | t3 | start:message    | GET /api/info avec X-CB-Token
        i_gate  | t3 | xor              | Jeton connu et valide ?
        i_ok    | t3 | end:message      | 200 : la TV répond
        i_no    | t3 | end:message      | Jeton refusé (jamais un faux PIN)
    """,
    flows="""
        p_start -> p_known
        p_known -> p_find : Oui *
        p_known -> a_bond : Non
        a_bond -> a_bt
        a_bt -> p_find : Oui *
        a_bt -> a_pin : Non
        a_pin -> p_find
        p_find -> p_tok
        p_tok -> p_conn : Oui
        p_tok -> p_hello : Non *
        p_hello -> p_conn
        b_deny -> p_err
        p_conn -> p_ka
        p_ka -> p_probe
        p_probe -> p_resp
        p_resp -> p_ka : Jeton valide *
        p_resp -> p_hello : Mi-vie ou jeton refusé
        p_resp -> p_fall : Aucune réponse
        p_fall -> p_ka
        n_ann -> n_pub
        h_recv -> h_peer
        h_peer -> h_deny : Non
        h_peer -> h_ans : Oui *
        t_win -> t_auth
        t_auth -> t_ok
        i_recv -> i_gate
        i_gate -> i_ok : Oui *
        i_gate -> i_no : Non
    """,
    messages="""
        n_pub => p_find : mDNS
        a_bond => t_win : Demande d'ajout
        t_ok => a_bond : Autorisé
        p_hello => h_recv
        h_deny => b_deny : Refus
        h_ans => p_hello : Jeton
        p_probe => i_recv : GET /api/info
        i_ok => p_probe : 200
        i_no => p_probe : 401
    """,
    sources=["docs/BT-PLUG-AND-PLAY.md (en-tête, sécurité, états du lien, pertes et reprises)", "android/core/.../trust/LinkDriver.kt (commentaire de tête)"],
    code=[
        ("Boucle du lien : garde-vivant, repli de route, renouvellement du jeton", "`android/core/.../trust/LinkDriver.kt`, `LinkMachine.kt` (états et délais)"),
        ("HELLO côté TV : pair de confiance, jeton haché, 8 téléphones au plus", "`android/core/.../trust/HelloHandler.kt`, `TrustRegistry.kt`, `TrustFiles.kt`"),
        ("Codes d'erreur du HELLO (ERR_UNTRUSTED = 8, ERR_DENIED = 9, ERR_BUSY = 12, ERR_FULL = 14)", "`android/core/.../tv/BtProtocol.kt`"),
        ("PIN gardé par TV, jamais redemandé", "`android/core/.../trust/PinBook.kt`, `android/sender/.../PinStore.kt`"),
        ("Appairage, fenêtre « Ajouter un téléphone » de 2 minutes", "`android/core/.../trust/PairFlow.kt`, `PairingSession.kt`"),
        ("Découverte mDNS et annonce de la TV", "`android/sender/.../TvDiscovery.kt`, `android/receiver/.../TvService.kt`"),
    ],
))

# ========================================================================================================== 3. activation-tv
RAW.append(dict(
    id="activation-tv",
    title="Activer une TV verrouillée",
    summary="""
    Une TV verrouillée (build `-PrequireActivation=true`) n'expose que la surface d'activation : le code de connexion à 6 chiffres, l'annonce mDNS `locked=1`,
    l'annonce BLE `…0008` et le groupe Wi-Fi Direct dérivé du code. Une installation antérieure au 2026-10-02 garde une grâce de 30 jours, jusqu'au 2026-11-01,
    avec les restrictions de l'essai. La clé arrive par l'une des voies : celles du téléphone, classées par priorité (réseau local 10 s, Bluetooth sans appairage
    25 s, Wi-Fi Direct dérivé du code 50 s, Bluetooth appairé 20 s), la clé USB préparée par l'agent, ou la saisie à la télécommande. Quelle que soit la voie, la
    TV vérifie la signature d'une clé de confiance, le matériel (k parmi n), la fenêtre de 48 heures et sa propre clé d'installation (`install_sig`). Une clé de
    production s'ajoute à la clé d'essai ; la TV notifie ensuite le serveur, qui ouvre ou rattache la licence et le poste, ou met la ligne en attente du
    propriétaire. Les décisions D-ACT-1 à D-ACT-3 restent ouvertes.
    """,
    pools=[
        ("user", "Utilisateur / Propriétaire", "user", [("o1", "Devant la TV")]),
        ("usb", "Clé USB", "usb", [("k1", "Fichier d'activation")]),
        ("phone", "CastBridge (téléphone)", "phone", [("p1", "Activer la TV (ActivationDriver)")]),
        ("tv", "CastBridge-TV", "tv", [("t1", "État et écran d'activation (FeatureGate)"), ("t2", "Réception de la clé (ActivationReceiver)")]),
        ("server", "Serveur bridge.sti-cm.com", "server", [("v1", "Licences et portefeuille")]),
    ],
    nodes="""
        o_start | o1 | start            | TV verrouillée, ni téléphone ni clé
        o_rc    | o1 | end:message      | Saisir la clé compacte (165 caractères)
        k_start | k1 | start            | L'agent prépare la clé du client
        k_write | k1 | task             | emettre --cle-usb : fichiers écrits et relus | | castbridge-activation emettre --appareil demande.txt --cle-usb : 4 fichiers (Android/data/castbridge.receiver/files/activation, Download/CastBridge/activation, racine, LISEZMOI), chacun relu (taille, SHA-256).
        k_plug  | k1 | end:message      | Clé branchée sur la TV
        a_start | p1 | start            | « Activer la TV » : code de connexion saisi | | Le code à 6 chiffres affiché par la TV suffit (règle du 2026-10-07).
        a_plan  | p1 | task             | Essayer les voies : réseau local, Bluetooth, Wi-Fi Direct | | ActivationRoutePlan : réseau local (10 s), Bluetooth sans appairage (25 s), réseau de la TV (50 s), Bluetooth appairé (20 s, 90 s si appairage).
        a_joined| p1 | xor              | TV jointe ?
        a_req   | p1 | service | Lire la demande d'appareil |  | GET /api/activation/device-request avec le code dans X-CB-Pin ; la demande porte code=, k=, factor= et install_sig=, jamais install=.
        a_key   | p1 | task             | Obtenir la clé : console, collage ou fichier | | Console du propriétaire (émettre et installer), clé collée ou fichier ; la demande au serveur est éteinte (ServerActivationRequests.ENABLED = false).
        a_send  | p1 | end:message      | Envoyer la clé et le code à la TV | | POST /api/activation/install ; la clé et le code ne partent qu'à la TV qui a répondu 200.
        a_none  | p1 | end              | Dernier recours : clé USB ou télécommande
        s0      | t1 | start            | TV démarrée (version verrouillée)
        g_state | t1 | xor              | État au démarrage ?
        t_grace | t1 | task             | Grâce : API complète, restrictions de l'essai | | Installation antérieure au 2026-10-02 avec clock.txt, jusqu'au 2026-11-01 ; aucune route verrouillée ni annonce.
        t_lock  | t1 | task             | Écran d'activation : conditions et code à 6 chiffres | | Conditions d'usage à accepter avant toute lecture de clé ; annonces mDNS locked=1, BLE …0008, groupe Wi-Fi Direct dérivé du code.
        evgw    | t1 | evgw             | Attendre une clé
        c_rc    | t2 | catch:message    | Clé saisie à la télécommande
        c_usb   | t2 | catch:message    | Fichier d'activation sur la clé USB
        c_net   | t2 | catch:message    | Clé reçue du téléphone
        k_ver   | t2 | task             | Vérifier : signature, matériel, 48 h, clé d'installation | | ActivationReceiver : ActivationVerifier puis ActivationBinding ; les clés de confiance sont compilées dans la TV (BuildConfig.TRUSTED_KEYS).
        k_ok    | t2 | xor              | Clé valable ?
        k_ko    | t2 | end | Refus avec la cause |  | Clé périmée, faite pour une autre TV, clé de confiance inconnue, conditions d'usage non acceptées.
        k_inst  | t2 | task             | Installer la clé (la production s'ajoute à l'essai) | | InstalledActivations garde l'essai et la production ; TvGate rend essai, production ou super illimité.
        k_done  | t2 | end:message      | TV activée : notifier le serveur
        v_in    | v1 | start:message    | Activation notifiée (preuve bind, install_sig)
        v_chk   | v1 | xor              | Clé de confiance, ik signée, 48 h, plafonds ?
        v_open  | v1 | end              | Licence et poste ouverts ou rattachés
        v_wait  | v1 | end              | En attente de la décision du propriétaire
    """,
    flows="""
        o_start -> o_rc
        k_start -> k_write
        k_write -> k_plug
        a_start -> a_plan
        a_plan -> a_joined
        a_joined -> a_req : Oui *
        a_joined -> a_none : Non
        a_req -> a_key
        a_key -> a_send
        s0 -> g_state
        g_state -> t_lock : Verrouillée *
        g_state -> t_grace : Grâce en cours
        t_lock -> evgw
        t_grace -> evgw
        evgw -> c_rc
        evgw -> c_usb
        evgw -> c_net
        c_rc -> k_ver
        c_usb -> k_ver
        c_net -> k_ver
        k_ver -> k_ok
        k_ok -> k_inst : Oui *
        k_ok -> k_ko : Non
        k_inst -> k_done
        v_in -> v_chk
        v_chk -> v_open : Oui *
        v_chk -> v_wait : Non
    """,
    messages="""
        o_rc => c_rc : Clé compacte
        k_plug => c_usb : Clé branchée
        a_send => c_net : POST /api/activation/install
        k_done => v_in : wallet/sync ou rapport
    """,
    sources=["docs/ACTIVATION-ETATS-ET-VOIES.md (états, voies, D-ACT-1 à D-ACT-3)", "docs/TV-ACTIVATION-CLE-USB.md (en-tête)"],
    code=[
        ("États de la TV : NotRequired, Locked, Grace, Activated", "`android/core/.../owner/FeatureGate.kt` (FeatureGate, GateState, FleetMigration)"),
        ("Voies du téléphone par priorité et bornes", "`android/core/.../owner/ActivationRoutePlan.kt`, `android/sender/.../ActivationDriver.kt`"),
        ("Bluetooth sans appairage (act-bt, PAKE sur le code)", "`android/receiver/.../ActivationBtHost.kt`, `android/sender/.../BtActivationClient.kt`"),
        ("Clé USB préparée par l'agent et lecture par la TV", "`tools/activation-desktop` (castbridge-activation), `android/receiver/.../UsbActivationWatch.kt`"),
        ("Vérification de la clé : signature, matériel, 48 h, clé d'installation", "`android/core/.../owner/Activation.kt` (ActivationVerifier, TvGate, ActivationBinding)"),
        ("Notification et licence côté serveur", "`backend/.../wallet/WalletSyncController.java`, `docs/LICENSE-ADMIN.md` § 5.1"),
    ],
))

# ========================================================================================================== 4. lecture-video
RAW.append(dict(
    id="lecture-video",
    title="Lire une vidéo sur la TV",
    summary="""
    Un fichier ouvert avec CastBridge (Ouvrir avec, Partager) passe par le lecteur du téléphone (Media3), puis par « Caster ». Vers une CastBridge-TV : lecture
    directe (`POST /api/play` ou `/api/playurl`) ou « Copier et lire », où la TV lit le fichier pendant qu'il arrive. Si CastBridge-TV n'est pas devant, la TV
    répond 409 `needsForeground` : le téléphone l'ouvre (`OpenTvFlow`) puis réessaie jusqu'à cinq fois. Pendant une copie, la lecture passe d'abord : la copie est
    plafonnée à la moitié du débit d'écriture du disque (de 1 à 6 Mo/s, 40 % sur le même disque) et la TV affiche « Copie ralentie ». Vers une TV tierce, le
    téléphone envoie `SetAVTransportURI` (DLNA) ; l'application YouTube trouve la TV par DIAL (SSDP, port 30765) et lance YouTube TV.
    """,
    pools=[
        ("user", "Utilisateur / Propriétaire", "user", [("u1", "Sur le téléphone")]),
        ("phone", "CastBridge (téléphone)", "phone", [("p1", "Lecteur et « Caster » (player/)")]),
        ("dlna", "TV tierce (DLNA)", "other", [("l1", "Récepteur DLNA")]),
        ("tv", "CastBridge-TV", "tv", [("t1", "Lecture (PlayerActivity, libVLC)"), ("t2", "DIAL (DialHost)")]),
        ("yt", "Application YouTube", "other", [("y1", "YouTube (téléphone)")]),
    ],
    nodes="""
        u_start | u1 | start            | Ouvrir une vidéo avec CastBridge
        u_pick  | u1 | send             | Toucher « Caster » et choisir la cible | | Bouton cast du lecteur : feuille « Diffuser sur ».
        u_end   | u1 | end              | Vidéo regardée
        p_open  | p1 | start:message    | Vidéo ouverte dans le lecteur | | « Ouvrir avec » et « Partager » : content://, http(s) direct, HLS, DASH.
        p_cast  | p1 | xor              | Quelle cible ?
        p_play  | p1 | send             | Lire en direct : POST /api/play ou /api/playurl | | La TV lit le petit serveur HTTP du téléphone (route /api/playurl, TV 0.6.5 et plus).
        p_up    | p1 | send             | Copier et lire : envoi progressif | | UploadService en mode progressif ; le fichier est mis dans la file d'envoi.
        p_dlna  | p1 | send | Envoyer l'URL à la TV tierce (DLNA) |  | MediaServer (port 8089) sert le fichier avec Range ; Upnp envoie la commande.
        b_409   | p1 | boundary:message | 409 needsForeground | attach=p_play
        p_fg    | p1 | task             | Ouvrir CastBridge-TV sur la TV (OpenTvFlow) | | OpenTv.playOpeningTv : ouvre CastBridge-TV puis réessaie 5 fois à 1 s d'intervalle.
        p_ctl   | p1 | task             | Devenir la télécommande : lecture, ±10 s, volume | | « Revenir sur le téléphone » reprend à la position de la TV.
        p_end   | p1 | end              | Lecture pilotée
        l_set   | l1 | start:message    | SetAVTransportURI reçu
        l_play  | l1 | task             | Lire l'URL du téléphone (Range)
        l_end   | l1 | end              | Lecture sur la TV tierce
        r_play  | t1 | start:message    | Demande de lecture reçue
        r_fg    | t1 | xor              | CastBridge-TV devant ?
        r_409   | t1 | end:message      | Répondre 409 needsForeground
        r_vlc   | t1 | task             | Lire avec libVLC (Remplir l'écran) | | VideoFit : remplir l'écran par défaut ; pour « Copier et lire », libVLC lit le .part qui grandit.
        r_cp    | t1 | xor              | Une copie écrit sur le disque ?
        r_slow  | t1 | task             | Ralentir la copie : « Copie ralentie » | | PlaybackPriority.receiveCap : moitié du débit du disque, 1 à 6 Mo/s ; PlaybackAwareCopyPolicy : 40 % (0,5 à 3 Mo/s) sur le même disque.
        r_end   | t1 | end              | Lecture en cours
        d_in    | t2 | start:message    | Requête DIAL reçue
        d_chk   | t2 | xor              | Source locale, Host exact, Origin YouTube ? | | DialRules : sources du réseau local seulement, Host = ip:port de la TV, Origin refusé sauf YouTube, 6 lancements par minute.
        d_launch| t2 | task             | Lancer YouTube TV (code d'appairage)
        d_end   | t2 | end              | Fin : lancé ou refusé
        y_start | y1 | start            | Toucher « Caster » dans YouTube
        y_send  | y1 | end:message      | Chercher la TV (SSDP), lancer (DIAL)
    """,
    flows="""
        u_start -> u_pick
        u_pick -> u_end
        p_open -> p_cast
        p_cast -> p_play : En direct *
        p_cast -> p_up : Copier et lire
        p_cast -> p_dlna : TV DLNA
        p_play -> p_ctl
        p_up -> p_ctl
        p_dlna -> p_ctl
        b_409 -> p_fg
        p_fg -> p_play : Réessayer (5 fois)
        p_ctl -> p_end
        l_set -> l_play
        l_play -> l_end
        r_play -> r_fg
        r_fg -> r_vlc : Oui *
        r_fg -> r_409 : Non
        r_vlc -> r_cp
        r_cp -> r_slow : Oui
        r_cp -> r_end : Non *
        r_slow -> r_end
        d_in -> d_chk
        d_chk -> d_launch : Oui *
        d_chk -> d_end : Non
        d_launch -> d_end
        y_start -> y_send
    """,
    messages="""
        u_pick => p_open : Vidéo à caster
        p_play => r_play : /api/play
        p_up => r_play : PUT /upload
        r_409 => b_409 : 409
        p_dlna => l_set : DLNA
        y_send => d_in : DIAL
    """,
    sources=["docs/TV-PLAYER.md (en-tête, « lecture d'abord »)", "docs/TV-CAST-DIAL.md (en-tête, sécurité)", "docs/PHONE-PLAYER.md (en-tête, Caster)"],
    code=[
        ("Lecteur du téléphone, feuille « Diffuser sur », session de cast", "`android/sender/.../player/PlayerActivity.kt`, `CastSheet.kt`, `CastSession.kt`"),
        ("409 needsForeground et ouverture de CastBridge-TV", "`android/sender/.../OpenTv.kt` (playOpeningTv), `android/core/.../tv/Background.kt`, `ReceiverServer.kt`"),
        ("Copie ralentie pendant la lecture", "`android/core/.../xfer/PlaybackPriority.kt`, `PlaybackAwareCopyPolicy.kt`"),
        ("DLNA vers une TV tierce", "`android/sender/.../Upnp.kt`, `MediaServer.kt`, `DlnaHandoff.kt`"),
        ("DIAL pour YouTube : SSDP, port 30765, garde-fous", "`android/core/.../cast/dial/` (DialRules, DialRouter), `android/receiver/.../DialHost.kt`"),
        ("Lecteur de la TV : libVLC, affichage, qualité", "`android/receiver/.../PlayerActivity.kt`, `android/core/.../tv/VideoFit.kt`"),
    ],
))

# ========================================================================================================== 5. mise-a-jour-tv
RAW.append(dict(
    id="mise-a-jour-tv",
    title="Mettre CastBridge-TV à jour depuis le téléphone",
    summary="""
    La mise à jour se fait en local, sans le serveur de jeu. Le téléphone obtient l'APK de CastBridge-TV (WhatsApp, Fichiers ou `/telecharger`), vérifie le paquet
    `castbridge.receiver` et le certificat `ef2908…`, peut le partager à un autre téléphone, puis l'envoie à la TV par Wi-Fi local ou Wi-Fi Direct (jamais le
    Bluetooth, trop lent) : CastBridge › CastBridge TV › Avancé › Installer des APK. La TV inspecte l'APK (`/api/apk/install`, code de la TV seulement) et refuse
    une version plus ancienne, une autre signature ou un fichier illisible ; elle répond 403 tant que « Installer des apps inconnues » n'est pas autorisé.
    `PackageInstaller` ouvre la session et la confirmation se fait à l'écran de la TV. Sinon, l'APK est déposé dans le dossier Download de la clé USB, avec
    `SHA256SUMS.txt`, et installé par l'explorateur de la TV. L'import et le partage entre téléphones sont codés sur la branche `claude/phone-updates-tv`,
    non fusionnée à la date de cette page.
    """,
    pools=[
        ("phone", "CastBridge (téléphone)", "phone", [("p1", "Mettre à jour la TV")]),
        ("tv", "CastBridge-TV", "tv", [("t1", "API et installateur (UpdateInstaller)"), ("t2", "Écran et explorateur")]),
        ("usb", "Clé USB", "usb", [("k1", "Dossier Download")]),
    ],
    nodes="""
        p_start | p1 | start            | APK de CastBridge-TV obtenu | | WhatsApp, Fichiers ou la page /telecharger du serveur.
        p_imp   | p1 | task             | Importer l'APK : paquet et certificat | | Vérifie le paquet castbridge.receiver et le certificat ef2908… ; aucun APK de la TV n'est embarqué dans CastBridge.
        p_x     | p1 | xor              | Partager à un autre téléphone ?
        p_share | p1 | task             | Partager CastBridge-TV (autre téléphone)
        p_adv   | p1 | task             | Avancé › Installer des APK | | CastBridge › CastBridge TV › Avancé › Installer des APK sur la TV (par Wi-Fi), TvHub.
        p_put   | p1 | task             | Envoyer l'APK en Wi-Fi (PUT /upload) | | Wi-Fi local ou Wi-Fi Direct ; jamais le Bluetooth pour une mise à jour.
        p_inst  | p1 | send             | POST /api/apk/install avec le code | | Le jeton de confiance n'ouvre pas cette route : le code de la TV seulement.
        b_perm  | p1 | boundary:message | 403 non autorisé | attach=p_inst
        b_err   | p1 | boundary:message | 400, 404 ou 409 | attach=p_inst
        p_wait  | p1 | task             | Autoriser « Installer des apps inconnues »
        p_fail  | p1 | end:error        | APK refusé | | Version plus ancienne (409), signature différente ou fichier illisible (400), APK introuvable (404).
        p_end   | p1 | end              | Installation lancée
        t_in    | t1 | start:message    | Installation demandée
        t_chk   | t1 | task             | Inspecter l'APK : paquet, version, signature | | UpdateInstaller : 404 introuvable, 400 illisible ou signature différente, 409 version plus ancienne (pas de rétrogradation).
        t_ok    | t1 | xor              | APK valide ?
        t_ref   | t1 | end:message | Répondre 400, 404 ou 409
        t_perm  | t1 | xor              | Apps inconnues autorisées ?
        t_403   | t1 | end:message      | Répondre 403 needsPermission | | Ouvre aussi les réglages « Installer des apps inconnues » de la TV.
        t_pi    | t1 | task             | Ouvrir une session PackageInstaller
        t_conf  | t2 | user | Valider l'installation à l'écran |  | Avec la télécommande : Android demande une confirmation pour chaque application.
        t_repl  | t2 | task             | Android remplace l'app puis la relance | | MY_PACKAGE_REPLACED, BootReceiver, TvService.
        t_end   | t2 | end              | TV à jour
        x_in    | t2 | start:message    | Clé USB branchée
        x_expl  | t2 | user             | Explorateur : Download › Installer
        k_start | k1 | start            | Livraison par la clé USB
        k_copy  | k1 | task             | Copier l'APK et SHA256SUMS.txt (SSH, sync) | | ssh -p 2223 tv@<ip> « cat > <clé>/Download/<apk> » puis sync ; vérifier la taille et le SHA-256.
        k_plug  | k1 | end:message      | Éjecter la clé, la brancher sur la TV
    """,
    flows="""
        p_start -> p_imp
        p_imp -> p_x
        p_x -> p_adv : Non *
        p_x -> p_share : Oui
        p_share -> p_adv
        p_adv -> p_put
        p_put -> p_inst
        p_inst -> p_end
        b_perm -> p_wait
        p_wait -> p_inst : Réessayer
        b_err -> p_fail
        t_in -> t_chk
        t_chk -> t_ok
        t_ok -> t_perm : Oui *
        t_ok -> t_ref : Non
        t_perm -> t_pi : Oui *
        t_perm -> t_403 : Non
        t_pi -> t_conf
        t_conf -> t_repl
        t_repl -> t_end
        x_in -> x_expl
        x_expl -> t_conf
        k_start -> k_copy
        k_copy -> k_plug
    """,
    messages="""
        p_inst => t_in
        t_ref => b_err : 400, 404, 409
        t_403 => b_perm : 403
        k_plug => x_in : Clé branchée
    """,
    sources=["docs/ADMIN.md (routes, installation d'APK)", "docs/RELEASES.md § 8 (clé USB, SHA256SUMS)", "mémoire du projet : décision du 2026-10-11 (mise à jour locale)"],
    code=[
        ("Inspection de l'APK, session PackageInstaller, confirmation", "`android/receiver/.../UpdateInstaller.kt`"),
        ("Route d'installation (code de la TV seulement)", "`android/core/.../tv/ReceiverServer.kt` (/api/apk/install), `docs/ADMIN.md` § 3"),
        ("Écran « Installer des APK sur la TV » du téléphone", "`android/sender/.../TvHub.kt`"),
        ("Dépôt sur la clé USB et sommes de contrôle", "`docs/RELEASES.md` § 8, `tools/release/sha256sums.sh`"),
        ("Import et partage entre téléphones", "branche `claude/phone-updates-tv` (non fusionnée)"),
    ],
))

# ========================================================================================================== 6. publication-version
RAW.append(dict(
    id="publication-version",
    title="Publier une version",
    summary="""
    Une livraison suit toujours le même chemin : `check_versions.py` contrôle `version.properties` (SemVer, `versionCode` strictement croissant, variante verrouillée de la TV avec code + 1 et suffixe `-verrouillee`). La TV est construite verrouillée (`-PrequireActivation=true`, clés de confiance hors dépôt) et le téléphone en deux variantes, simple et superadmin. Chaque APK est aligné (`zipalign`), signé (`apksigner`, `release.jks`, certificat `ef2908…`) et reçoit sa somme `SHA256SUMS`. Les APK sont déposés dans le dossier Download de la TV par SSH, sur la clé USB de distribution (jamais la variante superadmin), puis sur le téléphone du propriétaire par ADB avec l'installation de la console. Les documents (`CHANGELOG`, `RELEASES` § 9, `REGRESSIONS`, `HANDOFF`) et le guide sont mis à jour, le guide est copié sur le serveur (`/guide/`) et republié sur la page claude.ai. Les étiquettes suivent ; le push, la fusion d'`integration/agents` et l'avance de `main` sont des décisions du propriétaire.
    """,
    pools=[
        ("owner", "Utilisateur / Propriétaire", "user", [("o1", "Propriétaire")]),
        ("mac", "Mac d'assistance", "mac", [("m1", "Contrôles et build"), ("m2", "Signature et dépôts"), ("m3", "Docs et étiquettes")]),
        ("tv", "CastBridge-TV", "tv", [("t1", "Dossier Download")]),
        ("usb", "Clé USB", "usb", [("k1", "Clé de distribution")]),
        ("phone", "CastBridge (téléphone)", "phone", [("p1", "Téléphone (ADB)")]),
        ("server", "Serveur bridge.sti-cm.com", "server", [("v1", "Guide public")]),
    ],
    nodes="""
        o_start | o1 | start         | Version décidée
        o_go    | o1 | send          | Lancer la livraison (version.properties à jour)
        o_dec   | o1 | user          | Décider : push, fusion, avance de main | | Fusion d'integration/agents puis avance rapide de main : décision du propriétaire (docs/coordination/VERSIONING-DEVOPS-2026-10-02.md).
        o_end   | o1 | end           | Version publiée
        m_in    | m1 | start:message | Livraison demandée
        m_chk   | m1 | task          | check_versions.py | | Forme, SemVer, versionCode croissant, variante verrouillée (code + 1, suffixe -verrouillee), état git, cohérence avec backend/pom.xml.
        m_ok    | m1 | xor           | Contrôle sans erreur ?
        m_stop  | m1 | end:error     | Corriger les versions et recommencer
        m_bt    | m1 | task          | Construire la TV verrouillée (-PrequireActivation=true) | | Avec -PtrustedKeysFile=~/.castbridge-signing/activation-trusted-keys.txt ; plus de variante déverrouillée.
        m_bp    | m1 | task          | Construire le téléphone : simple et superadmin
        m_sign  | m2 | task          | zipalign, apksigner (release.jks), SHA256SUMS | | apksigner sign --ks release.jks --ks-pass file:… ; certificat ef2908… vérifié ; check_versions.py --apk compare chaque APK à version.properties.
        m_dtv   | m2 | send          | Copier dans Download de la TV (SSH) | | CastBridge Dev, port 2223 ; vérifier la taille et le SHA-256 des deux côtés.
        m_dusb  | m2 | send          | Copier sur la clé USB avec SHA256SUMS.txt | | Les dernières versions seulement : TV verrouillée et téléphone simple, jamais la variante superadmin ; éjecter la clé avant de la retirer.
        m_dph   | m2 | send          | adb install -r de la console, adb push des APK | | Les trois APK signés et SHA256SUMS dans /sdcard/Download, vérifiés par sha256sum -c sur l'appareil.
        m_docs  | m3 | task          | CHANGELOG, RELEASES § 9, REGRESSIONS, HANDOFF
        m_guide | m3 | task          | build_guide.py : guide HTML régénéré
        m_srv   | m3 | send          | scp et docker cp vers /guide/
        m_page  | m3 | task          | Republier la page claude.ai du guide
        m_tag   | m3 | task          | Étiquettes tv-, phone-, tvweb-
        m_ask   | m3 | end:message   | Proposer le push et la fusion
        t_in    | t1 | start:message | APK déposé par SSH
        t_end   | t1 | end           | APK prêt à installer
        k_in    | k1 | start:message | APK et sommes copiés
        k_end   | k1 | end           | Clé prête : l'éjecter avant de la retirer
        p_in    | p1 | start:message | APK poussés par ADB
        p_inst  | p1 | task          | Installer la console par-dessus, contrôler les sommes
        p_end   | p1 | end           | Téléphone à jour
        v_in    | v1 | start:message | Guide reçu
        v_end   | v1 | end           | Guide servi sur /guide/
    """,
    flows="""
        o_start -> o_go
        o_go -> o_dec
        o_dec -> o_end
        m_in -> m_chk
        m_chk -> m_ok
        m_ok -> m_bt : Oui *
        m_ok -> m_stop : Non
        m_bt -> m_bp
        m_bp -> m_sign
        m_sign -> m_dtv
        m_dtv -> m_dusb
        m_dusb -> m_dph
        m_dph -> m_docs
        m_docs -> m_guide
        m_guide -> m_srv
        m_srv -> m_page
        m_page -> m_tag
        m_tag -> m_ask
        t_in -> t_end
        k_in -> k_end
        p_in -> p_inst
        p_inst -> p_end
        v_in -> v_end
    """,
    messages="""
        o_go => m_in : Livraison
        m_dtv => t_in : SSH
        m_dusb => k_in : Copie
        m_dph => p_in : ADB
        m_srv => v_in : scp
        m_ask => o_dec : Décision
    """,
    sources=["docs/RELEASES.md § 1-2 et § 13", "tools/release/check_versions.py (en-tête)", "mémoire du projet : signature des APK, clé USB de distribution"],
    code=[
        ("Contrôle des versions", "`tools/release/check_versions.py`, `version.properties`"),
        ("Règles de numérotation et variante verrouillée", "`docs/RELEASES.md` § 2, § 3"),
        ("Sommes SHA-256 et plan d'étiquettes", "`tools/release/sha256sums.sh`, `tools/release/tag-plan.sh`, `docs/RELEASES.md` § 12-13"),
        ("Guide utilisateur régénéré", "`docs/guide-utilisateur/tools/build_guide.py`, `docs/GUIDE-PUBLICATION.md`"),
        ("Modèle de branches, fusion et avance de main", "`docs/coordination/VERSIONING-DEVOPS-2026-10-02.md`"),
    ],
))

# ========================================================================================================== 7. quiz-en-ligne
RAW.append(dict(
    id="quiz-en-ligne",
    title="Jouer au Quiz en ligne",
    summary="""
    Seule une TV activée et connectée à Internet, directement ou par le relais d'un téléphone synchronisé, joue en ligne ; les téléphones passent par leur TV
    (8 au plus, liaison de référence de 40 kbit/s). Pour une partie avec mise, la TV obtient de l'API un blocage `cbe1` signé (NDEM ou MBOKO, une mise par
    siège), puis un ticket `cbp1` (600 secondes, à usage unique), et ouvre la salle du service de jeu avec son activation `cbx1`. Le service contrôle le ticket,
    les droits, les plafonds et le blocage, ouvre la salle (code de 8 caractères, 2 heures) et enchaîne les questions : 1 à 2 secondes entre deux questions,
    horodatages du serveur. À la fin, le service signe le résultat `cbr1` ; la TV le poste à l'API (`/api/v1/wallet/settle`), qui crédite et débite le
    portefeuille en une transaction.
    """,
    pools=[
        ("phone", "CastBridge (téléphone)", "phone", [("h1", "Joueur relayé par sa TV")]),
        ("tv", "CastBridge-TV", "tv", [("t1", "Hôte (Quiz, PlayHub, portefeuille)")]),
        ("server", "Serveur bridge.sti-cm.com", "server", [("a1", "castbridge-api"), ("s1", "castbridge-play (salle de jeu)")]),
    ],
    nodes="""
        h_start | h1 | start            | Un joueur veut jouer en ligne
        h_join  | h1 | send             | Rejoindre par la TV : code de salle | | Page /quiz de la TV, jeton aléatoire de 128 bits par joueur ; aucun téléphone ne parle au service de jeu.
        h_end   | h1 | end              | Partie jouée par le relais de la TV
        t_in    | t1 | start            | Ouvrir Quiz › Partie Internet
        t_chk   | t1 | xor              | TV activée et en ligne (direct ou relais) ?
        t_ref   | t1 | end              | Refus : activer la TV ou connecter Internet
        t_x     | t1 | xor              | Partie avec mise ?
        t_esc   | t1 | service          | Bloquer la mise : cbe1 (NDEM ou MBOKO) | | Une mise par siège, de 1 à 8 sièges par TV ; une TV d'essai ne mise jamais.
        t_tk    | t1 | service          | Demander le ticket cbp1 (600 s) | | Usage unique (jti), signé Ed25519 par l'API ; l'API en redonne 20 par heure et par appareil.
        t_create| t1 | send             | create : ticket, activation cbx1, preuve | | Ticket, activation cbx1 et preuve de possession de la clé d'installation (PLAY-PROTOCOL § Ticket et droits).
        b_ko    | t1 | boundary:message | Refus du service | attach=t_create
        t_rel   | t1 | task             | Relayer les téléphones : 8 au plus | | relayJoin et relayAct ; fenêtre de réponse allongée de la latence mesurée, bornée à 1 s ; 40 kbit/s.
        t_res   | t1 | catch:message    | Résultat cbr1 signé reçu
        t_set   | t1 | service          | Régler : POST /api/v1/wallet/settle (cbr1)
        t_end   | t1 | end              | Solde mis à jour (cbw1)
        a_start | a1 | start            | API principale disponible
        a_esc   | a1 | task             | Bloquer la mise au grand livre, signer cbe1
        a_tk    | a1 | task             | Émettre le ticket cbp1 (Ed25519, jti)
        a_set   | a1 | task             | Régler : crédit et débit en une transaction | | Idempotent : un seul résultat par salle (rid = SHA-256 de la salle).
        a_end   | a1 | end              | Solde signé cbw1
        p_in    | s1 | start:message    | create ou join reçu
        p_gate  | s1 | xor              | Ticket, droits, plafonds, blocage valides ?
        p_ko    | s1 | end:message | Refuser : ticket, droits ou plafonds |  | Codes : PLAY_TICKET_REFUSED, PLAY_SCOPE_FORBIDDEN, PLAY_BUSY, STAKE_ESCROW_INVALID.
        p_room  | s1 | task             | Ouvrir la salle : code XXXX-XXXX (2 h) | | 8 joueurs au plus par table, 8 sièges relayés au plus par TV ; 50 essais faux changent le code.
        p_q     | s1 | task             | Annoncer la question : ouverture après 1,5 s | | PlayTiming : opensAtServerMs = révélation + délai de 1 à 2 s ; une réponse avant est refusée (TOO_EARLY).
        p_ans   | s1 | task             | Recevoir les réponses, révéler, scorer
        p_more  | s1 | xor              | Dernière question ?
        p_settle| s1 | task             | Règlement : cagnotte partagée | | QuizSettlement : 1 joueur 100 % ; 2 joueurs 70/30 ; 3 et plus 60/30/10 ; personne n'a marqué : chacun reprend sa mise.
        p_res   | s1 | end:message      | Résultat cbr1 signé
    """,
    flows="""
        h_start -> h_join
        h_join -> h_end
        t_in -> t_chk
        t_chk -> t_x : Oui *
        t_chk -> t_ref : Non
        t_x -> t_esc : Oui
        t_x -> t_tk : Non *
        t_esc -> t_tk
        t_tk -> t_create
        t_create -> t_rel
        b_ko -> t_ref
        t_rel -> t_res
        t_res -> t_set
        t_set -> t_end
        a_start -> a_esc
        a_esc -> a_tk
        a_tk -> a_set
        a_set -> a_end
        p_in -> p_gate
        p_gate -> p_room : Oui *
        p_gate -> p_ko : Non
        p_room -> p_q
        p_q -> p_ans
        p_ans -> p_more
        p_more -> p_q : Non
        p_more -> p_settle : Oui *
        p_settle -> p_res
    """,
    messages="""
        h_join => t_rel : join
        t_esc => a_esc : /wallet/escrow
        a_esc => t_esc : cbe1
        t_tk => a_tk : /play/ticket
        a_tk => t_tk : cbp1
        t_create => p_in : create
        p_ko => b_ko : Refus
        p_res => t_res : cbr1
        t_set => a_set : /wallet/settle
        a_set => t_set : Solde
    """,
    sources=["docs/PLAY-PROTOCOL.md (en-tête, « Salle de Quiz misée »)", "docs/QUIZ.md § 5"],
    code=[
        ("Protocole play-v1, salle, timing de 1,5 s", "`docs/PLAY-PROTOCOL.md`, `android/core/.../quiz/online/` (PlayProtocol, ServerRoom, PlayTiming)"),
        ("Ticket cbp1 et droits de l'hôte", "`docs/PLAY-PROTOCOL.md` § Ticket et droits, `backend/.../play/`, `server-play/`"),
        ("Blocage cbe1, résultat cbr1 et règlement", "`android/core/.../quiz/online/QuizSettlement.kt`, `backend/.../wallet/`, `docs/QUIZ.md` § 5 bis"),
        ("Relais des téléphones et liaison de 40 kbit/s", "`android/core/.../quiz/online/PlayRelayProfile.kt`, `docs/QUIZ.md` § 4 bis"),
        ("Portefeuille de la TV (NDEM, MBOKO)", "`android/core/.../wallet/`, `android/receiver/.../wallet/`"),
    ],
))

# ========================================================================================================== 8. jetons-et-lots
RAW.append(dict(
    id="jetons-et-lots",
    title="Jetons du portefeuille et lots en location",
    summary="""
    Deux circuits se rejoignent à la TV. Le portefeuille est un grand livre tenu par le serveur : une commande payée en espèces est validée par un administrateur (compte nommé et code TOTP), qui crédite le compte de la TV par un don ; au-delà du plafond, un second administrateur doit approuver, et la TV ne garde qu'un solde signé (`cbw1`). Une location part de la Boutique de la TV, qui refuse l'essai, le profil enfant et les lots libres ; la demande `castbridge-rent-request-v1` est confirmée par un adulte sur le téléphone, puis l'émetteur signe le droit `rental` (1 à 366 jours). Le téléphone télécharge le lot signé et le livre à la TV, qui ne télécharge jamais un lot elle-même (sauf le bouton « Mettre à jour les lots Langues »). À la fin de la location, ou sur ordre signé de retrait, la TV détruit la clé puis efface les fichiers ; la progression est gardée. L'émission des locations par le serveur est une conception cible ; l'émetteur actuel est l'outil de bureau du propriétaire.
    """,
    pools=[
        ("user", "Utilisateur / Propriétaire", "user", [("c1", "Client")]),
        ("server", "Serveur bridge.sti-cm.com", "server", [("a1", "Administration (/admin)"), ("w1", "Portefeuille (wallet)"), ("l1", "Lots et licences")]),
        ("phone", "CastBridge (téléphone)", "phone", [("p1", "Messager des lots")]),
        ("tv", "CastBridge-TV", "tv", [("t1", "Boutique et portefeuille"), ("x1", "Lots (RentalHub, carnet des locations)")]),
    ],
    nodes="""
        c_start | c1 | start | Commande de jetons ou de location
        c_pay   | c1 | end:message     | Régler en espèces au point focal
        a_in    | a1 | start:message   | Commande payée à valider
        a_val   | a1 | user            | Valider : compte nommé et code TOTP
        a_don   | a1 | task            | Créer le don au compte de la TV | | POST /api/v1/admin/wallet/grant ; plafond par don et par jour ; au-delà, un second administrateur (TOTP distinct) approuve.
        a_rstart| a1 | start           | Retrait décidé
        a_rev   | a1 | end:message | Révoquer : ordre signé | rank=1 | Ordre cbx1 de type order, liste d'actions fermée ; le téléphone le transporte.
        w_post  | w1 | task            | Écrire au grand livre : NDEM ou MBOKO | | Écritures immuables à double entrée, clé d'idempotence ; l'attribution mensuelle par édition passe par la même écriture (essai 100 NDEM par mois ; production 1 000 NDEM et 10 MBOKO).
        w_snap  | w1 | end:message     | Solde signé cbw1 à la synchronisation
        l_in    | l1 | start:message   | Demande de location transmise
        l_right | l1 | task            | Émettre le droit rental (1 à 366 jours) | | Durée du bouquet (rentalDays, 30 jours par défaut), une location par bouquet ; renouvellement : kind=extend avec le même period.
        l_pub   | l1 | end:message     | Lot signé et droit disponibles
        p_adult | p1 | start:message   | Demande de location reçue
        p_ok    | p1 | user            | Un adulte confirme sur le téléphone
        p_fwd   | p1 | end:message     | Transmettre à l'émetteur
        p_in    | p1 | start:message   | Lot et droit disponibles
        p_push  | p1 | end:message     | Télécharger le lot, le livrer à la TV | | GET /api/v1/lots/{feature}/{scope}/{version} avec reprise par plages (100 Mo au plus) ; livraison différée par Bluetooth ou Wi-Fi (10 Mo au plus sur la TV).
        t_start | t1 | start           | Boutique : Louer un bouquet
        t_chk   | t1 | xor             | Essai, profil enfant ou lot libre ?
        t_ref   | t1 | end | Refus : essai, enfant ou lot libre |  | Raisons du cœur : TRIAL_TV, KID_PROFILE, FREE_BUNDLE, UNKNOWN_FAMILY, OVER_LIMIT, DUPLICATE.
        t_req   | t1 | end:message     | Créer la demande castbridge-rent-request-v1 | | Une demande n'est pas un droit : seul l'émetteur décide, après confirmation d'un adulte.
        t_bal   | t1 | start:message   | Solde signé cbw1 reçu
        t_bend  | t1 | end             | Solde affiché (NDEM, MBOKO)
        x_in    | x1 | start:message   | Lot livré par le téléphone
        x_use   | x1 | task            | Vérifier, installer, ouvrir les lots loués | | Catalogue signé et SHA-256 vérifiés ; coffre chiffré par TV ; compteur d'usage ; fenêtre d'essai unique : 3 jours et 720 minutes.
        x_evgw  | x1 | evgw            | Attendre la fin de la location
        x_exp   | x1 | catch:timer     | Fin de la location
        x_rev   | x1 | catch:message   | Ordre de retrait reçu
        x_shred | x1 | task            | Détruire la clé, effacer les fichiers | | RentalSweeper : clé d'abord, fichiers ensuite, reprise après coupure ; ne touche que les lots loués expirés.
        x_end   | x1 | end             | Lot supprimé, progression gardée
    """,
    flows="""
        c_start -> c_pay
        a_in -> a_val
        a_val -> a_don
        a_don -> w_post
        w_post -> w_snap
        a_rstart -> a_rev
        l_in -> l_right
        l_right -> l_pub
        p_adult -> p_ok
        p_ok -> p_fwd
        p_in -> p_push
        t_start -> t_chk
        t_chk -> t_ref : Oui
        t_chk -> t_req : Non *
        t_bal -> t_bend
        x_in -> x_use
        x_use -> x_evgw
        x_evgw -> x_exp
        x_evgw -> x_rev
        x_exp -> x_shred
        x_rev -> x_shred
        x_shred -> x_end
    """,
    messages="""
        c_pay => a_in : Espèces
        t_req => p_adult : Demande
        p_fwd => l_in : Demande confirmée
        l_pub => p_in : Lot et droit
        p_push => x_in : Lot livré
        w_snap => t_bal : Solde
        a_rev => x_rev : Retrait
    """,
    sources=["docs/LICENSE-ADMIN.md § 5", "docs/RENTAL-LOTS.md (en-tête, § 13-15)", "docs/STORE.md (en-tête, § 5-6)", "docs/LOTS.md § 1"],
    code=[
        ("Dons de l'administrateur : TOTP, plafonds, second administrateur", "`backend/.../wallet/WalletAdminController.java` (/grant, /approve, /reject)"),
        ("Grand livre et attributions par édition", "`backend/.../wallet/` (JdbcLedger, GrantService), `docs/coordination/DESIGN-W22-JETONS-NDEM-MBOKO-2026-10-04.md`"),
        ("Boutique, états d'un article, demande de location", "`docs/STORE.md`, `android/core/.../store/`"),
        ("Droit rental, carnet, destruction de clé", "`docs/RENTAL-LOTS.md`, `android/core/.../lots/Rental*.kt`"),
        ("Lots : catalogue signé, livraison différée, budget de la TV", "`docs/LOTS.md`, `android/sender/.../LotsRuntime.kt`, `android/receiver/.../LotsHub.kt`, `backend/.../lots/`"),
        ("Ordres signés de retrait", "`docs/ORDRES.md`, `backend/.../orders/`"),
    ],
))

# ========================================================================================================== 9. assistance-a-distance
RAW.append(dict(
    id="assistance-a-distance",
    title="Assistance à distance d'une TV",
    summary="""
    La TV ne monte le tunnel de l'éditeur que si les conditions d'usage sont acceptées, si elle est activée et non verrouillée, et si elle a Internet. Sans réseau
    propre, elle demande un tuyau au téléphone synchronisé, qui l'ouvre en silence si sa politique de coût le permet (5 Mo par jour sur données mobiles). La TV
    s'enrôle auprès du serveur (clé ed25519 propre, port 22100 à 22999), puis ouvre un tunnel SSH inverse vers `127.0.0.1:2224`, où seul un expert signé par le
    propriétaire est accepté. L'expert peut aussi joindre la TV par le réseau local (SSH 2222 ou CastBridge Dev 2223) ou par la passerelle Bluetooth du téléphone
    (SSH 2222 et API 18765, `adb forward`). Chaque session est écrite au journal local (À propos, Assistance à distance) avec l'identifiant de l'expert. Le code du
    tunnel est écrit et testé en JVM ; il n'a jamais été essayé sur une vraie TV.
    """,
    pools=[
        ("user", "Utilisateur / Propriétaire", "user", [("u1", "Devant la TV")]),
        ("phone", "CastBridge (téléphone)", "phone", [("g1", "Tuyau Internet"), ("g2", "Passerelle Bluetooth")]),
        ("tv", "CastBridge-TV", "tv", [("t1", "Conditions et chemin réseau (TunnelHub)"), ("t2", "Session SSH (TvSshServer)")]),
        ("mac", "Mac d'assistance", "mac", [("e1", "Poste de l'expert")]),
        ("server", "Serveur bridge.sti-cm.com", "server", [("s1", "Tunnels (/api/v1/tunnel)")]),
    ],
    nodes="""
        u_start | u1 | start           | Une aide à distance est demandée
        u_req   | u1 | end:message     | Se connecter maintenant (À propos) | | Fenêtre d'assistance de 30 minutes quand le tuyau du téléphone est nécessaire (AssistPipePolicy).
        g_in    | g1 | start:message   | Demande de tuyau reçue
        g_open  | g1 | task            | Ouvrir la passerelle si la politique le permet | | RelayPolicy : réseau gratuit libre ; données mobiles : le serveur CastBridge seulement, 5 Mo par jour ; une seule notification neutre.
        g_end   | g1 | end:message     | Tuyau Internet ouvert
        g_fwd   | g2 | start:message   | Mac branché en ADB : adb forward
        g_rend  | g2 | end:message     | Relayer les octets chiffrés vers la TV | | 127.0.0.1:2222 (SSH) et 18765 (API) du téléphone vers la TV par RFCOMM ; SSH reste chiffré de bout en bout.
        t_req   | t1 | start:message   | Assistance demandée
        t_terms | t1 | xor             | Conditions d'usage acceptées ?
        t_acc   | t1 | user            | Cocher « J'ai lu et j'accepte » | | Écran d'activation, ou boîte de dialogue de l'accueil ; l'acceptation est liée au code d'appareil.
        t_gate  | t1 | xor             | TV activée, déverrouillée, en ligne ?
        t_idle  | t1 | end             | Rien ne démarre : hors ligne ou verrouillée
        t_path  | t1 | xor             | Chemin réseau ?
        t_pipe  | t1 | service         | Demander le tuyau au téléphone | | RELAY_ASK_PIPE sur le canal propriétaire ; le tunnel passe par le proxy SOCKS5 local de la TV.
        t_enr   | t1 | service         | Enrôler et monter le tunnel inverse | | Clé ed25519 propre à la TV ; POST /api/v1/tunnel/enroll ; ssh -R 127.0.0.1:22xxx:127.0.0.1:2224 ; garde-vivant toutes les 30 s.
        t_wait  | t2 | catch:message   | Connexion d'un expert
        t_auth  | t2 | xor             | Expert signé par le propriétaire ?
        t_deny  | t2 | end             | Refus (blocage de 60 s après des échecs)
        t_sess  | t2 | task            | Session ouverte, expert écrit au journal | | files/tunnel/journal.log : connexions, déconnexions, identifiant de l'expert ; jamais de clé ni de code.
        t_close | t2 | task            | Fermer à la fin de la session
        t_end   | t2 | end             | Tuyau libéré 10 minutes après
        e_start | e1 | start           | L'expert prend l'assistance
        e_x     | e1 | xor | Voie d'accès ? | rank=5
        e_jump  | e1 | end:message     | ssh -J cbexpert@serveur:2200 -p 22xxx | | Le port 22xxx n'écoute que sur 127.0.0.1 du serveur ; les experts ont restrict,pty.
        e_gw    | e1 | end:message     | tv-tunnel.sh up : adb forward 2222 et 18765
        e_lan   | e1 | end:message     | ssh -p 2222 tv@<ip> (ou 2223) | | SSH de l'utilisateur (SshControl, 2222) ou CastBridge Dev (2223), activés par la TV.
        s_in    | s1 | start:message   | Enrôlement demandé
        s_port  | s1 | task            | Contrôler l'activation, attribuer un port | | Plage 22100 à 22999 liée à 127.0.0.1 ; 403 : TV révoquée (pause de 24 h) ; authorized_keys écrit par le serveur.
        s_ans   | s1 | end:message     | Répondre : hôte, port, empreinte
    """,
    flows="""
        u_start -> u_req
        g_in -> g_open
        g_open -> g_end
        g_fwd -> g_rend
        t_req -> t_terms
        t_terms -> t_gate : Oui *
        t_terms -> t_acc : Non
        t_acc -> t_gate
        t_gate -> t_path : Oui *
        t_gate -> t_idle : Non
        t_path -> t_enr : Réseau propre *
        t_path -> t_pipe : Par le téléphone
        t_pipe -> t_enr
        t_enr -> t_wait
        t_wait -> t_auth
        t_auth -> t_sess : Oui *
        t_auth -> t_deny : Non
        t_sess -> t_close
        t_close -> t_end
        e_start -> e_x
        e_x -> e_jump : Tunnel de l'éditeur
        e_x -> e_gw : Passerelle Bluetooth
        e_x -> e_lan : Réseau local *
        s_in -> s_port
        s_port -> s_ans
    """,
    messages="""
        u_req => t_req : Demande
        t_pipe => g_in : Tuyau
        g_end => t_pipe : Ouvert
        t_enr => s_in : Enrôlement
        s_ans => t_enr : Port
        e_jump => t_wait : Tunnel
        e_gw => g_fwd : ADB
        g_rend => t_wait : SSH
        e_lan => t_wait : SSH
    """,
    sources=["docs/REMOTE-TUNNEL.md (en-tête)", "docs/CONDITIONS-ASSISTANCE-A-DISTANCE.md (en-tête)", "docs/REMOTE-TUNNEL-BT.md (en-tête)", "docs/REMOTE-TUNNEL-TV.md § 2-6"],
    code=[
        ("Machine du tunnel côté TV : portes, enrôlement, reconnexion, experts", "`android/receiver/.../TunnelHub.kt`, `android/core/.../tunnel/TunnelMachine.kt`, `android/sshd/.../TunnelSshClient.kt`"),
        ("Conditions d'usage, acceptation liée au code d'appareil", "`android/core/.../tunnel/TunnelTerms.kt`, `docs/CONDITIONS-ASSISTANCE-A-DISTANCE.md`"),
        ("Serveur : enrôlement, ports, authorized_keys, experts signés", "`backend/.../tunnel/`, `ops/tunnel/`, `docs/REMOTE-TUNNEL.md`"),
        ("Tuyau Internet par le téléphone et sa politique de coût", "`android/receiver/.../TvNet.kt`, `android/sender/.../RelayRuntime.kt`, `android/core/.../relay/`"),
        ("Passerelle Bluetooth vers la TV (SSH 2222, API 18765)", "`android/sender/.../BtSshGateway.kt`, `tools/remote/tv-tunnel.sh`, `docs/REMOTE-TUNNEL-BT.md`"),
        ("SSH de l'utilisateur et CastBridge Dev", "`android/receiver/.../SshControl.kt`, `android/devbridge/`"),
    ],
))

# ========================================================================================================== 10. controle-parental
RAW.append(dict(
    id="controle-parental",
    title="Contrôle parental et retrait de la clé USB",
    summary="""
    Le parent crée un code parental (4 à 6 chiffres, PBKDF2, verrou progressif de 1 minute à 1 heure après 5 échecs) et un profil enfant : tranche d'âge, heures,
    durée quotidienne, catégories bloquées. En mode enfant, l'accueil se réduit à huit tuiles ; le code de connexion de la TV et les lignes de copie sont masqués,
    et la tuile de retrait de la clé USB n'est pas proposée. À chaque action de l'enfant, `ParentalHub` décide : vidéo au-delà de l'âge, catégorie ou application
    bloquée, heures ou durée dépassées. Un refus ramène l'écran verrouillé, où seul le code parental ouvre une séance de 30 minutes, et envoie une alerte au
    téléphone du parent par Bluetooth (rapport signé HMAC, livré quand le téléphone vient le chercher). Le parent prépare le retrait de la clé USB (arrêt des
    copies, `fsync`, `sync`) avant de la débrancher.
    """,
    pools=[
        ("user", "Utilisateur / Propriétaire", "user", [("u1", "Parent"), ("u2", "Enfant")]),
        ("tv", "CastBridge-TV", "tv", [("t1", "Configuration"), ("t2", "Application des règles et alertes"), ("t3", "Retrait de la clé USB (UsbRemoval)")]),
        ("phone", "CastBridge (téléphone)", "phone", [("p1", "Téléphone du parent")]),
    ],
    nodes="""
        u_p0    | u1 | start           | Le parent protège la TV
        u_p1    | u1 | send            | Créer le code, le profil enfant et les règles
        u_p2    | u1 | send            | Saisir le code parental (séance parent)
        u_p3    | u1 | end:message     | Préparer le retrait de la clé USB
        u_c0    | u2 | start           | L'enfant utilise la TV
        u_c1    | u2 | end:message | Lancer une vidéo ou une application
        c_cfg   | t1 | start:message   | Configuration demandée
        c_pin   | t1 | task            | Enregistrer le code parental | | PBKDF2-HMAC-SHA256, sel de 16 octets, 60 000 itérations ; jamais en clair ni dans un journal ; 4444 et 1234 refusés ; verrou progressif 1 min, 5, 15 min, 1 h.
        c_prof  | t1 | task            | Créer le profil : âge, heures, durée, catégories | | Tranches -12, 12-15, 16-17, adulte ; vidéo classée par fichier, mot-clé ou dossier ; une vidéo non classée vaut adulte.
        c_mode  | t1 | task            | Mode enfant : accueil réduit, code TV masqué | | KID_HOME : Apprendre, Jeux, Quiz, Échecs, Bibliothèque, Boutique, Aide, Contrôle parental ; les lignes « Copie… » relèvent de la catégorie Réglages.
        c_end   | t1 | end             | Contrôle actif
        e_in    | t2 | start:message   | Action de l'enfant détectée
        e_dec   | t2 | xor | Règle respectée ? |  | ParentalEngine : décision pure ; surveillance de toute la TV par application (ForegroundWatcher, statistiques d'usage ou service d'accessibilité).
        e_ok    | t2 | end             | Usage autorisé
        e_fork  | t2 | and             | Refus
        e_lock  | t2 | task            | Écran verrouillé : « Saisir le PIN parental » | | Toujours « Retour à l'accueil » ; RETOUR et ACCUEIL ne sont jamais interceptés.
        r_rep   | t2 | end:message     | Alerte dans l'outbox signée (HMAC) | | 40 rapports par téléphone, 200 Ko, 14 jours ; message CBTP ; seul un téléphone de confiance et désigné reçoit.
        e_wait  | t2 | catch:message   | Code parental saisi
        e_chk   | t2 | xor             | Code correct ?
        e_end   | t2 | end | Séance parent de 30 min | lbl=N | Les règles sont suspendues pendant la séance parent.
        e_fail  | t2 | task            | Échec compté : verrou progressif
        k_start | t3 | start:message   | Retrait de la clé demandé
        k_x     | t3 | xor             | Des copies écrivent sur la clé ?
        k_stop  | t3 | task            | Attendre la fin ou mettre les copies en pause | | Aucune nouvelle copie n'est acceptée : 503 volume removed, cause held ; rien n'est redirigé sur la mémoire interne.
        k_sync  | t3 | task            | fsync de chaque .part, puis sync | | Au mieux 15 s ; jamais un faux « prête » : si une copie ne lâche pas la clé, l'écran dit de ne pas la retirer.
        k_ready | t3 | end | Clé retirable (10 min) |  | Dix minutes sans retrait : les copies reprennent toutes seules.
        ph_in   | p1 | start:message   | Le téléphone vient chercher (toutes les 15 min)
        ph_store| p1 | task            | Vérifier la signature, stocker, notifier en privé | | 150 rapports, 90 jours, hors sauvegarde ; accusé de réception envoyé seulement après stockage.
        ph_end  | p1 | end             | Parent informé
    """,
    flows="""
        u_p0 -> u_p1
        u_p1 -> u_p2
        u_p2 -> u_p3
        u_c0 -> u_c1
        c_cfg -> c_pin
        c_pin -> c_prof
        c_prof -> c_mode
        c_mode -> c_end
        e_in -> e_dec
        e_dec -> e_ok : Autorisé *
        e_dec -> e_fork : Refusé
        e_fork -> e_lock
        e_fork -> r_rep
        e_lock -> e_wait
        e_wait -> e_chk
        e_chk -> e_end : Oui *
        e_chk -> e_fail : Non
        e_fail -> e_wait : Réessayer
        k_start -> k_x
        k_x -> k_stop : Oui
        k_x -> k_sync : Non *
        k_stop -> k_sync
        k_sync -> k_ready
        ph_in -> ph_store
        ph_store -> ph_end
    """,
    messages="""
        u_p1 => c_cfg : Configuration
        u_c1 => e_in : Action
        u_p2 => e_wait : Code
        u_p3 => k_start : Retrait
        r_rep => ph_in : Rapport
    """,
    sources=["docs/PARENTAL.md (en-tête, profils, rapports)", "docs/STORAGE.md § 11 (Préparer le retrait)"],
    code=[
        ("Décisions pures : profils, catégories, règles par application", "`android/core/.../parental/` (ParentalEngine, ParentalModel, AppModel)"),
        ("Application sur la TV, filtre de bibliothèque, écran verrouillé", "`android/receiver/.../ParentalHub.kt`, `ParentalLockActivity.kt`"),
        ("Code parental haché, verrou progressif", "`android/core/.../parental/ParentalPin.kt`"),
        ("Surveillance de toute la TV", "`android/core/.../parental/Supervision.kt`, `android/receiver/.../ForegroundWatcher.kt`, `AppCatalog.kt`"),
        ("Rapports au téléphone du parent (CBTP, HMAC)", "`android/core/.../parental/ParentalReports.kt`, `ParentalSync.kt`, `android/sender/.../ParentalInbox.kt`"),
        ("Retrait sûr de la clé USB", "`android/core/.../tv/UsbSafeRemoval.kt`, `android/receiver/.../UsbRemoval.kt`, `docs/STORAGE.md` § 11"),
    ],
))

# --- FIN DES PROCESSUS ---

PROCESSES = [load(r) for r in RAW]

if __name__ == "__main__":
    for p in PROCESSES:
        print("%-22s %2d nœuds, %2d flux, %d messages" % (p["id"], len(p["nodes"]), len(p["flows"]), len(p["messages"])))
