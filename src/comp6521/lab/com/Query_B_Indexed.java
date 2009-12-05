package comp6521.lab.com;

import comp6521.lab.com.Hashing.HashFunction;
import comp6521.lab.com.Pages.CustomerPage;
import comp6521.lab.com.Records.CustomerRecord;
import comp6521.lab.com.Records.RecordElement;
import comp6521.lab.com.Records.StringRecordElement;

public class Query_B_Indexed extends Query_B 
{
	public void PerformQuery( String[] SelList, String[] AvgList )
	{
		// First, use a hash table for the cntrycode
		LinearHashTable< CustomerPage > index = new LinearHashTable< CustomerPage >();
		CountryHashFunction cntry_hf = new CountryHashFunction();
		index.CreateHashTable( CustomerPage.class, "customer_cntrycode.txt", "c_phone", cntry_hf );
		
		// Perform the inner query ...
		int countAvg = 0;
		double avgBalance = 0;
		
		// String length of 3 == "x12"
		StringRecordElement el = new StringRecordElement(3);
		
		CustomerPage page = null;
				
		for(int k = 0; k < AvgList.length; k++ )
		{
			el.setString( AvgList[k]);
			int[] pageList = index.getPageList( el );
			
			for( int p = 0; p < pageList.length; p++ )
			{
				page = MemoryManager.getInstance().getPage( CustomerPage.class, pageList[p], "customer_cntrycode.txt" );
				CustomerRecord[] customers = page.m_records;

				for( int r = 0; r < customers.length; r++ )
				{
					// Check if the record matches the conditions
					if( customers[r].get("c_phone").getString().substring(0,2).compareTo(AvgList[k]) == 0 &&
						customers[r].get("c_acctBal").getFloat() > 0 )
					{
						countAvg++;
						avgBalance += customers[r].get("c_acctBal").getFloat();
					}							
				}
				
				MemoryManager.getInstance().freePage(page);
			}			
		}
		
		if( countAvg > 0 )
			avgBalance /= (double)countAvg;
		
		// Perform the outer query ...
		// First step: print the header of the results
		System.out.println("cntrycode\tc_acctbal");
		
		// Now, perform the main query
		page = null;
		
		for( int k = 0; k < SelList.length; k++ )
		{
			el.setString( SelList[k]);
			int[] pageList = index.getPageList( el );
			
			for( int p = 0; p < pageList.length; p++ )
			{
				page = MemoryManager.getInstance().getPage( CustomerPage.class, pageList[p], "customer_cntrycode.txt" );
				CustomerRecord[] customers = page.m_records;

				for( int r = 0; r < customers.length; r++ )
				{				
					// Check if the record matches the conditions
					if( customers[r].get("c_phone").getString().substring(0,2).compareTo(SelList[k]) == 0 &&
						customers[r].get("c_acctBal").getFloat() > avgBalance )
					{
						// Print record info
						System.out.println(customers[r].get("c_phone").getString().substring( 0, 2) + "\t" + customers[r].get("c_acctBal").getFloat() );
					}					
				}
				
				MemoryManager.getInstance().freePage(page);
			}
		}		
	}
}

class CountryHashFunction extends HashFunction
{
	public int Hash( RecordElement el )
	{
		return Integer.parseInt(el.getString().substring(0, 2));
	}
}