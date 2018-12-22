/*
 * Copyright 2015-2018 Jason Winning
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

package org.hypernomicon.view.workMerge;

import static org.hypernomicon.util.Util.*;

import org.hypernomicon.bib.BibData;
import org.hypernomicon.bib.BibData.BibFieldEnum;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;

public class MergeWorksMLController extends BibFieldRow
{
  @FXML private RadioButton rb1;
  @FXML private RadioButton rb2;
  @FXML private RadioButton rb3;
  @FXML private RadioButton rb4;

  @FXML private TextArea ta1;
  @FXML private TextArea ta2;
  @FXML private TextArea ta3;
  @FXML private TextArea ta4;
  
  @FXML private GridPane gp;
  
  @FXML private Label lbl;

//---------------------------------------------------------------------------  
//---------------------------------------------------------------------------  

  @Override public void mergeInto(BibData bd)
  {
    String str;
    
    if      (rb1.isSelected()) str = ta1.getText();
    else if (rb2.isSelected()) str = ta2.getText();
    else if (rb3.isSelected()) str = ta3.getText();
    else                       str = ta4.getText();
    
    bd.setMultiStr(bibFieldEnum, convertMultiLineStrToStrList(str, false));
  }
  
//---------------------------------------------------------------------------  
//---------------------------------------------------------------------------  

  @Override protected void init(BibFieldEnum bibFieldEnum, AnchorPane ap, BibData bd1, BibData bd2, BibData bd3, BibData bd4)
  {
    this.ap = ap;
    this.bibFieldEnum = bibFieldEnum;
    
    lbl.setText(BibData.getFieldName(bibFieldEnum));
    
    if (bd4 == null)
      deleteGridPaneColumn(gp, 3);
    else if (bd4.fieldNotEmpty(bibFieldEnum))
    {
      ta4.setText(strListToStr(bd4.getMultiStr(bibFieldEnum), true));
      rb4.setSelected(true);
    }
    
    if (bd3 == null)
      deleteGridPaneColumn(gp, 2);
    else if (bd3.fieldNotEmpty(bibFieldEnum))
    {
      ta3.setText(strListToStr(bd3.getMultiStr(bibFieldEnum), true));
      rb3.setSelected(true);      
    }
    
    if (bd2.fieldNotEmpty(bibFieldEnum))
    {
      ta2.setText(strListToStr(bd2.getMultiStr(bibFieldEnum), true));
      rb2.setSelected(true);      
    }
    
    if (bd1.fieldNotEmpty(bibFieldEnum))
    {
      ta1.setText(strListToStr(bd1.getMultiStr(bibFieldEnum), true));
      rb1.setSelected(true);      
    }    
  }

//---------------------------------------------------------------------------  
//---------------------------------------------------------------------------  

}
