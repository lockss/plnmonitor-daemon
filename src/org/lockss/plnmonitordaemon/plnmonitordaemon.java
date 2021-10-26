package org.lockss.plnmonitordaemon;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

//import org.lockss.plnmonitordaemon.DaemonStatusWebService;
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
	private static String LOCKSS_PROP_SERVER = "https://lockssadmin.ulb.ac.be/lockss.xml";

	/** Postgresql driver */
	private static String dbDriver = "org.postgresql.Driver";

	/** Database IP or hostname*/
	private static String dbIP = "plnmonitordb";

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

		//Instantiating the File class



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

					System.out.println((char)27 + "[34mLoading lockss.xml configuration file from: " + configFile + (char)27 + "[39m");
					List<String> boxIpAddresses = dsws.loadPLNConfiguration(plnID, "pln", configFile);
					for (String boxIpAddress : boxIpAddresses) {
						System.out.println((char)27 + "[34mLoading configuration of: " + boxIpAddress);
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


			// As Pivot is not available in Postgres, we need to set the column names in the SQL query performed by the dashboard
			// This is replacing the "PIVOTRAWSQLREQUEST" tag in the Grafana dashboard template

			// First part of the SQL query string
			String pivotTableRequest = "select au_current.name, ";

			//TODO: make template path configurable
			String dashboardTemplatePath = "/opt/template/dashboard_template.json";

			//TODO: make template path configurable
			String lockssBoxTemplatePath = "/opt/template/lockss_box_template.json";

			//TODO: make template path configurable
			String tdbPublisherTemplatePath = "/opt/template/tdb_publisher_template.json";

			System.out.println((char)27 + "[34mSetting up PLN dashboard based on Grafana"  + (char)27 + "[39m");

			Scanner sc;

			try {

				// TODO: this is a temporary solution to automate dashboard creation -> it will later be replaced by dashboard spec/Grafonnet
				// Reading Grafana Dashboard template file and puting contents in the dahsboardTemplateContents variable
				sc = new Scanner(new File(dashboardTemplatePath));
				StringBuffer buffer = new StringBuffer();
				while (sc.hasNextLine()) {
					buffer.append(sc.nextLine()+System.lineSeparator());
				}
				String dahsboardTemplateContents = buffer.toString();

				//clear buffer before reading another template file
				buffer.setLength(0);
				sc = new Scanner(new File(lockssBoxTemplatePath));

				// Reading LOCKSS Box template file and putting contents in the dahsboardTemplateContents variable
				while (sc.hasNextLine()) {
					buffer.append(sc.nextLine()+System.lineSeparator());
				}
				String lockssBoxTemplateContents = buffer.toString();

				//clear buffer before reading another template file
				buffer.setLength(0);
				sc = new Scanner(new File(lockssBoxTemplatePath));

				// Reading TDB Publisher template file and putting contents in the dahsboardTemplateContents variable
				while (sc.hasNextLine()) {
					buffer.append(sc.nextLine()+System.lineSeparator());
				}
				String tdbPublisherTemplateContents = buffer.toString();
				sc.close();

				Scanner userAnswer = new Scanner(System.in);
				String currentAnswer;

				System.out.println("This script helps you configure the plnmonitor database for your lockss network ");

				System.out.println("lockss.xml URL (LOCKSS network props server): ["+ LOCKSS_PROP_SERVER + "]");
				currentAnswer = userAnswer.nextLine();
				if (!currentAnswer.isEmpty()) {
					LOCKSS_PROP_SERVER = currentAnswer;
				}

				System.out.println("\n\nPLN monitor posgres database configuration (leave default settings by pressing enter) ");

				System.out.println("Postgres server hostname: ["+ dbIP + "]");
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
						System.out.println("\n\n" + (char)27 + "[36mSetting configuration of: " + boxIpAddress + (char)27 + "[39m");

						System.out.println("Getting most likely lockss box location :");	

						String geourl = " https://freegeoip.app/xml/"+ boxIpAddress;

						String boxname = "ULB";
						String longitude = "4.383539";
						String latitude = "50.810061";
						String country = "Belgium";
						String city = "Brussels";
						//String institutionname = "ULB";


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

						System.out.println("\n\n" + (char)27 + "[33mTrying to load the current configuration of: " + boxIpAddress + " - Please wait." + (char)27 + "[39m");

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
						System.out.println((char)27 + "[33mPlease provide or confirm LOCKSS box information (just hit enter for default value in brackets):"  + (char)27 + "[39m");

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
									System.out.println((char)27 + "[31mError: Please provide a valid number for your answer.\n"  + (char)27 + "[39m");
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
									System.out.println((char)27 + "[31mError: Please provide a valid number for your answer.\n"  + (char)27 + "[39m");
								}
							}
						}

						System.out.println("Country: [" + country + "]");
						currentAnswer = userAnswer.nextLine();
						if (!currentAnswer.isEmpty()) {
							country = currentAnswer;
						}

						System.out.println((char)27 + "[33mPlease give this LOCKSS box a nickname: [" + boxname + "]"  + (char)27 + "[39m");
						currentAnswer = userAnswer.nextLine();
						if (!currentAnswer.isEmpty()) {
							boxname = currentAnswer;
						}

						//						institutionname = boxname;
						//						System.out.println((char)27 + "[33mWTo which institution does this box belong? [" + institutionname + "]"  + (char)27 + "[39m");
						//						currentAnswer = userAnswer.nextLine();
						//						if (!currentAnswer.isEmpty()) {
						//							institutionname = currentAnswer;
						//						}


						System.out.println("Please wait, the database is being updated with LOCKSS box data from " + boxname + " ...");
						// update postgres accordingly
						dsws.setBoxInfo(1, boxIpAddress, username, password, latitude, longitude, country, boxname);
						dsws.loadDaemonStatus(1, boxIpAddress);

						pivotTableRequest =  pivotTableRequest + " MAX(CASE WHEN lockss_box_info.name = '" + boxname +"' THEN recent_poll_agreement END) AS \\\\\"" + boxname + "\\\\\" ,"; 

						System.out.println("****************************************************************");

						System.out.println("Creating dashboard for LOCKSS box " + boxname);
						File dir = new File("/opt/provisioning/dashboards/boxes/");

						// create box directory if it does not exist
						dir.mkdirs();

						// replace LOCKSS_BOX_NAME -> boxname in Grafana LOCKSS Box template file
						String lockssBoxContents = lockssBoxTemplateContents.replaceAll("LOCKSS_BOX_NAME", boxname);

						FileWriter writer = new FileWriter("/opt/provisioning/dashboards/boxes/"+ boxname.replaceAll("\\W+", "")  +".json");
						writer.append(lockssBoxContents);
						writer.flush();
						writer.close();

					}

					pivotTableRequest = pivotTableRequest.substring(0, pivotTableRequest.length() - 2) + " from plnmonitor.au_current inner join plnmonitor.lockss_box_info on au_current.box=lockss_box_info.box GROUP BY 1 ORDER BY 1";

					System.out.println("Creating global network dashboard");
					//Replacing the pivot request line with box info
					dahsboardTemplateContents = dahsboardTemplateContents.replaceAll("PIVOTRAWSQLREQUEST", pivotTableRequest);
					//instantiating the FileWriter class
					FileWriter writer = new FileWriter("/opt/provisioning/dashboards/dashboard.json");
					writer.append(dahsboardTemplateContents);
					writer.flush();
					writer.close();

					// Creation of one dashboard per TDB publisher
					// LOCKSS_TDB_PUBLISHER_NAME
					System.out.println("Creating TDB publisher dashboards");

					File dir = new File("/opt/provisioning/dashboards/tdb_publishers/");

					// create box directory if it does not exist
					dir.mkdirs();


					// Collect TDB publisher names from database

					System.out.println("Loading TDB publishers from database");
					List<String> tdbPublishers = dsws.getTdbPublishers(1);

					System.out.println("\n\n" + tdbPublishers.size() + " TDB Publishers available");

					for (String tdbPublisher : tdbPublishers) {


						// replace LOCKSS_BOX_NAME -> boxname in Grafana LOCKSS Box template file
						if ((tdbPublisher != null) && (tdbPublisher.length()!=0)) {
							System.out.println("Creating dashboard for TDB Publisher: " + tdbPublisher);
							String tdbPublisherContents = tdbPublisherTemplateContents.replaceAll("LOCKSS_TDB_PUBLISHER_NAME", tdbPublisher);

							FileWriter dirWriter = new FileWriter(dir + tdbPublisher.replaceAll("\\W+", "")  +".json");
							dirWriter.append(tdbPublisherContents);
							dirWriter.flush();
							dirWriter.close();
						}
					}


					System.out.println("Setting administrator credentials for webapp");

					String adminname = "admin"; 
					String adminpassword = "admin";
					System.out.println("\n\n" + (char)27 + "[33mPlease provide the admin username: [" + adminname + "]"  + (char)27 + "[39m");
					currentAnswer = userAnswer.nextLine();
					if (!currentAnswer.isEmpty()) {
						adminname = currentAnswer;
					}
					System.out.println((char)27 + "[33mPlease provide the admin password: [" + adminpassword + "]"  + (char)27 + "[39m");
					currentAnswer = userAnswer.nextLine();
					if (!currentAnswer.isEmpty()) {
						adminname = currentAnswer;
					}


					dsws.setCredentials(adminname, "ADMIN", adminpassword);

					System.out.println("\n\nplnmonitor is ready to run");
					System.out.println((char)27 + "[32mYou can now launch plnmonitor by executing ./start.sh"  + (char)27 + "[39m");
					userAnswer.close();





				} catch (Exception e) {
					System.out.println((char)27 + "[31mAn error occured during the configuration of your LOCKSS network."  + (char)27 + "[39m");
					e.printStackTrace();
				}
			} catch (FileNotFoundException e1) {
				System.out.println("Can't find dashboard_template.json file in the template directory");
				e1.printStackTrace();
			}

		}
		else {
			System.out.println("Usage: \'java -jar plnmonitor-daemon-service config\' to configure or \\'java -jar plnmonitordaemon\\' to run");
		}



	}

}


