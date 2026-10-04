package castbridge.server.wallet;

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
     * @param endAt     {@code null} = licence sans fin (ILLIMITÉE)
     * @param updatedAt dernière modification de la ligne : sert de date de suspension ou de révocation (approximation documentée)
     */
    record LicenseView(String licenseId, State state, Instant startAt, Instant endAt, int graceDays, Instant updatedAt) {}

    /** Licences PAYANTES dont un poste {@code tv} ACTIVE porte ce code d'appareil, de la plus ancienne à la plus récente ; vide si le module des licences est éteint ou si aucune n'existe. */
    List<LicenseView> forDevice(String deviceCode);
}
