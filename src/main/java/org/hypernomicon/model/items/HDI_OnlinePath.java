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

import org.hypernomicon.model.Exceptions.HDB_InternalError;
import org.hypernomicon.model.Exceptions.RelationCycleException;

import static org.hypernomicon.model.HyperDB.*;
import static org.hypernomicon.model.relations.RelationSet.RelationType.rtNone;
import static org.hypernomicon.model.records.HDT_RecordType.*;

import java.util.List;

import org.hypernomicon.model.HDI_Schema;
import org.hypernomicon.model.HyperDB.Tag;
import org.hypernomicon.model.records.HDT_Record;
import org.hypernomicon.model.records.HDT_Folder;
import org.hypernomicon.model.records.SimpleRecordTypes.HDT_RecordWithPath;
import org.hypernomicon.model.relations.HyperObjList;
import org.hypernomicon.model.relations.RelationSet.RelationType;
import org.hypernomicon.util.filePath.FilePath;

public class HDI_OnlinePath extends HDI_OnlineBase<HDI_OfflinePath>
{
  private HyperPath hyperPath;
  private final RelationType relType;

//---------------------------------------------------------------------------

  public HDI_OnlinePath(HDI_Schema newSchema, HDT_RecordWithPath newRecord)
  {
    super(newSchema, newRecord);

    relType = schema.getRelType();
    initPath();
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  private void initPath()
  {
    hyperPath = HDT_RecordWithPath.class.cast(record).getPath();

    if ((hyperPath == null) || (record.getType() != hdtPerson))
      return;

    HDT_Folder folder = db.folders.getByID(PICTURES_FOLDER_ID);
    FilePath fileName = hyperPath.getFileName();
    hyperPath.assignInternal(folder, fileName); // It is okay if the hyperPath.fileName is null. Then this line just assigns the hyperPath to
  }                                             // point to the pictures folder. That is necessary when the database is first being brought "online".

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  @Override public void setFromOfflineValue(HDI_OfflinePath val, Tag tag) throws RelationCycleException
  {
    switch (tag)
    {
      case tagParentFolder : case tagFolder :

        HyperObjList<HDT_Record, HDT_Record> objList = db.getObjectList(relType, record, false);
        objList.clear();

        if (val.folderID > 0)
        {
          HDT_Folder folder = db.folders.getByID(val.folderID);
          if (folder != null)
          {
            objList.add(folder);
            objList.throwLastException();
          }
        }

        break;

      default :

        initPath();

        if (hyperPath == null) return;
        if (hyperPath.isEmpty() && val.fileName.isEmpty()) return;

        hyperPath.assignNameInternal(new FilePath(val.fileName));
    }
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  @Override public void resolvePointers() throws HDB_InternalError
  {
    if (relType != rtNone)
      db.resolvePointersByRelation(relType, record);
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  @Override public void getStrings(List<String> list, Tag tag, boolean searchLinkedRecords)
  {
    if (hyperPath.isNotEmpty())
      list.add(hyperPath.getFileName().getNameOnly().toString());
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  @Override public String getResultTextForTag(Tag tag)
  {
    if (hyperPath.isEmpty()) return "";

    switch (tag)
    {
      case tagFolder :

        if (hyperPath.filePath().isDirectory())
          return hyperPath.getNameStr();

        // now it should fall through to tagParentFolder case

      case tagParentFolder :
        if (hyperPath.parentFolder() == null)
          return "";

        return hyperPath.parentFolder().getPath().getNameStr();

      default :
        return hyperPath.getNameStr();
    }
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  @Override public void getToOfflineValue(HDI_OfflinePath val, Tag tag)
  {
    val.folderID = -1;
    val.fileName = "";

    if (hyperPath.isEmpty()) return;

    HDT_Folder parentFolder = hyperPath.parentFolder();

    if (parentFolder != null)
      val.folderID = parentFolder.getID();

    FilePath fileName = hyperPath.getFileName();

    if (fileName != null)
      val.fileName = fileName.getNameOnly().toString();
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

}
