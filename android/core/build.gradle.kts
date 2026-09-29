plugins { kotlin("jvm") }  // pure-logic module shared by the Android apps
kotlin { jvmToolchain(17) }
dependencies { testImplementation(kotlin("test")) }
