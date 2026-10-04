package castbridge.server.wallet;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/**
 * Point d'extension de {@code POST /api/v1/wallet/sync} (publié par w22-02, implémenté par w22-05 : blocages échus, résultats ; et w22-06 : bons en attente). Chaque contributeur
 * est un bean ; sa réponse est rendue sous son {@link #name()} dans {@code contributions}. Un contributeur ne peut RIEN créditer sur la parole de la TV (I-7) : il lit
 * {@link SyncContext#request()} comme une donnée non fiable. Une exception d'un contributeur n'empêche jamais la synchronisation : sa réponse est alors {@code {"erreur":…}}.
 */
public interface SyncContributor {
    /** Clé de la réponse dans {@code contributions} (stable, minuscules). */
    String name();

    /** Appelé après la matérialisation des tranches ; {@code ctx.identity()} est une identité DÉJÀ ouverte et prouvée par une activation. */
    Object contribute(SyncContext ctx);

    /** Ce que le contributeur voit : l'identité, l'horloge du serveur, la requête de la TV (non fiable) et la situation d'édition calculée par le serveur. */
    record SyncContext(String identity, Instant now, JsonNode request, GrantService.Standing standing) {}
}
