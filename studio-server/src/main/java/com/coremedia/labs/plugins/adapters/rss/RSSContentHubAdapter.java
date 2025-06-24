package com.coremedia.labs.plugins.adapters.rss;

import com.coremedia.contenthub.api.*;
import com.coremedia.contenthub.api.column.ColumnProvider;
import com.coremedia.contenthub.api.exception.ContentHubException;
import com.coremedia.contenthub.api.pagination.PaginationRequest;
import com.coremedia.contenthub.api.search.ContentHubSearchResult;
import com.coremedia.contenthub.api.search.ContentHubSearchService;
import com.coremedia.contenthub.api.search.Sort;
import com.coremedia.labs.plugins.adapters.rss.imageurlextractor.FeedImageExtractor;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndPerson;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


class RSSContentHubAdapter implements ContentHubAdapter, ContentHubSearchService {
  private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final RSSContentHubTransformer rssContentHubTransformer;
  private final FeedImageExtractor feedImageExtractor;
  private final RSSContentHubSettings settings;
  private final String connectionId;
  private SyndFeed feed;
  private final RSSColumnProvider columnProvider;

  RSSContentHubAdapter(@NonNull RSSContentHubTransformer rssContentHubTransformer,
                       @NonNull FeedImageExtractor feedImageExtractor,
                       @NonNull RSSContentHubSettings settings,
                       @NonNull String connectionId) {
    this.rssContentHubTransformer = rssContentHubTransformer;
    this.feedImageExtractor = feedImageExtractor;
    this.settings = settings;
    this.connectionId = connectionId;
    columnProvider = new RSSColumnProvider();
  }

  @Override
  public Optional<ContentHubSearchService> searchService() {
    return Optional.of(this);
  }

  @Override
  public ContentHubSearchResult search(String term, @Nullable Folder folder, @Nullable ContentHubType contentHubType, Collection<String> collection, List<Sort> list, int limit) {
    List<ContentHubObject> collect = new ArrayList<>();
    int hits = 0;
    List<SyndEntry> entries = getFeed().getEntries();
    for (SyndEntry entry : entries) {
      ContentHubObjectId id = new ContentHubObjectId(connectionId, entry.getUri());
      if (matches(term, entry)) {
        hits++;
        if (limit <= 0 || collect.size() < limit) {
          collect.add(new RSSItem(feedImageExtractor, id, getFeed(), entry));
        }
      }
    }
    return new ContentHubSearchResult(collect, hits);
  }

  @NonNull
  @Override
  public Folder getRootFolder(@NonNull ContentHubContext context) throws ContentHubException {
    String displayName = settings.getDisplayName();
    if (StringUtils.isEmpty(displayName)) {
      displayName = settings.getUrl();
    }
    ContentHubObjectId rootId = new ContentHubObjectId(connectionId, connectionId);
    return new RSSFolder(rootId, getFeed(), displayName);
  }

  @Nullable
  @Override
  public Item getItem(@NonNull ContentHubContext context, @NonNull ContentHubObjectId id) throws ContentHubException {
    List<SyndEntry> entries = getFeed().getEntries();
    String externalId = id.getExternalId();
    for (SyndEntry entry : entries) {
      if (entry.getUri().equals(externalId)) {
        return new RSSItem(feedImageExtractor, id, getFeed(), entry);
      }
    }
    return null;
  }

  @Nullable
  @Override
  public Folder getFolder(@NonNull ContentHubContext context, @NonNull ContentHubObjectId id) throws ContentHubException {
    return getRootFolder(context);
  }

  @NonNull
  @Override
  public GetChildrenResult getChildren(@NonNull ContentHubContext context, @NonNull Folder folder, @Nullable PaginationRequest paginationRequest) {
    List<ContentHubObject> result = new ArrayList<>();
    List<SyndEntry> entries = getFeed().getEntries();
    for (SyndEntry entry : entries) {
      ContentHubObjectId id = new ContentHubObjectId(connectionId, entry.getUri());
      result.add(new RSSItem(feedImageExtractor, id, getFeed(), entry));
    }
    return new GetChildrenResult(result);
  }

  @Nullable
  @Override
  public Folder getParent(@NonNull ContentHubContext context, @NonNull ContentHubObject contentHubObject) throws ContentHubException {
    if (!contentHubObject.getId().equals(getRootFolder(context).getId())) {
      return getRootFolder(context);
    }
    return null;
  }

  @Override
  @NonNull
  public ContentHubTransformer transformer() {
    return rssContentHubTransformer;
  }

  @NonNull
  private synchronized SyndFeed getFeed() {
    if (this.feed != null) {
      return this.feed;
    }
    this.feed = getFeedInternal();
    return this.feed;
  }

  private SyndFeed getFeedInternal() {
    String url = settings.getUrl();
    if (url == null) {
      String msg = "Error reading url for RSS stream with id:" + connectionId;
      LOGGER.error(msg);
      throw new ContentHubException(msg);
    }
    try {
      String proxyHost = settings.getProxyHost();
      String proxyType = settings.getProxyType();
      Integer proxyPort = settings.getProxyPort();

      SyndFeedInput input = new SyndFeedInput();

      URL feedSource = new URL(url);

      if (proxyType != null && proxyHost != null && proxyPort != null) {
        Proxy proxy = new Proxy(Proxy.Type.valueOf(proxyType.toUpperCase()), new InetSocketAddress(proxyHost, proxyPort));
        URLConnection urlConnection = feedSource.openConnection(proxy);
        XmlReader.setDefaultEncoding("utf8");
        XmlReader reader = new XmlReader(urlConnection);
        return input.build(reader);
      } else {
        XmlReader.setDefaultEncoding("utf8");
        XmlReader reader = new XmlReader(feedSource);
        return input.build(reader);
      }
    } catch (Exception e) {
      String msg = "Error reading RSS stream '" + url + "': " + e.getMessage();
      LOGGER.error("{}, see DEBUG log for details.", msg);
      if (LOGGER.isDebugEnabled()) {
        String plainXML = getFeedXML(url);
        LOGGER.debug("{}, feed XML:\n{}", msg, plainXML, e);
      }
      throw new ContentHubException(msg);
    }
  }
  /**
   * Just a helper to detect what went wrong when parsing RSS feed.
   * E.g. the result may be a 301 moved permanently or contain a special character
   * that is not parsable, even with utf8.
   */
  private String getFeedXML(String url) {
    StringBuilder response = new StringBuilder();
    try {
      URLConnection connection = new URL(url).openConnection();
      BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
      String inputLine;
      while ((inputLine = in.readLine()) != null) {
        response.append(inputLine);
        response.append(System.getProperty("line.separator"));
      }
      in.close();
    } catch (Exception e) {
      //ignore
    }
    return response.toString();
  }


  //------------------------ Helper ------------------------------------------------------------------------------------

  private boolean matches(String term, SyndEntry entry) {
    if (match(term, entry.getTitle())) {
      return true;
    }
    if (entry.getDescription() != null && match(term, entry.getDescription().getValue())) {
      return true;
    }
    if (match(term, entry.getAuthor())) {
      return true;
    }
    if (entry.getAuthors() != null && match(term, entry.getAuthors().stream().map(SyndPerson::getName).collect(Collectors.joining(" ")))) {
      return true;
    }
    return false;
  }

  private boolean match(String term, String value) {
    if (value != null) {
      return value.toLowerCase().contains(term.toLowerCase());
    }
    return false;
  }

  @NonNull
  @Override
  public ColumnProvider columnProvider() {
    return columnProvider;
  }
}
