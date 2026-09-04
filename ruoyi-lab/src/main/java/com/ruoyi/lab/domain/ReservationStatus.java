package com.ruoyi.lab.domain;

/** Reservation lifecycle states persisted by lab_reservation. */
public enum ReservationStatus
{
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED,
    EXPIRED,
    NO_SHOW,
    CHECKED_OUT,
    COMPLETED
}
