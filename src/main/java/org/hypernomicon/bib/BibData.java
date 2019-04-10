/*
 * Copyright 2015-2019 Jason Winning
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

package org.hypernomicon.bib;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jbibtex.BibTeXDatabase;
import org.jbibtex.BibTeXEntry;
import org.jbibtex.BibTeXParser;
import org.jbibtex.Key;
import org.jbibtex.LaTeXParser;
import org.jbibtex.LaTeXPrinter;
import org.jbibtex.ParseException;
import org.jbibtex.TokenMgrException;
import org.jbibtex.Value;

import com.google.common.collect.Lists;

import org.hypernomicon.model.items.PersonName;
import org.hypernomicon.model.records.HDT_Work;
import org.hypernomicon.model.records.SimpleRecordTypes.HDT_WorkType;
import org.hypernomicon.model.records.SimpleRecordTypes.WorkTypeEnum;
import org.hypernomicon.util.json.JsonArray;
import org.hypernomicon.util.json.JsonObj;

import static org.hypernomicon.bib.BibData.YearType.*;
import static org.hypernomicon.bib.BibData.BibFieldEnum.*;
import static org.hypernomicon.bib.BibData.BibFieldType.*;
import static org.hypernomicon.bib.BibUtils.*;
import static org.hypernomicon.bib.BibData.EntryType.*;
import static org.hypernomicon.util.Util.*;

public abstract class BibData
{

//---------------------------------------------------------------------------

  public static enum AuthorType { author, editor, translator }

//---------------------------------------------------------------------------

  public static enum EntryType
  {
    etJournal, etJournalVolume, etJournalArticle, etJournalIssue, etJournalSection, etElectronicArticle, etInPress,
    etBook, etMonograph, etBookVolume, etBookSection, etBookChapter, etBookPart, etBookSet, etBookTrack, etEditedBook, etBookSeries, etMultiVolumeWork, etBooklet,
    etElectronicBook, etElectronicBookSection,
    etSerialPublication, etMagazine, etMagazineArticle, etNewspaper, etNewspaperArticle, etLetterToTheEditor, etNewsletter, etNewsletterArticle,
    etWebPage, etPostedContent, etTwitterPost, etFacebookPost, etForumPost, etInstantMessage, etBlogPost, etEmail, etFeedItem, etInternetCommunication,
    etConference, etConferencePaper, etConferenceProceedings, etPoster, etSymposium, etSymposiumPaper, etSymposiumProceedings, etPresentation,
    etReferenceBook, etReferenceEntry, etDictionaryEntry, etEncyclopediaArticle, etCatalog, etCatalogItem,
    etAncientText, etClassicalWork,
    etCase, etHearing, etStatute, etBill, etRegulation, etRuling, etGrant, etGovernmentDocument,
    etAudiovisualMaterial, etOnlineMultimedia, etMusicScore, etAudioRecording, etRadioBroadcast, etTVBroadcast, etFilm, etVideoRecording, etPodcast,
    etPortfolio, etArtwork,
    etIssueBrief, etReportSeries, etReport, etTechnicalReport, etApparatus, etMeasurementInstrument, etStandard, etStandardSeries, etManual, etPatent,
    etThesis, etMastersThesis, etDoctoralThesis, etManuscript, etUnpublishedWork, etWorkingPaper, etUnpublishedRawData, etPersonalCommunication, etDocument,
    etMap, etChart, etEquation, etFigure, etSlide, etDataSet, etDataFile, etOnlineDatabase, etAggregatedDatabase, etSoftware, etComponent,
    etAbstract, etCommentary, etInterview, etArchivalDocument, etArchivalCollection, etLetter, etPamphlet, etBrochure,

    etUnentered,  // This just means the field hasn't been populated yet
    etOther,      // This means it is a type that does not fit into any of the above categories
    etNone        // This means it should be treated as a non-entry
  }

//---------------------------------------------------------------------------

  public static enum BibFieldEnum
  {
    bfDOI, bfPublisher, bfPubLoc, bfURL, bfVolume, bfIssue, bfLanguage, bfEdition, bfPages, bfYear, bfAuthors, bfEditors, bfTranslators,
    bfTitle, bfISBNs, bfISSNs, bfContainerTitle, bfMisc, bfEntryType, bfWorkType
  }

//---------------------------------------------------------------------------

  public static enum BibFieldType { bftString, bftMultiString, bftEntryType, bftWorkType, bftAuthor }

//---------------------------------------------------------------------------

  private static final HashMap<String, YearType> descToYearType = new HashMap<>();

  static enum YearType
  {
    ytUnknown(""),
    ytCreated("created"),
    ytCopyright("copyright"),
    ytIssued("issued"),
    ytPublishedDate("publishedDate"),
    ytPublicationDate("publicationDate"),
    ytPublishedPrint("published-print"),
    ytPublicationDisplayDate("publicationDisplayDate"),
    ytCoverDate("coverDate"),
    ytCoverDisplayDate("coverDisplayDate");

    private final String desc;

    private YearType(String desc) { this.desc = desc; descToYearType.put(desc, this); }

    static YearType getByDesc(String desc) { return descToYearType.getOrDefault(desc, ytUnknown); }

    static YearType highestPriority()
    {
      int ordinal = Integer.MIN_VALUE;
      YearType highestYT = null;

      for (YearType yt : EnumSet.allOf(YearType.class))
      {
        if (yt.ordinal() > ordinal)
        {
          highestYT = yt;
          ordinal = yt.ordinal();
        }
      }

      return highestYT;
    }
  }

//---------------------------------------------------------------------------

  private static final HashMap<BibFieldEnum, String> bibFieldEnumToName;
  protected static final HashMap<BibFieldEnum, BibFieldType> bibFieldEnumToType;

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  static
  {
    bibFieldEnumToName = new HashMap<>();
    bibFieldEnumToType = new HashMap<>();

    addBibField(bfEntryType      , "Entry Type"                , bftEntryType);
    addBibField(bfWorkType       , "Work Type"                 , bftWorkType);
    addBibField(bfAuthors        , "Authors"                   , bftAuthor);
    addBibField(bfContainerTitle , "Container Title"           , bftMultiString); // For articles this is the journal title; for chapters it is the book title
    addBibField(bfDOI            , "DOI"                       , bftString);      // Document Object ID only, without "doi:" or url
    addBibField(bfEdition        , "Edition"                   , bftString);      // Information about publication edition
    addBibField(bfEditors        , "Editors"                   , bftAuthor);
    addBibField(bfISBNs          , "ISBNs"                     , bftMultiString); // International standard book numbers
    addBibField(bfISSNs          , "ISSNs"                     , bftMultiString); // International standard serial numbers
    addBibField(bfIssue          , "Issue"                     , bftString);      // Issue number
    addBibField(bfLanguage       , "Language"                  , bftString);      // Language
    addBibField(bfMisc           , "Miscellaneous Information" , bftMultiString); // Used internally to hold uncategorized extracted information
    addBibField(bfPages          , "Page Numbers"              , bftString);      // Page range
    addBibField(bfPubLoc         , "Publisher Location"        , bftString);      // Where published
    addBibField(bfPublisher      , "Publisher"                 , bftString);      // May or may not include city
    addBibField(bfTitle          , "Title"                     , bftMultiString); // Title of this work
    addBibField(bfTranslators    , "Translators"               , bftAuthor);
    addBibField(bfURL            , "URL"                       , bftString);      // URL where this work can be found
    addBibField(bfVolume         , "Volume"                    , bftString);      // Volume number
    addBibField(bfYear           , "Year"                      , bftString);      // Publication year
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  private static void addBibField(BibFieldEnum bibFieldEnum, String fieldName, BibFieldType type)
  {
    bibFieldEnumToName.put(bibFieldEnum, fieldName);
    bibFieldEnumToType.put(bibFieldEnum, type);
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public static String getFieldName(BibFieldEnum bibFieldEnum)         { return bibFieldEnumToName.get(bibFieldEnum); }
  static BibFieldType getFieldType(BibFieldEnum bibFieldEnum)          { return bibFieldEnumToType.get(bibFieldEnum); }
  public static boolean bibFieldIsMultiLine(BibFieldEnum bibFieldEnum) { return bibFieldEnumToType.get(bibFieldEnum) == bftMultiString; }

  public abstract EntryType getEntryType();
  public abstract void setMultiStr(BibFieldEnum bibFieldEnum, List<String> list);
  protected abstract void setEntryType(EntryType entryType);
  public abstract void setStr(BibFieldEnum bibFieldEnum, String newStr);
  public abstract List<String> getMultiStr(BibFieldEnum bibFieldEnum);
  public abstract String getStr(BibFieldEnum bibFieldEnum);
  public abstract BibAuthors getAuthors();
  public abstract HDT_Work getWork();
  protected abstract boolean linkedToWork();
  public abstract HDT_WorkType getWorkType();
  public abstract void setWorkType(HDT_WorkType workType);

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public boolean entryTypeNotEmpty()
  {
    EntryType entryType = getEntryType();
    return (entryType != null) && (entryType != etNone) && (entryType != etUnentered);
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  protected static String getMultiStrSpaceDelimited(List<String> list)
  {
    StringBuilder all = new StringBuilder();

    list.forEach(one -> all.append((all.length() > 0 ? " " : "") + ultraTrim(one)));

    return ultraTrim(all.toString());
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public boolean fieldNotEmpty(BibFieldEnum bibFieldEnum)
  {
    if (bibFieldIsMultiLine(bibFieldEnum))
      return getMultiStr(bibFieldEnum).size() > 0;

    return getStr(bibFieldEnum).length() > 0;
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  protected static HDT_WorkType convertEntryTypeToWorkType(EntryType et)
  {
    switch (et)
    {
      case etBook : case etBooklet: case etBookVolume: case etJournalIssue : case etMagazine : case etManual : case etMonograph :
      case etMultiVolumeWork : case etReferenceBook : case etEditedBook : case etAncientText : case etClassicalWork : case etElectronicBook :

        return HDT_WorkType.get(WorkTypeEnum.wtBook);

      case etAbstract : case etArchivalDocument : case etBookChapter : case etCommentary : case etConferencePaper :
      case etEncyclopediaArticle : case etJournalArticle : case etLetter : case etLetterToTheEditor :
      case etMagazineArticle : case etNewsletterArticle : case etNewspaperArticle : case etReferenceEntry : case etInPress : case etUnpublishedWork :
      case etReport : case etTechnicalReport : case etDictionaryEntry : case etWorkingPaper : case etElectronicArticle : case etGovernmentDocument :

        return HDT_WorkType.get(WorkTypeEnum.wtPaper);

      default : break;
    }

    return null;
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  protected static EntryType convertWorkTypeToEntryType(WorkTypeEnum workTypeEnum)
  {
    switch (workTypeEnum)
    {
      case wtBook         : return EntryType.etBook;
      case wtChapter      : return EntryType.etBookChapter;
      case wtNone         : return EntryType.etUnentered;
      case wtPaper        : return EntryType.etJournalArticle;
      case wtRecording    : return EntryType.etAudiovisualMaterial;
      case wtUnenteredSet : return EntryType.etNone;
      case wtWebPage      : return EntryType.etWebPage;

      default             : return EntryType.etUnentered;
    }
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public void setTitle(String newTitle)
  {
    setMultiStr(bfTitle, Arrays.asList(newTitle));
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  protected void addStr(BibFieldEnum bibFieldEnum, String newStr)
  {
    List<String> list = getMultiStr(bibFieldEnum);
    list.add(newStr);
    setMultiStr(bibFieldEnum, list);
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public void extractDOIandISBNs(String value)
  {
    setDOI(value);
    addISBN(value);
    addISSN(value);
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  protected void setDOI(String newStr)
  {
    if (safeStr(newStr).length() == 0) return;
    String doi = matchDOI(newStr);
    if (doi.length() > 0)
      setStr(bfDOI, doi);
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  protected void addISBN(String newStr)
  {
    matchISBN(newStr).forEach(isbn -> addStr(bfISBNs, isbn));
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  void addISSN(String newStr)
  {
    matchISSN(newStr).forEach(issn -> addStr(bfISSNs, issn));
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  // Authors have to be checked separately

  public boolean fieldsAreEqual(BibFieldEnum bibFieldEnum, BibData otherBD)
  {
    if ((fieldNotEmpty(bibFieldEnum) || otherBD.fieldNotEmpty(bibFieldEnum)) == false) return true;

    switch (bibFieldEnum)
    {
      case bfDOI       : case bfURL       : case bfVolume    : case bfIssue     : case bfPages     : case bfEntryType :
      case bfPublisher : case bfPubLoc    : case bfEdition   : case bfLanguage  : case bfYear      : case bfWorkType :

        if (ultraTrim(getStr(bibFieldEnum)).equals(ultraTrim(otherBD.getStr(bibFieldEnum))) == false) return false;
        break;

      case bfContainerTitle: case bfTitle: case bfMisc: case bfISBNs: case bfISSNs:

        if (strListsEqual(getMultiStr(bibFieldEnum), otherBD.getMultiStr(bibFieldEnum), false) == false) return false;
        break;

      case bfAuthors: case bfEditors: case bfTranslators:

        break;
    }

    return true;
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  private static List<String> jsonStrList(JsonArray jArr)
  {
    return jArr == null ? new ArrayList<>() : Lists.newArrayList((Iterable<String>)jArr.getStrs());
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public static BibData createFromGoogleJSON(JsonObj jsonObj, String queryIsbn)
  {
    try
    {
      jsonObj = jsonObj.getArray("items").getObj(0).getObj("volumeInfo");
    }
    catch (NullPointerException e)
    {
      return null;
    }

    BibDataStandalone bd = new BibDataStandalone();

    String title    = jsonObj.getStrSafe("title"),
           subtitle = jsonObj.getStrSafe("subtitle");

    bd.addStr(bfTitle, title);

    if (title.equalsIgnoreCase(subtitle) == false)
      bd.addStr(bfTitle, subtitle);

    bd.setStr(bfPublisher, jsonObj.getStrSafe("publisher"));
    bd.setEntryType(parseGoogleBooksType(jsonObj.getStrSafe("printType"))); // supposedly this will either be "BOOK" or "MAGAZINE", nothing else

    String publishedDate = jsonObj.getStrSafe(ytPublishedDate.desc);
    if (publishedDate.length() > 0)
      bd.setYear(publishedDate.substring(0, 4), ytPublishedDate);

    BibAuthors authors = bd.getAuthors();
    jsonStrList(jsonObj.getArray("authors")).forEach(authStr -> authors.add(new BibAuthor(AuthorType.author, new PersonName(authStr))));

    nullSwitch(jsonObj.getArray("industryIdentifiers"), iiArr -> iiArr.getObjs().forEach(iiObj ->
    {
      if (iiObj.getStrSafe("type").toLowerCase().contains("isbn"))
        bd.addISBN(iiObj.getStrSafe("identifier"));
    }));

    if (bd.fieldNotEmpty(bfISBNs) == false)
      bd.addISBN(queryIsbn);

    return bd;
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public static BibData createFromBibTex(List<String> lines) throws TokenMgrException, ParseException
  {
    BibTeXParser parser = new BibTeXParser();
    LaTeXParser latexParser = new LaTeXParser();
    LaTeXPrinter latexPrinter = new LaTeXPrinter();

    BibTeXDatabase entries = parser.parse(new BufferedReader(new StringReader(String.join("\n", lines))));

    if ((entries == null) || (entries.getEntries().size() == 0)) return null;

    BibTeXEntry entry = entries.getEntries().values().iterator().next();

    BibDataStandalone bd = new BibDataStandalone();

    bd.setEntryType(parseBibTexType(entry.getType().getValue()));

    for (Entry<Key, Value> mapping : entry.getFields().entrySet())
    {
      String val = mapping.getValue().toUserString();

      if (val.indexOf('\\') > -1 || val.indexOf('{') > -1)
        val = latexPrinter.print(latexParser.parse(val));

      bd.extractDOIandISBNs(val);

      switch (mapping.getKey().getValue())
      {
        case "address"   : bd.setStr(bfPubLoc, val); break;
        case "author"    : bd.addBibTexAuthor(val, AuthorType.author); break;
        case "booktitle" : bd.setMultiStr(bfContainerTitle, Arrays.asList(val)); break;
        case "edition"   : bd.setStr(bfEdition, val); break;
        case "editor"    : bd.addBibTexAuthor(val, AuthorType.editor); break;
        case "journal"   : bd.setMultiStr(bfContainerTitle, Arrays.asList(val)); break;
        case "language"  : bd.setStr(bfLanguage, val); break;
        case "note"      : bd.addStr(bfMisc, val); break;
        case "number"    : bd.setStr(bfIssue, val); break;
        case "pages"     : bd.setStr(bfPages, val); break;
        case "publisher" : bd.setStr(bfPublisher, val); break;
        case "series"    :

          if (bd.getMultiStr(bfContainerTitle).size() == 0)
            bd.addStr(bfContainerTitle, val);
          break;

        case "title"     : bd.addStr(bfTitle, val); break;
        case "type"      : bd.setEntryType(parseBibTexType(val)); break;
        case "url"       : bd.setStr(bfURL, val); break;
        case "volume"    : bd.setStr(bfVolume, val); break;
        case "year"      : bd.setYear(val, ytPublicationDate); break;

        case "doi" : case "isbn" : case "issn" : break; // captured already

        default          : bd.addStr(bfMisc, mapping.getKey().getValue() + ": " + val); break;
      }
    }

    return bd;
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  protected void addBibTexAuthor(String val, AuthorType authorType)
  {
    BibAuthors authors = getAuthors();

    Arrays.stream(val.split("\n")).forEach(auth ->
    {
      if (auth.startsWith("and "))
        auth = auth.substring(4);

      auth = ultraTrim(auth);

      if (auth.length() > 0)
        authors.add(authorType, new PersonName(auth));
    });
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public static BibData createFromRIS(List<String> lines)
  {
    BibDataStandalone bd = new BibDataStandalone();
    boolean gotType = false;

    for (String line : lines)
    {
      if (line.isEmpty() || line.equals("") || line.matches("^\\s*$"))
        continue;

      bd.extractDOIandISBNs(line);

      Pattern p = Pattern.compile("^([A-Z][A-Z0-9])  -(.*)$");
      Matcher m = p.matcher(line);

      if (m.matches())
      {
        String tag = m.group(1),
               val = m.group(2).trim();

        if ( ! (gotType || tag.equals("TY")))
          return null;

        switch (tag)
        {
          case "DO": break; // DOI was captured already

          case "ER": return gotType ? bd : null;
          case "TY": bd.setEntryType(parseRISType(val)); gotType = true; break;

          case "A1": case "A2": case "A3": case "A4": case "AU":

            bd.getAuthors().add(AuthorType.author, new PersonName(val)); break;

          case "ED":

            bd.getAuthors().add(AuthorType.editor, new PersonName(val)); break;

          case "CY": case "PP":

            bd.setStr(bfPubLoc, val); break;

          case "DA": bd.setYear(val, ytCopyright); break;
          case "PY": bd.setYear(val, ytPublicationDate); break;
          case "Y1": bd.setYear(val, ytCoverDisplayDate); break;
          case "Y2": bd.setYear(val, ytCreated); break;

          case "OP": break;    // Original Publication
          case "RP": break;    // Reprint Edition

          case "ET": bd.setStr(bfEdition, val); break;
          case "IS": bd.setStr(bfIssue, val); break;

          case "JF": case "JO":

            bd.addStr(bfContainerTitle, val); break;

          case "L1": case "L2": case "LK": case "UR":

            bd.setStr(bfURL, val); break;

          case "LA": bd.setStr(bfLanguage, val); break;
          case "PB": bd.setStr(bfPublisher, val); break;


          case "SE": break;    // Section

          case "SP":

            String pages = bd.getStr(bfPages);
            bd.setStr(bfPages, pages.length() == 0 ? val : (val + "-" + pages));
            break;

          case "EP":

            pages = bd.getStr(bfPages);
            bd.setStr(bfPages, pages.length() == 0 ? val : (pages + "-" + val));
            break;

          case "TI": case "TT": case "T1": case "T2": case "T3":

            bd.addStr(bfTitle, val);
            break;

          case "VL": case "VO":

            bd.setStr(bfVolume, val); break;

          default :

            bd.addStr(bfMisc, val); break;
        }
      }
      else if (ultraTrim(line).equals("ER"))
        return gotType ? bd : null;
    }

    return null; // It has to end with "ER"; otherwise malformed
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public static BibData createFromCrossrefJSON(JsonObj jsonObj, String queryDoi)
  {
    try
    {
      jsonObj = jsonObj.getObj("message");
      jsonObj = nullSwitch(jsonObj.getArray("items"), jsonObj, items -> items.getObj(0));
    }
    catch (NullPointerException | IndexOutOfBoundsException e)
    {
      return null;
    }

    BibDataStandalone bd = new BibDataStandalone();

    bd.setStr(bfDOI, jsonObj.getStrSafe("DOI"));
    bd.setEntryType(parseCrossrefType(jsonObj.getStrSafe("type")));
    bd.setStr(bfPages, jsonObj.getStrSafe("page"));
    bd.setStr(bfPublisher, jsonObj.getStrSafe("publisher"));

    if (jsonObj.containsKey(ytPublishedPrint.desc))
    {
      bd.setStr(bfYear, jsonObj.getObj(ytPublishedPrint.desc).getArray("date-parts").getArray(0).getLongAsStrSafe(0));
      bd.yearType = ytPublishedPrint;
    }
    else if (jsonObj.containsKey(ytIssued.desc))
    {
      bd.setStr(bfYear, jsonObj.getObj(ytIssued.desc).getArray("date-parts").getArray(0).getLongAsStrSafe(0));
      bd.yearType = ytIssued;
    }
    else if (jsonObj.containsKey(ytCreated.desc))
    {
      bd.setStr(bfYear, jsonObj.getObj(ytCreated.desc).getArray("date-parts").getArray(0).getLongAsStrSafe(0));
      bd.yearType = ytCreated;
    }

    bd.setStr(bfURL, jsonObj.getStrSafe("URL"));
    bd.setStr(bfVolume, jsonObj.getStrSafe("volume"));
    bd.setStr(bfIssue, jsonObj.getStrSafe("issue"));

    List<String> title = jsonStrList(jsonObj.getArray("title")),
                 subtitle = jsonStrList(jsonObj.getArray("subtitle"));

    if (strListsEqual(title, subtitle, true) == false)
      title.addAll(subtitle);

    bd.setMultiStr(bfTitle, title);

    bd.setMultiStr(bfContainerTitle, jsonStrList(jsonObj.getArray("container-title")));
    bd.setMultiStr(bfISBNs, jsonStrList(jsonObj.getArray("ISBN")));
    bd.setMultiStr(bfISSNs, jsonStrList(jsonObj.getArray("ISSN")));

    BibAuthors authors = bd.getAuthors();

    authors.setFromCrossRefJson(jsonObj.getArray("author"    ), AuthorType.author);
    authors.setFromCrossRefJson(jsonObj.getArray("editor"    ), AuthorType.editor);
    authors.setFromCrossRefJson(jsonObj.getArray("translator"), AuthorType.translator);

    if (bd.fieldNotEmpty(bfDOI) == false)
      bd.setDOI(queryDoi);

    return bd;
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  protected static String extractYear(String text)
  {
    Matcher m = Pattern.compile("(\\A|\\D)([12]\\d\\d\\d)(\\z|\\D)").matcher(text);
    return m.find() ? m.group(2) : "";
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  private void addReportStr(BibFieldEnum bibFieldEnum, List<String> list)
  {
    appendToReport(getFieldName(bibFieldEnum), getStr(bibFieldEnum), list);
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  private void addReportMultiStr(BibFieldEnum bibFieldEnum, List<String> list)
  {
    String line = getMultiStr(bibFieldEnum).stream().reduce((s1, s2) -> s1 + "; " + s2).orElse("");

    appendToReport(getFieldName(bibFieldEnum), line, list);
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  private void appendToReport(String fieldName, String fieldVal, List<String> list)
  {
    if (fieldVal.trim().length() > 0)
      list.add(fieldName + ": " + fieldVal);
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public String createReport()
  {
    ArrayList<String> list = new ArrayList<>();
    BibAuthors authors = getAuthors();

    addReportStr(bfTitle         , list);
    addReportStr(bfEntryType     , list);

    appendToReport(getFieldName(bfAuthors    ), authors.getStr(AuthorType.author    ), list);
    appendToReport(getFieldName(bfEditors    ), authors.getStr(AuthorType.editor    ), list);
    appendToReport(getFieldName(bfTranslators), authors.getStr(AuthorType.translator), list);

    addReportStr(bfYear          , list);
    addReportStr(bfDOI           , list);
    addReportMultiStr(bfISBNs    , list);
    addReportStr(bfURL           , list);
    addReportStr(bfContainerTitle, list);
    addReportStr(bfVolume        , list);
    addReportStr(bfIssue         , list);
    addReportStr(bfPages         , list);
    addReportStr(bfPublisher     , list);
    addReportStr(bfPubLoc        , list);
    addReportStr(bfEdition       , list);
    addReportStr(bfMisc          , list);
    addReportMultiStr(bfISSNs    , list);

    return strListToStr(list, false);
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public void copyAllFieldsFrom(BibData bd, boolean includeAuthors, boolean includeEntryType)
  {
    EnumSet.allOf(BibFieldEnum.class).forEach(bibFieldEnum -> { switch (bibFieldEnumToType.get(bibFieldEnum))
    {
      case bftString :

        setStr(bibFieldEnum, bd.getStr(bibFieldEnum));
        break;

      case bftMultiString :

        setMultiStr(bibFieldEnum, bd.getMultiStr(bibFieldEnum));
        break;

      case bftEntryType :

        if (includeEntryType) setEntryType(bd.getEntryType());
        break;

      case bftWorkType :

        setWorkType(bd.getWorkType());
        break;

      case bftAuthor :

        break;
    }});

    if (includeAuthors == false) return;

    BibAuthors authors = getAuthors();
    bd.getAuthors().forEach(authors::add);
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public EnumSet<BibFieldEnum> fieldsWithExternalData()
  {
    EnumSet<BibFieldEnum> set = EnumSet.allOf(BibFieldEnum.class);

    set.removeIf(bibFieldEnum -> { switch (bibFieldEnum)
    {
      case bfAuthors   : case bfEditors  : case bfTranslators : case bfTitle:
      case bfDOI       : case bfISBNs    : case bfMisc        : case bfYear:
      case bfEntryType : case bfWorkType : case bfURL         :

        return true;

      default:

        return fieldNotEmpty(bibFieldEnum) == false;
    }});

    return set;
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

}
