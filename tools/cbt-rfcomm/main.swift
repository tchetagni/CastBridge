// cbt-rfcomm: raw Bluetooth RFCOMM client for CastBridge TV, for macOS (IOBluetooth).
//
//   cbt-rfcomm list  <tv-address>                      SDP services of the TV (name, RFCOMM channel)
//   cbt-rfcomm send  <tv-address> <pin> <file> [name]  push a file with the CBT1 protocol (resumable)
//   cbt-rfcomm proxy <tv-address> <port> api|ssh [--channel N]
//                                                      local TCP listener (127.0.0.1:<port>) bridged to the TV's
//                                                      "CastBridge API" (its HTTP API) or "CastBridge SSH" service
//
// The Mac must be paired with the TV first (System Settings > Bluetooth). Protocol: see BtProtocol.kt.
import Foundation
import IOBluetooth

let serviceUUID = "7c5e3b9a-4d2f-4c61-9b0e-cb0000000001"

/// The TV's RFCOMM services. `api` answers one status byte first (0 = ok, 1 busy, 2 refused, 3 server down, 4 internal);
/// `ssh` is the raw SSH byte stream (the SSH server speaks first: "SSH-2.0-...").
struct Service {
    let key: String, uuid: String, name: String
    static let file = Service(key: "file", uuid: serviceUUID, name: "CastBridge TV")
    static let ssh = Service(key: "ssh", uuid: "7c5e3b9a-4d2f-4c61-9b0e-cb0000000002", name: "CastBridge SSH")
    static let api = Service(key: "api", uuid: "7c5e3b9a-4d2f-4c61-9b0e-cb0000000003", name: "CastBridge API")
}

let tunnelStatus = [1: "la TV a déjà trop de liaisons Bluetooth ouvertes, réessayez dans quelques secondes",
                    2: "la TV n'autorise pas ce Mac sur ce service (appairage Bluetooth, ou « API par Bluetooth » coupée sur la TV : MENU > Administration)",
                    3: "le service de la TV ne répond pas (SSH activé sur la TV ? CastBridge-TV vient de redémarrer ?)",
                    4: "erreur interne sur la TV"]

func info(_ msg: String) { FileHandle.standardError.write((msg + "\n").data(using: .utf8)!) }

func fail(_ msg: String) -> Never { FileHandle.standardError.write((msg + "\n").data(using: .utf8)!); exit(1) }

func uuidBytes(_ s: String) -> [UInt8] {
    let hex = s.replacingOccurrences(of: "-", with: "")
    return stride(from: 0, to: hex.count, by: 2).map {
        UInt8(hex[hex.index(hex.startIndex, offsetBy: $0)..<hex.index(hex.startIndex, offsetBy: $0 + 2)], radix: 16)!
    }
}

final class SDPWaiter: NSObject {
    var done = false
    var status: IOReturn = 0
    @objc func sdpQueryComplete(_ device: IOBluetoothDevice!, status: IOReturn) { self.status = status; done = true }
}

func refreshSDP(_ d: IOBluetoothDevice) {
    // Fresh query (not the cache from pairing time): the CastBridge services are registered only while the TV app runs
    // (and "CastBridge SSH" only while SSH is on there), and their channel numbers change when they are re-published.
    let waiter = SDPWaiter()
    let r = d.performSDPQuery(waiter)
    if r != kIOReturnSuccess { fail("requête SDP échouée (\(r)) : la TV est-elle allumée et à portée ?") }
    let end = Date().addingTimeInterval(20)
    while !waiter.done && Date() < end { RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(0.1)) }
    if !waiter.done { info("SDP : pas de réponse en 20 s, cache utilisé") }
    else if waiter.status != kIOReturnSuccess { info("SDP : statut \(waiter.status)") }
}

func device(_ address: String) -> IOBluetoothDevice {
    guard let d = IOBluetoothDevice(addressString: address.replacingOccurrences(of: ":", with: "-")) else { fail("adresse invalide") }
    if !d.isPaired() { fail("le Mac n'est pas appairé avec \(address)") }
    refreshSDP(d)
    return d
}

// ---- channel cache: the channel found last time is tried first (SDP can be slow or stale on macOS) ----
let cachePath = NSHomeDirectory() + "/.cache/cbt-rfcomm.json"
func cacheKey(_ d: IOBluetoothDevice, _ svc: Service) -> String { (d.addressString ?? "?").uppercased().replacingOccurrences(of: "-", with: ":") + "/" + svc.key }
func cachedChannel(_ d: IOBluetoothDevice, _ svc: Service) -> BluetoothRFCOMMChannelID? {
    guard let data = FileManager.default.contents(atPath: cachePath),
          let m = (try? JSONSerialization.jsonObject(with: data)) as? [String: Int], let v = m[cacheKey(d, svc)], v > 0, v < 31 else { return nil }
    return BluetoothRFCOMMChannelID(v)
}
func saveChannel(_ d: IOBluetoothDevice, _ svc: Service, _ ch: BluetoothRFCOMMChannelID) {
    var m: [String: Int] = [:]
    if let data = FileManager.default.contents(atPath: cachePath), let old = (try? JSONSerialization.jsonObject(with: data)) as? [String: Int] { m = old }
    m[cacheKey(d, svc)] = Int(ch)
    try? FileManager.default.createDirectory(atPath: (cachePath as NSString).deletingLastPathComponent, withIntermediateDirectories: true)
    if let out = try? JSONSerialization.data(withJSONObject: m) { try? out.write(to: URL(fileURLWithPath: cachePath)) }
}

/// Channel from the SDP answer (by UUID, then by service name); nil if the TV does not publish the service.
func channelID(_ d: IOBluetoothDevice, _ svc: Service = .file) -> BluetoothRFCOMMChannelID? {
    var b = uuidBytes(svc.uuid)
    let uuid = IOBluetoothSDPUUID(bytes: &b, length: b.count)
    if let rec = d.getServiceRecord(for: uuid) {
        var ch: BluetoothRFCOMMChannelID = 0
        if rec.getRFCOMMChannelID(&ch) == kIOReturnSuccess { return ch }
    }
    // Some stacks publish the record without the 128-bit UUID in a findable form: fall back on the name.
    for case let rec as IOBluetoothSDPServiceRecord in d.services ?? [] where (rec.getServiceName() ?? "") == svc.name {
        var ch: BluetoothRFCOMMChannelID = 0
        if rec.getRFCOMMChannelID(&ch) == kIOReturnSuccess { return ch }
    }
    return nil
}

final class Link: NSObject, IOBluetoothRFCOMMChannelDelegate {
    var channel: IOBluetoothRFCOMMChannel?
    var inbox = Data()
    var closed = false
    /// proxy mode: bytes go straight to the TCP client instead of the inbox
    var onData: ((Data) -> Void)?
    var onClosed: (() -> Void)?

    func rfcommChannelData(_ c: IOBluetoothRFCOMMChannel!, data p: UnsafeMutableRawPointer!, length n: Int) {
        if let f = onData { f(Data(bytes: p, count: n)) } else { inbox.append(p.assumingMemoryBound(to: UInt8.self), count: n) }
    }
    func rfcommChannelClosed(_ c: IOBluetoothRFCOMMChannel!) { closed = true; onClosed?() }

    var openStatus: IOReturn? = nil
    func rfcommChannelOpenComplete(_ c: IOBluetoothRFCOMMChannel!, status error: IOReturn) { openStatus = error }

    /// Baseband link first (with authentication: the Android side listens on a *secure* RFCOMM socket), then the
    /// channel, asynchronously with a few retries: the sync variant fails on some stacks right after an SDP query.
    func open(_ d: IOBluetoothDevice, _ id: BluetoothRFCOMMChannelID) {
        if !tryOpen(d, id, attempts: 4) { fail("impossible d'ouvrir le canal RFCOMM \(id)") }
    }

    /// Same, but returns false instead of exiting (the proxy keeps running and reports the failure to the client).
    func tryOpen(_ d: IOBluetoothDevice, _ id: BluetoothRFCOMMChannelID, attempts: Int) -> Bool {
        if !d.isConnected() {
            let r = d.openConnection(nil, withPageTimeout: 0x4000, authenticationRequired: true)
            if r != kIOReturnSuccess { FileHandle.standardError.write("liaison ACL : \(r)\n".data(using: .utf8)!) }
        }
        for attempt in 1...attempts {
            openStatus = nil
            var ch: IOBluetoothRFCOMMChannel?
            let r = d.openRFCOMMChannelAsync(&ch, withChannelID: id, delegate: self)
            if r == kIOReturnSuccess {
                let end = Date().addingTimeInterval(15)
                while openStatus == nil && Date() < end { RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(0.05)) }
                if openStatus == kIOReturnSuccess, let c = ch { channel = c; return true }
            }
            FileHandle.standardError.write("ouverture RFCOMM canal \(id), essai \(attempt) : \(openStatus.map { String($0) } ?? String(r))\n".data(using: .utf8)!)
            RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(1.5))
        }
        return false
    }

    /// Non-fatal write: false if the link is closed or a write fails.
    func writeOK(_ data: Data) -> Bool {
        guard let ch = channel else { return false }
        let mtu = max(Int(ch.getMTU()), 64)
        var off = 0
        while off < data.count {
            if closed { return false }
            let n = min(mtu, data.count - off)
            var chunk = [UInt8](data[data.startIndex + off ..< data.startIndex + off + n])
            if ch.writeSync(&chunk, length: UInt16(n)) != kIOReturnSuccess { return false }
            off += n
        }
        return true
    }

    /// Non-fatal read of exactly [n] bytes (pumps the run loop); nil on timeout or when the TV closes the link first.
    func readOptional(_ n: Int, timeout: TimeInterval) -> Data? {
        let end = Date().addingTimeInterval(timeout)
        while inbox.count < n {
            if closed || Date() > end { return nil }
            RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(0.05))
        }
        let out = inbox.prefix(n); inbox.removeFirst(n); return Data(out)
    }

    func write(_ data: Data) {
        guard let ch = channel else { fail("canal fermé") }
        let mtu = max(Int(ch.getMTU()), 64)
        var off = 0
        while off < data.count {
            if closed { fail("lien fermé par la TV") }
            let n = min(mtu, data.count - off)
            var chunk = [UInt8](data[data.startIndex + off ..< data.startIndex + off + n])
            let r = ch.writeSync(&chunk, length: UInt16(n))
            if r != kIOReturnSuccess { fail("écriture RFCOMM échouée (\(r)) à l'octet \(off)") }
            off += n
        }
    }

    /// Waits (pumping the run loop, which delivers the delegate callbacks) until [n] bytes arrived.
    func read(_ n: Int, timeout: TimeInterval = 30) -> Data {
        let end = Date().addingTimeInterval(timeout)
        while inbox.count < n {
            if closed { fail("lien fermé par la TV avant la réponse") }
            if Date() > end { fail("pas de réponse de la TV (\(timeout) s)") }
            RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(0.05))
        }
        let out = inbox.prefix(n); inbox.removeFirst(n); return Data(out)
    }

    func close() { channel?.close() }
}

func be<T: FixedWidthInteger>(_ v: T) -> Data { withUnsafeBytes(of: v.bigEndian) { Data($0) } }
func readI64(_ d: Data) -> Int64 { d.reduce(0) { ($0 << 8) | Int64($1) } }

let errors = [1: "protocole inconnu", 2: "PIN incorrect", 3: "nom refusé", 4: "espace insuffisant sur la TV",
              5: "trop d'essais, TV verrouillée", 6: "erreur d'écriture sur la TV", 7: "taille invalide"]

// ---- proxy: a local TCP port bridged to one of the TV's RFCOMM services ----

/// Probes channels 1...30 for [svc] when SDP and the cache do not give a usable channel. The API service answers a status byte (0...4) at
/// once, the SSH service its banner ("S"); the file service stays silent and is skipped.
func probeChannel(_ d: IOBluetoothDevice, _ svc: Service) -> BluetoothRFCOMMChannelID? {
    info("recherche du service \(svc.name) (canaux RFCOMM 1 à 30)…")
    for n in 1...30 {
        let id = BluetoothRFCOMMChannelID(n)
        let l = Link()
        if !l.tryOpen(d, id, attempts: 1) { continue }
        defer { l.close() }
        guard let first = l.readOptional(1, timeout: 2.5) else { continue }
        if (svc.key == "api" && first[0] <= 4) || (svc.key == "ssh" && first[0] == 0x53) {
            info("service trouvé sur le canal \(n)")
            saveChannel(d, svc, id)
            return id
        }
    }
    return nil
}

/// Opens an RFCOMM link to [svc]: forced channel, else SDP, else the cached channel, else (after a fresh SDP query) a probe.
func openLink(_ d: IOBluetoothDevice, _ svc: Service, forced: BluetoothRFCOMMChannelID?) -> Link? {
    func attempt(_ ids: [BluetoothRFCOMMChannelID]) -> Link? {
        for id in ids {
            let l = Link()
            if l.tryOpen(d, id, attempts: 2) { saveChannel(d, svc, id); return l }
            info("canal RFCOMM \(id) : échec")
        }
        return nil
    }
    if let f = forced { return attempt([f]) }
    var ids: [BluetoothRFCOMMChannelID] = []
    if let c = channelID(d, svc) { ids.append(c) }
    if let c = cachedChannel(d, svc), !ids.contains(c) { ids.append(c) }
    if let l = attempt(ids) { return l }
    info("canal introuvable ou fermé ; nouvelle requête SDP…")
    refreshSDP(d)
    if let c = channelID(d, svc), !ids.contains(c), let l = attempt([c]) { return l }
    if let c = probeChannel(d, svc), let l = attempt([c]) { return l }
    return nil
}

func sendAll(_ fd: Int32, _ data: Data) {
    var off = 0
    data.withUnsafeBytes { (raw: UnsafeRawBufferPointer) in
        while off < data.count {
            let n = send(fd, raw.baseAddress! + off, data.count - off, 0)
            if n <= 0 { return }
            off += n
        }
    }
}

func serveClient(_ fd: Int32, _ d: IOBluetoothDevice, _ svc: Service, _ forced: BluetoothRFCOMMChannelID?) {
    func refuse(_ msg: String) {
        info("cbt-rfcomm : \(msg)")
        if svc.key == "api" {       // the HTTP client (curl...) shows the reason instead of "empty reply"
            sendAll(fd, Data("HTTP/1.1 502 Bad Gateway\r\nContent-Type: text/plain; charset=utf-8\r\nConnection: close\r\n\r\n\(msg)\n".utf8))
        }
        close(fd)
    }
    var linkOpt: Link? = nil
    DispatchQueue.main.sync { linkOpt = openLink(d, svc, forced: forced) }
    guard let link = linkOpt else {
        refuse("liaison Bluetooth impossible vers \(svc.name) : TV allumée et à portée ? Mac appairé ? Sur la TV, SSH activé (service SSH) / CastBridge-TV à jour ?")
        return
    }
    if svc.key == "api" {
        var st: Data? = nil
        DispatchQueue.main.sync { st = link.readOptional(1, timeout: 10) }
        guard let status = st?[0] else {
            DispatchQueue.main.sync { link.close() }
            refuse("la TV n'a pas répondu sur le service \(svc.name) (CastBridge-TV trop ancien ?)"); return
        }
        if status != 0 {
            DispatchQueue.main.sync { link.close() }
            refuse("la TV refuse : " + (tunnelStatus[Int(status)] ?? "code \(status)")); return
        }
    }
    let q = DispatchQueue(label: "cbt-rfcomm.client")
    DispatchQueue.main.sync {
        link.onData = { data in q.async { sendAll(fd, data) } }
        link.onClosed = { _ = shutdown(fd, SHUT_RDWR) }
        if !link.inbox.isEmpty { let rest = link.inbox; link.inbox = Data(); q.async { sendAll(fd, rest) } }   // SSH banner that arrived early
    }
    var buf = [UInt8](repeating: 0, count: 16 * 1024)
    while true {
        let n = recv(fd, &buf, buf.count, 0)
        if n <= 0 { break }
        var ok = true
        DispatchQueue.main.sync { ok = link.writeOK(Data(buf[0..<n])) }       // blocks until sent: the TCP client is slowed down, nothing piles up
        if !ok { break }
    }
    DispatchQueue.main.sync { link.onData = nil; link.close() }
    q.sync {}
    close(fd)
}

func runProxy(_ d: IOBluetoothDevice, _ port: UInt16, _ svc: Service, _ forced: BluetoothRFCOMMChannelID?) -> Never {
    let lfd = socket(AF_INET, SOCK_STREAM, 0)
    var one: Int32 = 1
    setsockopt(lfd, SOL_SOCKET, SO_REUSEADDR, &one, socklen_t(MemoryLayout<Int32>.size))
    var addr = sockaddr_in()
    addr.sin_family = sa_family_t(AF_INET)
    addr.sin_port = port.bigEndian
    addr.sin_addr.s_addr = inet_addr("127.0.0.1")          // loopback only: the tunnel is never offered to the network
    let bound = withUnsafePointer(to: &addr) { p in
        p.withMemoryRebound(to: sockaddr.self, capacity: 1) { bind(lfd, $0, socklen_t(MemoryLayout<sockaddr_in>.size)) }
    }
    if bound != 0 || listen(lfd, 8) != 0 { fail("port \(port) indisponible sur 127.0.0.1 (déjà utilisé ?)") }
    print("Pont Bluetooth \(svc.name) : 127.0.0.1:\(port) -> \(d.name ?? "TV") (Ctrl-C pour arrêter)")
    if svc.key == "api" {
        print("  curl -H 'X-CB-Pin: <code de la TV>' http://127.0.0.1:\(port)/api/hello")
    } else {
        print("  ssh -p \(port) tv@127.0.0.1")
    }
    Thread.detachNewThread {
        while true {
            let c = accept(lfd, nil, nil)
            if c < 0 { continue }
            var nodelay: Int32 = 1
            setsockopt(c, IPPROTO_TCP, TCP_NODELAY, &nodelay, socklen_t(MemoryLayout<Int32>.size))
            Thread.detachNewThread { serveClient(c, d, svc, forced) }
        }
    }
    RunLoop.main.run()
    exit(0)
}

let args = CommandLine.arguments
guard args.count >= 3 else { fail("usage : cbt-rfcomm list <adresse> | send <adresse> <pin> <fichier> [nom] | proxy <adresse> <port> api|ssh [--channel N]") }
let dev = device(args[2])

switch args[1] {
case "list":
    print("Appareil : \(dev.name ?? "?") (\(dev.addressString ?? "?"))")
    for case let rec as IOBluetoothSDPServiceRecord in dev.services ?? [] {
        var ch: BluetoothRFCOMMChannelID = 0
        let rf = rec.getRFCOMMChannelID(&ch) == kIOReturnSuccess ? "RFCOMM \(ch)" : "-"
        print("  \(rec.getServiceName() ?? "(sans nom)")  \(rf)")
    }
    for svc in [Service.file, Service.api, Service.ssh] {
        print("\(svc.name) : " + (channelID(dev, svc).map { "canal RFCOMM \($0)" } ?? "introuvable" + (svc.key == "ssh" ? " (SSH activé sur la TV ?)" : "")))
    }

case "proxy":
    guard args.count >= 5, let port = UInt16(args[3]), port > 0 else { fail("usage : cbt-rfcomm proxy <adresse> <port> api|ssh [--channel N]") }
    let svc: Service
    switch args[4] { case "api": svc = .api; case "ssh": svc = .ssh; default: fail("service inconnu : \(args[4]) (api ou ssh)") }
    var forced: BluetoothRFCOMMChannelID? = nil
    if args.count >= 7, args[5] == "--channel", let n = UInt8(args[6]), n >= 1, n <= 30 { forced = BluetoothRFCOMMChannelID(n) }
    runProxy(dev, port, svc, forced)

case "send":
    guard args.count >= 5 else { fail("usage : cbt-rfcomm send <adresse> <pin> <fichier> [nom]") }
    let pin = args[3], path = args[4]
    let name = args.count >= 6 ? args[5] : (path as NSString).lastPathComponent
    guard pin.count == 6, pin.allSatisfy(\.isNumber) else { fail("le PIN fait 6 chiffres") }
    guard let file = FileHandle(forReadingAtPath: path),
          let size = (try? FileManager.default.attributesOfItem(atPath: path)[.size]) as? Int64 else { fail("fichier illisible") }
    guard let id = channelID(dev) else { fail("service CastBridge introuvable sur la TV (app TV lancée ?)") }
    print("TV \(dev.name ?? "?") : service CastBridge sur le canal RFCOMM \(id)")

    let link = Link()
    link.open(dev, id)
    var header = Data("CBT1".utf8) + Data(pin.utf8)
    let nameBytes = Data(name.utf8)
    header += be(UInt16(nameBytes.count)) + nameBytes + be(size)
    link.write(header)

    let st = Int(link.read(1)[0])
    if st != 0 { fail("refusé par la TV : \(errors[st] ?? "code \(st)")") }
    let offset = readI64(link.read(8))
    print("TV prête, reprise à l'octet \(offset) / \(size)")

    let t0 = Date()
    try file.seek(toOffset: UInt64(offset))
    var sent = offset
    var lastShown = Date.distantPast
    while sent < size {
        let chunk = file.readData(ofLength: Int(min(Int64(32 * 1024), size - sent)))
        if chunk.isEmpty { fail("fichier tronqué") }
        link.write(chunk)
        sent += Int64(chunk.count)
        if Date().timeIntervalSince(lastShown) > 2 || sent == size {
            let el = Date().timeIntervalSince(t0)
            print(String(format: "  %5.1f %%  %lld / %lld octets  %.0f ko/s", Double(sent) * 100 / Double(size), sent, size,
                         el > 0 ? Double(sent - offset) / el / 1024 : 0))
            lastShown = Date()
        }
    }
    let fin = Int(link.read(1, timeout: 60)[0])
    let el = Date().timeIntervalSince(t0)
    link.close()
    if fin != 0 { fail("la TV signale une erreur à la fin : \(errors[fin] ?? "code \(fin)")") }
    print(String(format: "OK : %lld octets en %.1f s (%.0f ko/s)", size - offset, el, Double(size - offset) / max(el, 0.001) / 1024))

default:
    fail("commande inconnue : \(args[1])")
}
