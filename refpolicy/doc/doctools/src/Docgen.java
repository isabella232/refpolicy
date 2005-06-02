/* Copyright (C) 2005 Tresys Technology, LLC
 * License: refer to COPYING file for license information.
 * Authors: Spencer Shimko <sshimko@tresys.com>
 * 
 * Docgen.java: The reference policy xml analyzer and documentation generator		
 */
import policy.*;

import java.io.*;
import java.util.*;

import javax.xml.parsers.DocumentBuilder; 
import javax.xml.parsers.DocumentBuilderFactory;  
import javax.xml.parsers.ParserConfigurationException;

import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Schema;

import javax.xml.XMLConstants;

import org.xml.sax.*;
import org.w3c.dom.*;

/**
 * The reference policy documentation generator and xml analyzer class.
 * It pulls in XML describing reference policy, transmogrifies it,
 * and spits it back out in some other arbitrary format.
 */
public class Docgen{
	// store the PIs here
	private static Vector procInstr = new Vector();
	private static boolean verbose = false;
	// the policy structure built after xml is parsed
	private Policy policy = null;
	// the xml document
	private Document dom = null;
	
	// the files/directories passed in from the command line
	private static File xmlFile;
	private static File headerFile;
	private static File footerFile;
	private static File outputDir;
		
	private static void printUsage(){
		System.out.println("Reference Policy Documentation Compiler usage:");
		System.out.println("\tjava -cp ./src Docgen [-h] [-v] -xf xmlFileIn -hf headerFile -ff footerFile -od outDirectory");
		System.out.println("-h display this message and exit");
		System.out.println("-xf XML file to parse");
		System.out.println("-hf header file for HTML output");
		System.out.println("-ff footer file for HTML output");
		System.out.println("-od output directory");
		System.exit(1);
	}
	
	/**
	 * Docgen constructor
	 * 
	 * @param output	Filename to setup for output
	 */
	public Docgen(String output) 
	throws FileNotFoundException {
	}
	
	/**
	 * The main() driver for the policy documentation generator.
	 * @param argv	Arguments, takes 1 filename parameter
	 */
	public static void main(String argv[]) {
		if (argv.length == 0){
			printUsage();
			System.exit(1);
		}
		// hacked up version of getopt()
		for (int x=0; x < argv.length; x++){
			if (argv[x].equals("-xf")){
				x++;
				if (x<argv.length){
					xmlFile = new File(argv[x]);
					if (!xmlFile.isFile()){
						printUsage();
						System.err.println("XML file is not really a file!");
						System.exit(1);
					}
				} else {
					printUsage();
					System.exit(1);
				}
			} else if (argv[x].equals("-hf")){
				x++;
				if (x<argv.length){
					headerFile = new File(argv[x]);
					if (!headerFile.isFile()){
						printUsage();
						System.err.println("Header file is not really a file!");
						System.exit(1);
					}
				} else {
					printUsage();
					System.exit(1);
				}
			} else if (argv[x].equals("-ff")){
				x++;
				if (x<argv.length){
					footerFile = new File(argv[x]);
					if (!footerFile.isFile()){
						printUsage();
						System.err.println("Footer file is not really a file!");
						System.exit(1);
					}
				} else {
					printUsage();
					System.exit(1);
				}
			} else if (argv[x].equals("-od")){
				x++;
				if (x<argv.length){
					outputDir = new File(argv[x]);
					if (!outputDir.isDirectory()){
						printUsage();
						System.err.println("Output directory is not really a directory!");
						System.exit(1);
					}
				} else {
					printUsage();
					System.exit(1);
				}
			} else if (argv[x].equals("-h")){
				printUsage();
				System.exit(1);
			} else if (argv[x].equals("-v")){
				verbose = true;
			} else {
				printUsage();
				System.out.println("Error unknown argument: " + argv[x]);
				System.exit(1);
			}
		}
		
		try {
			// create document factory 
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
			Schema schema = schemaFactory.newSchema();
			
			factory.setValidating(true);   
			factory.setNamespaceAware(true);

			// in order for this setting to hold factory must be validating
			factory.setIgnoringElementContentWhitespace(true);

			// get builder from factory
			DocumentBuilder builder = factory.newDocumentBuilder();
				
			// create an anonymous error handler for parsing errors
			builder.setErrorHandler(
					new org.xml.sax.ErrorHandler() {
						// fatal errors
						public void fatalError(SAXParseException exception)
						throws SAXException {
							throw exception;
						}
						
						// parse exceptions will be fatal
						public void error(SAXParseException parseErr)
						throws SAXParseException
						{
							// Error generated by the parser
							System.err.println("\nPARSE ERROR: line " + parseErr.getLineNumber() 
									+  ", URI " + parseErr.getSystemId());
							System.err.println("PARSE ERROR: " + parseErr.getMessage() );
							
							// check the wrapped exception
							Exception  x = parseErr;
							if (parseErr.getException() != null)
								x = parseErr.getException();
							x.printStackTrace();					}
						
						// dump warnings too
						public void warning(SAXParseException err)
						throws SAXParseException
						{
							System.err.println("\nPARSE WARNING: line " + err.getLineNumber()
									+ ", URI " + err.getSystemId());
							System.err.println("PARSE WARNING:   " + err.getMessage());
						}
					}
			);
			
			Docgen redoc = new Docgen(argv[1]);

			redoc.dom = builder.parse(xmlFile);
			
			// do our own transformations
			redoc.processDocumentNode();
			
			// build our own converter then convert
			Converter converter = new Converter(redoc.policy, headerFile, footerFile);
			converter.Convert(outputDir);
		// TODO: figure out which of these is taken care of by the anonymous error handler above
		} catch (SAXException saxErr) {
			// sax error
			Exception  x = saxErr;
			if (saxErr.getException() != null)
				x = saxErr.getException();
			x.printStackTrace();
		} catch (ParserConfigurationException parseConfigErr) {
			// Sometimes we can't build the parser with the specified options
			parseConfigErr.printStackTrace();
		} catch (IOException ioe) {
			// I/O error
			ioe.printStackTrace();
		} catch (Exception err) {
			err.printStackTrace();
		}
		

	} // main

	public static void Debug(String msg){
		if (verbose)
			System.out.println(msg);
	}
	
	void processDocumentNode() throws SAXException{
		Element docNode = dom.getDocumentElement();
		
		if (docNode != null && docNode.getNodeName().equals("policy")){
			policy = new Policy("policy");
			
			NodeList children = docNode.getChildNodes();
			int len = children.getLength();
		
			for (int index = 0; index < len; index++){
				processNode(children.item(index), policy);
			}
		} else {
			System.err.println("Failed to find document/policy node!");
			System.exit(1);
		}
	}
	
	/**
	 * Process children of the policy node (aka modules).
	 * 
	 * @param node		A child node of the policy.
	 * @param parent	The parent PolicyElement.
	 */
	void processNode(Node node, Policy parent) throws SAXException{
		Layer layer = null;
		Module module = null;
		
		// save us from null pointer de-referencing
		if (node == null){
			System.err.println(
			"Nothing to do, node is null");
			return;
		}
		
		// snag the name
		String nodeName = node.getNodeName();
		
		// validity check and pull layer attribute
		if (node.getNodeType() == Node.ELEMENT_NODE && nodeName.equals("module")){
			// display the name which might be generic
			Docgen.Debug("Encountered node: " + nodeName);
			NamedNodeMap attrList =	node.getAttributes();
			
			// the required attributes
			int attrLen = 0;
			if(attrList != null){
				attrLen = attrList.getLength();
			} else{
				fatalNodeError("Missing attributes in module. \""  
						+ "\".  \"layer\" and \"name\" are required attributes.");
			}

			Node moduleNode = attrList.getNamedItem("name");
			Node layerNode = attrList.getNamedItem("layer");
			
			if (moduleNode == null || layerNode == null)
				fatalNodeError("Missing attributes in module element.  \"layer\" and \"name\" are required attributes.");

			String moduleName = moduleNode.getNodeValue();
			String layerName = layerNode.getNodeValue();
		
			// check to see if this is a new layer or a pre-existing layer
			layer = parent.Children.get(layerName);
			if (layer == null){
					Docgen.Debug("Adding new layer: " + layerName);
					layer = new Layer(layerName, parent);	
			} else {
				Docgen.Debug("Lookup succeeded for: " + layerName);
			}
			
			if (layer.Children.containsKey(moduleName)){
				Docgen.Debug("Reusing previously defined module: " + moduleName);
				module = layer.Children.get(moduleName);
			} else {
				Docgen.Debug("Creating module: " + moduleName);
				module = new Module(moduleName, layer);
			}
			
			// take care of the attributes
			for(int i = 0; i < attrLen; i++){
				Node attrNode = attrList.item(i);
				String attrName = attrNode.getNodeName();
				String attrValue = attrNode.getNodeValue();
				if (!attrName.equals("layer") && !attrName.equals("name")){
					Docgen.Debug("\tAdding attribute: " + attrNode.getNodeName()
							+ "=" + attrValue);
					module.AddAttribute(attrName,attrValue);
				}
			}
		} else if (!isEmptyTextNode(node)){
			fatalNodeError("Unexpected child \"" + nodeName 
					+"\" node of parent \"" + parent.Name + "\".");
		}
		
		// recurse over children if both module and layer defined
		if (module != null && layer != null){
			// the containsKey check verified no duplicate module
			layer.Children.put(module.Name, module);
			parent.Children.put(layer.Name, layer);

			NodeList children = node.getChildNodes();
			if (children != null){
				int len = children.getLength();
				for (int index = 0; index < len; index++){
					processNode(children.item(index), module);
				}
			}
		}
	}
	
	/**
	 * Process children of the module node (aka interfaces).
	 * 
	 * @param node		A child node of the policy.
	 * @param parent	The parent PolicyElement.
	 */
	void processNode(Node node, Module parent) throws SAXException{
		Interface iface = null;
		
		// save us from null pointer de-referencing
		if (node == null){
			System.err.println(
			"Nothing to do, node is null");
			return;
		}
		
		// snag the name
		String nodeName = node.getNodeName();
		
		// if summary node
		if (node.getNodeType() == Node.ELEMENT_NODE && nodeName.equals("summary")){
			// unfortunately we still need to snag the PCDATA child node for the actual text
			Docgen.Debug("Encountered node: " + nodeName);
			NodeList children = node.getChildNodes();
			if (children != null && children.getLength() == 1){
				if (children.item(0).getNodeType() == Node.TEXT_NODE){
					parent.PCDATA = children.item(0).getNodeValue();
					return;
				} 
			}
			fatalNodeError("Unexpected child \"" + nodeName 
					+"\" node of parent \"" + parent.Name + "\".");
		// if interface node
		} else if (node.getNodeType() == Node.ELEMENT_NODE && nodeName.equals("interface")){
			NamedNodeMap attrList =	node.getAttributes();
			// the required attributes
			int attrLen = 0;
			if(attrList != null){
				attrLen = attrList.getLength();
			} else{
				fatalNodeError("Missing attribute in interface.  " 
						+ "\"name\" is a required attribute.");
			}

			Node nameNode = attrList.getNamedItem("name");
						
			if (nameNode == null )
				fatalNodeError("Missing attribute in interface.  " 
						+ "\"name\" is a required attribute.");


			String iName = nameNode.getNodeValue();
		
			Docgen.Debug("Creating interface: " + iName);
			iface = new Interface(iName, parent);
			
			// take care of the attributes
			for(int i = 0; i < attrLen; i++){
				Node attrNode = attrList.item(i);
				String attrName = attrNode.getNodeName();
				String attrValue = attrNode.getNodeValue();
				if (!attrName.equals("name")){
					Docgen.Debug("\tAdding attribute: " + attrNode.getNodeName()
							+ "=" + attrValue);
					iface.AddAttribute(attrName,attrValue);
				}
			}
		} else if (!isEmptyTextNode(node)){
			fatalNodeError("Unexpected child \"" + nodeName 
					+"\" node of parent \"" + parent.Name + "\".");
		}
		
		// recurse over children if both module and layer defined
		if (iface != null && parent != null){
			// FIXME: containsKey() check for duplicate
			parent.Children.put(iface.Name, iface);

			NodeList children = node.getChildNodes();
			if (children != null){
				int len = children.getLength();
				for (int index = 0; index < len; index++){
					processNode(children.item(index), iface);
				}
			}
		}
	}

	/**
	 * Process children of the interface node (aka parameters, desc., infoflow).
	 * 
	 * @param node		A child node of the policy.
	 * @param parent	The parent PolicyElement.
	 */
	void processNode(Node node, Interface parent) throws SAXException{
		Parameter param = null;
		
		// save us from null pointer de-referencing
		if (node == null){
			System.err.println(
			"Nothing to do, node is null");
			return;
		}
		
		// snag the name
		String nodeName = node.getNodeName();
		
		// if description node
		if (node.getNodeType() == Node.ELEMENT_NODE && nodeName.equals("description")){
			// unfortunately we still need to snag the PCDATA child node for the actual text
			NodeList children = node.getChildNodes();
			if (children != null && children.getLength() == 1){
				if (children.item(0).getNodeType() == Node.TEXT_NODE){
					parent.PCDATA = children.item(0).getNodeValue();
					return;
				} 
			}
			fatalNodeError("Unexpected child \"" + nodeName 
					+"\" node of parent \"" + parent.Name + "\".");
		// if infoflow node
		} else if (node.getNodeType() == Node.ELEMENT_NODE && nodeName.equals("infoflow")){
			NamedNodeMap attrList =	node.getAttributes();
			// the required attributes
			int attrLen = 0;
			if(attrList != null){
				attrLen = attrList.getLength();
			} else{
				fatalNodeError("Missing attribute in infoflow." 
						+ "  \"type\" and \"weight\" are required attributes.");
			}

			Node typeNode = attrList.getNamedItem("type");
			Node weightNode = attrList.getNamedItem("weight");
				
			String type = typeNode.getNodeValue();
			if (typeNode == null || 
					(!type.equals("none") && weightNode == null))
				fatalNodeError("Missing attribute in infoflow." 
						+ "  \"type\" and \"weight\" are required attributes (unless type is none).");

			if (type.equals("read")){
				parent.Type = InterfaceType.Read;
				parent.Weight = Integer.parseInt(weightNode.getNodeValue());
			}else if (type.equals("write")){
				parent.Type = InterfaceType.Write;
				parent.Weight = Integer.parseInt(weightNode.getNodeValue());
			}else if (type.equals("both")){
				parent.Type = InterfaceType.Both;
				parent.Weight = Integer.parseInt(weightNode.getNodeValue());
			}else if (type.equals("none")){
				parent.Type = InterfaceType.None;
				parent.Weight = -1;
			} else {
				System.err.println("Infoflow type must be read, write, both, or none!"); 
			}
			
		} else if (node.getNodeType() == Node.ELEMENT_NODE && nodeName.equals("parameter")){
			NamedNodeMap attrList =	node.getAttributes();
			// the required attributes
			int attrLen = 0;
			if(attrList != null){
				attrLen = attrList.getLength();
			} else{
				fatalNodeError("Missing attribute in parameter \"" 
						+ "\".  \"name\" is a required attribute.");
			}

			Node nameNode = attrList.getNamedItem("name");
						
			if (nameNode == null )
				fatalNodeError("Missing attribute in parameter \"" 
						+ "\".  \"name\" is a required attribute.");

			String paramName = nameNode.getNodeValue();
		
			Docgen.Debug("Creating parameter: " + paramName);
			param = new Parameter(paramName, parent);

			// unfortunately we still need to snag the PCDATA child node for the actual text
			NodeList children = node.getChildNodes();
			if (children != null && children.getLength() == 1){
				if (children.item(0).getNodeType() == Node.TEXT_NODE){
					param.PCDATA = children.item(0).getNodeValue();
				} 
			} else {
				fatalNodeError("Unexpected child \"" 
						+"\" node of parameter.");
			}
				
			// take care of the attributes
			for(int i = 0; i < attrLen; i++){
				Node attrNode = attrList.item(i);
				String attrName = attrNode.getNodeName();
				String attrValue = attrNode.getNodeValue();
				if (!attrName.equals("name")){
					Docgen.Debug("\tAdding attribute: " + attrNode.getNodeName()
							+ "=" + attrValue);
					param.AddAttribute(attrName,attrValue);
				}
			}
		} else if (!isEmptyTextNode(node)){
			fatalNodeError("Unexpected child \"" + nodeName 
					+"\" node of parent \"" + parent.Name + "\".");
		}
		
		// recurse over children if both parent and param defined
		if (param != null && parent != null){
			// the containsKey check verified no duplicate module
			// FIXME: containsKey() check for duplicate
			parent.Children.put(param.Name, param);
		}
	}

	public boolean isEmptyTextNode(Node node){
		/*
		 * FIXME: remove once properly validating
		 * Since we aren't validating yet we needed our
		 * own pointless whitespace remover.
		 */

		if (node.getNodeType() == Node.TEXT_NODE &&
				node.getNodeValue().trim().length() == 0)
				return true;
		return false;
	}	
	
	public void fatalNodeError(String msg) 
	throws SAXException {
		// FIXME: figure out how to throw SAXParseException w/ location
		throw new SAXException(msg);
	}
}
