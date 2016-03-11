package models.core;

import com.google.common.collect.Maps;
import models.utility.Config;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public class LSearcher {

    public IndexSearcher getSearcher() {
        return searcher;
    }

    private IndexSearcher searcher;
    private Analyzer analyzer;
    private DirectoryReader reader;

    public LSearcher() {
        analyzer = new LAnalyzer();
        try {
            Directory directory = FSDirectory.open(Paths.get(Config.indexRoot));
            reader = DirectoryReader.open(directory);
            searcher = new IndexSearcher(reader);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public Map<String, ScoreDoc[]> search(List<String> fields, String queryString) {
        Map<String, ScoreDoc[]> hitsDocMap = Maps.newHashMap();
        for (String fieldName : fields) {
            QueryParser parser = new QueryParser(fieldName, analyzer);
            parser.setAllowLeadingWildcard(true);
            try {
                Query query = parser.parse(queryString);
                BooleanQuery booleanQuery = new BooleanQuery.Builder().add(query, BooleanClause.Occur.MUST).build();
                TopDocs topDocs = searcher.search(booleanQuery, Config.topN);
                ScoreDoc[] scoreDocs = topDocs.scoreDocs;
                hitsDocMap.put(fieldName, scoreDocs);
            } catch (ParseException | IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
        return hitsDocMap;
    }

    public void close() {
        try {
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

}