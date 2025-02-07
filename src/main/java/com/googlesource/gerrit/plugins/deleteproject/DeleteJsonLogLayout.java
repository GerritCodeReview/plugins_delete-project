// Copyright (C) 2025 The Android Open Source Project
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

import com.google.gerrit.util.logging.JsonLayout;
import com.google.gerrit.util.logging.JsonLogEntry;
import com.google.gson.annotations.SerializedName;
import org.apache.log4j.spi.LoggingEvent;

/** Layout for formatting error log events in the JSON format. */
public class DeleteJsonLogLayout extends JsonLayout {
  @Override
  public JsonLogEntry toJsonLogEntry(LoggingEvent event) {
    return new DeleteJsonLogEntry(event);
  }

  @SuppressWarnings("unused")
  private class DeleteJsonLogEntry extends JsonLogEntry {
    /** Timestamp of when the log entry was created. */
    @SerializedName("@timestamp")
    public final String timestamp;

    /** Logging level/severity. */
    public final String level;

    public final String accountId;

    public final String user;

    public final String status;

    public final String project;

    public final String options;

    public final String error;

    /** Version of log format. */
    @SerializedName("@version")
    public final int version = 1;

    public DeleteJsonLogEntry(LoggingEvent event) {
      this.timestamp = timestampFormatter.format(event.getTimeStamp());
      this.level = event.getLevel().toString();
      this.accountId = (String) event.getMDC(DeleteLog.ACCOUNT_ID);
      this.user = (String) event.getMDC(DeleteLog.USER_NAME);
      this.status = (String) event.getMessage();
      this.project = (String) event.getMDC(DeleteLog.PROJECT_NAME);
      this.options = (String) event.getMDC(DeleteLog.OPTIONS);
      this.error = (String) event.getMDC(DeleteLog.ERROR);
    }
  }
}
