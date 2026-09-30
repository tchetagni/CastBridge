package castbridge.server.updates;

import castbridge.server.config.CastbridgeProperties;
import castbridge.server.devices.Device;
import castbridge.server.devices.DeviceService;
import castbridge.server.web.ApiException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Public: "is there an update for me?" (signed manifest) and the public key the apps embed. */
@RestController
@RequestMapping("/api/v1/updates")
public class UpdateController {
    private final ReleaseService service;
    private final ManifestSigner signer;
    private final CastbridgeProperties props;
    private final DeviceService devices;

    public UpdateController(ReleaseService service, ManifestSigner signer, CastbridgeProperties props, DeviceService devices) {
        this.service = service;
        this.signer = signer;
        this.props = props;
        this.devices = devices;
    }

    @GetMapping("/public-key")
    public ResponseEntity<Map<String, String>> publicKey() {
        if (!signer.enabled()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Clé de signature non configurée");
        return ResponseEntity.ok().cacheControl(CacheControl.noCache())
                .body(Map.of("algorithm", "Ed25519", "format", "raw-32-bytes-base64", "publicKey", signer.publicKeyBase64(),
                        "keyId", signer.keyId(), "manifestFormat", UpdateManifest.FORMAT));
    }

    /**
     * 200 + signed manifest if a newer version is available for this device, 204 if it is up to date.
     * {@code abis} = Build.SUPPORTED_ABIS joined by commas (preferred first); {@code abi} = a single ABI (older clients).
     * With the device token (Authorization: Bearer) the admin's settings apply: blocked device = 403, forced channel.
     */
    @GetMapping("/{app}/latest")
    public ResponseEntity<UpdateManifest> latest(@PathVariable String app,
                                                 @RequestParam(required = false) String abi,
                                                 @RequestParam(required = false) String abis,
                                                 @RequestParam(required = false) String channel,
                                                 @RequestParam(defaultValue = "0") int versionCode,
                                                 @RequestParam(required = false) String deviceId,
                                                 @RequestParam(required = false) Integer sdk,
                                                 @RequestHeader(name = "Authorization", required = false) String authorization) {
        Optional<Device> device = devices.authenticate(authorization);
        if (device.isEmpty()) device = devices.byPublicId(deviceId);
        if (device.isPresent() && device.get().blocked)
            throw new ApiException(HttpStatus.FORBIDDEN, "Cet appareil est bloqué par l'administrateur");
        if (device.isPresent() && device.get().channelOverride != null) channel = device.get().channelOverride;
        String rolloutId = deviceId != null && !deviceId.isBlank() ? deviceId : device.map(d -> d.publicId).orElse(null);

        List<String> abiList = new ArrayList<>();
        if (abis != null && !abis.isBlank()) abiList.addAll(Arrays.stream(abis.split(",")).map(String::trim).filter(s -> !s.isEmpty()).limit(8).toList());
        if (abi != null && !abi.isBlank() && !abiList.contains(abi.trim())) abiList.add(abi.trim());

        String base = props.publicBaseUrl().isBlank()
                ? ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()
                : props.publicBaseUrl().replaceAll("/+$", "");
        return service.latest(app, abiList, channel, versionCode, rolloutId, sdk, base)
                .map(m -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(m))
                .orElseGet(() -> ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build());
    }
}
