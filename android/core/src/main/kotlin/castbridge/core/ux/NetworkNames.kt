package castbridge.core.ux

/**
 * Le nom du réseau sur les accueils, sans rien encombrer : une seule ligne existante enrichie.
 * TV : la puce « Prêt à recevoir · Wi-Fi Maison · code 12•••• ». Téléphone : une petite ligne sous l'état de la TV (« le même que la TV » ou avertissement).
 * Aucune permission : le téléphone ne lit pas son SSID, il compare son adresse IPv4 + préfixe à celle de la TV ([sameSubnet]).
 */
object NetworkNames {
    /** Nom raccourci proprement (≈ 24 caractères + « … »), sans espaces de bord ; null si vide. */
    fun short(name: String?, max: Int = 24): String? {
        val n = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (n.length <= max) n else n.take(max).trimEnd() + "…"
    }

    /**
     * Ce que la TV dit de son réseau dans la puce : « Wi-Fi <nom> », « Câble réseau », ou null (pas de réseau, adresse en attente, nom inconnu :
     * la tête de la puce dit déjà le problème). Null aussi quand la tête [head] contient déjà le nom (jamais répété).
     */
    fun tvPart(f: TvFacts, head: String? = null): String? {
        val part = when {
            f.lan == LanKind.ETHERNET -> "Câble réseau"
            f.lan == LanKind.WIFI && !f.wifiNoAddress -> short(f.wifiName)?.let { "Wi-Fi $it" }
            else -> null
        } ?: return null
        val name = f.wifiName?.trim().orEmpty()
        if (head != null && name.isNotEmpty() && f.lan == LanKind.WIFI && head.contains(name, ignoreCase = true)) return null
        return part
    }

    /** Le texte de la puce de l'accueil de la TV : tête, réseau (s'il y en a un à dire), code. */
    fun chip(head: String, network: String?, code: String): String =
        listOfNotNull(head, network, "code $code").joinToString("   ·   ")

    /** Où en est l'accès à la position (Android l'exige pour lire le nom du Wi-Fi) : accordé, demandable, ou refusé pour de bon (il faut passer par les réglages de l'appli). */
    enum class LocationAccess { GRANTED, CAN_ASK, BLOCKED }

    /** Ce qu'un appui sur le lien déclenche (le téléphone l'exécute : la règle ne touche à aucune API Android). */
    enum class NetAction { ASK_PERMISSION, OPEN_APP_SETTINGS, OPEN_LOCATION_SETTINGS }

    const val LINK_SEE = "Voir le Wi-Fi de ce téléphone"
    const val LINK_LOCATION_OFF = "Activez la localisation pour voir le nom du Wi-Fi"
    /** Dite avant la demande système (jamais au lancement : seulement après un appui sur [LINK_SEE]). */
    const val WHY_LOCATION = "Android exige l'accès à la position pour lire le nom du Wi-Fi. CastBridge ne garde ni n'envoie votre position."

    /**
     * La ligne de l'accueil du téléphone : [text] (peut manquer), [warn] = couleur d'avertissement, [changeWifi] = bouton « Changer de Wi-Fi »,
     * [linkText] + [action] = le lien discret (permission, réglages de l'appli, réglages de localisation).
     */
    data class Line(val text: String?, val warn: Boolean, val changeWifi: Boolean = false, val linkText: String? = null, val action: NetAction? = null)

    /** SSID lu par Android → nom propre ; null si absent, vide ou « <unknown ssid> » (permission ou localisation manquante). */
    fun cleanSsid(raw: String?): String? = raw?.trim()?.trim('"')?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("<unknown", ignoreCase = true) }

    /**
     * [tvWifi] = le nom du Wi-Fi dit par la TV (absent sur une ancienne TV ou en câble) ; [sameByIp] = [sameSubnet] (null = on ne sait pas) ;
     * [access] = l'accès à la position ; [phoneSsid] = le nom lu sur le téléphone (null sans permission ou localisation éteinte) ; [onWifi] = le téléphone a une adresse Wi-Fi.
     * Verdict : sur les noms quand les deux sont connus, sinon sur l'adresse IP. Rien tant que ce n'est pas sûr.
     */
    fun phoneLine(tvWifi: String?, sameByIp: Boolean?, access: LocationAccess = LocationAccess.GRANTED, phoneSsid: String? = null, onWifi: Boolean = true): Line? {
        val tv = short(tvWifi)
        val me = short(phoneSsid)
        // by name, when both are known
        val byName: Line? = when {
            me != null && tv != null -> if (cleanSsid(phoneSsid) == cleanSsid(tvWifi)) Line("Wi-Fi : $me · même réseau que la TV", warn = false)
                else Line("Ce téléphone est sur $me, la TV sur $tv", warn = true, changeWifi = true)
            else -> null
        }
        // by address (no permission needed)
        val byIp: Line? = if (tv == null) null else when (sameByIp) {
            true -> Line("Wi-Fi : $tv (le même que la TV)", warn = false)
            false -> Line("Ce téléphone n'est pas sur le Wi-Fi de la TV ($tv)", warn = true, changeWifi = true)
            null -> null
        }
        val base: Line? = byName ?: byIp ?: me?.let { Line("Wi-Fi : $it", warn = false) }
        val empty = Line(null, warn = false)
        val out: Line? = when (access) {
            // permission given but nothing readable while on Wi-Fi: the location is off
            LocationAccess.GRANTED -> if (onWifi && me == null) (base ?: empty).copy(linkText = LINK_LOCATION_OFF, action = NetAction.OPEN_LOCATION_SETTINGS) else base
            LocationAccess.CAN_ASK -> (base ?: empty).copy(linkText = LINK_SEE, action = NetAction.ASK_PERMISSION)
            LocationAccess.BLOCKED -> (base ?: empty).copy(linkText = LINK_SEE, action = NetAction.OPEN_APP_SETTINGS)
        }
        return out?.takeIf { it.text != null || it.linkText != null }
    }

    /**
     * Même sous-réseau IPv4 ? [phoneIp]/[prefix] = adresse du Wi-Fi actif du téléphone, [tvHost] = adresse connue de la TV.
     * null = on ne peut pas dire (pas d'IPv4, boucle locale du relais Bluetooth, préfixe absurde). Limite : un réseau invité qui réutilise le même préfixe passe pour « le même ».
     */
    fun sameSubnet(phoneIp: String?, prefix: Int, tvHost: String?): Boolean? {
        val a = ipv4(phoneIp) ?: return null
        val b = ipv4(tvHost) ?: return null
        if (prefix !in 8..30 || (b ushr 24) == 127L || (a ushr 24) == 127L) return null
        val mask = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        return (a and mask) == (b and mask)
    }

    private fun ipv4(s: String?): Long? {
        val p = s?.trim()?.split('.') ?: return null
        if (p.size != 4) return null
        var v = 0L
        for (x in p) { val n = x.toIntOrNull()?.takeIf { it in 0..255 && x.length <= 3 } ?: return null; v = (v shl 8) or n.toLong() }
        return v
    }
}
