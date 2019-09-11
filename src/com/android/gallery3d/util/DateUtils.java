package com.android.gallery3d.util;

import android.content.Context;
import android.content.res.Resources;
import android.text.format.Time;

import com.android.gallery3d.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Title:TODO
 * Description:TODO
 * Copyright:Copyright (c) 2015
 * Company:wentai
 * Author:lijin
 * Date:16-11-7
 * Version 1.0
 */

public class DateUtils {

    public static final SimpleDateFormat FORMAT_DETAIL = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    public static final SimpleDateFormat FORMAT_DATE = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    public static final SimpleDateFormat FORMAT_ONLY_MONTH = new SimpleDateFormat("MM-dd-");
    public static final SimpleDateFormat FORMAT_ONLY_MONTH_2 = new SimpleDateFormat("MM-dd");
    public static final SimpleDateFormat FORMAT_MONTH = new SimpleDateFormat("MM-dd HH:mm");
    public static final SimpleDateFormat FORMAT_TIME = new SimpleDateFormat("HH:mm");
    public static final SimpleDateFormat FORMAT_DAY_OF_MONTH = new SimpleDateFormat("yyyy-MM-dd");
    public static final SimpleDateFormat FORMAT_DAY_OF_MONTH_2 = new SimpleDateFormat("yyyy-MM-dd");

    /**
     * @return true if the supplied when is today else false
     */
    public static boolean isToday(long when) {
        Time time = new Time();
        time.set(when);

        int thenYear = time.year;
        int thenMonth = time.month;
        int thenMonthDay = time.monthDay;

        time.set(System.currentTimeMillis());
        return (thenYear == time.year)
                && (thenMonth == time.month)
                && (thenMonthDay == time.monthDay);
    }

    /**
     * @return true if the supplied when is yesterday else false
     */
    public static boolean isYesterday(long when) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(when);
        int thenYear = c.get(Calendar.YEAR);
        int thenMonth = c.get(Calendar.MONTH);
        int thenMonthDay = c.get(Calendar.DAY_OF_MONTH);

        c.setTimeInMillis(System.currentTimeMillis());
        c.roll(Calendar.DAY_OF_YEAR, -1);

        return (thenYear == c.get(Calendar.YEAR))
                && (thenMonth == c.get(Calendar.MONTH))
                && (thenMonthDay == c.get(Calendar.DAY_OF_MONTH));
    }

    /**
     * @return true if the supplied when is year else false
     */
    public static boolean isYear(long when) {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        calendar.setTimeInMillis(when);
        return (calendar.get(Calendar.YEAR) == year);
    }

    /**
     * get week by stamp time
     *
     * @param context
     * @param when
     * @return
     */
    public static String getWeek(final Context context, long when) {
        String week = null;
        Resources res = context.getResources();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(when);
        switch (c.get(Calendar.DAY_OF_WEEK)) {
            case Calendar.MONDAY:
                week = res.getString(R.string.monday);
                break;
            case Calendar.TUESDAY:
                week = res.getString(R.string.tuesday);
                break;
            case Calendar.WEDNESDAY:
                week = res.getString(R.string.wednesday);
                break;
            case Calendar.THURSDAY:
                week = res.getString(R.string.thursday);
                break;
            case Calendar.FRIDAY:
                week = res.getString(R.string.friday);
                break;
            case Calendar.SATURDAY:
                week = res.getString(R.string.saturday);
                break;
            case Calendar.SUNDAY:
                week = res.getString(R.string.sunday);
                break;
            default:
                week = "";
                break;
        }
        return week;
    }

    public static long getTodayStartTime(long time) {
        Calendar todayStart = Calendar.getInstance();
        todayStart.setTime(new Date(time));
        todayStart.set(Calendar.HOUR_OF_DAY, 0);
        todayStart.set(Calendar.MINUTE, 0);
        todayStart.set(Calendar.SECOND, 0);
        todayStart.set(Calendar.MILLISECOND, 0);
        return todayStart.getTimeInMillis();
    }

    public static long getTodayEndTime(long time) {
        Calendar todayEnd = Calendar.getInstance();
        todayEnd.setTime(new Date(time));
        todayEnd.set(Calendar.HOUR_OF_DAY, 23);
        todayEnd.set(Calendar.MINUTE, 59);
        todayEnd.set(Calendar.SECOND, 59);
        todayEnd.set(Calendar.MILLISECOND, 999);
        return todayEnd.getTimeInMillis();
    }
}
