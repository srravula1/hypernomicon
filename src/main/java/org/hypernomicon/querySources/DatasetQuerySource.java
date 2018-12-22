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

package org.hypernomicon.querySources;

import static org.hypernomicon.model.HyperDB.*;

import org.hypernomicon.model.records.HDT_Base;
import org.hypernomicon.model.records.HDT_RecordType;

public class DatasetQuerySource implements QuerySource
{
  private final HDT_RecordType type;
  
//---------------------------------------------------------------------------  
//--------------------------------------------------------------------------- 
  
  public DatasetQuerySource(HDT_RecordType type) { this.type = type; }

  public HDT_RecordType recordType()             { return type; }

  @Override public int count()                             { return db.records(type).size(); }
  @Override public QuerySourceType sourceType()            { return QuerySourceType.QST_recordsByType; }
  @Override public boolean containsRecord(HDT_Base record) { return record.getType() == type; }
  @Override public HDT_Base getRecord(int ndx)             { return db.records(type).getByIDNdx(ndx); }
  
//---------------------------------------------------------------------------  
//--------------------------------------------------------------------------- 

}