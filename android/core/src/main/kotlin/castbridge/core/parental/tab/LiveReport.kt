package castbridge.core.parental.tab

import castbridge.core.parental.ChildProfile
import castbridge.core.parental.StoredReport

/**
 * The live report of the TV over Wi-Fi ([castbridge.core.parental.ParentalClient.report], parental PIN checked by the TV) turned into the same
 * daily reports the Bluetooth delivery brings, so that ONE pipeline ([ParentalLedger.absorb]) feeds every screen whatever the route. A pull is
 * the freshest report of the TV: its [StoredReport.ts] is the phone's clock at the time of the pull (newest wins for each day).
 */
object LiveReport {
    @Suppress("UNCHECKED_CAST")
    fun toReports(tv: String, report: Map<String, Any?>, profiles: List<ChildProfile>, nowMs: Long): List<StoredReport> {
        val sup = report["supervision"] as? Map<String, Any?>
        val blocked = (report["blocked"] as? List<Map<String, Any?>>).orEmpty()
        val tamper = (report["tamper"] as? List<Map<String, Any?>>).orEmpty()
        val out = ArrayList<StoredReport>()
        for (d in (report["days"] as? List<Map<String, Any?>>).orEmpty()) {
            val day = d["day"] as? String ?: continue
            for (p in (d["profiles"] as? List<Map<String, Any?>>).orEmpty()) {
                val id = p["id"] as? String ?: continue
                val name = p["name"] as? String ?: id
                fun n(k: String) = (p[k] as? Number)?.toLong() ?: 0L
                val cp = profiles.firstOrNull { it.id == id }
                val body = linkedMapOf<String, Any?>(
                    "v" to 1, "type" to "daily", "tv" to tv, "day" to day, "profile" to linkedMapOf("id" to id, "name" to name),
                    "totalMin" to n("play") + n("games") + n("downloads") + n("apps"),
                    "kinds" to linkedMapOf("play" to n("play"), "games" to n("games"), "downloads" to n("downloads"), "apps" to n("apps")),
                    "apps" to p["byApp"], "limitMin" to (cp?.dailyLimitMin ?: 0), "window" to cp?.window?.text(),
                    "blocked" to blocked.filter { it["who"] == name }.map { linkedMapOf("ts" to it["ts"], "what" to it["what"], "why" to it["why"]) },
                    "tamper" to tamper, "supervision" to sup?.let { linkedMapOf("state" to it["state"], "label" to it["label"]) },
                )
                out += StoredReport("live:$tv:$day:$id:$nowMs", tv, nowMs, "daily", body, true, nowMs)
            }
        }
        return out
    }
}
