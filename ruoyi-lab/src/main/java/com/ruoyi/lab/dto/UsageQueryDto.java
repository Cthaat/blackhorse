package com.ruoyi.lab.dto;

import java.time.LocalDateTime;
import com.ruoyi.lab.domain.ReturnCondition;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

public class UsageQueryDto
{
    @Size(max = 32)
    private String reservationNo;
    @Size(max = 64)
    private String assetNo;
    private ReturnCondition returnCondition;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime checkedOutFrom;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime checkedOutTo;
    @Pattern(regexp = "id|reservationNo|assetNo|checkedOutAt|returnedAt")
    private String sortBy = "id";
    @Pattern(regexp = "asc|desc")
    private String sortDirection = "desc";

    public String getReservationNo() { return reservationNo; }
    public void setReservationNo(String reservationNo) { this.reservationNo = reservationNo; }
    public String getAssetNo() { return assetNo; }
    public void setAssetNo(String assetNo) { this.assetNo = assetNo; }
    public ReturnCondition getReturnCondition() { return returnCondition; }
    public void setReturnCondition(ReturnCondition returnCondition) { this.returnCondition = returnCondition; }
    public LocalDateTime getCheckedOutFrom() { return checkedOutFrom; }
    public void setCheckedOutFrom(LocalDateTime checkedOutFrom) { this.checkedOutFrom = checkedOutFrom; }
    public LocalDateTime getCheckedOutTo() { return checkedOutTo; }
    public void setCheckedOutTo(LocalDateTime checkedOutTo) { this.checkedOutTo = checkedOutTo; }
    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }
    public String getSortDirection() { return sortDirection; }
    public void setSortDirection(String sortDirection) { this.sortDirection = sortDirection; }
}
