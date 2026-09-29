plugins { kotlin("jvm") version "2.1.0" }  // pure-logic module; Android-dependent code lives in :sender/:receiver
repositories { mavenCentral() }

dependencies { testImplementation(kotlin("test")) }
