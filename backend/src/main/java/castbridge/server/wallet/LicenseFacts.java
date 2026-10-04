package castbridge.server.wallet;

import castbridge.server.licenses.EnvelopeVerifier;
import java.time.Instant;
import java.util.List;

/**
 * Ce que le portefeuille sait des LICENCES (précision du propriétaire, 2026-10-04 : « les licences sont différentes des clés d'activation ; elles sont gérées exclusivement par le
 * serveur pour les clés activées de production »). Interface en LECTURE SEULE : le portefeuille ne modifie ni le module des licences ni ses tables. Implémentation JDBC :
 * {@link JdbcLicenseFacts}.
 */
public interface LicenseFacts {
    /** États du module des licences ({@code lic_license.state}). */
    enum State { ACTIVE, SUSPENDED, REVOKED, EXPIRED }

    /**
     * @param endAt          {@code null} = licence sans fin (ILLIMITÉE)
     * @param updatedAt      dernière modification de la ligne : JAMAIS une date d'état fiable (n'importe quelle modification la fait glisser) ; dernier recours, après le journal d'audit et la date figée
     * @param seatReleasedAt date de libération du poste de cette TV (révocation, libération par l'administrateur), sinon {@code null} : borne la fin de l'intervalle de CE poste
     */
    record LicenseView(String licenseId, State state, Instant startAt, Instant endAt, int graceDays, Instant updatedAt, Instant seatReleasedAt) {
        public LicenseView(String licenseId, State state, Instant startAt, Instant endAt, int graceDays, Instant updatedAt) { this(licenseId, state, startAt, endAt, graceDays, updatedAt, null); }
    }

    /** Une ligne du journal d'audit des licences concernant une licence : {@code action} = LICENSE_SUSPEND, LICENSE_RESUME, LICENSE_REVOKE, LICENSE_EXPIRE, LICENSE_EXTEND… */
    record StateEvent(String action, Instant at) {}

    /**
     * Licences PAYANTES dont un poste {@code tv} porte ce code d'appareil, de la plus ancienne à la plus récente : poste ACTIVE, ou poste libéré PAR UNE RÉVOCATION (une révocation libère tous les
     * postes : la licence reste lisible pour que les tranches passées non versées suivent la règle de la conception). Vide si le module des licences est éteint ou si aucune n'existe.
     */
    List<LicenseView> forDevice(String deviceCode);

    /**
     * Ce que le serveur sait de la NOTIFICATION d'une licence ouverte par une activation rapportée (registrar de W23-05) : l'instant de la première notification qui l'a ouverte ou rattachée
     * ({@code firstNotifiedAt}) et si l'émission est DÉCLARÉE (journal, registre, émission du serveur) ou acceptée par le propriétaire. Origine de la limite de rattrapage ({@link CatchUpPolicy}).
     */
    record Notification(Instant firstNotifiedAt, boolean declared) {}

    /** La notification d'une licence, ou {@code null} : licence créée par le propriétaire, le registre ou l'émission du serveur (aucune limite de rattrapage, comme avant). */
    default Notification notification(String licenseId) { return null; }

    /** Historique des changements d'état d'une licence (journal d'audit chaîné du module des licences), du plus ancien au plus récent ; vide si illisible. */
    default List<StateEvent> history(String licenseId) { return List.of(); }

    /** Révocations (clés et postes) enregistrées par le module des licences ({@code lic_revocation}) : s'ajoutent au fichier facultatif de révocations. */
    default EnvelopeVerifier.Revocations revocations() { return EnvelopeVerifier.Revocations.none(); }
}
