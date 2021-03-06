/*
 * Copyright 2015-2020 Jason Winning
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.hypernomicon.view.dialogs;

import static org.hypernomicon.util.Util.*;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;

public class NewQueryFavDlgCtrlr extends HyperDlg
{
  @FXML private TextField tfName;
  @FXML private CheckBox chkAutoExec;

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public String getNewName()   { return tfName.getText(); }
  public boolean getAutoExec() { return chkAutoExec.isSelected(); }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public static NewQueryFavDlgCtrlr create(String newName)
  {
    NewQueryFavDlgCtrlr nqf = HyperDlg.create("NewQueryFavDlg.fxml", "Add Query Favorite", true);
    nqf.init(newName);
    return nqf;
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  private void init(String newName)
  {
    tfName.setText(newName);

    onShown = () -> safeFocus(tfName);
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  @Override protected boolean isValid()
  {
    if (tfName.getText().length() > 0) return true;

    return falseWithErrorMessage("Name cannot be zero-length.", tfName);
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

}
