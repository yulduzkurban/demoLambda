package com.task12.exceptions;

public class OverlappingReservationException extends RuntimeException {

    private final int code = 400;
    private final String message = "Reservation is overlapping";

}
