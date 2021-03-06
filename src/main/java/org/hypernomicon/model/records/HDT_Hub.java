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

package org.hypernomicon.model.records;

import static org.hypernomicon.model.HyperDB.*;
import static org.hypernomicon.model.HyperDB.Tag.*;
import static org.hypernomicon.util.Util.*;
import static org.hypernomicon.util.Util.MessageDialogType.*;

import org.hypernomicon.model.HyperDataset;
import org.hypernomicon.model.Exceptions.SearchKeyException;

public class HDT_Hub extends HDT_RecordWithConnector
{
//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public HDT_Hub(HDT_RecordState xmlState, HyperDataset<HDT_Hub> dataset)
  {
    super(xmlState, dataset, tagName);
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  @Override public String listName() { return name(); }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  @Override public final void setSearchKey(String newKey, boolean noMod, boolean dontRebuildMentions) throws SearchKeyException
  {
    if (newKey.length() > 0)
      messageDialog("Internal error #72950", mtError);
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  @Override public void expire()
  {
    getMainText().getKeyWorks().forEach(keyWork -> db.handleKeyWork(this, keyWork.getRecord(), false));

    super.expire();
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

}
