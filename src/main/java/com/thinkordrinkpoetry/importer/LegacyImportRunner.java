package com.thinkordrinkpoetry.importer;

import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Explicit, one-shot legacy data importer. It is disabled unless the command line enables it. */
@Component
@ConditionalOnProperty(prefix = "app.legacy-import", name = "enabled", havingValue = "true")
class LegacyImportRunner implements ApplicationRunner {
    private static final Charset LEGACY_CHARSET = Charset.forName("windows-1252");
    /** Known ownership corrections for the historical export; do not alter the source CSV. */
    private static final Map<Long, String> CLAIMED_LEGACY_EMAILS = Map.of(77L, "acpinco01@gmail.com");
    private final JdbcTemplate jdbc;
    private final Path usersFile;
    private final Path poemsFile;
    private final boolean apply;
    private final String adminEmail;
    private final Map<Long, UUID> poetMappings = new HashMap<>();
    private final Set<String> importedEmails = new HashSet<>();

    LegacyImportRunner(JdbcTemplate jdbc, @Value("${app.legacy-import.users-file}") Path usersFile,
            @Value("${app.legacy-import.poems-file}") Path poemsFile,
            @Value("${app.legacy-import.apply:false}") boolean apply,
            @Value("${app.auth.admin-email:}") String adminEmail) {
        this.jdbc = jdbc; this.usersFile = usersFile; this.poemsFile = poemsFile; this.apply = apply;
        this.adminEmail = normalize(adminEmail);
    }

    @Override
    public void run(ApplicationArguments arguments) throws Exception {
        Counts counts = new Counts();
        importUsers(counts);
        importPoems(counts);
        System.out.printf("Legacy import %s: poets=%d poems=%d skippedPoems=%d fallbackEmails=%d warnings=%d%n",
                apply ? "applied" : "dry run", counts.poets, counts.poems, counts.skippedPoems,
                counts.fallbackEmails, counts.warnings);
    }

    @Transactional
    void importUsers(Counts counts) throws Exception {
        try (Reader reader = Files.newBufferedReader(usersFile, LEGACY_CHARSET)) {
            for (CSVRecord row : CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {
                long legacyId = Long.parseLong(row.get("poet_id").trim());
                String claimedEmail = CLAIMED_LEGACY_EMAILS.get(legacyId);
                UUID existing = mappedPoet(legacyId);
                if (existing != null) { poetMappings.put(legacyId, reconcileClaim(existing, claimedEmail)); continue; }
                String email = claimedEmail == null ? normalize(row.get("email")) : claimedEmail;
                boolean fallback = email.isBlank() || emailTaken(email) || !importedEmails.add(email);
                if (claimedEmail != null && emailTaken(email)) fallback = false;
                if (fallback) { email = "legacy-poet-" + legacyId + "@doesnotexist.com"; counts.fallbackEmails++; }
                importedEmails.add(email);
                String first = useful(row.get("first_name")) ? row.get("first_name").trim() : email;
                String last = useful(row.get("last_name")) ? row.get("last_name").trim() : email;
                first = limit(first, 100); last = limit(last, 100);
                LocalDate submitted = LocalDate.parse(row.get("date_submitted").trim().replace('/', '-'));
                if (apply) {
                    UUID poetId = claimedEmail == null ? createLegacyPoet(email, first, last, submitted)
                            : claimedPoet(email, first, last, submitted);
                    jdbc.update("insert into legacy_poet_import_map (legacy_poet_id, poet_id) values (?, ?)", legacyId, poetId);
                    poetMappings.put(legacyId, poetId);
                } else {
                    poetMappings.put(legacyId, UUID.randomUUID());
                }
                counts.poets++;
            }
        }
    }

    @Transactional
    void importPoems(Counts counts) throws Exception {
        try (Reader reader = Files.newBufferedReader(poemsFile, LEGACY_CHARSET)) {
            for (CSVRecord row : CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {
                long legacyId = Long.parseLong(row.get("poemId").trim());
                if (mapped("legacy_poem_import_map", "legacy_poem_id", legacyId)) continue;
                long legacyPoetId = Long.parseLong(row.get("poetId").trim());
                UUID poetId = poetMappings.get(legacyPoetId);
                if (poetId == null) { System.err.printf("WARNING: skipped legacy poem %d; unknown legacy poet %d%n", legacyId, legacyPoetId); counts.skippedPoems++; counts.warnings++; continue; }
                LocalDate submitted = LocalDate.parse(row.get("dateSubmitted").trim().replace('/', '-'));
                if (apply) {
                    UUID poemId = jdbc.queryForObject("""
                            insert into poem (poet_id, title, poem, legacy_submitted_on)
                            values (?, ?, ?, ?) returning id
                            """, UUID.class, poetId, row.get("title").trim(), row.get("body"), Date.valueOf(submitted));
                    jdbc.update("insert into legacy_poem_import_map (legacy_poem_id, poem_id) values (?, ?)", legacyId, poemId);
                }
                counts.poems++;
            }
        }
    }

    private boolean mapped(String table, String column, long id) { return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from " + table + " where " + column + " = ?)", Boolean.class, id)); }
    private UUID mappedPoet(long legacyId) { return jdbc.query("select poet_id from legacy_poet_import_map where legacy_poet_id = ?", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, legacyId); }
    private UUID createLegacyPoet(String email, String first, String last, LocalDate submitted) { return jdbc.queryForObject("""
            insert into poet (email, first_name, last_name, legacy_submitted_on, account_status, role)
            values (?, ?, ?, ?, 'LEGACY_UNCLAIMED', 'USER') returning id
            """, UUID.class, email, first, last, Date.valueOf(submitted)); }
    private UUID claimedPoet(String email, String first, String last, LocalDate submitted) {
        UUID existing = poetByEmail(email);
        if (existing != null) { activateClaim(existing); return existing; }
        return jdbc.queryForObject("""
                insert into poet (email, first_name, last_name, legacy_submitted_on, account_status, role)
                values (?, ?, ?, ?, 'ACTIVE', ?) returning id
                """, UUID.class, email, first, last, Date.valueOf(submitted), roleFor(email));
    }
    private UUID reconcileClaim(UUID mappedPoetId, String claimedEmail) {
        if (!apply || claimedEmail == null) return mappedPoetId;
        UUID account = poetByEmail(claimedEmail);
        if (account == null) {
            jdbc.update("update poet set email = ? where id = ?", claimedEmail, mappedPoetId);
            activateClaim(mappedPoetId);
            return mappedPoetId;
        }
        if (!account.equals(mappedPoetId)) {
            if (legacyIdForPoet(account) != null) throw new IllegalStateException("Claimed account is already linked to another legacy poet.");
            jdbc.update("update poem set poet_id = ? where poet_id = ?", account, mappedPoetId);
            jdbc.update("update legacy_poet_import_map set poet_id = ? where poet_id = ?", account, mappedPoetId);
            jdbc.update("delete from poet where id = ?", mappedPoetId);
        }
        activateClaim(account);
        return account;
    }
    private UUID poetByEmail(String email) { return jdbc.query("select id from poet where email = ?", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, email); }
    private Long legacyIdForPoet(UUID poetId) { return jdbc.query("select legacy_poet_id from legacy_poet_import_map where poet_id = ?", rs -> rs.next() ? rs.getLong(1) : null, poetId); }
    private void activateClaim(UUID poetId) { jdbc.update("update poet set account_status = 'ACTIVE', locked_at = null, locked_reason = null, role = ? where id = ?", roleFor("acpinco01@gmail.com"), poetId); }
    private String roleFor(String email) { return email.equals(adminEmail) ? "ADMIN" : "USER"; }
    private boolean emailTaken(String email) { return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from poet where email = ?)", Boolean.class, email)); }
    private static boolean useful(String value) { String v=value == null ? "" : value.trim(); return !v.isEmpty() && !v.equals("-") && !v.equals("--"); }
    private static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT); }
    private static String limit(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
    private static final class Counts { int poets; int poems; int skippedPoems; int fallbackEmails; int warnings; }
}
