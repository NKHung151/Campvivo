package vn.campvivo.review.moderation;

import org.springframework.stereotype.Component;
import vn.campvivo.review.moderation.TextNormalizer.Token;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kiểm duyệt bằng từ điển từ cấm tiếng Việt (D-33). Từ điển nạp một lần lúc khởi động; file sai định dạng → bean
 * không tạo được, app không khởi động (fail fast). Định dạng file: xem phần đầu {@code moderation/vi-banned-words.txt}.
 * <ul>
 *   <li>{@code [accented]}: so trên chữ còn dấu (từ bỏ dấu sẽ trùng từ thường: {@code lồn}/{@code lon nước});
 *       token cố tình biến dạng ({@code l0n}, {@code l.o.n}, {@code l*n}) được so theo bản bỏ dấu.</li>
 *   <li>{@code [plain]}: so sau khi bỏ dấu.</li>
 *   <li>{@code [abbr]}: viết tắt, khớp nguyên một token (bỏ dấu): {@code dm} không khớp trong {@code admin}.</li>
 *   <li>{@code [allow]}: cụm hợp lệ hay bị bắt nhầm, so trên chữ còn dấu; token thuộc cụm này không bị xét nữa.</li>
 * </ul>
 */
@Component
public class DictionaryModerator implements ContentModerator {

    static final String DEFAULT_RESOURCE = "moderation/vi-banned-words.txt";
    private static final int MAX_FLAGGED_LENGTH = 500;
    private static final Pattern SECTION = Pattern.compile("^\\[([a-z]+)]$");
    private static final Pattern ABBR = Pattern.compile("^[a-z0-9]+$");

    enum Section { ACCENTED, PLAIN, ABBR, ALLOW }

    /** Một mục từ điển; {@code accented}/{@code plain}: các từ đã chuẩn hoá. */
    record Entry(String original, Section section, List<String> accented, List<String> plain) {}

    private final List<Entry> blocked;
    private final List<Entry> allowed;

    public DictionaryModerator() {
        this(load(DEFAULT_RESOURCE));
    }

    private DictionaryModerator(List<Entry> entries) {
        this.blocked = entries.stream().filter(e -> e.section() != Section.ALLOW).toList();
        this.allowed = entries.stream().filter(e -> e.section() == Section.ALLOW).toList();
        if (blocked.isEmpty()) {
            throw new IllegalStateException("Từ điển kiểm duyệt không có mục nào");
        }
    }

    /** Dùng cho test: nạp từ một luồng bất kỳ. */
    static DictionaryModerator fromStream(InputStream in, String source) {
        return new DictionaryModerator(parse(in, source));
    }

    int size() {
        return blocked.size() + allowed.size();
    }

    @Override
    public ModerationResult check(String content) {
        List<Token> tokens = TextNormalizer.tokenize(content);
        if (tokens.isEmpty()) {
            return ModerationResult.SAFE;
        }
        boolean[] covered = new boolean[tokens.size()];
        for (Entry allow : allowed) {
            for (int i = 0; i + allow.accented().size() <= tokens.size(); i++) {
                if (matchesAllow(allow, tokens, i)) {
                    for (int k = 0; k < allow.accented().size(); k++) {
                        covered[i + k] = true;
                    }
                }
            }
        }
        Set<String> hits = new LinkedHashSet<>();
        int length = 0;
        for (Entry e : blocked) {
            if (hits.contains(e.original()) || !occurs(e, tokens, covered)) {
                continue;
            }
            int added = e.original().length() + (hits.isEmpty() ? 0 : 1);
            if (length + added > MAX_FLAGGED_LENGTH) {
                break;
            }
            hits.add(e.original());
            length += added;
        }
        return hits.isEmpty() ? ModerationResult.SAFE : new ModerationResult(true, List.copyOf(hits));
    }

    private static boolean matchesAllow(Entry allow, List<Token> tokens, int start) {
        for (int k = 0; k < allow.accented().size(); k++) {
            if (!tokens.get(start + k).accented().equals(allow.accented().get(k))) {
                return false;
            }
        }
        return true;
    }

    private static boolean occurs(Entry e, List<Token> tokens, boolean[] covered) {
        int n = e.plain().size();
        for (int i = 0; i + n <= tokens.size(); i++) {
            boolean all = true;
            for (int k = 0; k < n && all; k++) {
                all = !covered[i + k] && wordMatches(e, k, tokens.get(i + k));
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    private static boolean wordMatches(Entry e, int k, Token t) {
        String plain = e.plain().get(k);
        return switch (e.section()) {
            case ACCENTED -> t.accented().equals(e.accented().get(k))
                    || (t.obfuscated() && t.plain().equals(plain)) || t.maskMatches(plain);
            case PLAIN -> t.plain().equals(plain) || t.maskMatches(plain);
            case ABBR -> t.plain().equals(plain);
            case ALLOW -> false;
        };
    }

    private static List<Entry> load(String resource) {
        InputStream in = DictionaryModerator.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("Không tìm thấy từ điển kiểm duyệt: classpath:" + resource);
        }
        return parse(in, resource);
    }

    static List<Entry> parse(InputStream in, String source) {
        List<Entry> entries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            Section section = null;
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                String text = line.replace("﻿", "").trim();
                if (text.isEmpty() || text.startsWith("#")) {
                    continue;
                }
                Matcher m = SECTION.matcher(text);
                if (m.matches()) {
                    section = sectionOf(m.group(1), source, lineNo);
                    continue;
                }
                if (section == null) {
                    throw invalid(source, lineNo, "mục nằm ngoài section");
                }
                entries.add(entry(text, section, source, lineNo));
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Không đọc được từ điển kiểm duyệt " + source, ex);
        }
        return entries;
    }

    private static Section sectionOf(String name, String source, int lineNo) {
        try {
            return Section.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw invalid(source, lineNo, "section lạ [" + name + "]");
        }
    }

    private static Entry entry(String text, Section section, String source, int lineNo) {
        List<String> accented = List.of(TextNormalizer.clean(text).split("\\s+"));
        List<String> plain = accented.stream().map(TextNormalizer::removeDiacritics).toList();
        if (section == Section.ABBR && (accented.size() != 1 || !ABBR.matcher(accented.get(0)).matches())) {
            throw invalid(source, lineNo, "mục [abbr] phải là một từ chỉ gồm a-z, 0-9: " + text);
        }
        return new Entry(text, section, accented, plain);
    }

    private static IllegalStateException invalid(String source, int lineNo, String reason) {
        return new IllegalStateException("Từ điển kiểm duyệt " + source + " dòng " + lineNo + ": " + reason);
    }
}
