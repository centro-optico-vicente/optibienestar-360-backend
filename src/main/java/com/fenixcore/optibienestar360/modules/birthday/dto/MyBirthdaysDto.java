package com.fenixcore.optibienestar360.modules.birthday.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Body of {@code GET /v1/me/birthdays}.
 *
 * @param today        current date in the app timezone
 * @param upcomingDays size of the advisor's look-ahead window (days after today)
 * @param self         the caller's own birthday status; present only for an affiliate or a
 *                     beneficiary with portal access ({@code MEMBER_VIEW_OWN}) whose birth date is known
 * @param portfolio    birthdays of the advisor's portfolio (holders and their beneficiaries) from today
 *                     up to {@code upcomingDays} ahead, soonest first; empty unless the caller is an
 *                     active advisor ({@code PROMOTER_VIEW_OWN})
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MyBirthdaysDto(
        LocalDate today,
        int upcomingDays,
        SelfBirthday self,
        List<PortfolioBirthday> portfolio
) {

    /**
     * @param birthdayToday true when today is the caller's birthday (29 Feb birthdays are
     *                      celebrated on 28 Feb in non-leap years)
     * @param age           age turned on {@code nextBirthday}
     */
    public record SelfBirthday(String firstName, boolean birthdayToday, int age, LocalDate nextBirthday) {}

    /**
     * One person of the advisor's portfolio with a birthday in the window.
     *
     * @param memberUuid   the holder's member uuid (links to the portfolio row)
     * @param kind         {@code MEMBER} (the holder) or {@code BENEFICIARY}
     * @param holderName   holder's full name; only for beneficiaries
     * @param relationship beneficiary relationship code (SPOUSE, CHILD…); only for beneficiaries
     * @param daysUntil    0 = today
     * @param turningAge   age the person turns on {@code nextBirthday}
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PortfolioBirthday(
            UUID memberUuid,
            Kind kind,
            String fullName,
            String holderName,
            String relationship,
            String phone,
            LocalDate birthDate,
            LocalDate nextBirthday,
            int daysUntil,
            int turningAge
    ) {}

    public enum Kind { MEMBER, BENEFICIARY }
}
