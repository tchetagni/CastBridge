package castbridge.core.owner

/**
 * Option A of the owner (docs/TRIAL-EDITION.md § Usage soumis à autorisation): NOTHING is free without a key. Without a valid activation the TV app AND the phone app open no
 * function at all except what is needed to activate. [Feature.lockedAllowed] is false for every feature by default; the list of what stays reachable when locked is the
 * [LOCKED_WHITELIST], and a test pins it: a new feature that is reachable while locked fails the build.
 */
enum class Feature(val lockedAllowed: Boolean = false) {
    // ---- reachable while locked: the activation surface and nothing else ----
    USAGE_NOTICE(true), DEVICE_CODE(true), ACTIVATION_BLUETOOTH(true), ACTIVATION_FILE(true), ACTIVATION_MANUAL_ENTRY(true), OWNER_CHANNEL(true), DISPLAY_LANGUAGE(true),
    /** The bare Bluetooth link a phone needs to hand an activation to a TV (pairing and the ACTIVATION frame), no other service. */
    MINIMAL_BLUETOOTH_LINK(true),
    /** Phone: share the device code (WhatsApp, SMS, e-mail) and carry an activation for a TV ([CarrierMode]). */
    SHARE_DEVICE_CODE(true), CARRY_ACTIVATION_FOR_TV(true),

    // ---- everything else is locked ----
    PLAYER, LIBRARY, REMOTE_CONTROL, FILE_TRANSFER, USB_IMPORT, DOWNLOADS, LEARN, QUIZ, GAMES, CHESS, SUDOKU, PARENTAL, LOTS_SYNC, UPDATES, SSH, ADMIN_API, WIFI_DIRECT, DLNA, SCREEN_CAPTURE, TELEMETRY, TV_PAIRING_FULL
}

val LOCKED_WHITELIST: Set<Feature> = Feature.values().filter { it.lockedAllowed }.toSet()

/**
 * The activation requirement is a COMPILE SWITCH (`REQUIRE_ACTIVATION`, a BuildConfig field fed by the Gradle property `requireActivation`), OFF by default in development builds and in every
 * build installed on the owner's own TV and phone until the token tools are delivered and tested end to end (otherwise the owner would lock themselves out). Turning it on is an explicit,
 * documented act (docs/TRIAL-EDITION.md § Interrupteur de déploiement).
 */
data class ActivationRequirement(val required: Boolean = false, val graceDays: Int = 14) {
    init { require(graceDays in 0..365) }
}

/**
 * Grace period of the locked build, ABSOLUTE and never restarted: it applies only to an install that already existed BEFORE the lock was introduced
 * ([existing] = the package's first-install time, which survives « clear data » and an over-install, is earlier than [graceStartMs]), and it ends at
 * [graceStartMs] + graceDays whatever the user does (over-install, clear data). A fresh install, or an uninstall followed by an install, has a first-install
 * time after [graceStartMs]: locked at once. Both times are injected so the rule is testable.
 * The receiver also refuses the grace when `clock.txt` was absent at start while the first-install time predates the lock (a reinstall with the date wound back): see [FleetMigration.of].
 * `graceDays` comes from `lock.graceDays` of version.properties (0 = no grace at all).
 */
data class FleetMigration(val existingInstall: Boolean, val graceStartMs: Long) {
    fun graceUntil(req: ActivationRequirement): Long? = if (existingInstall && req.graceDays > 0) graceStartMs + req.graceDays * 24L * 3600 * 1000 else null

    companion object {
        /**
         * [firstInstallTimeMs] = PackageInfo.firstInstallTime; [lockGraceStartMs] = the build constant LOCK_GRACE_START_MS; [clockFileExistedAtStart] = `clock.txt` (or its `.bak`) existed
         * BEFORE this start created it: a genuine pre-lock install has run before and left it, while an uninstall + date wound back + reinstall shows a pre-lock install time WITHOUT it.
         */
        fun of(firstInstallTimeMs: Long, lockGraceStartMs: Long, clockFileExistedAtStart: Boolean = true) =
            FleetMigration(firstInstallTimeMs < lockGraceStartMs && clockFileExistedAtStart, lockGraceStartMs)
    }
}

sealed class GateState {
    abstract val label: String
    /** The requirement is off (development, owner's own devices before the tools are ready). */
    object NotRequired : GateState() { override val label = "Activation non exigée" }
    /** A key is installed: every feature is reachable (the edition decides the CONTENT, see EditionPolicy). */
    data class Activated(val access: TvAccess) : GateState() { override val label get() = access.label }
    /**
     * An existing install inside its grace period: the activation is offered at launch. The grace applies the restrictions of the trial edition ([trialRestricted], [TrialPolicy]):
     * it is a courtesy to keep the TV usable, not an « everything open » window.
     */
    data class Grace(val untilMs: Long, val trialRestricted: Boolean = true) : GateState() { override val label = "Période de grâce : activation à fournir" }
    /** No key: only the activation surface. Data already on the device stays untouched on disk. */
    object Locked : GateState() { override val label = "Usage soumis à autorisation" }
}

object FeatureGate {
    fun state(req: ActivationRequirement, access: TvAccess, nowMs: Long, migration: FleetMigration? = null): GateState = when {
        !req.required -> GateState.NotRequired
        access.keyInstalled -> GateState.Activated(access)
        migration?.graceUntil(req)?.let { nowMs < it } == true -> GateState.Grace(migration.graceUntil(req)!!)
        else -> GateState.Locked
    }

    fun canUse(feature: Feature, state: GateState): Boolean = when (state) {
        is GateState.Locked -> feature.lockedAllowed
        else -> true
    }

    /** What the locked screen shows besides the notice. */
    fun lockedFeatures(): Set<Feature> = LOCKED_WHITELIST
}

/** Where an activation arrives from (docs/TRIAL-EDITION.md § Trois canaux). */
enum class Channel { BLUETOOTH, FILE, MANUAL }

/**
 * Receives an activation by any of the three channels on a TV or a phone and checks it: the Bluetooth frame, the `activation` file of the USB drive (CRLF, BOM and a final newline tolerated),
 * and what a person types or pastes (a full token, the grouped text, or the compact key). One verification path ([ActivationVerifier] / [CompactActivation]).
 */
class ActivationReceiver(private val ring: KeyRing, private val trusted: List<TrustedKey>, private val device: Fingerprints, private val subject: Subject = Subject.TV,
                         private val revocations: RevocationState = RevocationState()) {
    private val deviceCode = DeviceCode.of(device)
    private val verifier = ActivationVerifier(ring, revocations = revocations, expect = subject)

    fun receive(channel: Channel, payload: ByteArray, nowMs: Long): ActivationResult = when (channel) {
        Channel.BLUETOOTH -> {
            val f = OwnerFrames.read(payload.inputStream())
            if (f == null || f.type != OwnerFrames.ACTIVATION) ActivationResult.Rejected(Rejection.MALFORMED, "Trame d'activation illisible")
            else verifier.verify(f.text, device, nowMs)
        }
        Channel.FILE -> verifier.verify(String(payload, Charsets.UTF_8).removePrefix("﻿").lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: "", device, nowMs)
        Channel.MANUAL -> manual(String(payload, Charsets.UTF_8).trim(), nowMs)
    }

    private fun manual(text: String, nowMs: Long): ActivationResult {
        if (text.startsWith(Envelope.PREFIX + ".")) return verifier.verify(text, device, nowMs)
        val grouped = GroupedText.decode(text)
        if (grouped is GroupedText.Decoded.Ok) {
            val token = String(grouped.bytes, Charsets.US_ASCII)
            if (token.startsWith(Envelope.PREFIX + ".")) return verifier.verify(token, device, nowMs)
        }
        return CompactActivation.verify(text, ring, trusted, deviceCode, nowMs)
    }
}

/** What a device holds once activations are installed: dedupes by licence and seat (the newest wins), so reinstalling the same file twice changes nothing. */
class InstalledActivations(initial: List<Activation> = emptyList()) {
    private val byKey = LinkedHashMap<String, Activation>()
    init { initial.forEach(::install) }
    fun install(a: Activation) {
        val key = if (a.kind == ActivationKind.TRIAL) "trial" else "${a.license}|${a.seat}"
        val old = byKey[key]
        if (old == null || a.issuedAt >= old.issuedAt) byKey[key] = a
    }
    fun all(): List<Activation> = byKey.values.toList()
}

/**
 * Locked phone as a CARRIER: it can receive an activation issued for a TV (typed, pasted, scanned or from a file) and hand it to the TV over Bluetooth, and it unlocks NOTHING for itself.
 * It reads the token only to say which TV it is for; it does not verify it against its own hardware (the TV does).
 */
object CarrierMode {
    class Item(val token: String, val forDeviceCode: String?, val kind: ActivationKind?, val description: String)

    /** [text] = a full token, the grouped text or a compact key, as typed or scanned. Null when it is none of these (nothing is forwarded). */
    fun accept(text: String): Item? {
        val t = text.trim()
        if (t.startsWith(Envelope.PREFIX + ".")) {
            val a = Activation.decode(t) ?: return null
            if (a.subject != Subject.TV) return null
            val code = DeviceCode.of(Fingerprints(a.factors))
            return Item(t, code, a.kind, "Activation pour la TV $code")
        }
        val g = GroupedText.decode(t)
        if (g is GroupedText.Decoded.Ok) return accept(String(g.bytes, Charsets.US_ASCII))
        val c = CompactActivation.parse(t)
        if (c is CompactActivation.Parsed.Ok) return Item(t, null, c.header.kind, "Clé saisissable liée à une TV")
        return null
    }

    /** The Bluetooth frame to hand a full token to the TV (a compact key is typed on the TV: it has no frame). */
    fun frame(item: Item): ByteArray? = if (item.token.startsWith(Envelope.PREFIX + ".")) OwnerFrames.encode(OwnerFrames.ACTIVATION, item.token) else null
}

/** Who issues the trial key (owner decision): MANUAL by default during the offline phase (the owner generates it from the device code), AUTOMATIC when the server may deliver it to an online phone. */
enum class TrialIssuancePolicy {
    MANUAL, AUTOMATIC;
    companion object { val DEFAULT = MANUAL }
}

object TrialIssuance {
    sealed class Decision {
        /** Show the device code and the ways to send it to the owner; wait for the key. */
        object AskOwner : Decision()
        /** The paired, online phone fetches the signed trial key from the server and hands it to the TV by Bluetooth, without typing. */
        object FetchFromServerViaPhone : Decision()
    }

    /** AUTOMATIC is never the default: it needs the explicit policy, a paired phone and a reachable server. */
    fun decide(policy: TrialIssuancePolicy, phonePaired: Boolean, serverReachable: Boolean): Decision =
        if (policy == TrialIssuancePolicy.AUTOMATIC && phonePaired && serverReachable) Decision.FetchFromServerViaPhone else Decision.AskOwner
}

/** The French texts of the locked screens (TV and phone). The legal wording of the usage notice is the owner's to validate: it is a placeholder, not legal advice. */
object LockedTexts {
    const val REQUEST = "Usage soumis à autorisation : fournissez ce code d'appareil à CastBridge pour obtenir votre clé."
    const val NOTICE_TITLE = "Avis d'usage"
    const val NOTICE = "CastBridge peut être installé librement, mais son usage est soumis à l'autorisation du propriétaire. " +
        "Pour l'utiliser, communiquez le code d'appareil affiché à CastBridge : une clé d'activation vous sera remise. " +
        "[Texte à valider par le propriétaire : il ne constitue pas un avis juridique.]"
    const val WAYS = "Envoyer le code : le lire à voix haute, le copier, ou le partager par WhatsApp, SMS ou e-mail depuis le téléphone."
    /** The three real ways to give the key to the TV (activation screen), short on purpose; see docs/TV-ACTIVATION-CLE-USB.md. */
    val KEY_WAYS = listOf(
        "1. Le plus simple : sur le téléphone, CastBridge > « Activer la TV » (Bluetooth).",
        "2. Ou le fichier de la clé USB : bouton « Choisir le fichier d'activation » (n'importe quel nom), ou nommé exactement « activation » dans Download/CastBridge ou Android/data/castbridge.receiver/files.",
        "3. Ou collez la clé dans le champ ci-dessous.")
    const val SEARCHING = "Recherche…"
    const val KEY_FOUND = "Clé trouvée : vérification…"
    const val PHONE_CARRIER = "Ce téléphone peut recevoir une clé d'activation pour votre TV et la lui remettre par Bluetooth, sans rien débloquer pour lui-même."
    const val GRACE = "Votre appareil était déjà installé : il continue de fonctionner pendant la période de grâce. Fournissez votre code d'appareil pour obtenir votre clé."

    /** The text the phone shares (WhatsApp, SMS, e-mail) with the device code. */
    fun shareMessage(code: String, subject: Subject) =
        "Code d'appareil CastBridge (${if (subject == Subject.TV) "TV" else "téléphone"}) : $code\nMerci de me transmettre la clé d'activation."
}
