package com.fenixcore.optibienestar360.modules.birthday.service;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.modules.birthday.dto.MyBirthdaysDto;
import com.fenixcore.optibienestar360.modules.birthday.dto.MyBirthdaysDto.Kind;
import com.fenixcore.optibienestar360.modules.birthday.dto.MyBirthdaysDto.PortfolioBirthday;
import com.fenixcore.optibienestar360.modules.birthday.dto.MyBirthdaysDto.SelfBirthday;
import com.fenixcore.optibienestar360.modules.birthday.repository.BirthdayQueries;
import com.fenixcore.optibienestar360.modules.member.entity.Beneficiary;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.BeneficiaryRepository;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Birthday notices for the portals ({@code GET /v1/me/birthdays}).
 *
 * <ul>
 *   <li><b>Self</b> ({@code MEMBER_VIEW_OWN}): the holder, or a beneficiary with portal access,
 *       learns whether today is their birthday so the portal shows the OptiBienestar greeting.</li>
 *   <li><b>Portfolio</b> ({@code PROMOTER_VIEW_OWN} + active promoter): holders attributed to
 *       the advisor and their active beneficiaries with a birthday today or within the next
 *       {@value #UPCOMING_DAYS} days.</li>
 * </ul>
 *
 * <p>"Today" is the app timezone date. A 29 February birthday is celebrated on 28 February in
 * non-leap years ({@link MonthDay#atYear}).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BirthdayService {

    public static final int UPCOMING_DAYS = 7;
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MM-dd");

    private final BirthdayQueries queries;
    private final PromoterRepository promoterRepository;
    private final MemberRepository memberRepository;
    private final BeneficiaryRepository beneficiaryRepository;

    /** Overridable in tests; production uses the app timezone. */
    private Clock clock = Clock.system(AppTimeZone.ZONE);

    void setClock(Clock clock) {
        this.clock = clock;
    }

    public MyBirthdaysDto getMyBirthdays(UUID userUuid, Collection<String> authorities) {
        Set<String> perms = Set.copyOf(authorities);
        LocalDate today = LocalDate.now(clock);

        SelfBirthday self = null;
        if (perms.contains("MEMBER_VIEW_OWN")) {
            self = ownPerson(userUuid)
                    .filter(p -> p.getBirthDate() != null)
                    .map(p -> selfBirthday(p, today))
                    .orElse(null);
        }

        List<PortfolioBirthday> portfolio = List.of();
        if (perms.contains("PROMOTER_VIEW_OWN")) {
            portfolio = promoterRepository.findActiveByUserUuid(userUuid)
                    .map(p -> portfolio(p.getId(), today))
                    .orElse(List.of());
        }
        return new MyBirthdaysDto(today, UPCOMING_DAYS, self, portfolio);
    }

    private Optional<Person> ownPerson(UUID userUuid) {
        Optional<Member> member = memberRepository.findByUserUuid(userUuid).filter(Member::isActive);
        if (member.isPresent()) {
            return Optional.ofNullable(member.get().getPerson());
        }
        return beneficiaryRepository.findByUser_UuidAndActiveTrue(userUuid).map(Beneficiary::getPerson);
    }

    static SelfBirthday selfBirthday(Person person, LocalDate today) {
        LocalDate next = nextBirthday(person.getBirthDate(), today);
        return new SelfBirthday(person.getFirstName(), next.equals(today),
                ageOn(person.getBirthDate(), next), next);
    }

    private List<PortfolioBirthday> portfolio(Long promoterId, LocalDate today) {
        return queries.findPortfolioBirthdays(promoterId, monthDaysInWindow(today, UPCOMING_DAYS)).stream()
                .map(row -> {
                    LocalDate next = nextBirthday(row.birthDate(), today);
                    return new PortfolioBirthday(row.memberUuid(),
                            row.beneficiary() ? Kind.BENEFICIARY : Kind.MEMBER,
                            row.fullName(), row.holderName(), row.relationship(), row.phone(),
                            row.birthDate(), next, (int) ChronoUnit.DAYS.between(today, next),
                            ageOn(row.birthDate(), next));
                })
                .filter(b -> b.daysUntil() <= UPCOMING_DAYS)
                .sorted(Comparator.comparingInt(PortfolioBirthday::daysUntil)
                        .thenComparing(PortfolioBirthday::fullName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    /**
     * {@code MM-dd} keys of every day in {@code [today, today + days]}; adds {@code 02-29} when
     * 28 February of a non-leap year is in the window, so leap-day birthdays are not missed.
     */
    static Set<String> monthDaysInWindow(LocalDate today, int days) {
        Set<String> keys = new LinkedHashSet<>();
        for (int i = 0; i <= days; i++) {
            LocalDate d = today.plusDays(i);
            keys.add(d.format(MONTH_DAY));
            if (d.getMonthValue() == 2 && d.getDayOfMonth() == 28 && !d.isLeapYear()) {
                keys.add("02-29");
            }
        }
        return keys;
    }

    /** First birthday on or after {@code today} (29 Feb → 28 Feb in non-leap years). */
    static LocalDate nextBirthday(LocalDate birthDate, LocalDate today) {
        MonthDay md = MonthDay.from(birthDate);
        LocalDate thisYear = md.atYear(today.getYear());
        return thisYear.isBefore(today) ? md.atYear(today.getYear() + 1) : thisYear;
    }

    static int ageOn(LocalDate birthDate, LocalDate birthday) {
        return birthday.getYear() - birthDate.getYear();
    }
}
