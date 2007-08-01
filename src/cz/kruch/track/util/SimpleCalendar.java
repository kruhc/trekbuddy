// (c) Copyright 2006-2007  Hewlett-Packard Development Company, L.P. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.util;

import java.util.Calendar;
import java.util.Date;

public final class SimpleCalendar {
    private Calendar calendar;
    private Date date;
    private long last;

    public int hour, minute, second;

    public SimpleCalendar(Calendar calendar) {
        this.calendar = calendar;
    }

    public void reset() {
        this.date = null;
    }

    public void setTime(long millis) {
        if (date == null) {
            date = new Date(millis);
            calendar.setTime(date);
            hour = calendar.get(Calendar.HOUR_OF_DAY);
            minute = calendar.get(Calendar.MINUTE);
            second = calendar.get(Calendar.SECOND);
        } else {
            final int dt = (int) (millis - last) / 1000;
            final int h = dt / 3600;
            final int m = (dt % 3600) / 60;
            final int s = (dt % 3600) % 60;
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
        }
        last = millis;
    }
}
