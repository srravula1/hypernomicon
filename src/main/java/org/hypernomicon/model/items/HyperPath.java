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

package org.hypernomicon.model.items;

import static org.hypernomicon.util.Util.*;
import static org.hypernomicon.util.Util.MessageDialogType.*;
import static org.hypernomicon.model.HyperDB.*;
import static org.hypernomicon.model.records.HDT_RecordType.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;

import org.hypernomicon.model.Exceptions.*;
import org.hypernomicon.model.records.HDT_Record;
import org.hypernomicon.model.records.HDT_Folder;
import org.hypernomicon.model.records.HDT_RecordState;
import org.hypernomicon.model.records.HDT_RecordType;
import org.hypernomicon.model.records.SimpleRecordTypes.HDT_RecordWithPath;
import org.hypernomicon.model.relations.HyperObjPointer;
import org.hypernomicon.util.filePath.FilePath;

public class HyperPath
{
  public static final HyperPath EmptyPath = new HyperPath(null);
  private final HyperObjPointer<? extends HDT_RecordWithPath, HDT_Folder> folderPtr;
  private final HDT_RecordWithPath record;
  private HDT_Folder folder = null;
  private FilePath fileName = null;

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public HyperPath(HyperObjPointer<? extends HDT_RecordWithPath, HDT_Folder> folderPtr, HDT_RecordWithPath record)
  {
    this.folderPtr = folderPtr;
    this.record = record;
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public HDT_RecordWithPath getRecord()     { return record; }
  public HDT_RecordType     getRecordType() { return record == null ? hdtNone : record.getType(); }
  public FilePath           getFileName()   { return fileName; }
  public HDT_Folder         parentFolder()  { return folderPtr == null ? folder : folderPtr.get(); }

  @Override public String toString() { return filePath().toString(); }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public HyperPath(FilePath filePath)
  {
    folderPtr = null;
    record = null;

    if (FilePath.isEmpty(filePath))
      return;

    if (db.getRootPath().isSubpath(filePath) == false)
    {
      messageDialog("Internal error: Hyperpath not in database folder tree", mtError);
      return;
    }

    if (HyperPath.getHyperPathSetForFilePath(filePath).size() > 0)
    {
      messageDialog("Internal error #90178", mtError);
      return;
    }

    folder = HyperPath.getFolderFromFilePath(filePath, false);
    assignNameInternal(filePath.getNameOnly());
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public boolean isNotEmpty() { return isEmpty() == false; }

  public boolean isEmpty()
  {
    if ((record != null) && (record.getType() == hdtFolder) && (record.getID() == ROOT_FOLDER_ID))
      return false;

    return FilePath.isEmpty(fileName);
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public String getNameStr()
  {
    if ((record != null) && (record.getType() == hdtFolder) && (record.getID() == ROOT_FOLDER_ID))
      return "Root";

    return fileName == null ? "" : fileName.getNameOnly().toString();
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public void clear() { clear(true); }

  public void clear(boolean deleteFile)
  {
    FilePath filePath = filePath();

    if (folderPtr != null)
      folderPtr.setID(-1);
    else
      folder = null;

    fileName = null;

    if (FilePath.isEmpty(filePath)   ||
        (filePath.exists() == false) ||
        (filePath.isFile() == false))   return;

    if (deleteFile && getHyperPathSetForFilePath(filePath).isEmpty())
      db.fileNoLongerInUse(filePath);
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public static Set<HyperPath> getHyperPathSetForFilePath(FilePath filePath)
  {
    return nullSwitch(db.filenameMap.get(filePath.getNameOnly().toString()), new HashSet<>(),
                      paths -> paths.stream().filter(path -> filePath.equals(path.filePath())).collect(Collectors.toSet()));
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public FilePath filePath()
  {
    if ((record != null) && (record.getType() == hdtFolder) && (record.getID() == ROOT_FOLDER_ID))
      return db.getRootPath();

    if (FilePath.isEmpty(fileName)) return null;

    return nullSwitch(parentFolder()   , fileName, folder   ->
           nullSwitch(folder.filePath(), fileName, folderFP -> folderFP.resolve(fileName)));
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public static HDT_Folder getFolderFromFilePath(FilePath dirFilePath, boolean doCreate)
  {
    dirFilePath = dirFilePath.getDirOnly();

    if (db.getRootPath().isSubpath(dirFilePath) == false)  // the path is not in the database folder tree
      return null;

    Set<HyperPath> set = getHyperPathSetForFilePath(dirFilePath);

    HDT_RecordWithPath folder = findFirst(set, hyperPath -> hyperPath.getRecordType() == hdtFolder, HyperPath::getRecord);
    if (folder != null) return (HDT_Folder) folder;

    if (dirFilePath.exists() == false) return null;

    HDT_Folder parentRecord = getFolderFromFilePath(dirFilePath.getParent(), doCreate);

    if ((parentRecord == null) || (doCreate == false)) return null;

    HDT_RecordState recordState = new HDT_RecordState(hdtFolder, -1, "", "", "", "");

    try
    {
      BasicFileAttributes attribs = Files.readAttributes(dirFilePath.toPath(), BasicFileAttributes.class);

      recordState.creationDate = attribs.creationTime    ().toInstant();
      recordState.modifiedDate = attribs.lastModifiedTime().toInstant();
      recordState.viewDate     = attribs.lastAccessTime  ().toInstant();
    }
    catch (IOException e) { noOp(); }

    HDT_Folder newFolder = null;

    try
    {
      newFolder = db.createNewRecordFromState(recordState, true);
    }
    catch (DuplicateRecordException | RelationCycleException | SearchKeyException | HubChangedException e)
    {
      noOp();
    }
    catch (HDB_InternalError e)
    {
      messageDialog(e.getMessage(), mtError);
      return null;
    }

    newFolder.getPath().assignInternal(parentRecord, dirFilePath.getNameOnly());

    return newFolder;
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public static HDT_RecordWithPath createRecordAssignedToPath(HDT_RecordType type, FilePath filePath)
  {
    if ((type != hdtWorkFile) && (type != hdtMiscFile))
    {
      messageDialog("Internal error #42221", mtError);
      return null;
    }

    if (filePath.isDirectory())
    {
      messageDialog("Internal error #42231", mtError);
      return null;
    }

    HDT_Folder folder = HyperPath.getFolderFromFilePath(filePath.getDirOnly(), true);
    if (folder == null) return null;

    HDT_RecordWithPath file = db.createNewBlankRecord(type);
    file.getPath().assign(folder, filePath.getNameOnly());

    return file;
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public static HDT_RecordWithPath getFileFromFilePath(FilePath filePath)
  {
    return findFirstHaving(getHyperPathSetForFilePath(filePath), HyperPath::getRecord, record ->
      (record.getType() == hdtMiscFile) || (record.getType() == hdtWorkFile));
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public boolean moveToFolder(int folderID, boolean confirm, boolean changeFilename, String newName) throws IOException
  {
    if ((folderID == -1) || (db.folders.getByID(folderID) == null))
      return falseWithErrorMessage("Internal error #77392");

    FilePath srcFilePath = filePath(), destFilePath;
    HDT_Folder newFolder = db.folders.getByID(folderID);

    if (srcFilePath.isDirectory())
      return falseWithErrorMessage("Internal error #77393");

    if (getRecordType() == hdtPerson)
      newFolder = parentFolder();

    Set<HyperPath> set = HyperPath.getHyperPathSetForFilePath(srcFilePath);

    destFilePath = newFolder.filePath().resolve(changeFilename ? new FilePath(newName) : srcFilePath.getNameOnly());

    if (srcFilePath.equals(destFilePath)) return true;

    if (srcFilePath.moveTo(destFilePath, confirm) == false) return false;

    db.unmapFilePath(srcFilePath);

    set.forEach(hyperPath -> hyperPath.assign(db.folders.getByID(folderID), destFilePath.getNameOnly()));

    return true;
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public void assign(HDT_Folder parentFolder, FilePath nameOnly)
  {
    if (FilePath.isEmpty(fileName) && FilePath.isEmpty(nameOnly)) return;

    if ((FilePath.isEmpty(fileName) == false) && (FilePath.isEmpty(nameOnly) == false))
      if ((parentFolder() == parentFolder) && (fileName.getNameOnly().equals(nameOnly.getNameOnly()))) return;

    clear();
    assignInternal(parentFolder, nameOnly);
    if (record != null) record.modifyNow();
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  void assignInternal(HDT_Folder parentFolder, FilePath nameOnly)
  {
    if (folderPtr == null)
    {
      if ((record != null) && (record.getType() != hdtPerson))
        messageDialog("Internal error #83902", mtError);

      folder = parentFolder;
    }
    else
      folderPtr.set(parentFolder);

    assignNameInternal(nameOnly);
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  void assignNameInternal(FilePath newFileName)
  {
    if (FilePath.isEmpty(fileName) == false)
      nullSwitch(db.filenameMap.get(fileName.toString()), set -> set.remove(this));

    if (FilePath.isEmpty(newFileName) == false)
    {
      newFileName = newFileName.getNameOnly();
      Set<HyperPath> set = db.filenameMap.get(newFileName.toString());
      if (set == null)
      {
        set = Sets.newConcurrentHashSet();
        db.filenameMap.put(newFileName.toString(), set);
      }

      set.add(this);
    }

    fileName = newFileName;

    if (record != null)
      record.updateSortKey();

    if (FilePath.isEmpty(fileName))
      return;

    // now remove duplicates

    db.filenameMap.get(fileName.getNameOnly().toString()).removeIf(path ->
    {
      return (path.isNotEmpty() &&
              path.filePath().equals(filePath()) && // for this to work, folder records have to be brought online first
              (path != this));
    });
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public String getRecordsString()
  {
    if (getRecord() == null) return "";

    StringBuilder val = new StringBuilder();

    if (parentFolder() == db.folders.getByID(PICTURES_FOLDER_ID))
    {
      HyperPath.getHyperPathSetForFilePath(filePath()).forEach(hyperPath ->
      {
        if (hyperPath.getRecordType() != hdtPerson) return;

        if (val.length() > 0) val.append("; ");
        val.append(db.getTypeName(hdtPerson) + ": " + hyperPath.getRecord().listName());
      });
    }

    LinkedHashSet<HDT_Record> set = new LinkedHashSet<>();
    db.getRelatives(getRecord(), set, 10);

    set.forEach(relative ->
    {
      if (relative.getType() == hdtFolder) return;

      if (val.length() > 0) val.append("; ");
      val.append(db.getTypeName(relative.getType()) + ": " + relative.listName());
    });

    if ((val.length() == 0) && (getRecordType() == hdtFolder))
    {
      for (HDT_Folder subFolder : HDT_Folder.class.cast(getRecord()).childFolders)
        if (subFolder.getPath().getRecordsString().length() > 0)
          return "(Subfolders have associated records)";
    }

    return val.toString();
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public static boolean renameFile(FilePath filePath, String newNameStr) throws IOException
  {
    Set<HyperPath> set = getHyperPathSetForFilePath(filePath);

    if (set.isEmpty())
      return filePath.renameTo(newNameStr);

    for (HyperPath hyperPath : set)
      if (hyperPath.moveToFolder(hyperPath.parentFolder().getID(), false, true, newNameStr) == false)
        return false;

    return true;
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

}
