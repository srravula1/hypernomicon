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

import org.hypernomicon.model.HDI_Schema;
import org.hypernomicon.model.HyperDB.Tag;
import org.hypernomicon.model.records.HDT_Folder;
import org.hypernomicon.model.records.HDT_RecordState;
import org.hypernomicon.model.records.HDT_RecordType;
import org.hypernomicon.model.relations.RelationSet.RelationType;

import static org.hypernomicon.model.records.HDT_RecordState.*;

import java.util.Map;

import static org.hypernomicon.model.records.HDT_RecordType.*;
import static org.hypernomicon.model.HyperDB.*;

public class HDI_OfflinePath extends HDI_OfflineBase
{
  int folderID = -1;
  String fileName = "";
  final RelationType relType;

  public HDI_OfflinePath(HDI_Schema newSchema, HDT_RecordState recordState)
  {
    super(newSchema, recordState);
    relType = schema.getRelType();
  }

  public void setFileName(String fileName) { this.fileName = fileName; }
  public void setFolderID(int folderID)    { this.folderID = folderID; }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  @Override public void setFromXml(Tag tag, String nodeText, HDT_RecordType objType, int objID, Map<Tag, HDI_OfflineBase> nestedItems)
  {
    if ((tag == Tag.tagParentFolder) || (tag == Tag.tagFolder))
      folderID = objID;
    else
      fileName = nodeText;
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  @Override public void writeToXml(Tag tag, StringBuilder xml)
  {
    String text = "";

    switch (tag)
    {
      case tagFolder : case tagParentFolder :

        HDT_Folder folder = db.folders.getByID(folderID);
        if (folder != null)
          text = folder.getXMLObjectName();

        writePointerTag(xml, tag, folderID, hdtNone, text);
        return;

      default :

        writeStringTag(xml, tag, fileName);
    }
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

}
