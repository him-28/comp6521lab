package comp6521.lab.com;

import comp6521.lab.com.Pages.Page;
import comp6521.lab.com.Records.*;

public class BPlusTree<T extends Page<?>, S extends RecordElement > {
	boolean m_treeCreated;
	BPlusTreeNode<S> m_root;
	
	Class<T> m_pageType;
	Class<S> m_recordElementClass;
	String   m_filename;

	String   m_key;	
	int      m_n;

	public BPlusTree()
	{
		m_treeCreated = false;
	}
	
	public void CreateBPlusTree( Class<T> pageClass, Class<S> recordElementClass, String pageFilename, String treeFilename, String key )
	{
		m_pageType = pageClass;
		m_recordElementClass = recordElementClass;
		m_filename = treeFilename;
		m_key      = key;
		
		// Consider size of the key field
		T dummyPage = null;
		try { dummyPage = pageClass.newInstance();} catch (InstantiationException e) {e.printStackTrace();} catch (IllegalAccessException e) {e.printStackTrace();}
		int nbRecordsPerPage = dummyPage.m_nbRecordsPerPage;
		Record dummyRecord = dummyPage.CreateElement();
		int keySize = dummyRecord.get(key).Size();
		
		// "pointer" will be record number
		int pointerSize = 8;
		
		// Block size is arbitrarily 1K
		// 1 parent pointer, n elements, n+1 child pointers
		m_n = (1024 - 2 * pointerSize - 1) / (keySize + pointerSize);
		
		// Consider number of levels we'll need.
		int nbRecords = MemoryManager.getInstance().GetNumberOfRecords( pageClass, pageFilename );
		int nbLevels = 1 + (int)(Math.log((double)nbRecords) / Math.log((double)m_n));
		
		// Temporarily
		System.out.println("Nb records: " + nbRecords);
		System.out.println("Nb levels needed: " + nbLevels);
		System.out.println("n : " + m_n );
		
		// Add a custom page type
		MemoryManager.getInstance().AddPageType( BPlusTreePage.class, m_filename );
		
		// Get the root and keep it in memory at this time.
		m_root = new BPlusTreeNode<S>(m_recordElementClass);
		m_root.Load(0, m_n, m_filename);
		m_root.m_isLeaf = true;
		
		// Parse the whole table
		int p = 0;

		T page = null;
		while ( (page = MemoryManager.getInstance().getPage( m_pageType , p++, pageFilename)) != null )
		{
			for(int i = 0; i < page.m_records.length; i++)
			{
				InsertLeaf( page.m_records[i], nbRecordsPerPage * p + i );
			}
			
			MemoryManager.getInstance().freePage(page);
		}
		
		m_treeCreated = true;
	}
	
	public void InsertLeaf( Record rec, int recordNumber )
	{
		KeyPointerPair pair = new KeyPointerPair( rec.get(m_key), recordNumber );
		// Load the root
		boolean RootLoaded = (m_root.IsLoaded());
		if( !RootLoaded )
			m_root.Load(0, m_n, m_filename);
		
		// We try to find a place for the new key in the appropriate leaf
		BPlusTreeNode<S> target = getLeafNode( m_root, pair.el );

		Insert( target, pair );		
		
		// Clear the root if it wasn't previously loaded
		if( !RootLoaded )
			m_root.Clear();		
	}
	
	public void Insert( BPlusTreeNode<S> node, KeyPointerPair pair)
	{
		assert(node.IsLoaded());
		
		if( node.m_nbElements == m_n )
		{
			// If there is no room in the proper leaf, we split the leaf into two
			// and divide the keys between the two new nodes, so each is half full
			
			// Start by creating new sorted arrays
			// Create a new split node
			BPlusTreeNode<S> splitNode = new BPlusTreeNode<S>(m_recordElementClass);
			splitNode.Load(-1, m_n, m_filename);
			
			KeyPointerPair nodeToAdd = SplitNode( node, splitNode, pair );
					
			if( node == m_root )
			{
				// In the case of the root, the root becomes a leaf or interior node
				// And we create a new root
				BPlusTreeNode<S> newroot = new BPlusTreeNode<S>(m_recordElementClass);
				newroot.Load(-1, m_n, m_filename);
				
				// Now, perform a big hack
				assert(newroot.m_node == newroot.m_page.m_pageNumber);
				assert(node.m_node == node.m_page.m_pageNumber);
				int tempPageNumber = newroot.m_node;
				newroot.m_node = node.m_node;
				newroot.m_page.m_pageNumber = node.m_page.m_pageNumber;
				node.m_node = tempPageNumber;
				node.m_page.m_pageNumber = tempPageNumber;
				
				// Setup new parent pointers
				node.m_parent = newroot.m_node;
				splitNode.m_parent = newroot.m_node;

				// We just swapped the page numbers, which will sort itself out when writing back the data.
				// Now setup the new root data correctly!
				m_root = newroot;
				
				m_root.m_elements[0] = nodeToAdd.el;
				m_root.m_records[0] = node.m_node;
				m_root.m_records[1] = splitNode.m_node;
				m_root.m_nbElements = 1;
				m_root.m_dirty = true;
				
				m_root.Clear();				
				splitNode.Clear();
				node.Clear();
				// No recursive calls in this case			
			}
			else
			{
				splitNode.m_parent = node.m_parent;
				splitNode.Clear();
				
				int parentptr = node.m_parent;
				node.Clear();
				
				BPlusTreeNode<S> parent = getNode(parentptr);

				// parent is cleared inside the Insert.
				Insert( parent, nodeToAdd );
			}
		}
		else
		{
			node.Insert( pair );
			node.Clear();
		}
	}
		
	BPlusTreeNode<S> getNode( int nb )
	{
		BPlusTreeNode<S> node = new BPlusTreeNode<S>(m_recordElementClass);
		node.Load(nb, m_n, m_filename);
		return node;
	}
	
	BPlusTreeNode<S> getLeafNode( BPlusTreeNode<S> parent, RecordElement el )
	{
		if( parent.IsLeaf() )
			return parent;
		
		for( int i = 0; i <= parent.m_nbElements; i++ )
		{
			if( (i == parent.m_nbElements) ||                 // Last element
				(el.CompareTo( parent.m_elements[i] ) < 0) )  // Standard comparison
			{
				int parentnode = parent.m_node;
				int childnode  = parent.m_records[i];
				
				// for the moment, free the parent				
				if( parent != m_root )
					parent.Clear();
				
				// Recurse down one level
				BPlusTreeNode<S> child = getNode( childnode );
				child.m_parent = parentnode;
				
				return getLeafNode( child, el );
			}
		}
		// Not supposed to get here
		assert(false);
		return null;
	}
	
	KeyPointerPair SplitNode(BPlusTreeNode<S> node, BPlusTreeNode<S> splitNode, KeyPointerPair keyAdded )
	{
		RecordElement[] els = new RecordElement[m_n+1];
		int[]           ptrs = new int[m_n+2];
		boolean inserted = false;
		
		int e = 0;
		for( int i = 0; i < m_n+1; i++ )
		{
			if( inserted || node.m_elements[e].CompareTo(keyAdded.el) < 0 )
			{
				els[i]  = node.m_elements[e];
				ptrs[i] = node.m_records[e];
				e++;						
			}
			else
			{
				els[i] = keyAdded.el;
				ptrs[i] = keyAdded.ptr;
				inserted = true;
			}
		}
		
		node.m_dirty = true;
		splitNode.m_dirty = true;
		
		if( node.IsLeaf() )
		{
			// Last pointer points to split node..
			splitNode.m_records[m_n] = node.m_records[m_n];
			node.m_records[m_n] = splitNode.m_node;				
			
			// If we're splitting a leaf node:
			// The first ceil( (n+1)/2 ) key-pointer pairs stay with N
			// The remaining move to M.
			int kept  = (int) Math.ceil( (double)(m_n+1) / 2.0 );
			
			for( int i = 0; i < kept; i++ )
			{
				node.m_elements[i] = els[i];
				node.m_records[i]  = ptrs[i];
			}
			node.m_nbElements = kept;
			
			for( int i = kept; i < m_n+1; i++)
			{
				splitNode.m_elements[i-kept] = els[i];
				splitNode.m_records[i-kept] = ptrs[i];
			}		
			splitNode.m_nbElements = m_n+1 - kept;
			
			// Insert new key-pointer pair into the parent.
			return new KeyPointerPair(splitNode.m_elements[0], splitNode.m_node);

		}
		else
		{
			// ABSOLUTELY NOT SURE ABOUT THAT
			// I THINK IT'S MORE THE FIRST POINTER THAT NEVER CHANGES
			ptrs[m_n+1] = node.m_records[m_n];
			
			// If we're splitting an interior node:
			// Leave at N the first ceil( (n+2) / 2) pointers
			//  and move to M the remaining floor( (n+2) / 2) pointers
			// The first ceil(n/2) keys stay with N, 
			// while the last floor(n/2) keys move to M.
			// There's always a key left out: K
			// It must be inserted into the parent.
			int keptPtrs = (int)Math.ceil( (double)(m_n+2) / 2.0 );
			int movedPtrs = m_n+2 - keptPtrs;
			
			int keptKeys = (int)Math.ceil( (double)m_n / 2.0f );
			int movedKeys = (int)Math.floor( (double)m_n / 2.0f );
			
			assert( keptPtrs == keptKeys+1);
			assert( movedPtrs == movedKeys+1);
			assert( m_n + 1 == 1 + keptKeys + movedKeys );
			
			for(int i = 0; i < keptPtrs; i++)
				node.m_records[i] = ptrs[i];
			for(int i = m_n+2 - movedPtrs; i < m_n+2; i++)
				splitNode.m_records[i - (m_n+2)] = ptrs[i];
			for(int i = 0; i < keptKeys; i++)
				node.m_elements[i] = els[i];
			for(int i = m_n+1 - movedKeys; i < m_n+1; i++ )
				splitNode.m_elements[i - (m_n+1)] = els[i];
			
			node.m_nbElements      = keptKeys;
			splitNode.m_nbElements = movedKeys;
		
			// Insert new key-pointer pair into parent
			// i.e. [left out] - [split node]
			return new KeyPointerPair(els[keptKeys+1], splitNode.m_node);
		}
	}
}

//////////////////////////////////////
// Subclasses ////////////////////////
//////////////////////////////////////
class BPlusTreeRecord extends Record
{
	public BPlusTreeRecord()
	{
		AddElement( "data", new StringRecordElement(1024) );
	}
}

class BPlusTreePage extends Page< BPlusTreeRecord >
{
	public BPlusTreePage()
	{
		super();
		m_nbRecordsPerPage = 1;
	}
	public BPlusTreeRecord[] CreateArray(int n){ assert(n==1); return new BPlusTreeRecord[n]; }
	public BPlusTreeRecord   CreateElement()   { return new BPlusTreeRecord(); } 
}

class BPlusTreeNode<S extends RecordElement>
{
	Class<S>        m_class;
	boolean         m_loaded;
	BPlusTreePage   m_page;
	RecordElement[] m_elements;
	int[]           m_records;
	int             m_nbElements;
	boolean         m_isLeaf;
	int             m_parent;
	int             m_node;
	boolean         m_dirty;
	
	BPlusTreeNode(Class<S> classRec)
	{
		m_class      = classRec;
		m_dirty      = false;
		m_page       = null;
		m_elements   = null;
		m_records    = null;
		m_loaded     = false;
		m_nbElements = 0;
		m_isLeaf     = false;
		m_parent     = -1;
		m_node       = -1;
	}
	
	public void Load(int node, int n, String filename)
	{
		if(m_loaded)
			Clear();
		
		m_elements = new RecordElement[n];
		m_records  = new int[n+1];
		
		m_page = MemoryManager.getInstance().getRWPage( BPlusTreePage.class, node, filename );
		
		if( node < 0 )
			m_node = m_page.m_pageNumber;
		else
			m_node = node;
		
		if( m_page.m_records[0] == null )
		{
			m_page.AddRecord( m_page.CreateElement() );
			m_nbElements = 0;
		}
		else
		{
			Parse( m_page.m_records[0].get("data").getString() );
		}	
		
		m_loaded = true;
	}
	
	public void Clear()
	{
		if( m_page != null )
		{
			if(m_dirty)
			{
				Write( m_page.m_records[0].get("data") );
				m_page.m_cleanupToDo = true;
			}
			
			MemoryManager.getInstance().freePage( m_page );
		}
		
		m_elements = null;
		m_records  = null;
		m_loaded   = false;
		
		m_dirty  = false;
		m_parent = -1;
		m_node   = -1;
	}
	
	void Parse(String data)
	{
		int elementSize = CreateRecordElement().Size();
		int pointerSize = 8;
		
		assert( (data.length() - 2 * pointerSize - 1) % (elementSize + pointerSize) == 0);
		m_nbElements = (data.length() - 2 * pointerSize - 1) / (elementSize + pointerSize);
		
		int p = 0;
		// Very first, is it a leaf or not?
		m_isLeaf = (data.substring(0, 1).charAt(0) == '1' );
		p += 1;
		// First, parent pointer		
		m_parent = Integer.parseInt(data.substring(p, p+8), 16);
		p += 8;
		
		int lel = CreateRecordElement().Size();

		for( int i = 0; i < m_nbElements; i++ )
		{
			// Pointer first
			m_records[i] = Integer.parseInt(data.substring(p, p+8), 16);
			p += 8;
			// element second
			m_elements[i] = CreateRecordElement();
			m_elements[i].Parse(data.substring(p, p+lel));
			p += lel;
		}
		
		// Last pointer (possibly to another leaf or to another child)
		m_records[m_nbElements] = Integer.parseInt(data.substring(p, p+8), 16);
	}
	
	void Write(RecordElement el)
	{
		String data = "";
		
		// Very very first, 0 if interior node, 1 if leaf
		if( m_isLeaf )
			data += "1";
		else
			data += "0";
		
		// First, parent pointer
		data += String.format("%1$-8s", Integer.toHexString(m_parent));
		
		// Pointer/Key pairs
		for( int i = 0; i < m_nbElements; i++ )
		{
			data += String.format("%1$-8s", Integer.toHexString(m_records[i]));
			data += m_elements[i].Write();
		}
		
		// Last pointer
		data += String.format("%1$-8s", Integer.toHexString(m_records[m_nbElements]));
		
		el.setString(data);
	}
	
	void Insert( KeyPointerPair pair )
	{
		assert(m_nbElements != m_elements.length);
		
		// Find index we'll insert the value in
		int insertionIdx = -1;
		for( int i = 0; i < m_nbElements && insertionIdx < 0; i++ )
			if( pair.el.CompareTo( m_elements[i] ) < 0 )
				insertionIdx = i;
		
		if( insertionIdx == -1 )
			insertionIdx = m_nbElements;
		
		m_nbElements++;

		RecordElement keyValue = pair.el;
		RecordElement keyTemp  = null;
		int ptrValue = pair.ptr;
		int ptrTemp  = 0;
		
		for(int i = insertionIdx; i < m_nbElements; i++ )
		{
			keyTemp = m_elements[i];
			ptrTemp = m_records[i];
			
			m_elements[i] = keyValue;
			m_records[i]  = ptrValue;
			
			keyValue = keyTemp;
			ptrValue = ptrTemp;
		}		
		
		m_dirty = true;
	}
	
	boolean IsLoaded() { return m_loaded; }
	boolean IsLeaf()   { return m_isLeaf; }
	
	private S CreateRecordElement()
	{
		S el = null;
		try {
			el = m_class.newInstance();
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return el;
	}
}

class KeyPointerPair
{
	RecordElement el;
	int           ptr;
	
	KeyPointerPair()
	{
		el = null;
		ptr = 0;
	}
	
	KeyPointerPair(RecordElement rel, int pointer)
	{
		el = rel;
		ptr = pointer;
	}
}