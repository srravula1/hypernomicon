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

package org.hypernomicon.view.populators;

import static org.hypernomicon.model.HyperDB.*;
import static org.hypernomicon.util.Util.*;
import static org.hypernomicon.view.populators.Populator.CellValueType.*;

import java.util.HashMap;
import java.util.List;

import org.hypernomicon.model.records.HDT_Base;
import org.hypernomicon.model.records.HDT_RecordType;
import org.hypernomicon.model.relations.RelationSet.RelationType;
import org.hypernomicon.view.wrappers.HyperTableCell;
import org.hypernomicon.view.wrappers.HyperTableRow;

public class HybridSubjectPopulator extends Populator
{
  private final StandardPopulator standardPop;
  private final SubjectPopulator subjPop;
  private final HashMap<HyperTableRow, Populator> rowToPop;
  private final HashMap<HyperTableRow, Boolean> rowToChanged;
  private final RelationType relType;

//---------------------------------------------------------------------------  

  @Override public CellValueType getValueType()                    { return cvtRecord; }
  @Override public HDT_RecordType getRecordType(HyperTableRow row) { return db.getSubjType(relType); }

//---------------------------------------------------------------------------  
//---------------------------------------------------------------------------   
  public HybridSubjectPopulator(RelationType relType)
  {
    rowToChanged = new HashMap<>();
    rowToPop = new HashMap<>();
    
    standardPop = new StandardPopulator(db.getSubjType(relType));
    subjPop = new SubjectPopulator(relType, true);
    
    this.relType = relType;
  }

//---------------------------------------------------------------------------  
//---------------------------------------------------------------------------   

  public HDT_Base getObj(HyperTableRow row)
  {
    if (row == null) row = dummyRow;
    
    if (rowToPop.get(row) == subjPop) return subjPop.getObj(row);
    
    return null;
  }

//---------------------------------------------------------------------------  
//---------------------------------------------------------------------------   

  public void setObj(HyperTableRow row, HDT_Base obj)
  {
    if (row == null) row = dummyRow;
    
    rowToPop.putIfAbsent(row, standardPop);
    
    if (obj == null)
    {
      if (rowToPop.get(row) != standardPop)
      {
        rowToPop.put(row, standardPop);
        rowToChanged.put(row, true);
      }
    }
    else
    {
      subjPop.setObj(row, obj);
      
      if (rowToPop.get(row) == subjPop)
        rowToChanged.put(row, subjPop.hasChanged(row));
      else
      {
        rowToChanged.put(row, true);
        rowToPop.put(row, subjPop);
      }
    }  
  }

//---------------------------------------------------------------------------  
//---------------------------------------------------------------------------   

  @Override public List<HyperTableCell> populate(HyperTableRow row, boolean force)
  {
    if (row == null) row = dummyRow;
    
    rowToChanged.put(row, false);
    
    rowToPop.putIfAbsent(row, standardPop);
    return rowToPop.get(row).populate(row, force);
  }
  
//---------------------------------------------------------------------------  
//---------------------------------------------------------------------------  

  @Override public HyperTableCell match(HyperTableRow row, HyperTableCell cell)
  {
    return populate(nullSwitch(row, dummyRow), false).contains(cell) ?  cell.clone() : null;
  }

//---------------------------------------------------------------------------  
//---------------------------------------------------------------------------   

  @Override public boolean hasChanged(HyperTableRow row)
  {
    if (row == null) row = dummyRow;
    
    rowToChanged.putIfAbsent(row, true);
    rowToPop.putIfAbsent(row, standardPop);
    
    return rowToChanged.get(row).booleanValue() ? true : rowToPop.get(row).hasChanged(row);
  }

//---------------------------------------------------------------------------  
//---------------------------------------------------------------------------   

  @Override public void setChanged(HyperTableRow row)
  {
    if (row == null) row = dummyRow;
    
    rowToChanged.put(row, true);
    rowToPop.putIfAbsent(row, standardPop);
    rowToPop.get(row).setChanged(row);
  }
  
//---------------------------------------------------------------------------  
//---------------------------------------------------------------------------   

  @Override public void clear()
  {
    rowToChanged.clear();
    rowToPop.clear();
    
    subjPop.clear();
    standardPop.clear();
  }

//---------------------------------------------------------------------------  
//---------------------------------------------------------------------------   

  @Override public HyperTableCell addEntry(HyperTableRow row, int id, String value)
  {
    if (row == null) row = dummyRow;
    
    standardPop.addEntry(row, id, value);
    return subjPop.addEntry(row, id, value);
  }

}
