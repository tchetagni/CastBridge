package castbridge.server.quiz;

import castbridge.server.web.ApiException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV import/export of questions (RFC 4180: quotes doubled, fields with separators or line breaks quoted). Import
 * detects the separator from the header (',' or ';' as Excel writes it in French). {@code answer} is the 0-based index
 * of the right choice, like in JSON.
 */
public final class QuizCsv {
    public static final List<String> COLUMNS = List.of("uuid", "lang", "track", "level", "field", "region", "category", "difficulty",
            "question", "choice1", "choice2", "choice3", "choice4", "answer", "explanation", "source", "reviewStatus");

    private QuizCsv() {}

    public static String write(List<QuestionDto> questions, char sep) {
        StringBuilder sb = new StringBuilder();
        line(sb, COLUMNS, sep);
        for (QuestionDto q : questions) {
            List<String> c = q.choices();
            line(sb, List.of(q.uuid(), q.lang(), q.track(), nz(q.level()), nz(q.field()), q.region(), q.category(),
                    String.valueOf(q.difficulty()), q.question(), c.get(0), c.get(1), c.get(2), c.get(3), String.valueOf(q.answer()),
                    q.explanation(), q.source(), q.reviewStatus()), sep);
        }
        return sb.toString();
    }

    /** @return one DTO per data line; throws 400 with the line number on a malformed line */
    public static List<QuestionDto> read(String text) {
        if (text.startsWith("﻿")) text = text.substring(1);
        int nl = text.indexOf('\n');
        String header = nl < 0 ? text : text.substring(0, nl);
        char sep = header.indexOf(';') >= 0 && header.indexOf(',') < 0 ? ';' : ',';
        List<List<String>> rows = parse(text, sep);
        if (rows.isEmpty()) throw ApiException.badRequest("CSV vide");
        Map<String, Integer> col = new HashMap<>();
        List<String> head = rows.get(0);
        for (int i = 0; i < head.size(); i++) col.put(head.get(i).trim(), i);
        List<String> optional = List.of("uuid", "reviewStatus", "lang", "track", "level", "field");
        List<String> missing = COLUMNS.stream().filter(c -> !optional.contains(c) && !col.containsKey(c)).toList();
        if (!missing.isEmpty()) throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "Colonnes CSV manquantes", missing);
        List<QuestionDto> out = new ArrayList<>();
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            if (row.size() == 1 && row.get(0).isBlank()) continue;
            java.util.function.Function<String, String> get = name -> {
                Integer i = col.get(name);
                return i == null || i >= row.size() ? null : emptyToNull(row.get(i));
            };
            out.add(new QuestionDto(null, get.apply("uuid"), get.apply("lang"), get.apply("track"), get.apply("level"), get.apply("field"),
                    get.apply("region"), get.apply("category"), integer(get.apply("difficulty"), r, "difficulty"), get.apply("question"),
                    List.of(nz(get.apply("choice1")), nz(get.apply("choice2")), nz(get.apply("choice3")), nz(get.apply("choice4"))),
                    integer(get.apply("answer"), r, "answer"), get.apply("explanation"), get.apply("source"), get.apply("reviewStatus"),
                    null, null, null, null));
        }
        return out;
    }

    static List<List<String>> parse(String text, char sep) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        int i = 0, n = text.length();
        while (i < n) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < n && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else quoted = false;
                } else cell.append(ch);
            } else if (ch == '"' && cell.isEmpty()) {
                quoted = true;
            } else if (ch == sep) {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
                if (ch == '\r' && i + 1 < n && text.charAt(i + 1) == '\n') i++;
            } else cell.append(ch);
            i++;
        }
        if (quoted) throw ApiException.badRequest("CSV : guillemet non fermé");
        if (!cell.isEmpty() || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }

    private static void line(StringBuilder sb, List<String> cells, char sep) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(sep);
            String c = cells.get(i) == null ? "" : cells.get(i);
            if (c.indexOf(sep) >= 0 || c.indexOf('"') >= 0 || c.indexOf('\n') >= 0 || c.indexOf('\r') >= 0) {
                sb.append('"').append(c.replace("\"", "\"\"")).append('"');
            } else sb.append(c);
        }
        sb.append("\r\n");
    }

    private static Integer integer(String s, int row, String name) {
        if (s == null) return null;
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("CSV ligne " + (row + 1) + " : « " + name + " » doit être un nombre");
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String emptyToNull(String s) { return s == null || s.isBlank() ? null : s; }
}
