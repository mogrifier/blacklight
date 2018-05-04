package com.erich.util;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

public class Dtime {

    LocalTime beginning = null;

    public void getDeltaTime()
    {
        System.out.println("seconds from start equals= " + ChronoUnit.NANOS.between(beginning, LocalTime.now())/1000000000);
    }

    public Dtime() {
        beginning = LocalTime.now();
    }
}
