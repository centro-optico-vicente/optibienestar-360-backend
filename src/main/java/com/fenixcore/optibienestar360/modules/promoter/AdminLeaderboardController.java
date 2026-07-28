package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.promoter.dto.LeaderboardDto;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
import com.fenixcore.optibienestar360.modules.promoter.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/**
 * Promoter leaderboard (v2 PDF #5, V42). {@code GET /v1/admin/leaderboard} —
 * top real promoters (system rows excluded) by commission earnings within a
 * period, prizes attached. Gated by {@code LEADERBOARD_VIEW}.
 *
 * <p>{@code period} is parsed leniently: {@code 2026-06-15} (a date),
 * {@code 2026-06} (a month), or {@code 2026} (a year); any date inside the
 * target period works. Omitted → today.</p>
 */
@RestController
@RequestMapping("/v1/admin/leaderboard")
@RequiredArgsConstructor
public class AdminLeaderboardController {

    private final LeaderboardService leaderboardService;

    @GetMapping
    @PreAuthorize("hasAuthority('LEADERBOARD_VIEW')")
    public ResponseEntity<LeaderboardDto> leaderboard(
            @RequestParam(required = false) String period,
            @RequestParam(required = false, defaultValue = "MONTHLY") PeriodStrategy strategy,
            @RequestParam(required = false, defaultValue = "3") int limit) {
        return ResponseEntity.ok(leaderboardService.leaderboard(strategy, resolveReference(period), limit));
    }

    /** Lenient period → reference date: full date, then month, then year. */
    private static LocalDate resolveReference(String period) {
        if (period == null || period.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(period);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return YearMonth.parse(period).atDay(1);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return Year.parse(period).atDay(1);
        } catch (DateTimeParseException ignored) {
            throw new IllegalArgumentException("leaderboard.period.invalid");
        }
    }
}
