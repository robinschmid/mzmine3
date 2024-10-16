/*
 * Copyright (c) 2004-2024 The MZmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.mzmine.taskcontrol;

import org.jetbrains.annotations.NotNull;

public class TaskService {

  private static TaskController INSTANCE;

  /**
   * Initialize the task controller instance
   * @param numThreads number of concurrent threads, usually num of cores
   * @return TaskController instance
   */
  @NotNull
  public static TaskController init(int numThreads) {
    if (INSTANCE == null) {
      INSTANCE = new TaskControllerImpl(numThreads);
      return INSTANCE;
    }
    return INSTANCE;
//    throw new IllegalStateException("Cannot initialize TaskController twice");
  }

  @NotNull
  public static TaskController getController() {
    if (INSTANCE == null) {
      INSTANCE = new TaskControllerImpl(8);
      return INSTANCE;
//      throw new IllegalStateException("Initialize TaskController first");
    }
    return INSTANCE;
  }

}
