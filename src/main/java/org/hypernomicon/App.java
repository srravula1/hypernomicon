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

package org.hypernomicon;

import static org.hypernomicon.model.HyperDB.*;
import static org.hypernomicon.model.records.HDT_RecordType.*;
import static org.hypernomicon.Const.*;
import static org.hypernomicon.util.Util.*;
import static org.hypernomicon.util.Util.MessageDialogType.*;
import static org.hypernomicon.view.tabs.HyperTab.TabEnum.*;
import static org.hypernomicon.view.tabs.HyperTab.*;

import org.hypernomicon.bib.BibManager;
import org.hypernomicon.model.Exceptions.*;
import org.hypernomicon.model.records.*;
import org.hypernomicon.util.AsyncHttpClient;
import org.hypernomicon.util.JsonHttpClient;
import org.hypernomicon.util.VersionNumber;
import org.hypernomicon.util.filePath.FilePath;
import org.hypernomicon.util.json.JsonObj;
import org.hypernomicon.view.MainCtrlr;
import org.hypernomicon.view.dialogs.NewVersionDlgCtrlr;
import org.hypernomicon.view.fileManager.FileManager;
import org.hypernomicon.view.mainText.MainTextWrapper;
import org.hypernomicon.view.previewWindow.ContentsWindow;
import org.hypernomicon.view.previewWindow.PDFJSWrapper;
import org.hypernomicon.view.previewWindow.PreviewWindow;
import org.hypernomicon.view.tabs.HyperTab;
import org.hypernomicon.view.tabs.QueryTabCtrlr.QueryView;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.Socket;

import static java.lang.management.ManagementFactory.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;

import com.teamdev.jxbrowser.chromium.BrowserPreferences;
import com.teamdev.jxbrowser.chromium.internal.Environment;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;

//---------------------------------------------------------------------------

/**
 * Main application class for Hypernomicon
 *
 * @author  Jason Winning
 * @since   1.0
 */
public final class App extends Application
{
  private Stage primaryStage;
  private VersionNumber version;
  private boolean testMainTextEditing = false;
  private double deltaX;
  private long swipeStartTime;
  private static boolean isDebugging;
  private static final double baseDisplayScale = 81.89306640625;
  private static int total, ctr, lastPercent;

  public static App app;
  public static BibManager bibManagerDlg = null;
  public static ContentsWindow contentsWindow = null;
  public static FileManager fileManagerDlg = null;
  public static MainCtrlr ui;
  public static Preferences appPrefs;
  public static PreviewWindow previewWindow = null;
  public static QueryView curQV;
  public static TikaConfig tika;
  public static boolean jxBrowserInitialized = false,
                        jxBrowserDisabled    = false;
  public static double displayScale;
  public static final FolderTreeWatcher folderTreeWatcher = new FolderTreeWatcher();
  public static final String appTitle = "Hypernomicon";

  public Stage getPrimaryStage()    { return primaryStage; }
  public boolean debugging()        { return isDebugging; }
  public VersionNumber getVersion() { return version; }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  @Override public void init()
  {
    app = this;

    Logger.getLogger("org.apache").setLevel(Level.WARN);
    BasicConfigurator.configure();

    String rtArgs = getRuntimeMXBean().getInputArguments().toString();
    isDebugging = rtArgs.contains("-agentlib:jdwp") || rtArgs.contains("-Xrunjdwp");

    try (Socket clientSocket = new Socket("localhost", InterProcDaemon.PORT);
         PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
         BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream())))
    {
      List<String> args = getParameters().getUnnamed();
      out.println(args.size());
      args.forEach(out::println);
      for (String line = null; line == null; line = in.readLine());
      Platform.exit();
      return;
    }
    catch (ConnectException e) { new InterProcDaemon().start(); }
    catch (IOException e)      { Platform.exit(); return; }

    BrowserPreferences.setChromiumSwitches("--disable-web-security", "--user-data-dir", "--allow-file-access-from-files", "--enable-local-file-accesses");

    // On Mac OS X Chromium engine must be initialized in non-UI thread.
    if (Environment.isMac()) PDFJSWrapper.init();

    try
    {
      tika = new TikaConfig();

      appPrefs = Preferences.userNodeForPackage(App.class);
      db.init(appPrefs, folderTreeWatcher);
    }
    catch (SecurityException | HDB_InternalError | TikaException | IOException e)
    {
      appPrefs = null;
      messageDialog("Initialization error: " + e.getMessage(), mtError, true);

      Platform.exit();
      return;
    }

    //db.viewTestingInProgress = true;
    //testMainTextEditing = true;
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  @Override public void start(Stage primaryStage)
  {
    this.primaryStage = primaryStage;

    primaryStage.setTitle(appTitle);

    if (appPrefs == null)
    {
      Platform.exit();
      return;
    }

    try
    {
      initMainWindows();
    }
    catch(IOException e)
    {
      messageDialog("Initialization error: " + e.getMessage(), mtError);

      if (ui != null)
        ui.shutDown(false, false, false);
      else
        Platform.exit();

      return;
    }

    String versionStr = manifestValue("Impl-Version");

    if (safeStr(versionStr).isEmpty())
      versionStr = "1.17.2";

    version = new VersionNumber(2, versionStr);

    boolean hdbExists = false;
    String srcName = appPrefs.get(PREF_KEY_SOURCE_FILENAME, "");
    if (srcName.isBlank() == false)
    {
      String srcPath = appPrefs.get(PREF_KEY_SOURCE_PATH, "");
      if (srcPath.isBlank() == false)
      {
        FilePath hdbPath = new FilePath(srcPath).resolve(srcName);
        if (hdbPath.exists())
          hdbExists = true;
      }
    }

    List<String> args = new ArrayList<>(getParameters().getUnnamed());

    if (args.size() > 0)
    {
      FilePath filePath = new FilePath(args.get(0));

      if (filePath.getExtensionOnly().equalsIgnoreCase("hdb"))
      {
        appPrefs.put(PREF_KEY_SOURCE_FILENAME, filePath.getNameOnly().toString());
        appPrefs.put(PREF_KEY_SOURCE_PATH, filePath.getDirOnly().toString());
        hdbExists = true;
        args.remove(0);
      }
    }

    if (hdbExists) ui.loadDB();
    else           ui.startEmpty();

    if (args.size() > 0)
    {
      ui.handleArgs(args);
      return;
    }

    if (appPrefs.getBoolean(PREF_KEY_CHECK_FOR_NEW_VERSION, true)) checkForNewVersion(new AsyncHttpClient(), newVersion ->
    {
      if (newVersion.compareTo(app.getVersion()) > 0)
        NewVersionDlgCtrlr.create().showModal();
    }, e -> noOp());

    if (db.viewTestingInProgress && hdbExists)
      testUpdatingAllRecords();
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public static void checkForNewVersion(AsyncHttpClient httpClient, Consumer<VersionNumber> successHndlr, Consumer<Exception> failHndlr)
  {
    JsonHttpClient.getArrayAsync("https://api.github.com/repos/jasonwinning/hypernomicon/releases", httpClient, jsonArray ->
    {
      VersionNumber updateNum =  app.version;
      Pattern p = Pattern.compile("(\\A|\\D)(\\d(\\d|(\\.\\d))+)(\\z|\\D)");

      for (JsonObj jsonObj : jsonArray.getObjs())
      {
        if (jsonObj.getBoolean("prerelease", false) == false)
        {
          Matcher m = p.matcher(jsonObj.getStrSafe("tag_name"));

          if (m.find())
          {
            VersionNumber curNum = new VersionNumber(2, m.group(2));
            if (curNum.compareTo(updateNum) > 0)
              updateNum = curNum;
          }
        }
      }

      successHndlr.accept(updateNum);
    }, failHndlr::accept);
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  private void testUpdatingAllRecords()
  {
    List<HDT_RecordType> types = List.of(hdtPerson,   hdtInstitution, hdtInvestigation, hdtDebate,   hdtPosition,
                                         hdtArgument, hdtWork,        hdtTerm,          hdtMiscFile, hdtNote);

    total = 0; ctr = 0; lastPercent = 0;
    types.forEach(type -> total += db.records(type).size());

    types.forEach(this::testUpdatingRecords);
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  private void testUpdatingRecords(HDT_RecordType type)
  {
    db.records(type).forEach(record ->
    {
      ui.goToRecord(record, true);

      if (testMainTextEditing)
      {
        MainTextWrapper mainText;

        if (record.getType() == hdtInvestigation)
          mainText = ui.personHyperTab().getInvMainTextWrapper(record.getID());
        else
          mainText = ui.activeTab().getMainTextWrapper();

        if (mainText != null)
          mainText.beginEditing(false);
      }

      int curPercent = (ctr++ * 100) / total;

      if (curPercent > lastPercent)
      {
        System.out.println("Progress: " + curPercent + " %");
        lastPercent = curPercent;
      }
    });
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  private void initMainWindows() throws IOException
  {
    Application.setUserAgentStylesheet(STYLESHEET_MODENA);

    FXMLLoader loader = new FXMLLoader(App.class.getResource("view/Main.fxml"));
    Region rootNode = loader.load();

    ui = loader.getController();
    ui.init();

    Scene scene = new Scene(rootNode);

    scene.getStylesheets().add(App.class.getResource("resources/css.css").toExternalForm());

    scene.setOnKeyPressed(event -> { if (event.getCode() == KeyCode.ESCAPE)
    {
      ui.hideFindTable();
      event.consume();
    }});

    KeyCombination keyComb = new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN);
    scene.addEventHandler(KeyEvent.KEY_PRESSED, event ->
    {
      if (keyComb.match(event))
        ui.omniFocus();
    });

    scene.setOnScrollStarted(event ->
    {
      swipeStartTime = System.currentTimeMillis();
      deltaX = event.getDeltaX();
    });

    scene.setOnScroll(event -> deltaX = deltaX + event.getDeltaX());

    scene.setOnScrollFinished(event ->
    {
      double swipeTime = System.currentTimeMillis() - swipeStartTime;

      if (swipeTime < 200)
      {
        if      (deltaX >  500) ui.btnBackClick();
        else if (deltaX < -500) ui.btnForwardClick();
      }
    });

    scene.addEventFilter(DragEvent.DRAG_OVER, event ->
    {
      if (event.getDragboard().hasContent(HYPERNOMICON_DATA_FORMAT))
        return;

      if (event.getDragboard().hasFiles())
        event.acceptTransferModes(TransferMode.MOVE);

      event.consume();
    });

    scene.addEventFilter(DragEvent.DRAG_DROPPED, event ->
    {
      if (event.getDragboard().hasContent(HYPERNOMICON_DATA_FORMAT))
        return;

      Dragboard board = event.getDragboard();

      if (board.hasImage())
        if (isDebugging)
          System.out.println("has image");

      if (board.hasFiles())
      {
        List<String> args = board.getFiles().stream().map(File::getAbsolutePath).collect(Collectors.toList());
        Platform.runLater(() -> ui.handleArgs(args));
        event.setDropCompleted(true);
      }

      event.consume();
    });

    primaryStage.setScene(scene);

    primaryStage.getIcons().addAll(Stream.of("16x16", "32x32", "48x48", "64x64", "128x128", "256x256")
                                         .map(str -> new Image(App.class.getResourceAsStream("resources/images/logo-" + str + ".png")))
                                         .collect(Collectors.toList()));
    ui.hideFindTable();

    initScaling(rootNode);

    double  x          = appPrefs.getDouble (PREF_KEY_WINDOW_X,          primaryStage.getX()),
            y          = appPrefs.getDouble (PREF_KEY_WINDOW_Y,          primaryStage.getY()),
            width      = appPrefs.getDouble (PREF_KEY_WINDOW_WIDTH,      primaryStage.getWidth()),
            height     = appPrefs.getDouble (PREF_KEY_WINDOW_HEIGHT,     primaryStage.getHeight());
    boolean fullScreen = appPrefs.getBoolean(PREF_KEY_WINDOW_FULLSCREEN, primaryStage.isFullScreen()),
            maximized  = appPrefs.getBoolean(PREF_KEY_WINDOW_MAXIMIZED,  primaryStage.isMaximized());

    primaryStage.setX(x); // set X and Y first so that window gets full-screened or
    primaryStage.setY(y); // maximized onto the correct monitor if there are more than one

    if      (fullScreen) primaryStage.setFullScreen(true);
    else if (maximized)  primaryStage.setMaximized(true);
    else
    {
      primaryStage.setWidth(width);
      primaryStage.setHeight(height);

      ensureVisible(primaryStage, rootNode.getPrefWidth(), rootNode.getPrefHeight());
    }

    primaryStage.show();

    scaleNodeForDPI(rootNode);
    MainTextWrapper.rescale();
    getHyperTab(personTabEnum).rescale();

    forEachHyperTab(HyperTab::setDividerPositions);

    bibManagerDlg = BibManager.create();

    bibManagerDlg.getStage().setX(appPrefs.getDouble(PREF_KEY_BM_WINDOW_X, bibManagerDlg.getStage().getX()));
    bibManagerDlg.getStage().setY(appPrefs.getDouble(PREF_KEY_BM_WINDOW_Y, bibManagerDlg.getStage().getY()));

    bibManagerDlg.setInitHeight(PREF_KEY_BM_WINDOW_HEIGHT);
    bibManagerDlg.setInitWidth(PREF_KEY_BM_WINDOW_WIDTH);

    db.addBibChangedHandler(() ->
    {
      bibManagerDlg.setLibrary(db.getBibLibrary());

      if ((db.bibLibraryIsLinked() == false) && bibManagerDlg.getStage().isShowing())
        bibManagerDlg.getStage().close();

      ui.updateBibImportMenus();

      if (db.isLoaded())
        ui.update();
    });

    fileManagerDlg = FileManager.create();

    fileManagerDlg.getStage().setX(appPrefs.getDouble(PREF_KEY_FM_WINDOW_X, fileManagerDlg.getStage().getX()));
    fileManagerDlg.getStage().setY(appPrefs.getDouble(PREF_KEY_FM_WINDOW_Y, fileManagerDlg.getStage().getY()));

    fileManagerDlg.setInitHeight(PREF_KEY_FM_WINDOW_HEIGHT);
    fileManagerDlg.setInitWidth(PREF_KEY_FM_WINDOW_WIDTH);

    previewWindow = PreviewWindow.create();

    previewWindow.getStage().setX(appPrefs.getDouble(PREF_KEY_PREV_WINDOW_X, previewWindow.getStage().getX()));
    previewWindow.getStage().setY(appPrefs.getDouble(PREF_KEY_PREV_WINDOW_Y, previewWindow.getStage().getY()));

    previewWindow.setInitWidth(PREF_KEY_PREV_WINDOW_WIDTH);
    previewWindow.setInitHeight(PREF_KEY_PREV_WINDOW_HEIGHT);

    contentsWindow = ContentsWindow.create();

    contentsWindow.getStage().setX(appPrefs.getDouble(PREF_KEY_CONTENTS_WINDOW_X, contentsWindow.getStage().getX()));
    contentsWindow.getStage().setY(appPrefs.getDouble(PREF_KEY_CONTENTS_WINDOW_Y, contentsWindow.getStage().getY()));

    contentsWindow.setInitWidth(PREF_KEY_CONTENTS_WINDOW_WIDTH);
    contentsWindow.setInitHeight(PREF_KEY_CONTENTS_WINDOW_HEIGHT);
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  private void initScaling(Region rootNode)
  {
    setFontSize(rootNode);

    Text text = new Text("Mac @Wow Cem");
    double fontSize = appPrefs.getDouble(PREF_KEY_FONT_SIZE, DEFAULT_FONT_SIZE);
    if (fontSize > 0)
      text.setFont(new Font(fontSize));

    displayScale = text.getLayoutBounds().getWidth() / baseDisplayScale;
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

}
