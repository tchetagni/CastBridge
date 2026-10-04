package castbridge.core.wallet.ui

import castbridge.core.wallet.Snapshot

/**
 * Aperçu AFFICHÉ d'une conversion : avant / après d'après l'instantané signé et le taux lu de `GET /policy` (mêmes formules que `Conversion.quote` du serveur : N2M paie `rate·q` NDEM ;
 * M2N paie `q` MBOKO et reçoit `rate·q − ceil(rate·q·fee)`, l'arrondi toujours contre le joueur). C'est un affichage : jamais enregistré, jamais utilisé ailleurs que sur l'écran ; le serveur
 * calcule seul la vraie conversion et son instantané signé est la seule source du solde après coup. Les jetons bloqués en mise ne se convertissent pas (seul le disponible compte).
 */
data class ConvertPreview(val payCur: String, val pay: Long, val gainCur: String, val gain: Long, val fee: Long, val beforeN: Long, val beforeM: Long, val afterN: Long, val afterM: Long, val enough: Boolean) {
    companion object {
        fun of(dir: ConvertDir, q: Long, policy: PolicyView, s: Snapshot): ConvertPreview {
            val gross = try { Math.multiplyExact(policy.rate, q) } catch (e: ArithmeticException) { Long.MAX_VALUE }
            return if (dir == ConvertDir.N2M) {
                ConvertPreview("NDEM", gross, "MBOKO", q, 0, s.n, s.m, s.n - gross, s.m + q, s.n >= gross)
            } else {
                val fee = try { (Math.multiplyExact(gross, policy.reverseFeeBp) + 9_999L) / 10_000L } catch (e: ArithmeticException) { gross }
                val gain = gross - fee
                ConvertPreview("MBOKO", q, "NDEM", gain, fee, s.n, s.m, s.n + gain, s.m - q, s.m >= q)
            }
        }
    }
}
