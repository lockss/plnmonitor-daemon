package org.lockss.plnmonitordaemon;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.lockss.plnmonitordaemon.DaemonStatusWebService;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * The Class plnmonitordaemon-service
 * 
 * Main class of the daemon
 * Starts by method loadPLNConfiguration to get IPadresses of all boxes in the LOCKSS network
 * Then collects info from all boxes with method loadDaemonStatus and stores status in the DB
 * 
 * Originally intended to be a Java Thread then used as a cron task executed once a day at 08:30 am
 * 
 * /etc/cron.d/plnmonitor 
 *  30 8  *  *  * root java -jar /opt/plnmonitor-daemon.jar  
 */
public class plnmonitordaemon {


	/**  URL of props server */
	private static String LOCKSS_PROP_SERVER = "http://lockssadmin.ulb.ac.be/lockss.xml";

	/** Postgresql driver */
	private static String dbDriver = "org.postgresql.Driver";

	/** Database IP or hostname*/
	private static String dbIP = "127.0.0.1";

	/** Database port*/
	private static String dbPort = "5432";

	/** Database name*/
	private static String dbName = "plnmonitor";

	/** Default db username*/
	private static String dbUser = "plnmonitor";

	/** Default db password*/
	private static String dbPassword = "plnmonitor";

	/** Default URL for jdbc connection */		
	private static String dbConnectionURL = "jdbc:postgresql://plnmonitordb:5432/plnmonitor";

	/** boxUsername. Default daemon UI user name for user with debug info access only (read) for all lockss boxes in the network (8081)*/
	private static String boxUsername = "debug";

	/** boxPassword. Default daemon UI password for user with debug info access only (read) for all lockss boxes in the network (8081)*/
	private static String boxPassword = "debuglockss";	

	/**
	 * The main method.
	 *
	 * @param args the arguments
	 */
	public static void main(String[] args) {



		DaemonStatusWebService dsws = null;

		// daemon mode (assuming configuration has been set earlier)
		if ((args == null) || (args.length == 0)) {

			System.out.println("Updating LOCKSS network status..." );
			try {
				dsws = new DaemonStatusWebService(dbConnectionURL, dbUser, dbPassword, dbDriver);
				HashMap<Integer, String> configFiles = dsws.getPLNConfigurationFiles();
				System.out.println(configFiles.values());
				for (Map.Entry<Integer, String> entry : configFiles.entrySet()) {
					Integer plnID = entry.getKey();
					String configFile = entry.getValue();

					System.out.println("Loading lockss.xml configuration file from: " + configFile);
					List<String> boxIpAddresses = dsws.loadPLNConfiguration(plnID, "pln", configFile);
					for (String boxIpAddress : boxIpAddresses) {
						System.out.println("Loading configuration of: " + boxIpAddress);
						dsws.loadDaemonStatus(plnID, boxIpAddress);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		// config mode (setting daemon configuration)
		//TODO: record configuration in the config file
		else if ((args.length == 1) && (args[0].compareTo("config")==0 )  ) {

			Scanner userAnswer = new Scanner(System.in);
			String currentAnswer;

			System.out.println("This script helps you configure the plnmonitor database for your lockss network ");

			System.out.println("lockss.xml URL (LOCKSS network props server): ["+ LOCKSS_PROP_SERVER + "]");
			currentAnswer = userAnswer.nextLine();
			if (!currentAnswer.isEmpty()) {
				LOCKSS_PROP_SERVER = currentAnswer;
			}

			System.out.println("\n\nPLN monitor posgres database configuration (leave default settings by pressing enter) ");

			System.out.println("Postgres server IP or hostname: ["+ dbIP + "]");
			currentAnswer = userAnswer.nextLine();
			if (!currentAnswer.isEmpty()) {
				dbIP = currentAnswer;
			}

			System.out.println("Postgres server port: ["+ dbPort + "]");
			currentAnswer = userAnswer.nextLine();
			if (!currentAnswer.isEmpty()) {
				dbPort = currentAnswer;
			}

			System.out.println("Postgres database name: ["+ dbName + "]");
			currentAnswer = userAnswer.nextLine();
			if (!currentAnswer.isEmpty()) {
				dbName = currentAnswer;
			}

			System.out.println("Postgres database user: ["+ dbUser + "]");
			currentAnswer = userAnswer.nextLine();
			if (!currentAnswer.isEmpty()) {
				dbUser = currentAnswer;
			}

			System.out.println("Postgres database password: ["+ dbPassword + "]");
			currentAnswer = userAnswer.nextLine();
			if (!currentAnswer.isEmpty()) {
				dbPassword = currentAnswer;
			}

			try {
				dbConnectionURL = "jdbc:postgresql://" + dbIP + ":" + dbPort + "/" + dbName;
				dsws = new DaemonStatusWebService(dbConnectionURL, dbUser, dbPassword, dbDriver);

					// collect lockss_box info from user input

					System.out.println("Loading lockss.xml configuration file from: " + LOCKSS_PROP_SERVER);
					List<String> boxIpAddresses = dsws.loadPLNConfiguration(1, "pln", LOCKSS_PROP_SERVER);
					
					System.out.println("\n\n" + boxIpAddresses.size() + " LOCKSS boxes detected in your network");

					for (String boxIpAddress : boxIpAddresses) {
						System.out.println("\n\nSetting configuration of: " + boxIpAddress);

						System.out.println("Getting most likely lockss box location :");	

						String geourl = " https://freegeoip.app/xml/"+ boxIpAddress;

						String boxname = "ULB";
						String longitude = "4.383539";
						String latitude = "50.810061";
						String country = "Belgium";
						String city = "Brussels";

						DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
						factory.setNamespaceAware(true);
						try{
							DocumentBuilder db = factory.newDocumentBuilder();

							Document doc = db.parse(new URL(geourl).openStream());
							doc.getDocumentElement().normalize();
							Element response = doc.getDocumentElement();

							if (response.getNodeType() == Node.ELEMENT_NODE) {
								country = response.getElementsByTagName("CountryName").item(0).getTextContent();
								longitude = response.getElementsByTagName("Longitude").item(0).getTextContent();
								latitude = response.getElementsByTagName("Latitude").item(0).getTextContent();
								city = response.getElementsByTagName("City").item(0).getTextContent();
								boxname = city;
							}

						}
						catch(Exception e){
							System.out.println(e.toString());

						}

						System.out.println(city + "," + country + " -- " + "long. : " + longitude + " - lat. : " + latitude);


						String username = boxUsername;
						String password = boxPassword;

						System.out.println("\n\nDebug user name: [" + username + "]");
						currentAnswer = userAnswer.nextLine();
						if (!currentAnswer.isEmpty()) {
							username = currentAnswer;
						}

						System.out.println("Debug user password: [" + password + "]");
						currentAnswer = userAnswer.nextLine();
						if (!currentAnswer.isEmpty()) {
							password = currentAnswer;
						}

						System.out.println("\n\nLoading current configuration of: " + boxIpAddress + " (if available)");

						// get current config info from box
						HashMap <String, String> boxInfo = dsws.getBoxInfo(1, boxIpAddress, username, password);

						if (boxInfo != null) {
							username = boxInfo.get("username");
							password = boxInfo.get("password");
							longitude = boxInfo.get("longitude");
							latitude = boxInfo.get("latitude");
							country = boxInfo.get("country");
							boxname = boxInfo.get("boxname");
							System.out.println("Box info already present and collected from database");
						}
						else {
							System.out.println("No box info currently available in the database");
						}
						System.out.println("Please provide or confirm LOCKSS box information:");

						while (true) {

							System.out.println("Latitude: [" + latitude + "]");
							currentAnswer = userAnswer.nextLine();

							if (currentAnswer.isEmpty()) {
								break;
							}
							else {
								try {
									Double.parseDouble(currentAnswer);
									latitude = currentAnswer;
									break;
								} 
								catch (NumberFormatException e) {
									System.out.println("Error: Please provide a valid number for your answer.\n");
								}
							}
						}

						while (true) {

							System.out.println("Longitude: [" + longitude + "]");
							currentAnswer = userAnswer.nextLine();

							if (currentAnswer.isEmpty()) {
								break;
							}
							else {
								try {
									Double.parseDouble(currentAnswer);
									latitude = currentAnswer;
									break;
								} 
								catch (NumberFormatException e) {
									System.out.println("Error: Please provide a valid number for your answer.\n");
								}
							}
						}

						System.out.println("Country: [" + country + "]");
						currentAnswer = userAnswer.nextLine();
						if (!currentAnswer.isEmpty()) {
							country = currentAnswer;
						}

						System.out.println("Please give this LOCKSS box a nickname: [" + boxname + "]");
						currentAnswer = userAnswer.nextLine();
						if (!currentAnswer.isEmpty()) {
							boxname = currentAnswer;
						}

						
						// update postgres accordingly
						dsws.setBoxInfo(1, boxIpAddress, username, password, latitude, longitude, country, boxname);
						System.out.println("****************************************************************");
					}
					
					System.out.println("Setting administrator credentials for plnmonitor webapp");
					
					String adminname = "admin"; 
					String adminpassword = "admin";
					System.out.println("\n\nPlease give admin username: [" + adminname + "]");
					currentAnswer = userAnswer.nextLine();
					if (!currentAnswer.isEmpty()) {
						adminname = currentAnswer;
					}
					System.out.println("Please give admin password: [" + adminpassword + "]");
					currentAnswer = userAnswer.nextLine();
					if (!currentAnswer.isEmpty()) {
						adminname = currentAnswer;
					}
					
					
					dsws.setCredentials(adminname, "ADMIN", adminpassword);

					System.out.println("\n\nplnmonitor is ready to run");
					System.out.println("You can now launch plnmonitor by executing ./start.sh");
					userAnswer.close();
					
					
			} catch (Exception e) {
				System.out.println("An error occured during the configuration of your LOCKSS network.");
				e.printStackTrace();
			}
		
		}
		else {
			System.out.println("Usage: \'java -jar plnmonitor-daemon-service config\' to configure or \\'java -jar plnmonitordaemon\\' to run");
		}
	}

}


