package castbridge.desktop

import kotlin.system.exitProcess

/** `castbridge-activation <commande>` ; sans argument : ouvre l'interface graphique si un écran est disponible, sinon l'aide. */
fun main(args: Array<String>) {
    if (args.isEmpty() && !java.awt.GraphicsEnvironment.isHeadless()) { Gui.launch(null); return }
    val code = try { Cli(Env()).run(args.toList()) } catch (e: Exception) { System.err.println("Erreur : ${e.message}"); 1 }
    if (code != Cli.GUI_RUNNING) exitProcess(code)
}
