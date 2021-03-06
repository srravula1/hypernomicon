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

import org.hypernomicon.util.filePath.FilePath;

import static org.hypernomicon.App.*;
import static org.hypernomicon.util.Util.*;

import java.util.List;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.layout.AnchorPane;

public class WelcomeDlgCtrlr extends HyperDlg
{
  @FXML private Button btnNew, btnClose;
  @FXML private Hyperlink linkIntroVideo, linkFileMgmtVideo, linkForums, linkWiki, linkNews, linkMore;
  @FXML private AnchorPane apRecent;

  private boolean newClicked = false, openClicked = false;
  private FilePath openPath = null;

  public static WelcomeDlgCtrlr create()
  {
    WelcomeDlgCtrlr wdc = HyperDlg.create("WelcomeDlg.fxml", "Welcome - " + appTitle, false);
    wdc.init();
    return wdc;
  }

  public boolean newClicked()   { return newClicked ; }
  public boolean openClicked()  { return openClicked; }

  public FilePath getOpenPath() { return openPath; }

  @Override protected boolean isValid() { return true; }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  private void init()
  {
    btnNew.setOnAction(event ->
    {
      newClicked = true;
      btnOkClick();
    });

    linkMore.setOnAction(event ->
    {
      openClicked = true;
      btnOkClick();
    });

    linkIntroVideo   .setOnAction(event -> openWebLink("http://hypernomicon.org/support.html"));
    linkFileMgmtVideo.setOnAction(event -> openWebLink("http://hypernomicon.org/support.html"));
    linkForums       .setOnAction(event -> openWebLink("https://sourceforge.net/p/hypernomicon/discussion/"));
    linkWiki         .setOnAction(event -> openWebLink("https://sourceforge.net/p/hypernomicon/wiki/Home/"));
    linkNews         .setOnAction(event -> openWebLink("https://sourceforge.net/p/hypernomicon/news/"));

    List<String> mruList = ui.getHdbMRUs();

    double layoutY = 3;
    for (String mru : mruList)
    {
      if (mru.isBlank()) continue;

      String mruCaption = mru.length() <= 50 ? mru : mru.substring(0, 30) + "..." + mru.substring(mru.length() - 20, mru.length());

      Hyperlink hl = new Hyperlink(mruCaption);
      apRecent.getChildren().add(hl);
      hl.setPrefWidth(386.0);
      hl.setLayoutX(6.0);
      hl.setLayoutY(layoutY);
      layoutY = layoutY + 15.0;

      hl.setOnAction(event ->
      {
        openClicked = true;
        openPath = new FilePath(mru);
        btnOkClick();
      });
    }
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

}
