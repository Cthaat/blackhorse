package com.ruoyi.web.controller.lab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.lab.dto.ReservationApplyDto;
import com.ruoyi.lab.service.ReservationApplyResult;
import com.ruoyi.lab.service.ReservationCommandService;
import com.ruoyi.lab.service.ReservationQueryService;
import com.ruoyi.lab.vo.ReservationVo;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class LabReservationControllerApplyStatusTest
{
    private static final long USER_ID = 7L;

    @Test
    void returnsCreatedForANewReservation()
    {
        TestFixture fixture = fixture(false);

        ResponseEntity<AjaxResult> response = fixture.controller().apply("request-key", fixture.request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsEntry("data", fixture.reservation());
    }

    @Test
    void returnsOkForAnIdempotentReplay()
    {
        TestFixture fixture = fixture(true);

        ResponseEntity<AjaxResult> response = fixture.controller().apply("request-key", fixture.request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("data", fixture.reservation());
    }

    private static TestFixture fixture(boolean replayed)
    {
        ReservationCommandService commandService = mock(ReservationCommandService.class);
        ReservationApplyDto request = new ReservationApplyDto();
        ReservationVo reservation = new ReservationVo(11L, "LR11", 3L, USER_ID,
                null, null, "teaching", null, null, null, null, null, null, null, 0, null);
        when(commandService.apply(USER_ID, "request-key", request))
                .thenReturn(new ReservationApplyResult(reservation, replayed));
        LabReservationController controller = new LabReservationController(commandService,
                mock(ReservationQueryService.class))
        {
            @Override
            public Long getUserId()
            {
                return USER_ID;
            }
        };
        return new TestFixture(controller, request, reservation);
    }

    private record TestFixture(LabReservationController controller,
            ReservationApplyDto request, ReservationVo reservation)
    {
    }
}
