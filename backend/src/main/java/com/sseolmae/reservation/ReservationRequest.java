package com.sseolmae.reservation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

public record ReservationRequest(
    @NotNull(message = "방문 날짜를 선택해 주세요.") LocalDate visitDate,
    @NotNull(message = "이용권을 선택해 주세요.") TicketType ticketType,
    @NotNull(message = "성인 인원을 입력해 주세요.")
    @PositiveOrZero(message = "성인 인원은 0명 이상이어야 합니다.")
    @Max(value = 20, message = "성인 인원은 최대 20명입니다.") Integer adults,
    @NotNull(message = "어린이 인원을 입력해 주세요.")
    @PositiveOrZero(message = "어린이 인원은 0명 이상이어야 합니다.")
    @Max(value = 20, message = "어린이 인원은 최대 20명입니다.") Integer children
) {}
