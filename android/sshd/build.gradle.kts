plugins { kotlin("jvm") }  // SSH server for CastBridge TV: pure Java (Apache MINA SSHD), no native code

val mina = "2.12.1"
dependencies {
    api(project(":core"))
    api("org.apache.sshd:sshd-core:$mina")
    implementation("org.apache.sshd:sshd-sftp:$mina")
    implementation("org.apache.sshd:sshd-scp:$mina")
    implementation("net.i2p.crypto:eddsa:0.3.0")       // ed25519 client keys (not in the JDK on Android)
    implementation("org.slf4j:slf4j-nop:1.7.36")        // MINA logs through slf4j; we log ourselves
    testImplementation(kotlin("test"))
}
tasks.test { environment("LC_ALL", "C.UTF-8") }
