package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.promoter.dto.LeaderboardPrizeDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.LeaderboardPrizeRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.PrizeAwardResult;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
import com.fenixcore.optibienestar360.modules.promoter.service.LeaderboardPrizeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/**
 * Admin config of leaderboard prizes (v2 PDF #5, V42) + a manual award trigger.
 * All gated by {@code LEADERBOARD_PRIZE_MANAGE}. Automatic awarding at period
 * close is driven by {@code LeaderboardPrizeJobRunner}; this endpoint is the
 * manual override / preview ({@code dryRun}).
 */
@RestController
@RequestMapping("/v1/admin/leaderboard-prizes")
@RequiredArgsConstructor
public class AdminLeaderboardPrizeController {

    private final LeaderboardPrizeService service;

    @GetMapping
    @PreAuthorize("hasAuthority('LEADERBOARD_PRIZE_MANAGE')")
    public ResponseEntity<List<LeaderboardPrizeDto>> list() {
        return ResponseEntity.ok(service.list());
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('LEADERBOARD_PRIZE_MANAGE')")
    public ResponseEntity<LeaderboardPrizeDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(service.get(uuid));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('LEADERBOARD_PRIZE_MANAGE')")
    public ResponseEntity<LeaderboardPrizeDto> create(@Valid @RequestBody LeaderboardPrizeRequest request) {
        LeaderboardPrizeDto created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}").buildAndExpand(created.uuid()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('LEADERBOARD_PRIZE_MANAGE')")
    public ResponseEntity<LeaderboardPrizeDto> update(@PathVariable UUID uuid,
            @Valid @RequestBody LeaderboardPrizeRequest request) {
        return ResponseEntity.ok(service.update(uuid, request));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('LEADERBOARD_PRIZE_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid) {
        service.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    /** Manual award/preview for the {@code strategy} period containing {@code period}. */
    @PostMapping("/award")
    @PreAuthorize("hasAuthority('LEADERBOARD_PRIZE_MANAGE')")
    public ResponseEntity<PrizeAwardResult> award(
            @RequestParam(required = false) String period,
            @RequestParam(required = false, defaultValue = "MONTHLY") PeriodStrategy strategy,
            @RequestParam(required = false, defaultValue = "false") boolean dryRun) {
        return ResponseEntity.ok(service.award(strategy, resolveReference(period), dryRun));
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
