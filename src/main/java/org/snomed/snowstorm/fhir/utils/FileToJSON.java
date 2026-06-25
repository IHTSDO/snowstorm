package org.snomed.snowstorm.fhir.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class FileToJSON {

	private static final Logger LOGGER = LoggerFactory.getLogger(FileToJSON.class);

	public static void main(String[] args) throws Exception {
		if (args.length < 3) {
			debug ("Valueset TSV file to ValueSet JSON");
			debug ("Usage:");
			debug ("java -cp snowstorm.jar org.snomed.snowstorm.fhir.utils.FileToJSON <ValueSetName> <FilePath> <Description>");
			System.exit(-1);
		}
		String vsName = args[0];
		File file = new File (args[1]);
		if (!file.isFile() ||!file.canRead()) {
			throw new Exception ("Unable to read: " + file);
		}
		String description = args[2];
		convertFileToJSON(vsName, file, description);
	}
	
	private static void convertFileToJSON(String vsName, File file, String description) throws IOException {
		String id = encode(vsName);
		out ("{");
		out ("\t\"resourceType\": \"ValueSet\",");
		out ("\t\"id\": \"" + id + "\",");
		out ("\t\"meta\": {" );
		out ("\t\t\"lastUpdated\": \""+ now() + "\"");
		out ("\t},");
		out ("\t\"language\": \"en\",");
		out ("\t\"url\": \"http://snomed.org/fhir/ValueSet/" + id + "\",");
		out ("\t\"version\": \"0.0.1\"," );
		out ("\t\"name\": \"" + vsName + "\"," );
		out ("\t\"status\": \"draft\"," );
		out ("\t\"experimental\": true,");
		out ("\t\"publisher\": \"SNOMED International\"," ); 
		out("\t\"contact\": [{" ); 
		out("\t\t\"telecom\": [{" ); 
		out("\t\t\"system\": \"url\"," ); 
		out("\t\t\"value\": \"http://snomed.org\"" ); 
		out("\t\t},{" ); 
		out("\t\t\"system\": \"email\"," ); 
		out("\t\t\"value\": \"techsupport@snomed.org\"" ); 
		out("\t\t}] }]," ); 
		out("\t\"description\": \""+ description + "\",");
		out("\t\"expansion\": {");
		out("\t\t\"contains\": [ ");
		outputFile(file);
		out("\t\t]}");
		out("}");
	}

	private static void outputFile(File file) throws IOException {
		boolean isFirst = true;
		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			String line;
			while ((line = br.readLine()) != null) {
				if (!isFirst) {
					out("\t\t\t,");
				} else {
					isFirst = false;
				}
				String[] items = line.split("\t");
				outputLine(items[0], items[1], items[2], items[3]);
			}
		}
	}

	private static void outputLine(String sctId, String active, String fsn, String pt) {
		out("\t\t\t{ \"system\": \"http://snomed.info/sct\","); 
		out("\t\t\t\"code\": \""+ sctId + "\","); 
		out("\t\t\t\"display\": \""+ pt + "\","); 
		out("\t\t\t\"designation\": [{"); 
		out("\t\t\t\t\"language\": \"en\","); 
		out("\t\t\t\t\"use\": {"); 
		out("\t\t\t\t\t\"system\": \"http://snomed.info/sct\","); 
		out("\t\t\t\t\t\"code\": \"900000000000013009\","); 
		out("\t\t\t\t\t\"display\": \"Synonym\""); 
		out("\t\t\t\t},"); 
		out("\t\t\t\t\"value\": \""+ pt + "\"}, "); 
		out("\t\t\t\t{ \"language\": \"en\","); 
		out("\t\t\t\t\"use\": {"); 
		out("\t\t\t\t\t\"system\": \"http://snomed.info/sct\","); 
		out("\t\t\t\t\t\"code\": \"900000000000003001\","); 
		out("\t\t\t\t\t\"display\": \"Fully specified name\" },"); 
		out("\t\t\t\t\"value\": \""+ fsn + "\""); 
		out("\t\t\t\t}]}"); 
	}

	private static String now() {
		TimeZone tz = TimeZone.getTimeZone("UTC");
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'"); // Quoted "Z" to indicate UTC, no timezone offset
		df.setTimeZone(tz);
		return df.format(new Date());
	}

	private static String encode(String vsName) {
		return vsName.toLowerCase().replace(" ", "-");
	}

	public static void debug(String msg) {
		LOGGER.info(msg);
	}

	public static void out(String msg) {
		LOGGER.info(msg);
	}

}
