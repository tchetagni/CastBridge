package castbridge.core.tv.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HomeGroupsTest {
    private fun t(id: String, on: Boolean = false, warn: Boolean = false, status: String? = null) = HomeTileInfo(id, "L-$id", status, on, warn)
    private val ALL = listOf("library", "pair", "learn", "langues", "games", "downloads", "remote", "receive", "usb", "bluetooth", "internet",
        "wifi_direct", "admin", "updates", "settings", "dev_options", "parental", "help", "wallet", "online_game", "phones")
    private fun all() = ALL.map { t(it) }
    private fun ids(es: List<HomeEntry>) = es.map { it.id }
    private fun group(es: List<HomeEntry>, id: String) = es.first { it.id == id } as HomeEntry.Group

    @Test fun `cinq groupes dans l'ordre voulu avec les bons noms`() {
        assertEquals(listOf("Médias", "Apprendre", "Jeux et jetons", "Téléphones et réseau", "Administration"), HomeGroups.GROUPS.map { it.label })
        assertEquals(listOf("library", "downloads", "usb", "receive"), HomeGroups.GROUPS[0].tileIds)
        assertEquals(listOf("learn", "langues"), HomeGroups.GROUPS[1].tileIds)
        assertEquals(listOf("games", "online_game", "wallet"), HomeGroups.GROUPS[2].tileIds)
        assertEquals(listOf("pair", "remote", "bluetooth", "wifi_direct", "internet", "phones"), HomeGroups.GROUPS[3].tileIds)
        assertEquals(listOf("admin", "updates", "settings", "dev_options", "parental", "help"), HomeGroups.GROUPS[4].tileIds)
    }

    @Test fun `une tuile n'appartient qu'a un seul groupe`() {
        val flat = HomeGroups.GROUPS.flatMap { it.tileIds }
        assertEquals(flat.size, flat.toSet().size)
        assertEquals("media", HomeGroups.groupOf("usb")?.id)
        assertEquals("network", HomeGroups.groupOf("pair")?.id)
        assertNull(HomeGroups.groupOf("upgrade"))
    }

    @Test fun `les identifiants du mappage sont ceux de PlayerActivity tile, aucun oublie`() {
        // la source de vérité des identifiants : les appels tile("…") du récepteur (et les HomeTool à identifiant explicite)
        val src = File("../receiver/src/main/kotlin/castbridge/receiver/PlayerActivity.kt").takeIf { it.exists() }?.readText()
            ?: File("receiver/src/main/kotlin/castbridge/receiver/PlayerActivity.kt").readText()
        val used = Regex("""\btile\("([a-z_]+)"""").findAll(src).map { it.groupValues[1] }.toSet() +
            Regex("""(?:homeId|\bid) = "([a-z_]+)"""").findAll(src).map { it.groupValues[1] }.toSet()
        val mapped = HomeGroups.GROUPS.flatMap { it.tileIds }.toSet() + HomeGroups.QUICK_ACCESS
        val missing = used - mapped
        assertTrue("tuiles de l'accueil sans groupe ni accès rapide : $missing", missing.isEmpty())
        assertTrue("ids de groupes absents du récepteur", setOf("library", "downloads", "usb", "receive", "learn", "langues", "games", "remote", "bluetooth", "wifi_direct", "internet", "admin", "updates", "settings", "dev_options", "help", "pair").all { it in used })
    }

    @Test fun `accueil complet  - acces rapide puis cinq groupes, rien d'autre`() {
        val es = HomeGroups.layout(all())
        assertEquals(listOf("library", "media", "learn", "games", "network", "admin"), ids(es))
        assertTrue(es[0] is HomeEntry.Direct)
        assertTrue(es.drop(1).all { it is HomeEntry.Group })
    }

    @Test fun `la mise a niveau de l'essai reste sur l'accueil avant Bibliotheque`() {
        val es = HomeGroups.layout(listOf(t("upgrade"), t("library"), t("usb"), t("receive")))
        assertEquals(listOf("upgrade", "library", "media"), ids(es))
    }

    @Test fun `l'ordre des tuiles dans la grille suit la definition, pas l'ordre recu`() {
        val es = HomeGroups.layout(listOf(t("receive"), t("usb"), t("downloads"), t("library")))
        assertEquals(listOf("library", "downloads", "usb", "receive"), group(es, "media").tiles.map { it.id })
    }

    @Test fun `les tuiles masquees restent masquees et un groupe sans tuile disparait`() {
        val es = HomeGroups.layout(all().filter { it.id !in setOf("learn", "langues", "games", "online_game", "wallet") })
        assertEquals(listOf("library", "media", "network", "admin"), ids(es))
        assertFalse(es.any { it.id == "learn" || it.id == "games" })
    }

    @Test fun `un groupe a une seule tuile visible ouvre cette tuile directement`() {
        val es = HomeGroups.layout(listOf(t("learn"), t("games"), t("wallet")))     // Langues masquée
        assertEquals(listOf("learn", "games"), ids(es))                              // Apprendre direct ; Jeux et jetons = 2 tuiles = groupe
        assertTrue(es[0] is HomeEntry.Direct)
        assertEquals("L-learn", es[0].label)
        val es2 = HomeGroups.layout(listOf(t("learn"), t("langues"), t("games")))
        assertEquals(listOf("learn", "games"), ids(es2))
        assertTrue(es2[0] is HomeEntry.Group && es2[1] is HomeEntry.Direct)
    }

    @Test fun `Bibliotheque seule dans Medias n'est pas repetee`() {
        val es = HomeGroups.layout(listOf(t("library")))
        assertEquals(listOf("library"), ids(es))
    }

    @Test fun `une tuile inconnue n'est jamais perdue, elle suit les groupes`() {
        val es = HomeGroups.layout(listOf(t("zzz"), t("usb"), t("receive")))
        assertEquals(listOf("media", "zzz"), ids(es))
        assertTrue(es[1] is HomeEntry.Direct)
    }

    @Test fun `aucune tuile, aucun bouton`() = assertTrue(HomeGroups.layout(emptyList()).isEmpty())

    @Test fun `profil enfant  - seules les tuiles gardees apparaissent, les groupes se vident`() {
        val kept = listOf("library", "games", "learn")          // ce que ParentalHub.filterHome laisse passer pour un enfant
        val es = HomeGroups.layout(all().filter { it.id in kept })
        assertEquals(listOf("library", "learn", "games"), ids(es))
        assertTrue(es.all { it is HomeEntry.Direct })
    }

    @Test fun `resume du bouton de groupe`() {
        assertEquals("2 outils", HomeGroups.summary(listOf(t("a"), t("b"))))
        assertEquals("4 outils · 1 actif", HomeGroups.summary(listOf(t("a", on = true), t("b"), t("c"), t("d"))))
        assertEquals("3 outils · 2 actifs", HomeGroups.summary(listOf(t("a", on = true), t("b", on = true), t("c"))))
        assertEquals("3 outils · à vérifier", HomeGroups.summary(listOf(t("a", on = true), t("b", warn = true), t("c"))))
        assertEquals("1 outil", HomeGroups.summary(listOf(t("a"))))
    }

    @Test fun `le groupe porte les badges des tuiles pour la grille et le resume pour le bouton`() {
        val es = HomeGroups.layout(listOf(t("wifi_direct", on = true, status = "Activé"), t("bluetooth", status = "Désactivé"), t("pair", warn = true, status = "Aucun")))
        val g = group(es, "network")
        assertEquals(listOf("Aucun", "Désactivé", "Activé"), g.tiles.map { it.status })
        assertTrue(g.on); assertTrue(g.warn)
        assertEquals("3 outils · à vérifier", g.summary)
    }

    // ---- grille
    @Test fun `trois colonnes, lignes selon le nombre`() {
        assertEquals(3, HomeGrid.COLUMNS)
        assertEquals(0, HomeGrid.rows(0)); assertEquals(1, HomeGrid.rows(1)); assertEquals(1, HomeGrid.rows(3)); assertEquals(2, HomeGrid.rows(4))
        assertEquals(2, HomeGrid.rows(6)); assertEquals(3, HomeGrid.rows(7)); assertEquals(4, HomeGrid.rows(10))
        assertEquals(1, HomeGrid.row(3)); assertEquals(0, HomeGrid.col(3)); assertEquals(2, HomeGrid.col(5))
    }

    @Test fun `deplacements du focus au milieu d'une grille de 6`() {
        assertEquals(1, HomeGrid.move(0, HomeKey.RIGHT, 6))
        assertEquals(0, HomeGrid.move(1, HomeKey.LEFT, 6))
        assertEquals(3, HomeGrid.move(0, HomeKey.DOWN, 6))
        assertEquals(1, HomeGrid.move(4, HomeKey.UP, 6))
    }

    @Test fun `bords  - pas de bouclage, on reste sur place`() {
        assertEquals(0, HomeGrid.move(0, HomeKey.LEFT, 6))
        assertEquals(3, HomeGrid.move(3, HomeKey.LEFT, 6))        // début de la 2e ligne : ne remonte pas à la ligne d'avant
        assertEquals(2, HomeGrid.move(2, HomeKey.RIGHT, 6))
        assertEquals(5, HomeGrid.move(5, HomeKey.RIGHT, 6))
        assertEquals(1, HomeGrid.move(1, HomeKey.UP, 6))
        assertEquals(4, HomeGrid.move(4, HomeKey.DOWN, 6))
    }

    @Test fun `derniere ligne incomplete  - BAS tombe sur la derniere tuile, DROITE s'arrete a la derniere`() {
        // 5 tuiles : ligne 1 = 0 1 2 ; ligne 2 = 3 4
        assertEquals(4, HomeGrid.move(2, HomeKey.DOWN, 5))
        assertEquals(4, HomeGrid.move(1, HomeKey.DOWN, 5))
        assertEquals(3, HomeGrid.move(0, HomeKey.DOWN, 5))
        assertEquals(4, HomeGrid.move(4, HomeKey.RIGHT, 5))
        assertEquals(4, HomeGrid.move(4, HomeKey.DOWN, 5))
    }

    @Test fun `grille d'une tuile ou vide`() {
        for (k in listOf(HomeKey.LEFT, HomeKey.RIGHT, HomeKey.UP, HomeKey.DOWN)) assertEquals(0, HomeGrid.move(0, k, 1))
        assertEquals(-1, HomeGrid.move(0, HomeKey.DOWN, 0))
        assertEquals(-1, HomeGrid.clamp(3, 0))
    }

    @Test fun `le focus ne sort jamais de la grille, meme avec un indice perime`() {
        assertEquals(3, HomeGrid.clamp(9, 4))
        assertEquals(0, HomeGrid.clamp(-2, 4))
        assertEquals(3, HomeGrid.move(9, HomeKey.LEFT, 4))      // 9 -> 3 (dernier, colonne 0) ; GAUCHE reste
        for (n in 1..12) for (i in 0 until n) for (k in listOf(HomeKey.LEFT, HomeKey.RIGHT, HomeKey.UP, HomeKey.DOWN)) {
            val j = HomeGrid.move(i, k, n)
            assertTrue("n=$n i=$i k=$k -> $j", j in 0 until n)
        }
    }

    // ---- machine d'ouverture / fermeture
    private val entries = HomeGroups.layout(all())

    @Test fun `OK sur un groupe ouvre la grille sur la premiere tuile`() {
        val s = HomeNav.press(HomeNavState.Closed(), entries, HomeKey.OK, focusId = "network")
        assertEquals(HomeNavState.Open("network", 0, "network"), s.state)
        assertEquals(HomeAction.FocusGrid(0), s.action)
        assertTrue(s.consumed)
    }

    @Test fun `OK sur une tuile directe la lance sans ouvrir de grille`() {
        val s = HomeNav.press(HomeNavState.Closed(), entries, HomeKey.OK, focusId = "library")
        assertEquals(HomeAction.Launch("library"), s.action)
        assertTrue(s.state is HomeNavState.Closed)
    }

    @Test fun `ferme  - les fleches et RETOUR ne sont pas consommes, Android garde la main`() {
        for (k in listOf(HomeKey.LEFT, HomeKey.RIGHT, HomeKey.UP, HomeKey.DOWN, HomeKey.BACK)) {
            val s = HomeNav.press(HomeNavState.Closed(), entries, k, focusId = "media")
            assertFalse("$k", s.consumed); assertNull(s.action)
        }
    }

    @Test fun `grille ouverte  - les fleches deplacent le focus et sont consommees`() {
        var st: HomeNavState = HomeNavState.Open("network", 0, "network")      // 6 tuiles : pair remote bluetooth wifi_direct internet phones
        st = HomeNav.press(st, entries, HomeKey.RIGHT).also { assertEquals(HomeAction.FocusGrid(1), it.action); assertTrue(it.consumed) }.state
        st = HomeNav.press(st, entries, HomeKey.DOWN).state
        assertEquals(HomeNavState.Open("network", 4, "network"), st)
        st = HomeNav.press(st, entries, HomeKey.LEFT).state
        st = HomeNav.press(st, entries, HomeKey.LEFT).state
        assertEquals(HomeNavState.Open("network", 3, "network"), st)
    }

    @Test fun `grille ouverte  - OK lance la tuile focalisee`() {
        val s = HomeNav.press(HomeNavState.Open("media", 2, "media"), entries, HomeKey.OK)
        assertEquals(HomeAction.Launch("usb"), s.action)
        assertEquals(HomeNavState.Open("media", 2, "media"), s.state)       // la grille reste ouverte derrière l'écran lancé
    }

    @Test fun `RETOUR ferme la grille et rend le focus au bouton du groupe`() {
        val s = HomeNav.press(HomeNavState.Open("admin", 4, "admin"), entries, HomeKey.BACK)
        assertEquals(HomeNavState.Closed("admin"), s.state)
        assertEquals(HomeAction.FocusHome("admin"), s.action)
        assertTrue(s.consumed)
        // un second RETOUR n'est plus consommé : l'accueil quitte comme avant
        assertFalse(HomeNav.press(s.state, entries, HomeKey.BACK).consumed)
    }

    @Test fun `aller-retour complet sur le meme bouton`() {
        val open = HomeNav.press(HomeNavState.Closed(), entries, HomeKey.OK, focusId = "games")
        val moved = HomeNav.press(open.state, entries, HomeKey.RIGHT)
        val back = HomeNav.press(moved.state, entries, HomeKey.BACK)
        assertEquals(HomeAction.FocusHome("games"), back.action)
    }

    @Test fun `le groupe ouvert disparait pendant une mise a jour  - retour a l'accueil`() {
        val fewer = HomeGroups.layout(all().filter { it.id !in setOf("learn", "langues") })
        val st = HomeNav.reconcile(HomeNavState.Open("learn", 1, "learn"), fewer)
        assertEquals(HomeNavState.Closed("library"), st)
        val s = HomeNav.press(HomeNavState.Open("learn", 1, "learn"), fewer, HomeKey.OK)
        assertEquals(HomeNavState.Closed("library"), s.state)
        assertEquals(HomeAction.FocusHome("library"), s.action)
    }

    @Test fun `la grille rapetisse  - le focus suit la meme tuile ou se ramene dans la grille`() {
        val fewer = HomeGroups.layout(all().filter { it.id != "remote" })
        val st = HomeNav.reconcile(HomeNavState.Open("network", 3, "network"), fewer, focusedTileId = "wifi_direct")
        assertEquals(HomeNavState.Open("network", 2, "network"), st)        // pair bluetooth wifi_direct internet phones : wifi_direct = 2
        val small = HomeGroups.layout(all().filter { it.id !in setOf("remote", "wifi_direct", "internet", "phones") })   // pair bluetooth
        val st2 = HomeNav.reconcile(HomeNavState.Open("network", 5, "network"), small, focusedTileId = "phones")
        assertEquals(HomeNavState.Open("network", 1, "network"), st2)
    }

    @Test fun `accueil ferme  - le bouton memorise disparait, le focus passe au premier`() {
        val fewer = HomeGroups.layout(all().filter { it.id !in setOf("learn", "langues") })
        assertEquals(HomeNavState.Closed("library"), HomeNav.reconcile(HomeNavState.Closed("learn"), fewer))
        assertEquals(HomeNavState.Closed("media"), HomeNav.reconcile(HomeNavState.Closed("media"), fewer))
    }
}
