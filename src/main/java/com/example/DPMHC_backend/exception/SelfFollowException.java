package com.example.DPMHC_backend.exception;

public class SelfFollowException extends RuntimeException {
    public SelfFollowException() {
        super("You cannot follow yourself");
    }
}