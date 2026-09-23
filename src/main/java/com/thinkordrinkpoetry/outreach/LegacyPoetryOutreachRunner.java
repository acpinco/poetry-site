package com.thinkordrinkpoetry.outreach;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.legacy-outreach.enabled", havingValue = "true")
class LegacyPoetryOutreachRunner implements CommandLineRunner {
    private static final String LIVE_CONFIRMATION = "SEND_LEGACY_POETRY_TO_HISTORICAL_EMAILS";

    private final JdbcTemplate jdbc;
    private final LegacyOutreachProperties properties;
    private final LegacyPoetryOutreachMailer mailer;

    LegacyPoetryOutreachRunner(JdbcTemplate jdbc, LegacyOutreachProperties properties, LegacyPoetryOutreachMailer mailer) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.mailer = mailer;
    }

    @Override
    public void run(String... args) {
        Mode mode = Mode.parse(properties.mode());
        List<Candidate> candidates = candidates(limit());
        if (mode == Mode.DRY_RUN) {
            printDryRun(candidates);
            return;
        }

        if (mode == Mode.LIVE && !LIVE_CONFIRMATION.equals(properties.confirmation())) {
            throw new IllegalStateException("Live outreach requires confirmation: " + LIVE_CONFIRMATION);
        }
        List<String> testRecipients = mode == Mode.TEST ? testRecipients() : List.of();
        System.out.printf("Legacy outreach %s: %d candidate(s), %s, campaign %s%n",
                mode, candidates.size(), mode == Mode.TEST ? testRecipients.size() + " test delivery recipient(s)" : "historical delivery recipients",
                properties.campaignId());
        for (Candidate candidate : candidates) {
            List<String> recipients = mode == Mode.TEST ? testRecipients : List.of(candidate.historicalEmail());
            for (String recipient : recipients) {
                if (!properties.resend() && alreadySent(candidate.poetId(), recipient)) {
                    System.out.printf("SKIP already sent: %s -> %s%n", candidate.historicalEmail(), recipient);
                    continue;
                }
                try {
                    mailer.send(recipient, properties.replyTo(), candidate.displayName(), candidate.publicUrl());
                    save(candidate, recipient, mode, "SENT", null);
                    System.out.printf("SENT %s -> %s%n", candidate.displayName(), recipient);
                } catch (RuntimeException exception) {
                    save(candidate, recipient, mode, "FAILED", conciseError(exception));
                    System.err.printf("FAILED %s -> %s: %s%n", candidate.displayName(), recipient, exception.getMessage());
                }
            }
        }
    }

    private List<Candidate> candidates(int limit) {
        return jdbc.query("""
                select p.id, p.email, coalesce(nullif(p.pen_name, ''), p.full_name), count(po.id)
                from poet p
                join poem po on po.poet_id = p.id
                where lower(p.email) not like '%@doesnotexist.com'
                  and p.email like '%@%'
                group by p.id, p.email, p.pen_name, p.full_name
                order by lower(coalesce(nullif(p.pen_name, ''), p.full_name)), p.id
                limit ?
                """, (resultSet, rowNumber) -> {
            UUID poetId = resultSet.getObject(1, UUID.class);
            String name = resultSet.getString(3);
            return new Candidate(poetId, resultSet.getString(2), name, resultSet.getInt(4), publicUrl(poetId, name));
        }, limit);
    }

    private List<String> testRecipients() {
        List<String> testRecipients = Arrays.stream(properties.testRecipients().split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
        if (testRecipients.isEmpty()) {
            throw new IllegalStateException("Test outreach requires at least one test recipient.");
        }
        return testRecipients;
    }

    private boolean alreadySent(UUID poetId, String recipient) {
        Integer count = jdbc.queryForObject("""
                select count(*) from legacy_poetry_outreach_delivery
                where campaign_id = ? and poet_id = ? and delivery_email = ? and status = 'SENT'
                """, Integer.class, properties.campaignId(), poetId, recipient);
        return count != null && count > 0;
    }

    private void save(Candidate candidate, String recipient, Mode mode, String status, String error) {
        jdbc.update("""
                insert into legacy_poetry_outreach_delivery
                    (campaign_id, poet_id, historical_email, delivery_email, delivery_mode, status, public_url, sent_at, error_message)
                values (?, ?, ?, ?, ?, ?, ?, case when ? = 'SENT' then current_timestamp else null end, ?)
                on conflict (campaign_id, poet_id, delivery_email) do update set
                    historical_email = excluded.historical_email,
                    delivery_mode = excluded.delivery_mode,
                    status = excluded.status,
                    public_url = excluded.public_url,
                    sent_at = excluded.sent_at,
                    error_message = excluded.error_message
                """, properties.campaignId(), candidate.poetId(), candidate.historicalEmail(), recipient,
                mode.name(), status, candidate.publicUrl(), status, error);
    }

    private void printDryRun(List<Candidate> candidates) {
        System.out.printf("DRY RUN: %d eligible legacy poet(s) for campaign %s%n", candidates.size(), properties.campaignId());
        for (Candidate candidate : candidates) {
            System.out.printf("%s | %s | %d poem(s) | %s%n", candidate.displayName(), candidate.historicalEmail(),
                    candidate.poemCount(), candidate.publicUrl());
        }
    }

    private int limit() {
        if (properties.limit() < 1 || properties.limit() > 500) {
            throw new IllegalStateException("Outreach limit must be between 1 and 500.");
        }
        return properties.limit();
    }

    private String publicUrl(UUID poetId, String displayName) {
        String slug = displayName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return properties.publicBaseUrl().replaceAll("/+$", "") + "/poets/" + poetId + "/" + (slug.isBlank() ? "poet" : slug);
    }

    private static String conciseError(RuntimeException exception) {
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        return message.substring(0, Math.min(message.length(), 2_000));
    }

    private enum Mode {
        DRY_RUN, TEST, LIVE;

        static Mode parse(String value) {
            try {
                return Mode.valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Outreach mode must be dry-run, test, or live.");
            }
        }
    }

    private record Candidate(UUID poetId, String historicalEmail, String displayName, int poemCount, String publicUrl) {
    }
}
