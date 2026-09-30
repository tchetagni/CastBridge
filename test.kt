fun main() {
    val sorted = listOf(1, 2)
    val out = sorted.joinToString(",", "{\"files\":[", "]") { i -> "item$i" } + "],\"count\":${sorted.size}}"
    println(out)
}
