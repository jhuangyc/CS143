/*
Copyright (c) 2000 The Regents of the University of California.
All rights reserved.

Permission to use, copy, modify, and distribute this software for any
purpose, without fee, and without written agreement is hereby granted,
provided that the above copyright notice and the following two
paragraphs appear in all copies of this software.

IN NO EVENT SHALL THE UNIVERSITY OF CALIFORNIA BE LIABLE TO ANY PARTY FOR
DIRECT, INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES ARISING OUT
OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF THE UNIVERSITY OF
CALIFORNIA HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

THE UNIVERSITY OF CALIFORNIA SPECIFICALLY DISCLAIMS ANY WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY
AND FITNESS FOR A PARTICULAR PURPOSE.  THE SOFTWARE PROVIDED HEREUNDER IS
ON AN "AS IS" BASIS, AND THE UNIVERSITY OF CALIFORNIA HAS NO OBLIGATION TO
PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR MODIFICATIONS.
*/

// This is a project skeleton file

import java.io.PrintStream;
import java.util.Vector;
import java.util.Enumeration;
import java.util.*;

class CgenNode extends class_ implements Comparable<CgenNode>{
	/** The parent of this node in the inheritance tree */
	private CgenNode parent;

	/** The children of this node in the inheritance tree */
	private Vector children;

	/** Indicates a basic class */
	final static int Basic = 0;

	/** Indicates a class that came from a Cool program */
	final static int NotBasic = 1;
	
	/** Does this node correspond to a basic class? */
	private int basic_status;
	
	// the StringSymbol of the class name; need by class name table
	private StringSymbol nameStrSym;
	// the class tag of this class
	private int tag;
	// the maximum class tag among this class's children; need for 'case' statement
	private int maxChildTag;
	
	// record the method/attribute offsets; needed while building class features
	private int methOff;
	private int attrOff;
	
	// mapping from method/attribute to their respective offsets
	public Map<AbstractSymbol, Integer> methOffsets;
	public Map<AbstractSymbol, Integer> attrOffsets;

	// mapping from method/attribute to their respective AST node
	private Map<AbstractSymbol, attr> attrMap;
	private Map<AbstractSymbol, method> methMap;
	
	// mapping from method to the class containing it
	// needed for identifying which parent class an inherited method belongs to
	public Map<AbstractSymbol, AbstractSymbol> methClass;
	
	
	

	/** Constructs a new CgenNode to represent class "c".
	 * @param c the class
	 * @param basic_status is this class basic or not
	 * @param table the class table
	 * */
	CgenNode(Class_ c, int basic_status, CgenClassTable table) {
		super(0, c.getName(), c.getParent(), c.getFeatures(), c.getFilename());
		this.parent = null;
		this.children = new Vector();
		this.basic_status = basic_status;
		this.nameStrSym = (StringSymbol)AbstractTable.stringtable.addString(name.getString());

	}
	


	void addChild(CgenNode child) {
		children.addElement(child);
	}

	/** Gets the children of this class
	 * @return the children
	 * */
	Enumeration getChildren() {
		return children.elements(); 
	}

	/** Sets the parent of this class.
	 * @param parent the parent
	 * */
	void setParentNd(CgenNode parent) {
		if (this.parent != null) {
			Utilities.fatalError("parent already set in CgenNode.setParent()");
		}
		if (parent == null) {
			Utilities.fatalError("null parent in CgenNode.setParent()");
		}
		this.parent = parent;
	}	
		

	/** Gets the parent of this class
	 * @return the parent
	 * */
	CgenNode getParentNd() {
		return parent; 
	}

	/** Returns true is this is a basic class.
	 * @return true or false
	 * */
	boolean basic() { 
		return basic_status == Basic; 
	}
	
	// for debugging
	public String toString() {
		return String.format(
			"<[%d]class %s inherits %s>", 
			tag, getName(), getParent());
	}
	
	CgenNode getParentNode() {
		return parent;
	}
	
	// return the class's tag
	int getTag() {
		return tag;
	}
	
	// return the max tag among this class's children
	int getMaxChildTag() {
		return maxChildTag;
	}
	
	StringSymbol getNameStrSym() {
		return nameStrSym;
	}

	// collects method/attribute offsets from the inheritance tree
	void buildFeatures() {
		attrMap = new LinkedHashMap<AbstractSymbol, attr>();
		methMap = new LinkedHashMap<AbstractSymbol, method>();
		methOffsets = new LinkedHashMap<AbstractSymbol, Integer>();
		methClass = new LinkedHashMap<AbstractSymbol, AbstractSymbol>();
		attrOffsets = new LinkedHashMap<AbstractSymbol, Integer>();
		methOff = 0;
		attrOff = 0;
		
		
		// add the parent's method/attribute, if this class is not Object
		if(getName() != TreeConstants.Object_) {
			methOffsets.putAll(parent.methOffsets);
			methClass.putAll(parent.methClass);
			methOff = parent.methOff;
			attrOff = parent.attrOff;
		}
		
		// iterate through the features, add as either method or attribute
		for(Enumeration e = features.getElements(); e.hasMoreElements();) {
			Feature feat = (Feature)e.nextElement();
			if(feat instanceof method) {
			
				// record the method
				method m = (method)feat;
				methMap.put(m.name, m);
				methClass.put(m.name, getName());
		
				// if not already exist, add to the offset mapping
				if(!methOffsets.containsKey(m.name)) {
					methOffsets.put(m.name, methOff);
					methOff += 1;
				}

					
			} else {
			
				// record the attribute and add to the offset mapping
				attrMap.put( ((attr)feat).name, (attr)feat );
				attrOffsets.put( ((attr)feat).name, attrOff );
				attrOff += 1;
				
			}
		}
		
		if(Flags.cgen_debug)
			for(AbstractSymbol m : methOffsets.keySet())
				System.out.printf("\tclass %s method %s offset %d\n", 
					methClass.get(m), m, methOffsets.get(m));
		
		
		// build children's features
		for(Enumeration e = getChildren(); e.hasMoreElements();) {
			((CgenNode)e.nextElement()).buildFeatures();
		}
	}	
	
	
	
	static int classTag = 0;
	void assignTags() {
	
		// assign a tag to self
		tag = classTag;
		maxChildTag = classTag;
		classTag++;
		
		// assign a tag to each of the child
		// record the tag of the last (also the max) child
		for(Enumeration e = getChildren(); e.hasMoreElements();) {
			CgenNode child = (CgenNode)e.nextElement();
			child.assignTags();
			maxChildTag = child.maxChildTag;
		}
		
		if(Flags.cgen_debug) {
			System.out.printf("tag for %s:[%d, %d]\n", name, tag, maxChildTag);
		}
	}
	
	// for sorting the CgenNode
	public int compareTo(CgenNode another) {
		return tag - another.tag;
	}
	
	void codeDispTab(PrintStream s) {	
		if(Flags.cgen_debug) {
			System.out.println("codeDispTab " + name + " " + methOffsets.size());		
		}
		
		// for each of the method, code the reference
		// including class name, method name, and offset
		for(AbstractSymbol method : methOffsets.keySet()) {
			if(Flags.cgen_debug)
				System.out.println("\t" + method + " " + methOffsets.get(method));
			s.print(CgenSupport.WORD);
			CgenSupport.emitMethodRef(methClass.get(method), method, s);
			s.println();
		}		
	}
	
	void codeProtObj(PrintStream s) {
		if(Flags.cgen_debug) {
			System.out.println("codeProtObj " + name + " " + attrOffsets.size());		
		}
		
		// class tag
		s.println(CgenSupport.WORD + getTag());				
		
		// size
		s.println(CgenSupport.WORD + ((attrOff) + CgenSupport.DEFAULT_OBJFIELDS));	
		
		// disp table
		s.print(CgenSupport.WORD);	
		CgenSupport.emitDispTableRef(name, s);	
		s.println();					
		
		// recursively code the prototype
		codeProtObjRecur(s);
	}
	
	void codeProtObjRecur(PrintStream s) {
		if(Flags.cgen_debug) {
			System.out.println("recur at " + name);		
		}
		
		// if not Object, code the parent's prototype first
		if(name != TreeConstants.Object_)
			parent.codeProtObjRecur(s);
	
		// for each of the attribute, code the default value/reference
		for(AbstractSymbol name : attrMap.keySet()) {
			
			AbstractSymbol type = attrMap.get(name).type_decl;
			
			if(Flags.cgen_debug)
				System.out.println("\tattr: " + name + ":" + type + "["+attrOffsets.get(name) + "]");
			
			s.print(CgenSupport.WORD);
						
			
			if(type == TreeConstants.Int)
				((IntSymbol)AbstractTable.inttable.addInt(0)).codeRef(s);
				
			else if(type == TreeConstants.Bool)
				s.print(CgenSupport.BOOLCONST_PREFIX + "0");
				
			else if(type == TreeConstants.Str)
				((StringSymbol)AbstractTable.stringtable.addString("")).codeRef(s);
				
			else	// other class (default to void)
				s.print("0");
				
			s.println();
		}	
	}
	
	void codeObjInit(PrintStream s) {
		if(Flags.cgen_debug)	System.out.println("codeObjInit " + name);		
		
		// set class table for convenience
		CgenClassTable ct = CgenClassTable.ct;
		
		// enter new scope
		ct.enterScope();
		
		// add class attributes to the scope
		CgenNode nd = this;
		while(nd.getName() != TreeConstants.Object_) {
			for(AbstractSymbol a : nd.attrOffsets.keySet()) {
				ct.addId(a, new ClassVar(nd.attrOffsets.get(a)));
			}
			nd = nd.parent;
		}
		// set SELF_TYPE to current class
		ct.addId(TreeConstants.SELF_TYPE, name);
		if(Flags.cgen_debug) {
			System.out.printf("\tadd SELF_TYPE; lookup=%s\n",
				ct.lookup(TreeConstants.SELF_TYPE));
		}
		
		
		// entering routine for initializer
		CgenSupport.emitEnteringMethod(s);
		
		// call parent's initailizer this class is not Object
		if(name != TreeConstants.Object_) {
			CgenSupport.emitJal(parent.name + CgenSupport.CLASSINIT_SUFFIX, s);
		}
		
		for(AbstractSymbol name : attrMap.keySet()) {
			attr a = attrMap.get(name);
			if(Flags.cgen_debug)	
				System.out.printf("\t%s:%s<-%s[%d]\n", a.name, a.type_decl, a.init, attrOffsets.get(name));	
			
			// initailze an attribute if it has an initilized value
			if(!(a.init instanceof no_expr)) {
				a.init.code(s);
				CgenSupport.emitStore(
					CgenSupport.ACC, 
					attrOffsets.get(name) + CgenSupport.DEFAULT_OBJFIELDS, 
					CgenSupport.SELF, s);
				
				
			}
		}
		
		
		// return the self pointer
		CgenSupport.emitMove(CgenSupport.ACC, CgenSupport.SELF, s);
		
		// exiting method routine
		CgenSupport.emitExitingMethod(s);
		
		// exit scope
		ct.exitScope();
		
	}
	
	void codeClassMethod(PrintStream s) {
	
		// if the class is one of the basic classes, don't code its method
		if(name == TreeConstants.Object_ ||
			name == TreeConstants.IO ||
			name == TreeConstants.Int ||
			name == TreeConstants.Str) {
			
			return;
		}
		
		
		if(Flags.cgen_debug)	
			System.out.println("codeClassMethod " + name);		
		
		// set class table for convenience
		CgenClassTable ct = CgenClassTable.ct;
		
		// enter new scope
		ct.enterScope();
		
		// add class attributes to the scope
		CgenNode nd = this;
		while(nd.getName() != TreeConstants.Object_) {
			for(AbstractSymbol a : nd.attrOffsets.keySet()) {
				ct.addId(a, new ClassVar(nd.attrOffsets.get(a)));
			}
			nd = nd.parent;
		}
		// set SELF_TYPE to current class
		ct.addId(TreeConstants.SELF_TYPE, name);
		if(Flags.cgen_debug) {
			System.out.printf("\tadd SELF_TYPE; lookup=%s\n",
				ct.lookup(TreeConstants.SELF_TYPE));
		}
		
		// code the class methods
		for(method m : methMap.values()) {
			if(Flags.cgen_debug)	
				System.out.println("\tcodeClassMethod " + m.name);		
				
			CgenSupport.emitMethodRef(name, m.name, s);
			s.print(CgenSupport.LABEL);
			m.code(s);
		}
		
		// exit scope
		ct.exitScope();
	}
}
	

	
