package org.snomed.snowstorm.fhir.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class FileToJSON {

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
			throw new Exception ("Unable to read: " + file.toString());
		}
		String description = args[2];
		convertFileToJSON(vsName, file, description);
	}
	
	private static void convertFileToJSON(String vsName, File file, String description) throws IOException {
		String id = encode(vsName);
		out ("{");
		out ("	\"resourceType\": \"ValueSet\",");
		out ("	\"id\": \"" + id + "\",");
		out ("	\"meta\": {" );
		out ("		\"lastUpdated\": \""+ now() + "\"");
		out ("	},");
		out ("	\"language\": \"en\",");
		out ("	\"url\": \"http://snomed.org/fhir/ValueSet/" + id + "\",");
		out ("	\"version\": \"0.0.1\"," );
		out ("	\"name\": \"" + vsName + "\"," );
		out ("	\"status\": \"draft\"," );
		out ("	\"experimental\": true,");
		out ("	\"publisher\": \"SNOMED International\"," ); 
		out("	\"contact\": [{" ); 
		out("		\"telecom\": [{" ); 
		out("		\"system\": \"url\"," ); 
		out("		\"value\": \"http://snomed.org\"" ); 
		out("		},{" ); 
		out("		\"system\": \"email\"," ); 
		out("		\"value\": \"techsupport@snomed.org\"" ); 
		out("		}] }]," ); 
		out("	\"description\": \""+ description + "\",");
		out("	\"expansion\": {");
		out("		\"contains\": [ ");
		outputFile(file);
		out("		]}");
		out("}");
	}

	private static void outputFile(File file) throws IOException {
		boolean isFirst = true;
		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			String line;
			while ((line = br.readLine()) != null) {
				if (!isFirst) {
					out("			,");
				} else {
					isFirst = false;
				}
				String[] items = line.split("\t");
				outputLine(items[0], items[1], items[2], items[3]);
			}
		}
	}

	private static void outputLine(String sctId, String active, String fsn, String pt) {
		out("			{ \"system\": \"http://snomed.info/sct\","); 
		out("			\"code\": \""+ sctId + "\","); 
		out("			\"display\": \""+ pt + "\","); 
		out("			\"designation\": [{"); 
		out("				\"language\": \"en\","); 
		out("				\"use\": {"); 
		out("					\"system\": \"http://snomed.info/sct\","); 
		out("					\"code\": \"900000000000013009\","); 
		out("					\"display\": \"Synonym\""); 
		out("				},"); 
		out("				\"value\": \""+ pt + "\"}, "); 
		out("				{ \"language\": \"en\","); 
		out("				\"use\": {"); 
		out("					\"system\": \"http://snomed.info/sct\","); 
		out("					\"code\": \"900000000000003001\","); 
		out("					\"display\": \"Fully specified name\" },"); 
		out("				\"value\": \""+ fsn + "\""); 
		out("				}]}"); 
	}

	private static String now() {
		TimeZone tz = TimeZone.getTimeZone("UTC");
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'"); // Quoted "Z" to indicate UTC, no timezone offset
		df.setTimeZone(tz);
		return df.format(new Date());
	}

	private static String encode(String vsName) {
		return vsName.toLowerCase().replaceAll(" ", "-");
	}

	public static void debug(String msg) {
		System.out.println(msg);
	}
	
	public static void out(String msg) {
		System.out.println(msg);
	}

}
