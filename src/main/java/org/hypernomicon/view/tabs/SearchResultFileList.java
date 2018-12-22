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

package org.hypernomicon.view.tabs;

import static org.hypernomicon.model.HyperDB.*;
import static org.hypernomicon.Const.*;
import static org.hypernomicon.util.Util.*;
import static org.hypernomicon.util.Util.MessageDialogType.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import org.apache.commons.io.FilenameUtils;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.multipdf.PDFCloneUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;

import org.hypernomicon.HyperTask;
import org.hypernomicon.model.Exceptions.TerminateTaskException;
import org.hypernomicon.model.items.Author;
import org.hypernomicon.model.records.SimpleRecordTypes.HDT_RecordWithPath;
import org.hypernomicon.model.records.HDT_Work;
import org.hypernomicon.model.records.HDT_WorkFile;
import org.hypernomicon.util.filePath.FilePath;

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

public class SearchResultFileList
{
  public static class SearchResultFile
  {
    private final FilePath filePath;
    private int startPage, endPage;
    
  //---------------------------------------------------------------------------
  //---------------------------------------------------------------------------

    public SearchResultFile(FilePath filePath, int startPage, int endPage)
    {
      this.filePath = filePath;
      
      if (startPage < 1) startPage = 1;
      if (endPage < 1)   endPage = Integer.MAX_VALUE;
      
      if (endPage < startPage)
      {       
        int x = endPage;
        endPage = startPage;
        startPage = x;
      }
      
      this.startPage = startPage;
      this.endPage = endPage;
    }

  //---------------------------------------------------------------------------
  //---------------------------------------------------------------------------

    public boolean overlaps(SearchResultFile other)
    {
      if (filePath.equals(other.filePath) == false)
        return false;
            
      if (endPage < other.startPage) return false;
      if (other.endPage < startPage) return false;
      return true;
    }

  //---------------------------------------------------------------------------
  //---------------------------------------------------------------------------

    public boolean contains(SearchResultFile other)
    {
      if (filePath.equals(other.filePath) == false)
        return false;

      if ((startPage <= other.startPage) && (endPage >= other.endPage)) return true;
      if ((other.startPage <= startPage) && (other.endPage >= endPage)) return true;
      return false;
    }

    //---------------------------------------------------------------------------
    //---------------------------------------------------------------------------

    public SearchResultFile createCombined(SearchResultFile other)
    {
      int newStartPage, newEndPage;
      
      if (startPage < other.startPage)
        newStartPage = startPage;
      else
        newStartPage = other.startPage;
      
      if (endPage > other.endPage)
        newEndPage = endPage;
      else
        newEndPage = other.endPage;
      
      return new SearchResultFile(filePath, newStartPage, newEndPage);
    }

    //---------------------------------------------------------------------------
    //---------------------------------------------------------------------------

    private static boolean hasAnnotations(PDDocument pdf) throws IOException
    {
      int numPages = pdf.getNumberOfPages();
      for (int curPageNdx = 0; curPageNdx < numPages; curPageNdx++)
      {
        PDPage page = pdf.getPage(curPageNdx);
  
        for (PDAnnotation annotation : page.getAnnotations())
          if ((annotation.getSubtype().equals("Link") == false) && (annotation.getSubtype().equals("Widget") == false))
            return true;
      }
      
      return false;
    }

  //---------------------------------------------------------------------------
  //---------------------------------------------------------------------------

    private static FilePath getDestPath(FilePath filePath)
    {
      FilePath destFilePath = db.getPath(PREF_KEY_RESULTS_PATH, filePath.getNameOnly());
      String destStr = destFilePath.toString(),
             baseStr = FilenameUtils.removeExtension(destStr),
             ext = FilenameUtils.EXTENSION_SEPARATOR_STR + filePath.getExtensionOnly();
     
      int num = 1001;
      
      while (destFilePath.exists())
      {
        destStr = baseStr + "_" + String.valueOf(num).substring(1) + ext;        
        destFilePath = new FilePath(destStr);
        num++;
      }
      
      return destFilePath;
    }

    //---------------------------------------------------------------------------
    //---------------------------------------------------------------------------
    
    @SuppressWarnings("resource")
    public void copyToResultsFolder(boolean excludeAnnots, ArrayList<String> errList)
    {
      PDDocument srcPdf = null, destPdf = null;
      PDFCloneUtility cloneUtil = null;
      
      try
      {
        FilePath destFilePath = getDestPath(filePath);
        
        if (getMediaType(filePath).toString().contains("pdf") == false)
        {
          filePath.copyTo(destFilePath, false);
        }
        else if ((startPage == 1) && (endPage == Integer.MAX_VALUE) && !excludeAnnots)
        {
          filePath.copyTo(destFilePath, false);
        }
        else
        {
          srcPdf = PDDocument.load(filePath.toFile());
          
          int numPages = srcPdf.getNumberOfPages();
          
          if (numPages > 0)
          {
            if (endPage > numPages)
              endPage = numPages;
            
            if (excludeAnnots && (startPage == 1) && (numPages == endPage))           
              excludeAnnots = hasAnnotations(srcPdf);
          }
  
          if (numPages > 0)
          {
            if ((startPage == 1) && (numPages == endPage) && !excludeAnnots)
            {            
              filePath.copyTo(destFilePath, false);
            }
            else
            {              
              destPdf = new PDDocument();
              cloneUtil = new PDFCloneUtility(destPdf);
                
              for (int curPageNdx = startPage - 1; curPageNdx < endPage; curPageNdx++)
              {
                COSDictionary dict = (COSDictionary) cloneUtil.cloneForNewDocument(srcPdf.getPage(curPageNdx));
                if (excludeAnnots) 
                {
                  COSArray annots = (COSArray) dict.getItem(COSName.ANNOTS);
                  
                  if (annots != null)
                  {
                    Iterator<COSBase> it = annots.iterator();
                    
                    while (it.hasNext())
                    {                   
                      String subtype = COSName.class.cast(COSDictionary.class.cast(it.next()).getItem(COSName.SUBTYPE)).getName();
                      
                      if ((subtype.equals("Link") == false) && (subtype.equals("Widget") == false))
                        it.remove();
                    }
                  }
                }
                
                destPdf.addPage(new PDPage(dict));  
              }
              
              destPdf.save(destFilePath.toString());
            }
          }
        }
      }
      catch (Exception e)
      {
        errList.add("Error: Unable to copy \"" + filePath + "\". Reason: " + e.getMessage()); 
      }
      finally
      {
        if (destPdf != null) 
        {
          try { destPdf.close(); }
          catch (Exception e) { errList.add("Error while closing source PDF: " + e.getMessage()); }
        }
        
        if (srcPdf != null)
        {
          try { srcPdf.close(); }
          catch (Exception e) { errList.add("Error while closing source PDF: " + e.getMessage()); }
        }
      }
    }
  }
    
  //---------------------------------------------------------------------------
  //---------------------------------------------------------------------------

  private final ArrayList<SearchResultFile> list;
  private final ArrayList<String> errList;

  //---------------------------------------------------------------------------
  //---------------------------------------------------------------------------
  
  public SearchResultFileList()
  {
    list = new ArrayList<>();
    errList = new ArrayList<>();
  }
  
  //---------------------------------------------------------------------------
  //---------------------------------------------------------------------------
  
  public void addRecord(HDT_RecordWithPath record, boolean includeEdited)
  {
    switch (record.getType())
    {
      case hdtMiscFile : case hdtWorkFile :
        
        addFile(record.getPath().getFilePath(), -1, -1);
        break;
        
      case hdtWork :
        
        HDT_Work work = (HDT_Work)record;
        boolean isAuthored = false;
        
        for (Author author : work.getAuthors())
          if ((author.getIsEditor() == false) && (author.getIsTrans() == false))
            isAuthored = true;
        
        if (!isAuthored && !includeEdited) return;
        
        for (HDT_WorkFile workFile : work.workFiles)
        {  
          int startPage = work.getStartPageNum(workFile),
              endPage   = work.getEndPageNum  (workFile);
          
          if (((startPage < 1) && (endPage > 0)) ||
              ((endPage < 1) && (startPage > 0)))
            errList.add("Warning: Work \"" + work.name() + "\", ID " + work.getID() + " is missing a start or end page number.");
          
          addFile(workFile.getPath().getFilePath(), startPage, endPage);
        }
      
        work.subWorks .forEach(subWork  -> addRecord(subWork , includeEdited));     
        work.miscFiles.forEach(miscFile -> addRecord(miscFile, includeEdited));
        
        break;
        
      default :
        break;
    }
  }
  
  //---------------------------------------------------------------------------
  //---------------------------------------------------------------------------
  
  public void addFile(FilePath filePath, int startPage, int endPage)
  {
    if (filePath.exists() == false) return;
    
    SearchResultFile otherFile = new SearchResultFile(filePath, startPage, endPage);
   
    for (int ndx = 0; ndx < list.size(); ndx++)
    {
      SearchResultFile resultFile = list.get(ndx);
      
      if (resultFile.overlaps(otherFile))
      {
        if (resultFile.contains(otherFile) == false)
          list.set(ndx, resultFile.createCombined(otherFile));
        
        return;
      }
    }
    
    list.add(otherFile);
  }
  
  //---------------------------------------------------------------------------
  //---------------------------------------------------------------------------
  
  public static boolean fileIsPdf(FilePath filePath)
  {
    return getMediaType(filePath).toString().contains("pdf");
  }

  //---------------------------------------------------------------------------
  //---------------------------------------------------------------------------
  
  public void showErrors()
  {
    String errors = strListToStr(errList, false);
    
    if (ultraTrim(convertToSingleLine(errors)).length() > 0)
      messageDialog(errors, mtError);
  }

  //---------------------------------------------------------------------------
  //---------------------------------------------------------------------------

  public void copyAll(boolean excludeAnnots, HyperTask task) throws TerminateTaskException
  {
    int ndx = 0; for (SearchResultFile resultFile : list)
    {
      resultFile.copyToResultsFolder(excludeAnnots, errList);
      task.updateProgress(ndx++, list.size());
      
      if (task.isCancelled()) 
        throw new TerminateTaskException();
    }
  }

  //---------------------------------------------------------------------------
  //---------------------------------------------------------------------------
  
}
