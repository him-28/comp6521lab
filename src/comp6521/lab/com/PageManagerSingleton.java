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

import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;	
import java.io.IOException;
import java.io.RandomAccessFile;

public class PageManagerSingleton 
{	
	// TODO: method to return size of cbuf array	 
	// TODO: get file information (i.e. File class)?
	// TODO: handle case where page is out of bound. (i.e. program should not crash)
	
	private static final PageManagerSingleton INSTANCE = new PageManagerSingleton(); 	// page manager singleton
	
	private long readIO;
	private long writeIO;
	private String path;		// store file path 		 
	
	// default constructor
	private PageManagerSingleton()
	{
		readIO = 0;
		writeIO = 0;
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
	
	public long getReadIOCount() { return readIO; }
	public long getWriteIOCount() { return writeIO; }
		
	// get page from disk. a page is equal to 10 records.
	// preconditions: recordSize, pageNumber to retrieve, fileName containing relation data.
	// post-conditions: byte array containing read bytes 
	public char[] getRawPage(String fileName, int pageSize, int pageNumber)
	{
		char[] cbuf = new char[pageSize];	// store data from disk	
						
		try 
		{
			FileReader file = new FileReader(path+fileName);		// open file for reading
				
			file.skip(pageSize*pageNumber);			    	        // move to appropriate page number
			
			if( !file.ready() ) 									// if we're off the file (skipped too far)
				cbuf = null;
			else
				file.read(cbuf);		    						// read data into character buffer
							
			file.close();											// close file
			readIO++;
		}
		catch(IOException io)
		{
			// print error stack trace
			//io.printStackTrace(System.err);
			cbuf = null;
		}
		
		// return character buffer 
		return cbuf;
	}
	
	public void writeOutput(String filename, String cbuf)
	{
		try
		{
			// append String file
			FileWriter file = new FileWriter(path + filename, true);
			file.write(cbuf);
			file.close();
		}
		catch(IOException ioException)
		{
			ioException.printStackTrace();
		}
	}

	// two argument write page to disk
	public void writePage(String filename, String cbuf)
	{
		try
		{
			// append String file
			FileWriter file = new FileWriter(path + filename, true);
			file.write(cbuf);
			file.close();
			writeIO++;
		}
		catch(IOException ioException)
		{
			ioException.printStackTrace();
		}
	}
	
	// four argument write page to disk
	public void writePage(String filename, int pageSize, int pageNumber, String cbuf)
	{
		try
		{
			RandomAccessFile file = new RandomAccessFile( path + filename, "rw" );
			file.seek(pageSize * pageNumber);
			file.writeBytes(cbuf);
			file.close();
			writeIO++;
		}
		catch(IOException ioException)
		{
			ioException.printStackTrace();
		}
	}
	
	public long getLength(String filename)
	{
		long length = 0;
		try
		{
			RandomAccessFile file = new RandomAccessFile( path + filename, "r" );
			length = file.length();
			file.close();
		}
		catch(IOException ioException)
		{
			// Do nothing, since the file may or may not exist
		}
		return length;
	}
	
	public void deleteFileType(String ext)
	{
		File file = new File(path);	// get file object for path 
		File[] dirFiles = file.listFiles();	// get list of files in current path
			
		// delete .tmp files
		for ( File currFile : dirFiles )
		{
			String fName    = currFile.getName();	// get filename
			int lastIdx = fName.lastIndexOf(".");
			
			if( lastIdx < 0 )
				continue;
			
			String fNameExt = fName.substring( lastIdx );	// get file extension
			
			if ( ext.compareToIgnoreCase(fNameExt) == 0)	// match found
			{
				currFile.delete();	// delete file
			}
		}			
	}
	
	// delete temporary files
	public void deleteTmpFiles()
	{
		deleteFileType(".tmp");
	}
	
	public void deleteIdxFiles()
	{
		deleteFileType(".idx");
	}
	
	public void deleteFile(String outputFilename)
	{
		File file = new File(path + outputFilename);
		if( file.exists() )
			file.delete();
	}
}