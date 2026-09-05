package com.ruoyi.lab.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.List;
import com.ruoyi.lab.mapper.*;
import com.ruoyi.lab.restriction.*;
import com.ruoyi.lab.security.*;
import com.ruoyi.lab.domain.LabReservation;
import com.ruoyi.lab.exception.LabBusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

class RestrictionServiceTest
{
    final LabRestrictionMapper mapper=mock(LabRestrictionMapper.class);
    final RestrictionGuard guard=mock(RestrictionGuard.class);
    final LabObjectPermissionService permissions=mock(LabObjectPermissionService.class);
    final Clock clock=Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"),ZoneOffset.UTC);
    final LabAttachmentMapper attachments=mock(LabAttachmentMapper.class);
    final LabStatusHistoryService history=mock(LabStatusHistoryService.class);
    org.mockito.MockedStatic<com.ruoyi.common.utils.SecurityUtils> security;
    @BeforeEach void authorize()
    {
        security=mockStatic(com.ruoyi.common.utils.SecurityUtils.class);
        security.when(() -> com.ruoyi.common.utils.SecurityUtils.hasPermi(anyString())).thenReturn(true);
    }
    @AfterEach void closeSecurity() { security.close(); }
    RestrictionService service()
    {
        return new RestrictionService(mapper,guard,permissions,mock(LabDataScopeService.class),
                mock(LabUserDirectory.class),attachments,
                history,mock(LabStatusHistoryMapper.class),clock);
    }
    @Test void ownerCannotCreateOwnManualRestriction()
    {
        when(permissions.currentUserId()).thenReturn(7L);
        assertThatThrownBy(() -> service().manual(new RestrictionCommands.Manual(2L,7L,7,"原因")))
                .isInstanceOf(LabBusinessException.class);
        verify(mapper,never()).insert(any());
    }
    @Test void oldNoShowFactIsNotBackfilledAndDuplicateDoesNotExtend()
    {
        var now=LocalDateTime.now(clock);
        when(guard.gate()).thenReturn(now);
        var reservation=new LabReservation(); reservation.setId(3L);reservation.setApplicantId(7L);
        service().recordNoShow(reservation,2L,now.minusSeconds(1),1L);
        verify(mapper,never()).insert(any());
        when(mapper.noShow(3L)).thenReturn(new RestrictionRecord());
        service().recordNoShow(reservation,2L,now.plusSeconds(1),1L);
        verify(mapper,never()).insert(any());
    }
    @Test void repeatedAppealIsRejectedEvenAfterRestrictionExpires()
    {
        when(permissions.currentUserId()).thenReturn(7L);
        var row=new RestrictionRecord();row.id=1L;row.userId=7L;row.laboratoryId=2L;
        when(mapper.byId(eq(1L),any())).thenReturn(row);
        when(mapper.locked(1L)).thenReturn(row);
        when(mapper.appeal(1L)).thenReturn(new RestrictionAppeal());
        assertThatThrownBy(() -> service().appeal(1L,new RestrictionCommands.Appeal("申诉",List.of())))
                .isInstanceOf(LabBusinessException.class);
        verify(mapper,never()).insertAppeal(any());
    }

    RestrictionRecord row()
    {
        var row=new RestrictionRecord();row.id=1L;row.userId=7L;row.laboratoryId=2L;
        row.startsAt=LocalDateTime.now(clock).minusDays(10);row.endsAt=row.startsAt.plusDays(7);
        when(mapper.byId(eq(1L),any())).thenReturn(row);
        when(mapper.locked(1L)).thenReturn(row);
        return row;
    }

    @Test void ownerCannotRevokeOrReviewOwnRestriction()
    {
        row();when(permissions.currentUserId()).thenReturn(7L);
        assertThatThrownBy(() -> service().revoke(1L,"原因")).isInstanceOf(LabBusinessException.class);
        assertThatThrownBy(() -> service().review(1L,new RestrictionCommands.Decision(true,"原因")))
                .isInstanceOf(LabBusinessException.class);
        verify(mapper,never()).revoke(any(),any(),any(),any());
        verify(mapper,never()).review(any(),any(),any(),any(),any());
    }

    @Test void expiredRestrictionStillAllowsOneAppealWithoutLiftingAnyFact()
    {
        row();when(permissions.currentUserId()).thenReturn(7L);
        when(mapper.insertAppeal(any())).thenAnswer(call -> { ((RestrictionAppeal)call.getArgument(0)).id=3L;return 1; });
        var result=service().appeal(1L,new RestrictionCommands.Appeal("已经过期仍有异议",List.of()));
        assertThat(result.status).isEqualTo("PENDING");
        verify(mapper,never()).revoke(any(),any(),any(),any());
    }

    @Test void evidenceFromAnotherParentCannotBeSubmitted()
    {
        row();when(permissions.currentUserId()).thenReturn(7L);
        var attachment=new com.ruoyi.lab.domain.LabAttachment();
        attachment.setBusinessType("RESTRICTION");attachment.setBusinessId(99L);attachment.setDelFlag("0");
        when(attachments.selectByIdForUpdate(5L)).thenReturn(attachment);
        assertThatThrownBy(() -> service().appeal(1L,new RestrictionCommands.Appeal("原因",List.of(5L))))
                .isInstanceOf(LabBusinessException.class);
        verify(mapper,never()).insertAppeal(any());
    }

    @Test void submittedAppealFreezesEvidenceUsingCurrentLockingRead()
    {
        row();when(permissions.currentUserId()).thenReturn(7L);
        when(mapper.appealLocked(1L)).thenReturn(new RestrictionAppeal());
        assertThatThrownBy(() -> service().lockEvidenceOwner(1L)).isInstanceOf(LabBusinessException.class);
        verify(mapper).appealLocked(1L);
    }

    @Test void approvalRevokesOnlyTheTargetRestrictionAndKeepsNoShowUntouched()
    {
        row();when(permissions.currentUserId()).thenReturn(8L);
        when(mapper.review(eq(1L),eq("APPROVED"),eq(8L),any(),any())).thenReturn(1);
        service().review(1L,new RestrictionCommands.Decision(true,"事实有误"));
        verify(mapper).revoke(eq(1L),eq(8L),eq("事实有误"),any());
        verify(history).append(eq("RESTRICTION"),eq(1L),eq("APPEAL_PENDING"),eq("APPEAL_APPROVED"),eq(8L),eq("事实有误"));
    }

    @Test void automaticFactSnapshotsPublishedDurationAtCreation()
    {
        var now=LocalDateTime.now(clock);when(guard.gate()).thenReturn(now.minusDays(1));
        var rule=new RestrictionRule();rule.id=10L;rule.days=3;
        when(mapper.activeRule(2L)).thenReturn(rule);
        var reservation=new LabReservation();reservation.setId(9L);reservation.setApplicantId(7L);
        service().recordNoShow(reservation,2L,now,1L);
        var captor=org.mockito.ArgumentCaptor.forClass(RestrictionRecord.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().endsAt).isEqualTo(now.plusDays(3));
        assertThat(captor.getValue().ruleVersionId).isEqualTo(10L);
        assertThat(captor.getValue().ruleSnapshot).contains("\"days\":3");
    }

    @Test void newLaboratoryGetsPersistedDefaultRuleBeforeItsFirstFact()
    {
        var now=LocalDateTime.now(clock);when(guard.gate()).thenReturn(now.minusDays(1));
        when(mapper.publish(any())).thenAnswer(call -> { ((RestrictionRule)call.getArgument(0)).id=12L;return 1; });
        var reservation=new LabReservation();reservation.setId(9L);reservation.setApplicantId(7L);
        service().recordNoShow(reservation,2L,now,1L);
        var captor=org.mockito.ArgumentCaptor.forClass(RestrictionRecord.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().ruleVersionId).isEqualTo(12L);
        assertThat(captor.getValue().endsAt).isEqualTo(now.plusDays(7));
    }

    @Test void appealPermissionIsRequiredEvenForOwner()
    {
        row();when(permissions.currentUserId()).thenReturn(7L);
        security.when(() -> com.ruoyi.common.utils.SecurityUtils.hasPermi("lab:restriction:appeal")).thenReturn(false);
        assertThatThrownBy(() -> service().appeal(1L,new RestrictionCommands.Appeal("原因",List.of())))
                .isInstanceOf(LabBusinessException.class);
        verify(mapper,never()).insertAppeal(any());
    }
}
