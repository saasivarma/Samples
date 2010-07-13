package com.devwebsphere;
/**
 * A simple example of an in-memory search using Lucene.
 */
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import junit.framework.Assert;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexFileNameFilter;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ChecksumIndexInput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.NIOFSDirectory;

import com.devwebsphere.wxslucene.GridDirectory;
import com.devwebsphere.wxsutils.WXSUtils;
import com.ibm.websphere.objectgrid.ObjectGrid;
import com.ibm.websphere.objectgrid.ObjectGridException;
import com.ibm.websphere.objectgrid.plugins.TransactionCallbackException;

public class InMemoryExample 
{
	static final int BUFFER_SIZE = 16384;
	  public static void copy(Directory src, GridDirectory dest, boolean closeDirSrc) throws IOException {
		    final String[] files = src.listAll();
		    dest.setAsyncEnabled(true);
		    dest.setCompressionEnabled(true);
		    try
		    {
			    IndexFileNameFilter filter = IndexFileNameFilter.getFilter();
	
			    byte[] buf = new byte[BUFFER_SIZE];
			    for (int i = 0; i < files.length; i++) {
	
			      if (!filter.accept(null, files[i]))
			        continue;
	
			      IndexOutput os = null;
			      ChecksumIndexInput is = null;
			      try {
			        // create file in dest directory
			        os = dest.createOutput(files[i]);
			        // read current file
			        is = new ChecksumIndexInput(src.openInput(files[i]));
			        // and copy to dest directory
			        long len = is.length();
			        long readCount = 0;
			        while (readCount < len) {
			          int toRead = readCount + BUFFER_SIZE > len ? (int)(len - readCount) : BUFFER_SIZE;
			          is.readBytes(buf, 0, toRead);
			          os.writeBytes(buf, toRead);
			          readCount += toRead;
			        }
			        long src_sum = is.getChecksum();
			        os.flush();

			        // this code can just compare the new file with the old one
			        // to make sure it's copied correctly
			        ChecksumIndexInput dst_check_stream = new ChecksumIndexInput(dest.openInput(files[i]));
	
			        long dst_length = dst_check_stream.length();
			        Assert.assertEquals(len, dst_length);
			        len = dst_check_stream.length();
			        readCount = 0;
			        while(readCount < len) {
			            int toRead = readCount + BUFFER_SIZE > len ? (int)(len - readCount) : BUFFER_SIZE;
			            dst_check_stream.readBytes(buf, 0, toRead);
			            readCount += toRead;
			        }
			        long dst_sum = dst_check_stream.getChecksum();
			        Assert.assertEquals(src_sum, dst_sum);
			      } finally {
			        // graceful cleanup
			        try {
			          if (os != null)
			            os.close();
			        } finally {
			          if (is != null)
			            is.close();
			        }
			      }
			    }
			    if(closeDirSrc)
			      src.close();
		    }
		    finally
		    {
		    	dest.setAsyncEnabled(false);
		    }
		  }


    @SuppressWarnings("deprecation")
    public static void main(String[] args) throws TransactionCallbackException, ObjectGridException, IOException {
        // Construct a RAMDirectory to hold the in-memory representation
        // of the index.
        
        ObjectGrid grid = WXSUtils.startTestServer("Grid", "/objectgrid.xml", "/deployment.xml");
        ExecutorService threadPool = Executors.newFixedThreadPool(32);
        WXSUtils utils = new WXSUtils(grid, threadPool);
        GridDirectory idx = new GridDirectory(utils, "test");
        // enable zip compression of blocks
        idx.setCompressionEnabled(true);
        idx.setAsyncEnabled(true);
    	File file = new File("/Users/ibm/Downloads/index_hs0_2");
    	Directory diskidx = NIOFSDirectory.getDirectory(file);
    	// This copy version checks all copied files are the same as the inputs
    	InMemoryExample.copy(diskidx, idx, true);
    	idx.setAsyncEnabled(false);
    	

        try {
//            // Make an writer to create the index
//            IndexWriter writer =
//                new IndexWriter(idx, new StandardAnalyzer(), true, new IndexWriter.MaxFieldLength(5000));
//
//            // Add some Document objects containing quotes
//            writer.addDocument(createDocument("Theodore Roosevelt",
//                "It behooves every man to remember that the work of the " +
//                "critic, is of altogether secondary importance, and that, " +
//                "in the end, progress is accomplished by the man who does " +
//                "things."));
//            writer.addDocument(createDocument("Friedrich Hayek",
//                "The case for individual freedom rests largely on the " +
//                "recognition of the inevitable and universal ignorance " +
//                "of all of us concerning a great many of the factors on " +
//                "which the achievements of our ends and welfare depend."));
//            writer.addDocument(createDocument("Ayn Rand",
//                "There is nothing to take a man's freedom away from " +
//                "him, save other men. To be free, a man must be free " +
//                "of his brothers."));
//            writer.addDocument(createDocument("Mohandas Gandhi",
//                "Freedom is not worth having if it does not connote " +
//                "freedom to err."));
//
//            // Optimize and close the writer to finish building the index
//            writer.optimize();
//            writer.close();

            // Build an IndexSearcher using the in-memory index
            Searcher searcher = new IndexSearcher(idx);

            // Run some queries
//            search(searcher, "britney");
//            search(searcher, "free");
//            search(searcher, "progress or achievements");
            search(searcher, "a*");

            searcher.close();
        }
        catch(IOException ioe) {
            // In this example we aren't really doing an I/O, so this
            // exception should never actually be thrown.
            ioe.printStackTrace();
        }
        catch(ParseException pe) {
            pe.printStackTrace();
        }
    }

    /**
     * Make a Document object with an un-indexed title field and an
     * indexed content field.
     */
    private static Document createDocument(String title, String content) {
        Document doc = new Document();

        // Add the title as an unindexed field...
      
        doc.add(new Field("title", title, Field.Store.YES, Field.Index.NO));

        // ...and the content as an indexed field. Note that indexed
        // Text fields are constructed using a Reader. Lucene can read
        // and index very large chunks of text, without storing the
        // entire content verbatim in the index. In this example we
        // can just wrap the content string in a StringReader.               
        doc.add(new Field("content", new StringReader(content)));

        return doc;
    }

    /**
     * Searches for the given string in the "content" field
     */
    private static void search(Searcher searcher, String queryString)
        throws ParseException, IOException {

        QueryParser qp = new QueryParser("content", new StandardAnalyzer());
        // Build a Query object
        Query query = qp.parse(queryString);
        
        // Search for the query
        TopDocs td = searcher.search(query, 100);        

        // Examine the Hits object to see if there were any matches
        int hitCount = td.totalHits;
        if (hitCount == 0) {
            System.out.println(
                "No matches were found for \"" + queryString + "\"");
        }
        else {
            System.out.println("Hits for \"" +
                queryString + "\" were found in quotes by:");

            // Iterate over the Documents in the Hits object
            for (ScoreDoc hit : td.scoreDocs) {
                Document doc = searcher.doc(hit.doc);

                // Print the value that we stored in the "title" field. Note
                // that this Field was not indexed, but (unlike the
                // "contents" field) was stored verbatim and can be
                // retrieved.
                System.out.println("  " + doc.get("title"));
            }
        }
        System.out.println();
    }
    
}