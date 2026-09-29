plugins { kotlin("jvm") }  // pure-logic module shared by the Android apps
dependencies {
    api("org.nanohttpd:nanohttpd:2.3.1")
    testImplementation(kotlin("test"))
}
// UTF-8 file names in tests, as on Android (CI/containers often have no locale set)
tasks.test { environment("LC_ALL", "C.UTF-8") }
