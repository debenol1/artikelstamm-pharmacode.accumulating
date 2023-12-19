/**
 *  Copyright 2023 Framsteg GmbH / Olivier Debenath
 *  
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  
 *   http://www.apache.org/licenses/LICENSE-2.0
 *   
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package ch.framsteg.artikelstamm.pharmacode.accumulating;

import java.io.BufferedReader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import javax.xml.XMLConstants;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Accumulator {

	final static private String PREAMBLE = "How to start the Accumulator: java -jar accumulator.jar -p /PATH/TO/DICTIONARY.csv -gtin 0 -phar 1";
	final static private String NAMESPACE = "http://elexis.ch/Elexis_Artikelstamm_v5";
	final static private String CSV_INPU_MSG = "CSV input line: {0}";
	final static private String OPERAND_ERR_MSG = "Missing/wrong file operands";
	final static private String NO_PHAR_MSG = "No PHAR found";
	final static private String DETECTED_GTINS_MSG="Detected GTINs: ({0})";
	final static private String DETECTED_PAHRS_MSG="Detected PHARs: ({0})";
	final static private String APPENDED_PAHRS_MSG="Appended PHARs: ({0})";
	final static private String EMPTY_FILE_ERR_MSG = "Empty file";
	final static private String NEWLINE="\n";
	final static private String ARROW = " --> ";
	final static private String XML_TAG_GTIN="GTIN";
	final static private String XML_TAG_PHAR="PHAR";
	final static private String POSTAMBLE = "Finished";
	
	private static final Logger logger = LogManager.getLogger(Accumulator.class);

	public static void main(String[] args) {
		// Launching
		logger.info(PREAMBLE);
		if (args.length == 2 || args.length == 3) {
			boolean verbose = args.length == 3 ? true : false;
			try {
				HashMap<String, String> csvInput = readCSV(args[0], verbose);
				if (csvInput != null) {
					modifyXML(args[1], csvInput, verbose);
				}
			} catch (IOException | JDOMException e) {
				e.printStackTrace();
			}
		// Escaping
		} else {	
			logger.info(OPERAND_ERR_MSG);
			logger.info(Arrays.toString(args));
			System.exit(2);
		}
	}
	
	// Read dictionary (CSV) contaqining the PHArmACODES
	static HashMap<String, String> readCSV(String path, boolean verbose) throws IOException {
		HashMap<String, String> values = new HashMap<String, String>();
		BufferedReader br = new BufferedReader(new FileReader(path));
		String line;
		while ((line = br.readLine()) != null) {
			if (verbose) {
				logger.info(MessageFormat.format(CSV_INPU_MSG, line));
			}
			String[] lineValues = line.split(",");
			values.put(lineValues[0], lineValues[1]);
		}
		br.close();
		return values;
	}
	
	// Adding the pharmacodes to the Artikelstamm.xml
	static void modifyXML(String path, HashMap<String, String> values, boolean verbose)
			throws JDOMException, IOException {

		StringBuilder modifications = new StringBuilder();

		Namespace ns = Namespace.getNamespace(NAMESPACE);

		SAXBuilder sax = new SAXBuilder();
		sax.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		sax.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

		Document doc = sax.build(new File(path));

		Element rootNode = doc.getRootElement();
		Element itemsElement = rootNode.getChild("ITEMS", ns);

		List<Element> items = itemsElement.getChildren();

		int gtinCounter = 0;
		int pharCounter = 0;
		int addedPharCounter = 0;

		if (items.size() > 0) {

			for (Element itemElement : items) {
				Element gtinElement = itemElement.getChild(XML_TAG_GTIN, ns);
				Element pharElement = itemElement.getChild(XML_TAG_PHAR, ns);

				if (gtinElement != null) {
					gtinCounter++;
				}

				if (pharElement != null) {
					pharCounter++;
				} else {
					String newPharElementName = values.get(gtinElement.getText());
					if (newPharElementName != null && !newPharElementName.isBlank()) {
						pharElement = new Element(XML_TAG_PHAR, ns);
						pharElement.setText(newPharElementName);
						itemElement.addContent(2, pharElement);
						modifications.append(gtinElement.getText() + ARROW
								+ (!pharElement.getText().isBlank() ? pharElement.getText() : NO_PHAR_MSG) + NEWLINE);
						addedPharCounter++;
					}
				}
			}

			FileWriter writer = new FileWriter(path);
			XMLOutputter outputter = new XMLOutputter();

			outputter.setFormat(Format.getRawFormat());
			outputter.output(doc, writer);

			String xmlDocument = outputter.outputString(doc);
			if (verbose) {
				logger.info(xmlDocument);
				logger.info(modifications.toString());
			}
			logger.info(MessageFormat.format(DETECTED_GTINS_MSG, gtinCounter));
			logger.info(MessageFormat.format(DETECTED_PAHRS_MSG, pharCounter));
			logger.info(MessageFormat.format(APPENDED_PAHRS_MSG, addedPharCounter));
			logger.info(POSTAMBLE);
		} else {
			logger.info(EMPTY_FILE_ERR_MSG );
		}
	}
}
