package castbridge.server.devices;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * What an app reports on register / heartbeat (android/core castbridge.core.device.DeviceReport builds the same JSON).
 * Technical facts only: no file names, no content, no account. Every field but installId and app is optional.
 * Any kind of TV or box: the platform is detected by the app (android-tv, google-tv, fire-os, android-box, phone…),
 * never assumed by the server.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeviceReport(
        String installId,
        String androidIdHash,
        String app,
        Integer versionCode,
        String versionName,
        String channel,
        String abi,
        List<String> supportedAbis,
        Integer sdk,
        String platform,
        String manufacturer,
        String model,
        String deviceName,
        String osName,
        String osBuild,
        String fingerprint,
        String screen,
        Integer densityDpi,
        Integer ramTotalMb,
        Integer storageFreeMb,
        Integer storageTotalMb,
        Boolean usbPresent,
        Integer usbFreeMb,
        Boolean btGateway,
        Boolean sshEnabled,
        Boolean wifiDirect,
        Integer videoCount,
        String lastError,
        /** "essential" (identification, versions, errors: needed for the updates) or "usage" (also usage statistics) */
        String consent,
        /** version of the information text the user saw (e.g. "2026-10") */
        String consentVersion) {

    static DeviceReport empty() {
        return new DeviceReport(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
