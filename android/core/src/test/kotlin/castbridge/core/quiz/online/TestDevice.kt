package castbridge.core.quiz.online

private val devCounter = java.util.concurrent.atomic.AtomicInteger()

/** Un identifiant d'appareil neuf : le Quiz en ligne exige un `deviceHash` (un par appareil) en Internet. */
fun dv() = "device-%06d".format(devCounter.incrementAndGet())
