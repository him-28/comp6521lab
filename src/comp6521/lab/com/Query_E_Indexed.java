package comp6521.lab.com;

import java.util.ArrayList;
import java.util.Arrays;

import comp6521.lab.com.Pages.NationPage;
import comp6521.lab.com.Pages.PartSuppPage;
import comp6521.lab.com.Pages.SupplierPage;
import comp6521.lab.com.Records.FloatRecordElement;
import comp6521.lab.com.Records.IntegerRecordElement;
import comp6521.lab.com.Records.Record;
import comp6521.lab.com.Records.StringRecordElement;
import comp6521.lab.com.Util.DB;
import comp6521.lab.com.Util.ProcessingFunction;
import comp6521.lab.com.Util.RecordNumberToKeyPF;
import comp6521.lab.com.Util.key_page;

public class Query_E_Indexed extends Query_E 
{
	public void ProcessQuery(String innerName, String outerName)
	{
		Log.StartLog("e_i.out");
		///////////////////////////
		// Zero : Create indexes //
		///////////////////////////
		// n_name in Nation table
		BPlusTree< NationPage, StringRecordElement > NationNameIndex = IndexManager.getInstance().getNationNameIndex();
		
		// s_nationKey in Supplier (used in query C indexed)
		BPlusTree< SupplierPage, IntegerRecordElement > SupplierFKIndex = IndexManager.getInstance().getSupplierFKIndex();
		
		// ps_suppKey in PartSupp, used in query C indexed
		BPlusTree< PartSuppPage, IntegerRecordElement > PartSuppSuppFKIndex = IndexManager.getInstance().getPartSuppSuppFKIndex();
		
		////////////////////////////
		// Perform query          //
		////////////////////////////
		// First, the inner query
		Log.StartLogSection("Compute the total values (inner query)");
		double totalValue = PerformSubquery(innerName, true, NationNameIndex, SupplierFKIndex, PartSuppSuppFKIndex);
		Log.EndLogSection();
		// Second, perform the outer query (grouping)
		Log.StartLogSection("Perform the outer query");
		PerformSubquery(outerName, false, NationNameIndex, SupplierFKIndex, PartSuppSuppFKIndex);
		Log.EndLogSection();
		/////////////////
		// Perform sort//
		/////////////////
		TPMMS<?> sort = new TPMMS<QE_Page>(QE_Page.class, "qei_f.tmp");
		Log.StartLogSection("Sorting the subset of records kept");
		String sortedFilename = sort.Execute();
		Log.EndLogSection();
		MemoryManager.getInstance().AddPageType(QE_Page.class, sortedFilename);
		
		// Perform third pass
		MemoryManager.getInstance().AddPageType( QEGroups_Page.class, "qeig_f.tmp" );
		Log.StartLogSection("Grouping the results");
		ThirdPass( totalValue, sortedFilename, "qeig_f.tmp" );
		Log.EndLogSection();
		
		// Fourth pass: sort groups by value, descending order
		sort = new TPMMS<QEGroups_Page>( QEGroups_Page.class, "qeig_f.tmp");
		Log.StartLogSection("Sorting the groups by decreasing value");
		String groupedSorted = sort.Execute();
		Log.EndLogSection();
		MemoryManager.getInstance().AddPageType(QEGroups_Page.class, groupedSorted);
		
		
		// Last : output results
		Log.StartLogSection("Outputting results");
		OutputResults( groupedSorted );
		Log.EndLogSection();
		
		Log.EndLog();
	}
	
	protected double PerformSubquery( String name, boolean isInner, BPlusTree<?,?> NationNameIndex, BPlusTree<?,?> SupplierFKIndex, BPlusTree<?,?> PartSuppSuppFKIndex )
	{
		// First, find the n_nationKey for the UNITED_STATES
		StringRecordElement NN = new StringRecordElement(15);
		NN.setString(name);
		
		String prefix = "tmp_";
		if( isInner )
			prefix += "inner_";
		else
			prefix += "outer_";
		
		Log.StartLogSection("Find nations RN that match the name " + name );
		int[] nations = NationNameIndex.Get(NN);
		Log.EndLogSection();
		Arrays.sort(nations);
		
		// Record number -> n_nationKey
		RecordNumberToKeyPF<NationPage> NtNKpf = new RecordNumberToKeyPF<NationPage>(nations, NationPage.class, "n_nationKey", prefix + "nation_keys.tmp");
		Log.StartLogSection("Getting all nation keys (n_nationKey) from the nations RN");
		DB.ProcessingLoop(NtNKpf);
		Log.EndLogSection();
		// ATTN:: NtNKpf is not freed yet.
		
		// Second, find all suppliers in the united states
		// nation key(s) -> suppliers record numbers
		Log.StartLogSection("Find all suppliers RN that match the nation key (s_nationKey == n_nationKey)");
		int[] suppliersRN = DB.ReverseProcessingLoop( NtNKpf.keys, key_page.class, SupplierFKIndex, "key");
		Log.EndLogSection();
		// supplier record numbers -> supplier keys
		RecordNumberToKeyPF<SupplierPage> StSKpf = new RecordNumberToKeyPF<SupplierPage>(suppliersRN, SupplierPage.class, "s_suppKey", prefix + "supp_keys.tmp");
		Log.StartLogSection("Getting all suppliers keys from the suppliers RN");
		DB.ProcessingLoop(StSKpf);
		Log.EndLogSection();
		// ATTN:: StSKpf is not free yet
		// supplier keys -> ps record numbers
		Log.StartLogSection("Find all partsupp RN that match the supplier keys");
		int[] psRN = DB.ReverseProcessingLoop( StSKpf.keys , key_page.class, PartSuppSuppFKIndex, "key");
		Log.EndLogSection();
		Arrays.sort(psRN);
		
		// Once we have the matching record numbers, we can free the pages we were taking.
		NtNKpf.Clear(false);
		StSKpf.Clear(false);
		
		// Third : Pre-compute the total value && write kept values --> qei_f.txt
		PartSuppToTotalPrice PStTPpf = new PartSuppToTotalPrice( psRN, isInner );
		
		if(isInner)
			Log.StartLogSection("Computing the total price from the partSupp RN found");
		else
			Log.StartLogSection("Outputting the result subset (ps_partKey, value) from the partSupp RN found");
		
		DB.ProcessingLoop( PStTPpf );
		
		Log.EndLogSection();
		
		return PStTPpf.totalValue;
	}
}

class PartSuppToTotalPrice extends ProcessingFunction<PartSuppPage, FloatRecordElement>
{
	boolean m_isInner;
	QE_Page page;
	public double totalValue;
	
	public PartSuppToTotalPrice( int[] input, boolean isInner ) 
	{ 
		super( input, PartSuppPage.class ); 
		m_isInner = isInner;
		
		if( !m_isInner )
		{
			// Create new page type, create an empty page.
			MemoryManager.getInstance().AddPageType( QE_Page.class, "qei_f.tmp" );
			page = MemoryManager.getInstance().getEmptyPage( QE_Page.class, "qei_f.tmp");
		}
	}	
	
	public void  ProcessStart()      { totalValue = 0; }
	public void  Process( Record r ) 
	{ 
		double value = r.get("ps_supplyCost").getFloat() * (double)r.get("ps_availQty").getInt();
		
		if( !m_isInner )
		{
			// Create new record
			QE_Record qe = new QE_Record();
			qe.get("ps_partKey").set( r.get("ps_partKey"));
			qe.get("value").setFloat(value);
			page.AddRecord(qe);
		}
		
		totalValue +=  value;
	}
	public int[] EndProcess()        
	{ 
		if( !m_isInner )
			MemoryManager.getInstance().freePage(page);
		return null; 
	}	
}
