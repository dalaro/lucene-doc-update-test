package com.thinkaurelius.lucenetest;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

public class TestLucene {

    private static final Version LUCENE_VERSION = Version.LUCENE_41;
    private static final Analyzer ANALYZER = new StandardAnalyzer(LUCENE_VERSION);
    private static final File DATA_DIR = new File("target" + File.separator  + "indextmp");
    private static final String DOCID_FIELD_NAME = "_____elementid";
    private static final String NUM_FIELD_NAME = "ft1";
    private static final Logger log = LoggerFactory.getLogger(TestLucene.class);

    @Before
    public void deleteAndRemakeDataDir() throws IOException {
        FileUtils.deleteDirectory(DATA_DIR);
        DATA_DIR.mkdirs();
    }

    @Test
    public void testUpdateDocument() throws IOException {
        IndexWriter writer = getWriter();
        DirectoryReader reader = DirectoryReader.open(writer, true);
        IndexSearcher searcher = new IndexSearcher(reader);

        String docid = "7gg-sg-7x1-1ds";

        Document d = retrieveOrCreate(docid, searcher);
        long longValue1 = 10;
        LongField lf1 = new LongField(NUM_FIELD_NAME, longValue1, Field.Store.YES);
        d.add(lf1);
        writer.updateDocument(new Term(DOCID_FIELD_NAME, docid), d);

        assertTrue(writer.hasUncommittedChanges());

        writer.commit();


        // Reopen reader manually...
        reader.close();
        reader = DirectoryReader.open(writer, true);
        // ...or conditionally -- behavior seems the same
//        DirectoryReader newReaderOrNull = DirectoryReader.openIfChanged(reader);
//        if (null != newReaderOrNull) {
//            reader = newReaderOrNull;
//        } else {
//            fail();
//        }
        searcher = new IndexSearcher(reader);

        assertEquals(1, countAllDocuments(searcher));
        assertEquals(1, countDocIDTermHits(searcher, docid));

        d = retrieveOrCreate(docid, searcher);
        d.removeFields(NUM_FIELD_NAME);
        long longValue2 = 20;
        LongField lf2 = new LongField(NUM_FIELD_NAME, longValue2, Field.Store.YES);
        d.add(lf2);

        for (IndexableField f : d.getFields()) {
            log.info("Updated document will contain field: {}", f);
        }

        writer.updateDocument(new Term(DOCID_FIELD_NAME, docid), d);

        writer.commit();
        // Close & reopen seems to have no effect
//        writer.close();
//        writer = getWriter();

        // Reopen reader manually...
        reader.close();
        reader = DirectoryReader.open(writer, true);
        // ...or conditionally -- behavior seems the same
//        newReaderOrNull = DirectoryReader.openIfChanged(reader);
//        if (null != newReaderOrNull) {
//            reader = newReaderOrNull;
//        } else {
//            fail();
//        }
        searcher = new IndexSearcher(reader);

        assertEquals(1, countAllDocuments(searcher));
        assertEquals(1, countDocIDTermHits(searcher, docid));
    }

    private int countAllDocuments(IndexSearcher searcher) throws IOException {
        return countQueryMatches(searcher, new MatchAllDocsQuery());
    }

    private int countDocIDTermHits(IndexSearcher searcher, String docid) throws IOException {
        return countQueryMatches(searcher, new TermQuery(new Term(DOCID_FIELD_NAME, docid)));
    }

    private int countQueryMatches(IndexSearcher searcher, Query q) throws IOException {
        ScoreDoc[] docs = searcher.search(q, 100).scoreDocs;
        if (1 == docs.length) {
            log.info("Doc matched {}: {}", q, searcher.doc(docs[0].doc));
        }
        log.info("Hit count for query {}: {}", q, docs.length);
        return docs.length;
    }

    private IndexWriter getWriter() throws IOException {
        IndexWriterConfig iwc = new IndexWriterConfig(LUCENE_VERSION, ANALYZER);
        iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        return new IndexWriter(FSDirectory.open(DATA_DIR), iwc);
    }

    private Document retrieveOrCreate(String docID, IndexSearcher searcher) throws IOException {
        Document doc;
        TopDocs hits = searcher.search(new TermQuery(new Term(DOCID_FIELD_NAME, docID)), 10);

        if (hits.scoreDocs.length > 1)
            throw new IllegalArgumentException("More than one document found for document id: " + docID);

        if (hits.scoreDocs.length == 0) {
            log.debug("Creating new document for {}={}", DOCID_FIELD_NAME, docID);

            doc = new Document();
            doc.add(new StringField(DOCID_FIELD_NAME, docID, Field.Store.YES));
        } else {
            log.debug("Updating existing document for {}={}", DOCID_FIELD_NAME, docID);

            int docId = hits.scoreDocs[0].doc;
            //retrieve the old document
            doc = searcher.doc(docId);
        }

        return doc;
    }
}