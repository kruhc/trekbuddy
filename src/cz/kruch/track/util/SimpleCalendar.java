/*
 * Copyright 2006-2007 Ales Pour <kruhc@seznam.cz>.
 * All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */

package cz.kruch.track.util;

import java.util.Calendar;
import java.util.Date;

/**
 * Very simple calendar. Well, it is more like 'time counter', to avoid int[]
 * allocation that {@link java.util.Calendar} does, when we just needs hh:mm:ss
 * output.
 */
public final class SimpleCalendar {
    private Calendar calendar;
    private Date date;
    private final int[] fields;
    private volatile long last;

    public SimpleCalendar(Calendar calendar) {
        this.calendar = calendar;
        this.fields = new int[3];
    }

    public void reset() {
        this.date = null;
    }

    public void setTimeSafe(long millis) {
        if (Math.abs(millis - last) >= 3600) { // if time jumps too much (1 hour)
            date = null;
        }
        setTime(millis);
    }

    public void setTime(long millis) {
        if (date == null) {
            date = new Date(millis);
            calendar.setTime(date);
            fields[0] = calendar.get(Calendar.HOUR_OF_DAY);
            fields[1] = calendar.get(Calendar.MINUTE);
            fields[2] = calendar.get(Calendar.SECOND);
        } else {
            final int dt = (int) (millis - last) / 1000;
            final int h = dt / 3600;
            final int m = (dt % 3600) / 60;
            final int s = (dt % 3600) % 60;
            int hour = fields[0];
            int minute = fields[1];
            int second = fields[2];
            if (dt < 0) {
                second += s;
                if (second < 0) {
                    minute--;
                    second = 60 + second;
                }
                minute += m;
                if (minute < 0) {
                    hour--;
                    minute = 60 + minute;
                }
                hour += h;
                if (hour < 0) {
                    hour = 24 + hour % 24; // TODO what to do?
                }
            } else {
                second += s;
                if (second > 59) {
                    second -= 60;
                    minute++;
                }
                minute += m;
                if (minute > 59) {
                    minute -= 60;
                    hour++;
                }
                hour += h;
                if (hour > 23) {
                    hour %= 24; // TODO what to do?
                }
            }
            fields[0] = hour;
            fields[1] = minute;
            fields[2] = second;
        }
        last = millis;
    }

    public int get(final int field) {
        int value;
        switch (field) {
            case Calendar.HOUR_OF_DAY:
                value = fields[0];
            break;
            case Calendar.MINUTE:
                value = fields[1];
            break;
            case Calendar.SECOND:
                value = fields[2];
            break;
            default:
                throw new IllegalArgumentException("Unknown field");
        }

        return value;
    }
}
