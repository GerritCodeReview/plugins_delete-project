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

package com.googlesource.gerrit.plugins.deleteproject.fs;

import static com.googlesource.gerrit.plugins.deleteproject.Configuration.DEFAULT_INITIAL_DELAY_MILLIS;
import static com.googlesource.gerrit.plugins.deleteproject.Configuration.DEFAULT_PERIOD_DAYS;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.ScheduleConfig;
import com.google.gerrit.server.git.WorkQueue;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public abstract class AbstractScheduledTask implements LifecycleListener, Runnable {

  private final WorkQueue queue;
  private final Optional<ScheduleConfig.Schedule> schedule;
  private ScheduledFuture<?> scheduledTask;

  protected AbstractScheduledTask(WorkQueue queue, Optional<ScheduleConfig.Schedule> schedule) {
    this.queue = queue;
    this.schedule = schedule;
  }

  @Override
  public void start() {
    long initialDelayMs =
        schedule.map(ScheduleConfig.Schedule::initialDelay).orElse(DEFAULT_INITIAL_DELAY_MILLIS);
    long periodMs =
        schedule
            .map(ScheduleConfig.Schedule::interval)
            .orElse(TimeUnit.DAYS.toMillis(DEFAULT_PERIOD_DAYS));

    scheduledTask =
        queue
            .getDefaultQueue()
            .scheduleAtFixedRate(this, initialDelayMs, periodMs, TimeUnit.MILLISECONDS);
  }

  @Override
  public void stop() {
    if (scheduledTask != null) {
      scheduledTask.cancel(true);
      scheduledTask = null;
    }
  }

  @Override
  public abstract void run();

  @VisibleForTesting
  ScheduledFuture<?> getWorkerFuture() {
    return scheduledTask;
  }
}
