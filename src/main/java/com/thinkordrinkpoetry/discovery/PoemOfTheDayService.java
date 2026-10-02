package com.thinkordrinkpoetry.discovery;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PoemOfTheDayService {
    private static final long DAILY_ASSIGNMENT_LOCK = 4_538_649_217L;

    private final JdbcTemplate jdbc;
    /** "Today" for the Poem of the Day, so it changes at the site's midnight rather than UTC's. */
    private final ZoneId siteTimeZone;

    public PoemOfTheDayService(JdbcTemplate jdbc, @Value("${app.site-time-zone}") ZoneId siteTimeZone) {
        this.jdbc = jdbc;
        this.siteTimeZone = siteTimeZone;
    }

    @Transactional
    public UUID poemIdForToday() {
        LocalDate today = LocalDate.now(siteTimeZone);
        UUID assigned = assignedPoem(today);
        if (assigned != null) {
            return assigned;
        }

        // Only the first visit of the day gets here. The advisory lock makes simultaneous first
        // visits at midnight choose one poem rather than competing assignments for the same day.
        jdbc.execute("select pg_advisory_xact_lock(" + DAILY_ASSIGNMENT_LOCK + ")");
        // Another visit may have assigned today's poem while this one waited for the lock.
        assigned = assignedPoem(today);
        if (assigned != null) {
            return assigned;
        }

        int cycle = currentCycle();
        UUID next = unusedPoemIn(cycle);
        if (next == null) {
            cycle++;
            next = unusedPoemIn(cycle);
        }
        if (next == null) {
            return null;
        }

        jdbc.update("""
                insert into poem_of_the_day (assigned_on, poem_id, cycle_number)
                values (?, ?, ?)
                """, today, next, cycle);
        return next;
    }

    private UUID assignedPoem(LocalDate day) {
        List<UUID> assigned = jdbc.query("""
                select poem_id from poem_of_the_day where assigned_on = ?
                """, (resultSet, rowNumber) -> resultSet.getObject(1, UUID.class), day);
        return assigned.isEmpty() ? null : assigned.getFirst();
    }

    private int currentCycle() {
        Integer cycle = jdbc.queryForObject(
                "select coalesce(max(cycle_number), 1) from poem_of_the_day", Integer.class);
        return cycle == null ? 1 : cycle;
    }

    private UUID unusedPoemIn(int cycle) {
        List<UUID> poems = jdbc.query("""
                select poem.id
                from poem
                where not exists (
                    select 1
                    from poem_of_the_day assignment
                    where assignment.cycle_number = ? and assignment.poem_id = poem.id
                )
                order by random()
                limit 1
                """, (resultSet, rowNumber) -> resultSet.getObject(1, UUID.class), cycle);
        return poems.isEmpty() ? null : poems.getFirst();
    }
}
