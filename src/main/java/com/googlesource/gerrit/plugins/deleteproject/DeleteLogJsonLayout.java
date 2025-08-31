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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;
import java.nio.charset.Charset;

/** Layout for formatting error log events in the JSON format. */
public class DeleteLogJsonLayout extends JsonLayout {

  public DeleteLogJsonLayout() {
    super(Charset.defaultCharset());
  }

  @Override
  public JsonLogEntry toJsonLogEntry(LogEvent event) {
    return new DeleteJsonLogEntry(event);
  }

  @SuppressWarnings("unused")
  private class DeleteJsonLogEntry extends JsonLogEntry {
    /** Timestamp of when the log entry was created. */
    @SerializedName("@timestamp")
    public final String timestamp;

    /** Logging level/severity. */
    public final String level;

    /** Account ID of the user deleting the project. */
    public final String accountId;

    /** Username of the user deleting the project. */
    public final String user;

    /** Status of the project deletion. */
    public final String status;

    /** Deleted project. */
    public final String project;

    /** Options used in the project deletion. */
    public final JsonObject options;

    /** Optional error message. */
    public final String error;

    /** Version of log format. */
    @SerializedName("@version")
    public final int version = 1;

    public DeleteJsonLogEntry(LogEvent event) {
      this.timestamp = timestampFormatter.format(event.getTimeMillis());
      this.level = event.getLevel().toString();
      this.accountId = String.valueOf(event.getContextData().getValue(DeleteLog.ACCOUNT_ID));
      this.user = String.valueOf(event.getContextData().getValue(DeleteLog.USER_NAME));
      this.status = String.valueOf(event.getMessage().getFormattedMessage());
      this.project = String.valueOf(event.getContextData().getValue(DeleteLog.PROJECT_NAME));
      String optionsStr = String.valueOf(event.getContextData().getValue(DeleteLog.OPTIONS));
      this.options =
          optionsStr != null && !optionsStr.isEmpty()
              ? JsonParser.parseString(optionsStr).getAsJsonObject()
              : new JsonObject();
      this.error = String.valueOf(event.getContextData().getValue(DeleteLog.ERROR));
    }
  }
}

