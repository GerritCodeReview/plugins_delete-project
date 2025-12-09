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
  private final long defaultInitialDelay;
  private final TimeUnit defaultInitialDelayUnit;
  private final long defaultPeriod;
  private final TimeUnit defaultPeriodUnit;
  private ScheduledFuture<?> scheduledTask;

  protected AbstractScheduledTask(
      WorkQueue queue,
      Optional<ScheduleConfig.Schedule> schedule,
      long defaultInitialDelay,
      TimeUnit defaultInitialDelayUnit,
      long defaultPeriod,
      TimeUnit defaultPeriodUnit) {
    this.queue = queue;
    this.schedule = schedule;
    this.defaultInitialDelay = defaultInitialDelay;
    this.defaultInitialDelayUnit = defaultInitialDelayUnit;
    this.defaultPeriod = defaultPeriod;
    this.defaultPeriodUnit = defaultPeriodUnit;
  }

  @Override
  public void start() {
    long initialDelayMs =
        schedule.map(s -> s.initialDelay()).orElse(defaultInitialDelayUnit.toMillis(defaultInitialDelay));
    long periodMs = schedule.map(s -> s.interval()).orElse(defaultPeriodUnit.toMillis(defaultPeriod));

    if (periodMs > 0) {
      scheduledTask =
          queue
              .getDefaultQueue()
              .scheduleAtFixedRate(this, initialDelayMs, periodMs, TimeUnit.MILLISECONDS);
    } else if (initialDelayMs >= 0) {
      scheduledTask =
          queue.getDefaultQueue().schedule(this, initialDelayMs, TimeUnit.MILLISECONDS);
    }
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
