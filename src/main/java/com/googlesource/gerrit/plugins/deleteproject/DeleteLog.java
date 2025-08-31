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

import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.systemstatus.ServerInformation;
import com.google.gerrit.json.OutputFormat;
import com.google.gerrit.server.AuditEvent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.audit.AuditService;
import com.google.gerrit.server.config.LogConfig;
import com.google.gerrit.server.util.PluginLogFile;
import com.google.gerrit.server.util.SystemLog;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.ThreadContext;

@Singleton
class DeleteLog extends PluginLogFile {
  private static final String DELETE_LOG_NAME = "delete_log";
  private static final Logger log = LogManager.getLogger(DELETE_LOG_NAME);

  public static final String ACCOUNT_ID = "accountId";
  public static final String USER_NAME = "userName";
  public static final String PROJECT_NAME = "projectName";
  public static final String OPTIONS = "options";
  public static final String ERROR = "error";

  private final AuditService auditService;

  @Inject
  public DeleteLog(
      SystemLog systemLog,
      ServerInformation serverInfo,
      AuditService auditService,
      LogConfig config) {
    super(
        systemLog,
        serverInfo,
        DELETE_LOG_NAME,
        new DeleteLogLayout(),
        new DeleteLogJsonLayout(),
        config);
    this.auditService = auditService;
  }

  public void onDelete(
      IdentifiedUser user, Project.NameKey project, DeleteProject.Input options, Exception ex) {
    long ts = TimeUtil.nowMs();

    // Use ThreadContext for MDC properties
    ThreadContext.put(ACCOUNT_ID, user.getAccountId().toString());
    if (user.getUserName().isPresent()) {
      ThreadContext.put(USER_NAME, user.getUserName().get());
    }
    ThreadContext.put(PROJECT_NAME, project.get());

    if (options != null) {
      ThreadContext.put(OPTIONS, OutputFormat.JSON_COMPACT.newGson().toJson(options));
    }

    if (ex != null) {
      ThreadContext.put(ERROR, ex.toString());
    }

    if (ex == null) {
      log.info("OK");
    } else {
      log.error("FAIL", ex);
    }

    // Clear MDC properties to avoid leaking between threads
    ThreadContext.clearMap();

    audit(user, ts, project, options, ex);
  }

  private void audit(
      IdentifiedUser user,
      long ts,
      Project.NameKey project,
      DeleteProject.Input options,
      Exception ex) {
    ListMultimap<String, Object> params = MultimapBuilder.hashKeys().arrayListValues().build();
    params.put("class", DeleteLog.class);
    params.put("project", project.get());
    if (options != null) {
      params.put("force", String.valueOf(options.force));
      params.put("preserve", String.valueOf(options.preserve));
    }

    auditService.dispatch(
        new AuditEvent(
            null, // sessionId
            user, // who
            ex == null // what
                ? "ProjectDeletion"
                : "ProjectDeletionFailure",
            ts, // when
            params, // params
            ex != null // result
                ? ex.toString()
                : "OK"));
  }
}
