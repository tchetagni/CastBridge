// cbt-rfcomm: raw Bluetooth RFCOMM client for CastBridge TV, for macOS (IOBluetooth).
//
//   cbt-rfcomm list  <tv-address>                      SDP services of the TV (name, RFCOMM channel)
//   cbt-rfcomm send  <tv-address> <pin> <file> [name]  push a file with the CBT1 protocol (resumable)
//
// The Mac must be paired with the TV first (System Settings > Bluetooth). Protocol: see BtProtocol.kt.
import Foundation
import IOBluetooth

let serviceUUID = "7c5e3b9a-4d2f-4c61-9b0e-cb0000000001"

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

func device(_ address: String) -> IOBluetoothDevice {
    guard let d = IOBluetoothDevice(addressString: address.replacingOccurrences(of: ":", with: "-")) else { fail("adresse invalide") }
    if !d.isPaired() { fail("le Mac n'est pas appairé avec \(address)") }
    // Fresh query (not the cache from pairing time): the CastBridge service is registered only while the TV app runs.
    let waiter = SDPWaiter()
    let r = d.performSDPQuery(waiter)
    if r != kIOReturnSuccess { fail("requête SDP échouée (\(r)) : la TV est-elle allumée et à portée ?") }
    let end = Date().addingTimeInterval(20)
    while !waiter.done && Date() < end { RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(0.1)) }
    if !waiter.done { FileHandle.standardError.write("SDP : pas de réponse en 20 s, cache utilisé\n".data(using: .utf8)!) }
    else if waiter.status != kIOReturnSuccess { FileHandle.standardError.write("SDP : statut \(waiter.status)\n".data(using: .utf8)!) }
    return d
}

func channelID(_ d: IOBluetoothDevice) -> BluetoothRFCOMMChannelID? {
    var b = uuidBytes(serviceUUID)
    let uuid = IOBluetoothSDPUUID(bytes: &b, length: b.count)
    if let rec = d.getServiceRecord(for: uuid) {
        var ch: BluetoothRFCOMMChannelID = 0
        if rec.getRFCOMMChannelID(&ch) == kIOReturnSuccess { return ch }
    }
    // Some stacks publish the record without the 128-bit UUID in a findable form: fall back on the name.
    for case let rec as IOBluetoothSDPServiceRecord in d.services ?? [] where (rec.getServiceName() ?? "").contains("CastBridge") {
        var ch: BluetoothRFCOMMChannelID = 0
        if rec.getRFCOMMChannelID(&ch) == kIOReturnSuccess { return ch }
    }
    return nil
}

final class Link: NSObject, IOBluetoothRFCOMMChannelDelegate {
    var channel: IOBluetoothRFCOMMChannel?
    var inbox = Data()
    var closed = false

    func rfcommChannelData(_ c: IOBluetoothRFCOMMChannel!, data p: UnsafeMutableRawPointer!, length n: Int) {
        inbox.append(p.assumingMemoryBound(to: UInt8.self), count: n)
    }
    func rfcommChannelClosed(_ c: IOBluetoothRFCOMMChannel!) { closed = true }

    var openStatus: IOReturn? = nil
    func rfcommChannelOpenComplete(_ c: IOBluetoothRFCOMMChannel!, status error: IOReturn) { openStatus = error }

    /// Baseband link first (with authentication: the Android side listens on a *secure* RFCOMM socket), then the
    /// channel, asynchronously with a few retries: the sync variant fails on some stacks right after an SDP query.
    func open(_ d: IOBluetoothDevice, _ id: BluetoothRFCOMMChannelID) {
        if !d.isConnected() {
            let r = d.openConnection(nil, withPageTimeout: 0x4000, authenticationRequired: true)
            if r != kIOReturnSuccess { FileHandle.standardError.write("liaison ACL : \(r)\n".data(using: .utf8)!) }
        }
        for attempt in 1...4 {
            openStatus = nil
            var ch: IOBluetoothRFCOMMChannel?
            let r = d.openRFCOMMChannelAsync(&ch, withChannelID: id, delegate: self)
            if r == kIOReturnSuccess {
                let end = Date().addingTimeInterval(15)
                while openStatus == nil && Date() < end { RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(0.05)) }
                if openStatus == kIOReturnSuccess, let c = ch { channel = c; return }
            }
            FileHandle.standardError.write("ouverture RFCOMM canal \(id), essai \(attempt) : \(openStatus.map { String($0) } ?? String(r))\n".data(using: .utf8)!)
            RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(1.5))
        }
        fail("impossible d'ouvrir le canal RFCOMM \(id)")
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

let args = CommandLine.arguments
guard args.count >= 3 else { fail("usage : cbt-rfcomm list <adresse> | send <adresse> <pin> <fichier> [nom]") }
let dev = device(args[2])

switch args[1] {
case "list":
    print("Appareil : \(dev.name ?? "?") (\(dev.addressString ?? "?"))")
    for case let rec as IOBluetoothSDPServiceRecord in dev.services ?? [] {
        var ch: BluetoothRFCOMMChannelID = 0
        let rf = rec.getRFCOMMChannelID(&ch) == kIOReturnSuccess ? "RFCOMM \(ch)" : "-"
        print("  \(rec.getServiceName() ?? "(sans nom)")  \(rf)")
    }
    print("Service CastBridge : " + (channelID(dev).map { "canal RFCOMM \($0)" } ?? "introuvable"))

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
