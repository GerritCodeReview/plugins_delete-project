// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.plugins;

/**
 * Error indicating generic failure to delete a project
 */
public class DeleteProjectFailedException extends Exception {
  private static final long serialVersionUID = 1L;

  public DeleteProjectFailedException(final String message) {
    this(message, null);
  }

  public DeleteProjectFailedException(final String message,
      final Throwable why) {
    super(message, why);
  }
}