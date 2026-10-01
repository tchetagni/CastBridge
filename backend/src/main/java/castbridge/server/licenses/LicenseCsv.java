package castbridge.server.licenses;

import java.util.List;

/** CSV export with spreadsheet-injection protection: a cell starting with = + - @ or a tab is prefixed with an apostrophe. */
public final class LicenseCsv {
    private LicenseCsv() {}

    public static String of(List<LicenseService.LicenseRow> rows) {
        StringBuilder sb = new StringBuilder("licence;client;type;etat;postes_autorises;postes_utilises;debut;fin;grace_jours\n");
        for (LicenseService.LicenseRow l : rows) {
            sb.append(cell(l.licenseId())).append(';').append(cell(l.clientName())).append(';').append(l.kind()).append(';').append(l.effectiveState()).append(';')
                    .append(l.seatsAllowed()).append(';').append(l.seatsUsed()).append(';').append(l.startAt()).append(';').append(l.endAt() == null ? "" : l.endAt())
                    .append(';').append(l.graceDays()).append('\n');
        }
        return sb.toString();
    }

    static String cell(String s) {
        if (s == null) return "";
        String t = s.replace("\r", " ").replace("\n", " ");
        if (!t.isEmpty() && "=+-@\t".indexOf(t.charAt(0)) >= 0) t = "'" + t;
        return t.contains(";") || t.contains("\"") ? "\"" + t.replace("\"", "\"\"") + "\"" : t;
    }
}
