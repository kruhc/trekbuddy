// @LICENSE@

package cz.kruch.track.util;

import java.util.Calendar;
import java.util.Date;

/**
 * Very simple calendar. It is more like 'time counter', to avoid int[]
 * allocation that {@link java.util.Calendar} does, when we just need hh:mm:ss
 * output.
 */
public final class SimpleCalendar {
    private final Calendar calendar;
    private volatile Date date;
    private volatile int fieldHour, fieldMin, fieldSec;
    private volatile long last;

    public SimpleCalendar(Calendar calendar) {
        this.calendar = calendar;
    }

    public void reset() {
        this.date = null;
    }

    public void setTimeSafe(long millis) {
        if (Math.abs(millis - last) >= 60000) { // if time jumps too much (1 min)
            date = null;
        }
        setTime(millis);
    }

    public void setTime(long millis) {
        if (date != null) {
            final int dt = (int) (millis - last) / 1000;
            final int h = dt / 3600;
            final int m = (dt % 3600) / 60;
            final int s = (dt % 3600) % 60;
            int hour = fieldHour;
            int minute = fieldMin;
            int second = fieldSec;
            if (dt > 0) {
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
                    date = null;
                    setTime(millis);
                    return;
                }
            } else if (dt < 0) {
                second += s;
                if (second < 0) {
                    second += 60;
                    minute--;
                }
                minute += m;
                if (minute < 0) {
                    minute += 60;
                    hour--;
                }
                hour += h;
                if (hour < 0) {
                    date = null;
                    setTime(millis);
                    return;
                }
            }
            fieldHour = hour;
            fieldMin = minute;
            fieldSec = second;
        } else {
            date = new Date(millis);
            calendar.setTime(date);
            fieldHour = calendar.get(Calendar.HOUR_OF_DAY);
            fieldMin = calendar.get(Calendar.MINUTE);
            fieldSec = calendar.get(Calendar.SECOND);
        }
        last = (millis / 1000) * 1000; // TODO why round?
    }

    public int get(final int field) {
        int value;
        switch (field) {
            case Calendar.HOUR:
                value = fieldHour % 12;
            break;
            case Calendar.HOUR_OF_DAY:
                value = fieldHour;
            break;
            case Calendar.MINUTE:
                value = fieldMin;
            break;
            case Calendar.SECOND:
                value = fieldSec;
            break;
            default:
                throw new IllegalArgumentException("Unknown field");
        }

        return value;
    }
}
