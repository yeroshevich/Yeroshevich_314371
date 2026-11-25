package by.technosoft.bonus.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import by.technosoft.bonus.service.exception.DomainValidationException;

/** Даты кампании из браузера (dayjs().startOf('day').toISOString()) — дата в часовом поясе компании. */
class CampaignMapperTest {

    private final CampaignMapper mapper =
            new CampaignMapper(Clock.fixed(Instant.parse("2026-11-25T08:00:00Z"), ZoneId.of("Europe/Minsk")));

    @Test
    void acceptsDateAndInstant() {
        assertThat(mapper.businessDate("2026-12-01")).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(mapper.businessDate("2026-11-30T21:00:00.000Z")).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(mapper.businessDate("2026-12-07T20:59:59.999Z")).isEqualTo(LocalDate.of(2026, 12, 7));
    }

    @Test
    void rejectsUnknownFormat() {
        assertThatThrownBy(() -> mapper.businessDate("01.12.2026")).isInstanceOf(DomainValidationException.class);
    }
}
