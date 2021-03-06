// @LICENSE@

package cz.kruch.track.util;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * Very simple calendar. It is more like 'time counter', to avoid int[]
 * allocation that {@link java.util.Calendar} does, when we just need hh:mm:ss
 * output.
 */
public final class SimpleCalendar {
    private final Calendar calendar;
    private volatile Date date;
    private volatile long last;
    private volatile boolean simple;
    /* cached values for frequent access */
    private volatile int fieldHour, fieldMin, fieldSec, fieldMs;

    public SimpleCalendar(boolean simple) {
        this.simple = simple;
        this.calendar = Calendar.getInstance(TimeZone.getDefault());
    }

    public SimpleCalendar(String tz, boolean simple) {
        this.calendar = Calendar.getInstance(TimeZone.getTimeZone(tz));
        this.simple = simple;
    }

    public void reset() {
        this.date = null;
    }

    public void setTimeSafe(long millis) {
        if (!simple) {
            throw new RuntimeException("setTimeSafe is supported only for really simple calendar");
        }
        if (Math.abs(millis - last) >= 60000) { // if time jumps too much (1 min)
            date = null;
        }
        setTime(millis);
    }

    public void setTime(long millis) {
        if (date != null) {
            final long diff = millis - last;
            final int dt = (int) (diff / 1000);
            final int h = dt / 3600;
            final int m = (dt % 3600) / 60;
            final int s = (dt % 3600) % 60;
            final int ms = (int) (diff % 1000);
            int hour = fieldHour;
            int minute = fieldMin;
            int second = fieldSec;
            int millisecond = fieldMs;
            if (dt > 0) {
                millisecond += ms;
                if (millisecond > 999) {
                    millisecond -= 1000;
                    second++;
                }
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
                millisecond += ms;
                if (millisecond < 0) {
                    millisecond += 1000;
                    second--;
                }
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
            fieldMs = millisecond;
        } else {
            date = new Date(millis);
            calendar.setTime(date);
            fieldHour = calendar.get(Calendar.HOUR_OF_DAY);
            fieldMin = calendar.get(Calendar.MINUTE);
            fieldSec = calendar.get(Calendar.SECOND);
            fieldMs = calendar.get(Calendar.MILLISECOND);
        }
        last = millis;
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
                if (simple) {
                    throw new IllegalArgumentException("Unknown field");
                }
                value = calendar.get(field);
        }

        return value;
    }
}
