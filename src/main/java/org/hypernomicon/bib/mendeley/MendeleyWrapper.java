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

package org.hypernomicon.bib.mendeley;

import static org.hypernomicon.model.HyperDB.*;
import static org.hypernomicon.Const.*;
import static org.hypernomicon.bib.data.EntryType.*;
import static org.hypernomicon.util.Util.*;
import static org.hypernomicon.util.Util.MessageDialogType.*;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.nio.charset.StandardCharsets.*;

import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.StringEntity;
import org.json.simple.parser.ParseException;

import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.common.collect.EnumHashBiMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import org.hypernomicon.util.filePath.FilePath;
import org.hypernomicon.util.json.JsonArray;
import org.hypernomicon.util.json.JsonObj;
import org.hypernomicon.view.workMerge.MergeWorksDlgCtrlr;
import org.hypernomicon.bib.BibEntryRow;
import org.hypernomicon.bib.LibraryWrapper;
import org.hypernomicon.bib.data.EntryType;
import org.hypernomicon.bib.mendeley.MendeleyEntity.MendeleyEntityType;
import org.hypernomicon.model.Exceptions.HyperDataException;
import org.hypernomicon.model.Exceptions.TerminateTaskException;
import org.hypernomicon.model.items.PersonName;
import org.hypernomicon.model.records.HDT_Work;
import org.hypernomicon.util.CryptoUtil;
import org.hypernomicon.util.JsonHttpClient;

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

public class MendeleyWrapper extends LibraryWrapper<MendeleyDocument, MendeleyFolder>
{
  private String accessToken, refreshToken;
  private final JsonHttpClient jsonClient;
  private Instant lastSyncTime = Instant.EPOCH;

  static final EnumHashBiMap<EntryType, String> entryTypeMap = initTypeMap();

  private static EnumMap<EntryType, JsonObj> templates = null;

  @Override public LibraryType type() { return LibraryType.ltMendeley; }

  private static enum MendeleyCmd
  {
    readFolders,
    readDocuments,
    readDeletedDocuments,
    readTrash
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public MendeleyWrapper(String apiKey, String refreshToken)
  {
    this.accessToken = apiKey;
    this.refreshToken = refreshToken;
    jsonClient = new JsonHttpClient();
  }

  JsonObj getTemplate(EntryType type) { return templates.get(type); }

  @Override public EnumHashBiMap<EntryType, String> getEntryTypeMap() { return entryTypeMap; }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public static enum HttpRequestType
  {
    get,
    post,
    patch
  }

  private JsonArray doHttpRequest(String url, HttpRequestType requestType, String jsonData, String mediaType, StringBuilder nextUrl) throws IOException, UnsupportedOperationException, ParseException, TerminateTaskException
  {
    JsonArray jsonArray = null;
    MutableInt totalResults = new MutableInt(-1);

    RequestBuilder rb;

    if (syncTask.isCancelled()) throw new TerminateTaskException();

    switch (requestType)
    {
      case get :
        rb = RequestBuilder.get().setHeader(HttpHeaders.ACCEPT, mediaType);
        break;

      case patch :
        rb = RequestBuilder.patch().setEntity(new StringEntity(jsonData, UTF_8))
                                   .setHeader(HttpHeaders.CONTENT_TYPE, mediaType)
                                   .setHeader(HttpHeaders.IF_UNMODIFIED_SINCE, dateTimeToHttpDate(lastSyncTime));
        break;

      case post :
        rb = RequestBuilder.post().setEntity(new StringEntity(jsonData, UTF_8))
                                  .setHeader(HttpHeaders.CONTENT_TYPE, mediaType)
                                  .setHeader(HttpHeaders.ACCEPT, mediaType);
        break;

      default : return null;
    }

    request = rb.setUri(url)
                .setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .build();

    try
    {
      jsonArray = jsonClient.requestArrayInThisThread(request);
    }
    catch(SocketException e)
    {
      if (syncTask.isCancelled())
      {
        request = null;
        throw new TerminateTaskException();
      }

      request = null;
      throw e;
    }

    nullSwitch(jsonClient.getHeaders(), headers -> headers.forEach(header ->
    {
      switch (header.getName())
      {
        case "Mendeley-Count" : totalResults.setValue(parseInt(header.getValue(), -1)); break;

        case "Link" :

          String val = header.getValue();
          if (val.endsWith("rel=\"next\""))
            assignSB(nextUrl, val.split("<")[1].split(">")[0]);
      }
    }));

    request = null;

    if (syncTask.isCancelled()) throw new TerminateTaskException();

    if (jsonClient.getStatusCode() == HttpStatus.SC_UNAUTHORIZED)
    {
      if ((jsonArray != null) && (jsonArray.size() > 0))
      {
        JsonObj jsonObj = jsonArray.getObj(0);
        if (jsonObj.getStrSafe("errorId").equals("oauth/TOKEN_EXPIRED"))
        {
          OAuth2AccessToken token;

          try (OAuth20Service service = MendeleyOAuthApi.service())
          {
            token = MendeleyOAuthApi.service().refreshAccessToken(refreshToken);

            accessToken = token.getAccessToken();
            refreshToken = token.getRefreshToken();

            try
            {
              db.prefs.put(PREF_KEY_BIB_ACCESS_TOKEN , CryptoUtil.encrypt("", accessToken ));
              db.prefs.put(PREF_KEY_BIB_REFRESH_TOKEN, CryptoUtil.encrypt("", refreshToken));
            }
            catch (Exception e)
            {
              messageDialog("An error occurred while saving access token: " + e.getMessage(), mtError);
            }
          }
          catch (InterruptedException | ExecutionException e)
          {
            return jsonArray;
          }

          return doHttpRequest(url, requestType, jsonData, mediaType, nextUrl);
        }
      }
    }

    if (syncTask.isCancelled()) throw new TerminateTaskException();

    return jsonArray;
  }

  //---------------------------------------------------------------------------
  //---------------------------------------------------------------------------

  private JsonArray createDocumentOnServer(JsonObj jsonObj) throws UnsupportedOperationException, IOException, ParseException, TerminateTaskException
  {
    String url = "https://api.mendeley.com/documents",
           mediaType = "application/vnd.mendeley-document.1+json";

    JsonArray jsonArray = doHttpRequest(url, HttpRequestType.post, jsonObj.toString(), mediaType, null);

    switch (jsonClient.getStatusCode())
    {
      case HttpStatus.SC_CREATED :

        return jsonArray;
    }

    throw new HttpResponseException(jsonClient.getStatusCode(), jsonClient.getReasonPhrase());
  }

  //---------------------------------------------------------------------------
  //---------------------------------------------------------------------------

  private JsonArray updateDocumentOnServer(JsonObj jsonObj) throws UnsupportedOperationException, IOException, ParseException, TerminateTaskException
  {
    String url = "https://api.mendeley.com/documents/" + jsonObj.getStrSafe("id"),
           mediaType = "application/vnd.mendeley-document.1+json";

    jsonObj.remove("created");
    jsonObj.remove("last_modified");
    jsonObj.remove("profile_id");
    jsonObj.remove("id");
    jsonObj.remove("group_id");
    jsonObj.remove("accessed");

    JsonArray jsonArray = doHttpRequest(url, HttpRequestType.patch, jsonObj.toString(), mediaType, null);

    switch (jsonClient.getStatusCode())
    {
      case HttpStatus.SC_OK :
      case HttpStatus.SC_NOT_MODIFIED :
      case HttpStatus.SC_PRECONDITION_FAILED :

        return jsonArray;
    }

    String errMsg = jsonClient.getReasonPhrase();

    if (jsonArray != null)
    {
      JsonObj jObj = jsonArray.getObj(0);
      if (jObj.containsKey("message"))
        errMsg = jObj.getStr("message");
    }

    throw new HttpResponseException(jsonClient.getStatusCode(), errMsg);
  }

  //---------------------------------------------------------------------------
  //---------------------------------------------------------------------------

  private JsonArray doReadCommand(MendeleyCmd command) throws TerminateTaskException, UnsupportedOperationException, IOException, ParseException
  {
    String url = "https://api.mendeley.com/", mediaType = "";
    StringBuilder nextUrl = new StringBuilder();

    switch (command)
    {
      case readFolders :
        url += "folders";
        mediaType = "application/vnd.mendeley-folder.1+json";
        break;

      case readDocuments :
        url += "documents?modified_since=" + dateTimeToIso8601(lastSyncTime) + "&limit=50&view=all";
        mediaType = "application/vnd.mendeley-document.1+json";
        break;

      case readDeletedDocuments :
        url += "documents?deleted_since=" + dateTimeToIso8601(lastSyncTime) + "&limit=50";
        mediaType = "application/vnd.mendeley-document.1+json";
        break;

      case readTrash :
        url += "trash?modified_since=" + dateTimeToIso8601(lastSyncTime) + "&limit=50&view=all";
        mediaType = "application/vnd.mendeley-document.1+json";
        break;
    }

    JsonArray jsonArray = doHttpRequest(url, HttpRequestType.get, null, mediaType, nextUrl);

    while (nextUrl.length() > 0)
    {
      url = nextUrl.toString();
      assignSB(nextUrl, "");
      doHttpRequest(url, HttpRequestType.get, null, mediaType, nextUrl).getObjs().forEach(jsonArray::add);
    }

    switch (jsonClient.getStatusCode())
    {
      case HttpStatus.SC_OK :
      case HttpStatus.SC_NOT_MODIFIED :
      case HttpStatus.SC_PRECONDITION_FAILED :

        return jsonArray;
    }

    throw new HttpResponseException(jsonClient.getStatusCode(), jsonClient.getReasonPhrase());
  }

  //---------------------------------------------------------------------------
  //---------------------------------------------------------------------------

  private Boolean fxThreadReturnValue = null;

  boolean doMerge(MendeleyDocument document, JsonObj jObj)
  {
    fxThreadReturnValue = null;

    runInFXThread(() ->
    {
      MergeWorksDlgCtrlr mwd = null;

      try
      {
        mwd = MergeWorksDlgCtrlr.create("Merge Remote Changes with Local Changes", document, new MendeleyDocument(this, jObj, true),
                                        null, null, document.getWork(), false, false, false);
      }
      catch (IOException e)
      {
        messageDialog("Unable to initialize merge dialog window.", mtError);
        fxThreadReturnValue = Boolean.FALSE;
        return;
      }

      document.update(jObj, true, true);

      if (mwd.showModal())
        mwd.mergeInto(document);

      fxThreadReturnValue = Boolean.TRUE;
    });

    while (fxThreadReturnValue == null) sleepForMillis(100);

    return fxThreadReturnValue.booleanValue();
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  @Override public SyncTask createNewSyncTask()
  {
    syncTask = new SyncTask() { @Override public Boolean call() throws TerminateTaskException, HyperDataException
    {
    //---------------------------------------------------------------------------

      Set<String> trashedIDs = new HashSet<>(),
                  nonTrashedIDs = new HashSet<>();

      try
      {

        do
        {

// Get list of remotely changed documents ---------------------------------------------------------------------------
// ------------------------------------------------------------------------------------------------------------------

          Map<String, JsonObj> remoteChangeIDtoObj = new HashMap<>();

          nonTrashedIDs.clear();
          doReadCommand(MendeleyCmd.readDocuments).getObjs().forEach(jObj ->
          {
            String entryKey = jObj.getStrSafe("id");
            remoteChangeIDtoObj.put(entryKey, jObj);
            nonTrashedIDs.add(entryKey);
            changed = true;
          });

// Get list of remotely changed documents in trash ------------------------------------------------------------------
// ------------------------------------------------------------------------------------------------------------------

          trashedIDs.clear();
          doReadCommand(MendeleyCmd.readTrash).getObjs().forEach(jObj ->
          {
            String entryKey = jObj.getStrSafe("id");
            remoteChangeIDtoObj.put(entryKey, jObj);
            trashedIDs.add(entryKey);
            changed = true;
          });

// Get list of remotely deleted documents ---------------------------------------------------------------------------
// ------------------------------------------------------------------------------------------------------------------

          JsonArray remoteDeletions = doReadCommand(MendeleyCmd.readDeletedDocuments);

// Locally delete remotely deleted documents ------------------------------------------------------------------------
// ------------------------------------------------------------------------------------------------------------------

          remoteDeletions.getObjs().forEach(jObj ->
          {
            changed = true;
            String key = jObj.getStrSafe("id");

            if (keyToAllEntry.containsKey(key))
            {
              // A remote deletion will simply override local changes. Undocument code to change this behavior.

//              MendeleyDocument document = keyToAllEntry.get(key);
//
//              if (document.isSynced())
//              {
                HDT_Work work = db.getWorkByBibEntryKey(key);
                if (work != null)
                  work.setBibEntryKey("");

                keyToAllEntry.remove(key);
                keyToTrashEntry.remove(key);
//              }
//              else
//              {
//                // Perform conflict resolution!
//                noOp();
//              }
            }
          });

          lastSyncTime = Instant.now();

// Build list of locally changed documents and documents to merge; merge local and remote changes -------------------
// ------------------------------------------------------------------------------------------------------------------

          ArrayList<MendeleyDocument> localChanges = new ArrayList<>();

          getAllEntries().forEach(entry ->
          {
            if (entry.isSynced() == false)
            {
              localChanges.add(entry);
              String entryKey = entry.getEntryKey();

              if (remoteChangeIDtoObj.containsKey(entryKey))
              {
                doMerge(entry, remoteChangeIDtoObj.get(entryKey));
                remoteChangeIDtoObj.remove(entryKey);
              }
            }
          });

          remoteChangeIDtoObj.forEach((entryKey, jObj) ->
          {
            MendeleyDocument document = keyToAllEntry.get(entryKey);

            if (document == null)
            {
              document = (MendeleyDocument) MendeleyEntity.create(MendeleyWrapper.this, jObj);

              if (document != null)
                keyToAllEntry.put(entryKey, document);
            }
            else
              document.update(jObj, true, false);
          });

// Create new documents on server and update locally changed documents on server, checking for 412 status -----------
// ------------------------------------------------------------------------------------------------------------------

          Iterator<MendeleyDocument> it = localChanges.iterator();
          while (it.hasNext() && (jsonClient.getStatusCode() != HttpStatus.SC_PRECONDITION_FAILED))
          {
            MendeleyDocument document = it.next();

            if (document.isNewEntry())
            {
              String oldKey = document.getEntryKey();

              JsonArray jsonArray = createDocumentOnServer(document.exportJsonObjForUploadToServer());

              document.update(jsonArray.getObj(0), false, false);

              updateKey(oldKey, document.getKey());
              changed = true;
            }
            else
            {
              JsonArray jsonArray = updateDocumentOnServer(document.exportJsonObjForUploadToServer());
              if (jsonClient.getStatusCode() == HttpStatus.SC_OK)
              {
                document.update(jsonArray.getObj(0), false, false);
                changed = true;
              }
            }
          }

// If 412 status received, start over -------------------------------------------------------------------------------
// ------------------------------------------------------------------------------------------------------------------

        } while (jsonClient.getStatusCode() == HttpStatus.SC_PRECONDITION_FAILED);

// Get list of folders from server ----------------------------------------------------------------------------------
// ------------------------------------------------------------------------------------------------------------------

        Map<String, JsonObj> remoteFolderIDtoObj = new HashMap<>();
        doReadCommand(MendeleyCmd.readFolders).getObjs().forEach(jObj -> remoteFolderIDtoObj.put(jObj.getStrSafe("id"), jObj));

// Delete local folders not on list ---------------------------------------------------------------------------------
// ------------------------------------------------------------------------------------------------------------------

        new ArrayList<>(keyToColl.keySet()).forEach(collKey ->
        {
          if (remoteFolderIDtoObj.containsKey(collKey) == false)
          {
            // A remote deletion will simply override local changes. Undocument code to change this behavior.

//          MendeleyFolder folder = keyToColl.get(collKey);
//
//          if (folder.isSynced())
//          {
            keyToColl.remove(collKey);
            changed = true;
//          }
//          else
//          {
//            // Perform conflict resolution!
//            noOp();
//          }
          }
        });

// Create new local folders and update existing local folders -------------------------------------------------------
// ------------------------------------------------------------------------------------------------------------------

        remoteFolderIDtoObj.forEach((collKey, jObj) ->
        {
          MendeleyFolder folder = keyToColl.get(collKey);

          if (folder == null)
          {
            folder = (MendeleyFolder) MendeleyEntity.create(MendeleyWrapper.this, jObj);

            if (folder != null)
            {
              keyToColl.put(collKey, folder);
              changed = true;
            }
          }
          else
          {
            if (folder.lastModified().isBefore(parseIso8601(jObj.getStr("modified"))))
              changed = true;

            folder.update(jObj, true, false);
          }
        });

// Locally update whether documents are in the trash (using nonTrashedIDs and trashedIDs lists) ---------------------
// ------------------------------------------------------------------------------------------------------------------

        nonTrashedIDs.forEach(entryKey ->
        {
          if (keyToTrashEntry.remove(entryKey) != null)
            changed = true;
        });

        trashedIDs.forEach(entryKey ->
        {
          if (keyToTrashEntry.containsKey(entryKey))
            return;

          keyToTrashEntry.put(entryKey, keyToAllEntry.get(entryKey));
          changed = true;
        });

        return true;
      }
      catch (HttpResponseException e)
      {
        String msg = "An error occurred while syncing: " + String.valueOf(e.getStatusCode()) + " " + e.getMessage();
        throw new HyperDataException(msg, e);
      }
      catch (UnknownHostException e)
      {
        String msg = "Unable to connect to host: " + e.getMessage();
        throw new HyperDataException(msg, e);
      }
      catch (UnsupportedOperationException | IOException | ParseException e)
      {
        String msg = "An error occurred while syncing: " + e.getMessage();
        throw new HyperDataException(msg, e);
      }
    }};

    return syncTask;
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  private void loadFromJSON(JsonObj jObj)
  {
    jObj.getArray("documents").getObjs().forEach(itemJsonObj ->
    {
      MendeleyEntity entity = MendeleyEntity.create(this, itemJsonObj);

      if (entity == null) return;

      if (entity.getType() == MendeleyEntityType.mendeleyDocument)
      {
        MendeleyDocument document = (MendeleyDocument) entity;

        keyToAllEntry.put(entity.getKey(), document);
      }
    });

    nullSwitch(jObj.getArray("trash"), jArr -> jArr.getObjs().forEach(itemJsonObj ->
    {
      MendeleyEntity entity = MendeleyEntity.create(this, itemJsonObj);

      if (entity == null) return;

      if (entity.getType() == MendeleyEntityType.mendeleyDocument)
      {
        MendeleyDocument document = (MendeleyDocument) entity;

        keyToAllEntry.put(entity.getKey(), document);
        keyToTrashEntry.put(entity.getKey(), document);
      }
    }));

    nullSwitch(jObj.getArray("folders"), jArr -> jArr.getObjs().forEach(collJsonObj ->
    {
      MendeleyEntity entity = MendeleyEntity.create(this, collJsonObj);

      if (entity == null) return;

      if (entity.getType() == MendeleyEntityType.mendeleyFolder)
      {
        MendeleyFolder folder = (MendeleyFolder) entity;

        keyToColl.put(folder.getKey(), folder);
      }
    }));
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  @Override public void loadFromDisk(FilePath filePath) throws FileNotFoundException, IOException, ParseException
  {
    JsonObj jMainObj = null;
    keyToAllEntry.clear();
    keyToTrashEntry.clear();
    keyToColl.clear();

    try (InputStream in = new FileInputStream(filePath.toFile()))
    {
      jMainObj = parseJsonObj(new InputStreamReader(in, UTF_8));
    }
    catch (FileNotFoundException e) { noOp(); }

    if (jMainObj != null)
    {
      String lastSyncTimeStr = db.prefs.get(PREF_KEY_BIB_LAST_SYNC_TIME, "");
      lastSyncTime = lastSyncTimeStr.isBlank() ? Instant.EPOCH : parseIso8601(lastSyncTimeStr);

      loadFromJSON(jMainObj);
    }
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  @Override public void saveToDisk(FilePath filePath)
  {
    JsonObj jMainObj = new JsonObj();

    JsonArray jArr = new JsonArray();

    for (MendeleyDocument entry : getNonTrashEntries())
      entry.saveToDisk(jArr);

    jMainObj.put("documents", jArr);

    jArr = new JsonArray();

    for (MendeleyDocument entry : keyToTrashEntry.values())
      entry.saveToDisk(jArr);

    jMainObj.put("trash", jArr);

    jArr = new JsonArray();

    for (MendeleyFolder coll : keyToColl.values())
      coll.saveToDisk(jArr);

    jMainObj.put("folders", jArr);

    StringBuilder json = new StringBuilder(jMainObj.toString());

    try
    {
      saveStringBuilderToFile(json, filePath);
      db.prefs.put(PREF_KEY_BIB_LAST_SYNC_TIME, dateTimeToIso8601(lastSyncTime));
    }
    catch (IOException e)
    {
      messageDialog("An error occurred while saving bibliographic data to disk.", mtError);
    }
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  @Override public MendeleyDocument addEntry(EntryType newType)
  {
    MendeleyDocument item = new MendeleyDocument(this, newType);

    keyToAllEntry.put(item.getEntryKey(), item);

    return item;
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  @Override public Set<MendeleyDocument> getCollectionEntries(String collKey)
  {
    return getNonTrashEntries().stream().filter(item -> item.getCollKeys(false).contains(collKey))
                                        .collect(ImmutableSet.toImmutableSet());
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  @Override public Set<MendeleyDocument> getNonTrashEntries()
  {
    return keyToAllEntry.values().stream().filter(item -> keyToTrashEntry.containsKey(item.getEntryKey()) == false)
                                          .collect(ImmutableSet.toImmutableSet());
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  @Override public Set<MendeleyDocument> getUnsorted()
  {
    Predicate<MendeleyDocument> predicate = item ->
    {
      if (keyToTrashEntry.containsKey(item.getEntryKey()))
        return false;

      return item.getCollKeys(true).stream().noneMatch(keyToColl::containsKey);
    };

    return keyToAllEntry.values().stream().filter(predicate).collect(ImmutableSet.toImmutableSet());
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  private static EnumHashBiMap<EntryType, String> initTypeMap()
  {
    EnumHashBiMap<EntryType, String> map = EnumHashBiMap.create(EntryType.class);

    map.put(etJournalArticle, "journal");
    map.put(etBook, "book");
    map.put(etOther, "generic");
    map.put(etBookChapter, "book_section");
    map.put(etConferenceProceedings, "conference_proceedings");
    map.put(etWorkingPaper, "working_paper");
    map.put(etReport, "report");
    map.put(etWebPage, "web_page");
    map.put(etThesis, "thesis");
    map.put(etMagazineArticle, "magazine_article");
    map.put(etStatute, "statute");
    map.put(etPatent, "patent");
    map.put(etNewspaperArticle, "newspaper_article");
    map.put(etSoftware, "computer_program");
    map.put(etHearing, "hearing");
    map.put(etTVBroadcast, "television_broadcast");
    map.put(etEncyclopediaArticle, "encyclopedia_article");
    map.put(etCase, "case");
    map.put(etFilm, "film");
    map.put(etBill, "bill");

    return map;
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  private static String formatMendeleyFieldName(String str)
  {
    return titleCase(str.replace('_', ' '));
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  @Override public String getHtml(BibEntryRow row)
  {
    MendeleyDocument item = (MendeleyDocument) nullSwitch(row, null, BibEntryRow::getEntry);
    if (item == null) return "";

    JsonObj jObj  = item.exportJsonObjForUploadToServer();
    StringBuilder html = getHtmlStart();

    jObj.keySet().forEach(key ->
    {
      String fieldName = key;

      switch (fieldName)
      {
        case "profile_id" : case "id"       : case "created"      : case "last_modified" :
        case "group_id"   : case "accessed" : case "citation_key" : case "folder_uuids"  :

          return;

        default : fieldName = formatMendeleyFieldName(fieldName); break;
      }

      switch (jObj.getType(key))
      {
        case OBJECT :

          JsonObj idObj = jObj.getObj("identifiers");
          idObj.keySet().forEach(idType ->
          {
            String typeStr;

            switch (idType)
            {
              case "arxiv" :

                typeStr = "ArXiv";
                break;

              case "doi" : case "isbn" : case "issn" : case "pmid" : case "ssrn" :

                typeStr = idType.toUpperCase();
                break;

              default :

                typeStr = formatMendeleyFieldName(idType);
                break;
            }

            addStringHtml(typeStr, idObj.getStrSafe(idType), html);
          });
          break;

        case ARRAY :

          JsonArray jArr = jObj.getArray(key);

          if (key.equals("authors") || key.equals("editors") || key.equals("translators"))
            addCreatorsHtml(jArr, formatMendeleyFieldName(key.substring(0, key.length() - 1)), html);
          else
            addArrayHtml(fieldName, jArr, html);

          break;

        case STRING :

          addStringHtml(fieldName, jObj.getStrSafe(key), html);
          break;

        case INTEGER :

          addStringHtml(fieldName, jObj.getAsStr(key), html);
          break;

        default:
          break;
      }
    });

    return finishHtml(html);
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  private static void addStringHtml(String fieldName, String str, StringBuilder html)
  {
    if (str.isBlank()) return;

    if (fieldName.equals("Type"))
      str = formatMendeleyFieldName(str);

    addRowToHtml(fieldName, str, html);
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  private static void addCreatorsHtml(JsonArray creatorsArr, String type, StringBuilder html)
  {
    creatorsArr.getObjs().forEach(node ->
    {
      PersonName personName;
      String firstName = ultraTrim(node.getStrSafe("first_name")),
             lastName = ultraTrim(node.getStrSafe("last_name"));

      if ((firstName.length() > 0) || (lastName.length() > 0))
        personName = new PersonName(firstName, lastName);
      else
        return;

      if (personName.isEmpty() == false)
        addRowToHtml(type, personName.getLastFirst(), html);
    });
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  private static void addArrayHtml(String fieldName, JsonArray jArr, StringBuilder html)
  {
    List<String> list;

    if (fieldName.equalsIgnoreCase("websites"))
      list = StreamSupport.stream(jArr.getStrs().spliterator(), false).map(str -> anchorTag(str, str)).collect(Collectors.toList());
    else
      list = Lists.newArrayList((Iterable<String>)jArr.getStrs());

    addRowsToHtml(fieldName, list, html);
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

}
