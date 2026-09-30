package castbridge.server.devices;

import castbridge.server.config.CastbridgeProperties;
import com.maxmind.db.CHMCache;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.model.CountryResponse;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Approximate location of a device from its public IP: a local MaxMind GeoLite2 database when one is mounted
 * (CASTBRIDGE_GEOIP_DB, country + city), else the country header set by the reverse proxy / CDN
 * (CASTBRIDGE_GEO_COUNTRY_HEADER), else nothing. No external service is called.
 */
@Component
public class GeoLocator {
    private static final Logger log = LoggerFactory.getLogger(GeoLocator.class);

    public record Place(String country, String city) {}

    private final String countryHeader;
    private final DatabaseReader reader;

    public GeoLocator(CastbridgeProperties props) {
        this.countryHeader = props.geo().countryHeader() == null || props.geo().countryHeader().isBlank() ? null : props.geo().countryHeader();
        DatabaseReader r = null;
        var file = props.geo().databaseFile();
        if (file != null && !file.toString().isBlank()) {
            if (Files.isReadable(file)) {
                try {
                    r = new DatabaseReader.Builder(file.toFile()).withCache(new CHMCache()).build();
                    log.info("GeoIP database loaded: {}", r.getMetadata().getDatabaseType());
                } catch (IOException e) {
                    log.warn("GeoIP database {} unreadable: {}", file, e.getMessage());
                }
            } else {
                log.warn("GeoIP database {} not found: country from header only", file);
            }
        }
        this.reader = r;
    }

    public Place locate(HttpServletRequest req) {
        String ip = req.getRemoteAddr();
        if (reader != null && ip != null) {
            try {
                InetAddress addr = InetAddress.getByName(ip); // an IP literal: no DNS lookup
                if (!addr.isSiteLocalAddress() && !addr.isLoopbackAddress() && !addr.isLinkLocalAddress()) {
                    try {
                        Optional<CityResponse> c = reader.tryCity(addr);
                        if (c.isPresent()) return new Place(c.get().getCountry().getIsoCode(), c.get().getCity().getName());
                    } catch (UnsupportedOperationException notACityDb) {
                        Optional<CountryResponse> c = reader.tryCountry(addr);
                        if (c.isPresent()) return new Place(c.get().getCountry().getIsoCode(), null);
                    }
                }
            } catch (Exception e) {
                log.debug("GeoIP lookup failed: {}", e.getClass().getSimpleName());
            }
        }
        if (countryHeader != null) {
            String c = req.getHeader(countryHeader);
            if (c != null && c.trim().matches("[A-Za-z]{2}") && !c.trim().equalsIgnoreCase("XX"))
                return new Place(c.trim().toUpperCase(Locale.ROOT), null);
        }
        return new Place(null, null);
    }

    @PreDestroy
    void close() throws IOException {
        if (reader != null) reader.close();
    }
}
