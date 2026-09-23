package com.thinkordrinkpoetry.discovery;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PoemOfTheDayService {
    private static final ZoneId SITE_TIME_ZONE = ZoneId.of("America/Denver");
    private static final long DAILY_ASSIGNMENT_LOCK = 4_538_649_217L;

    private final JdbcTemplate jdbc;

    PoemOfTheDayService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    UUID poemIdForToday() {
        // The advisory lock makes simultaneous first visits at midnight choose one poem,
        // rather than creating competing assignments for the same day.
        jdbc.execute("select pg_advisory_xact_lock(" + DAILY_ASSIGNMENT_LOCK + ")");

        LocalDate today = LocalDate.now(SITE_TIME_ZONE);
        UUID assigned = assignedPoem(today);
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
