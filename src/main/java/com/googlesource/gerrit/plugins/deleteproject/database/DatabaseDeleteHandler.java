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

package com.googlesource.gerrit.plugins.deleteproject.database;

import java.util.Collection;

import com.google.gerrit.reviewdb.client.Project;

/**
 * Handles deleting a project from the database for a specific schema.
 */
// When implementing this interface for a specific schema, only use functions
// available at the time when that schema was created. While this is not
// strictly necessary, it helps when doing cherry-picking between branches
// for different api versions.
public interface DatabaseDeleteHandler {
  /**
   * Asserts that the project can be deleted from the database.
   * <p>
   * This method performs all required precursory checks whether or not the
   * project can by deleted from the database.
   * <p>
   * This method must not yet remove any data.
   * <p>
   * To signal that deletion of the project is not okay, throw an exception.
   *
   * @param project The project that should be checked whether or not it can
   *    be deleted.
   * @throws Exception If there is on obstacle to the deletion. The exception's
   *    message should describe the problem.
   */
  public void assertCanDelete(Project project) throws Exception;

  /**
   * Gets warnings to show to user before allowing to delete project.
   * <p>
   * This method expects that the caller called {@code assertCanDelete()} in
   * advance and no exceptions where thrown from this invocation.
   * <p>
   * It is not guaranteed that this method is invoked. It is also valid to call
   * {@code delete()} right after calling {@code assertCanDelete}.
   * <p>
   * The given project has to allow to be deleted by calling {@code delete()}
   * despite the returned warnings. To signal settings that forbid project
   * deletion, implement those guards in {@code assertCanDelete()}.
   *
   * @param project The project to obtain delete warnings for
   * @throws Exception
   * @return Collection of Strings, each holding a warning message to show
   *    to the user. If there are no warnings, return either null, or an empty
   *    Collection.
   */
  public Collection<String> getWarnings(Project project) throws Exception;

  /**
   * Deletes the project from the database.
   * <p>
   * This method expects that the caller called {@code assertCanDelete()} in
   * advance and no exceptions where thrown from this invocation.
   *
   * @param project The project that should get deleted.
   * @throws Exception
   */
  public void delete(Project project) throws Exception;
}
