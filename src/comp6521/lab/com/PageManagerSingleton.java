/**
 * September 27, 2009
 * COMP6521 Lab
 * PageManagerSingleton.java: class that retrieves a page of records from a relation data text file. 
 */
package comp6521.lab.com;

/**
 * @author dimitri.tiago
 *
 */

import java.io.FileReader;		
import java.io.IOException;		

public class PageManagerSingleton 
{	
	// TODO: method to return size of cbuf array	
	// TODO: file writer methods (i.e. write pages) 
	// TODO: method to map page records to record objects?
	// TODO: get file information (i.e. File class)?
	
	private static final PageManagerSingleton INSTANCE = new PageManagerSingleton(); 	// page manager singleton
	
	private String path;	// store file path 		 
	
	// default constructor
	private PageManagerSingleton()
	{
		path = "C:\\";		// set default path	 
	}
	
	// retrieve singleton instance with default 
	public static PageManagerSingleton getInstance()
	{
		return INSTANCE;
	}	
	
	// set path instance variable
	public void setPath(String path)
	{
		this.path = path;
	}
	
	// get path instance variable
	public String getPath(String path)
	{
		return this.path;
	}
	
	public final int getNumberOfRecordsPerPage() { return 10; }
	
	// get page from disk. a page is equal to 10 records.
	// preconditions: recordSize, pageNumber to retrieve, fileName containing relation data.
	// post-conditions: byte array containing read bytes 
	public char[] getPage(String fileName, int recordSize, int pageNumber)
	{
		int pageSize = recordSize*10;			// page size
		char[] cbuf = new char[recordSize*10];	// store data from disk	
		
		try 
		{
			FileReader file = new FileReader(fileName);		// open file for reading
			file.skip(pageSize*pageNumber);			    	// move to appropriate page number
			file.read(cbuf);								// read data into character buffer
			file.close();									// close file
		}
		catch(IOException io)
		{
			// print error stack trace
			io.printStackTrace(System.err);
		}
		
		// return character buffer 
		return cbuf;
	}

	// write page to disk
	public void writePage(String filename, char[] cbuf, int recordSize)
	{
		// TODO	
	}
}