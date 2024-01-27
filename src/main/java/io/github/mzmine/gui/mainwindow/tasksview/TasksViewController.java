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

package io.github.mzmine.gui.mainwindow.tasksview;

import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.taskcontrol.impl.TaskControllerImpl;
import javafx.scene.layout.Region;

public class TasksViewController {

  private final TasksViewModel model;
  private final TasksViewInteractor interactor;
  private final TasksView view;
  private final MiniTaskView miniView;

  public TasksViewController() {
    model = new TasksViewModel();
    interactor = new TasksViewInteractor(model);
    view = new TasksView(model);
    miniView = new MiniTaskView(model);

    // handle events
    model.setOnCancelAllTasks(interactor::cancelAllTasks);
    model.setOnCancelBatchTask(interactor::cancelBatchTasks);
    model.setOnShowTasksView(interactor::showTasksView);

    var readOnlyTasks = TaskControllerImpl.getInstance().getReadOnlyTasks();
    readOnlyTasks.addListener(interactor::onSubmittedTasksChanged);
  }

  /**
   * Thread safe operation running on the fx thread. Updates the tasks model
   */
  public void updateDataModel() {
    MZmineCore.runLater(interactor::updateDataModel);
  }

  public Region buildView() {
    return view.build();
  }

  public Region buildMiniView() {
    return miniView.build();
  }

}