// Copyright (C) 2015 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.deleteproject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import org.apache.log4j.Layout;
import org.apache.log4j.spi.LoggingEvent;
import org.eclipse.jgit.util.QuotedString;

final class DeleteLogLayout extends Layout {
  private final Calendar calendar;
  private long lastTimeMillis;
  private final char[] lastTimeString = new char[20];
  private final char[] timeZone;

  public DeleteLogLayout() {
    TimeZone tz = TimeZone.getDefault();
    calendar = Calendar.getInstance(tz);

    SimpleDateFormat sdf = new SimpleDateFormat("Z");
    sdf.setTimeZone(tz);
    timeZone = sdf.format(new Date()).toCharArray();
  }

  /**
   * Formats the events in the delete log.
   *
   * <p>A successful project deletion will result in a log entry like this: [2015-03-05 09:13:28,912
   * +0100] INFO 1000000 admin OK \ myProject {"preserve":false,"force":false}
   *
   * <p>The log entry for a failed project deletion will look like this: [2015-03-05 12:14:30,180
   * +0100] ERROR 1000000 admin FAIL \ myProject {"preserve":false,"force":false}
   * com.google.gwtorm.server.OrmException: \ Failed to access the database
   */
  @Override
  public String format(LoggingEvent event) {
    final StringBuffer buf = new StringBuffer(128);

    buf.append('[');
    formatDate(event.getTimeStamp(), buf);
    buf.append(' ');
    buf.append(timeZone);
    buf.append(']');

    buf.append(' ');
    buf.append(event.getLevel().toString());

    req(DeleteLog.ACCOUNT_ID, buf, event);
    req(DeleteLog.USER_NAME, buf, event);

    buf.append(' ');
    buf.append(event.getMessage());

    req(DeleteLog.PROJECT_NAME, buf, event);
    opt(DeleteLog.OPTIONS, buf, event);
    opt(DeleteLog.ERROR, buf, event);

    buf.append('\n');
    return buf.toString();
  }

  private void formatDate(final long now, final StringBuffer sbuf) {
    final int millis = (int) (now % 1000);
    final long rounded = now - millis;
    if (rounded != lastTimeMillis) {
      synchronized (calendar) {
        final int start = sbuf.length();
        calendar.setTimeInMillis(rounded);
        sbuf.append(calendar.get(Calendar.YEAR));
        sbuf.append('-');
        sbuf.append(toTwoDigits(calendar.get(Calendar.MONTH) + 1));
        sbuf.append('-');
        sbuf.append(toTwoDigits(calendar.get(Calendar.DAY_OF_MONTH)));
        sbuf.append(' ');
        sbuf.append(toTwoDigits(calendar.get(Calendar.HOUR_OF_DAY)));
        sbuf.append(':');
        sbuf.append(toTwoDigits(calendar.get(Calendar.MINUTE)));
        sbuf.append(':');
        sbuf.append(toTwoDigits(calendar.get(Calendar.SECOND)));
        sbuf.append(',');
        sbuf.getChars(start, sbuf.length(), lastTimeString, 0);
        lastTimeMillis = rounded;
      }
    } else {
      sbuf.append(lastTimeString);
    }
    sbuf.append(String.format("%03d", millis));
  }

  private String toTwoDigits(int input) {
    return String.format("%02d", input);
  }

  private void req(String key, StringBuffer buf, LoggingEvent event) {
    Object val = event.getMDC(key);
    buf.append(' ');
    if (val != null) {
      String s = val.toString();
      if (0 <= s.indexOf(' ')) {
        buf.append(QuotedString.BOURNE.quote(s));
      } else {
        buf.append(val);
      }
    } else {
      buf.append('-');
    }
  }

  private void opt(String key, StringBuffer buf, LoggingEvent event) {
    Object val = event.getMDC(key);
    if (val != null) {
      buf.append(' ');
      buf.append(val);
    }
  }

  @Override
  public boolean ignoresThrowable() {
    return true;
  }

  @Override
  public void activateOptions() {}
}
