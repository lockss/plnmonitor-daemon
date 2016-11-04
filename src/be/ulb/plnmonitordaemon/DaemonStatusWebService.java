package be.ulb.plnmonitordaemon;

import java.io.IOException;
import java.io.PrintWriter;

import javax.net.ssl.HttpsURLConnection;


import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.security.Security;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.ws.Service;
import javax.xml.ws.WebServiceException;

import org.apache.commons.io.FileUtils;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.lockss.ws.entities.AuWsResult;
import org.lockss.ws.entities.IdNamePair;
import org.lockss.ws.entities.LockssWebServicesFault;
import org.lockss.ws.entities.PeerWsResult;
import org.lockss.ws.entities.PlatformConfigurationWsResult;
import org.lockss.ws.entities.RepositorySpaceWsResult;
import org.lockss.ws.entities.RepositoryWsResult;
import org.lockss.ws.status.DaemonStatusService;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.net.URL;

public class DaemonStatusWebService {
	private static final String DB_DRIVER = "org.postgresql.Driver";
	private static final String DB_CONNECTION = "jdbc:postgresql://127.0.0.1:5432/plnmonitor";
	private static final String DB_USER = "plnmonitor";
	private static final String DB_PASSWORD = "plnmonitor";


	private static final String USER_NAME = "debug";
	private static final String PASSWORD = "debuglockss";
	private static final String prefixDSS = "http://";
	private static final String prefixSDSS = "https://";

	private static final String postfixDSS = ":8081/ws/DaemonStatusService?wsdl";
	private static final String TARGET_NAMESPACE = "http://status.ws.lockss.org/";
	private static final String SERVICE_NAME = "DaemonStatusServiceImplService";
	private static final String QUERY = "select auId, name, volume, pluginName, tdbYear, accessType, contentSize, diskUsage, recentPollAgreement, tdbPublisher, availableFromPublisher, substanceState, creationTime, crawlProxy, crawlWindow, crawlPool, lastCompletedCrawl, lastCrawl, lastCrawlResult, lastCompletedPoll, lastPollResult, currentlyCrawling, currentlyPolling, subscriptionStatus, auConfiguration, newContentCrawlUrls, urlStems, isBulkContent, peerAgreements";

	private static final String IPV4_PATTERN = 
			"(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)";


	// getPLNConfigurationFiles reads database entry in PLN table
	// returns url for lockss.xml config file for each pln in a List<String> 
	public HashMap<Integer, String> getPLNConfigurationFiles() throws SQLException{
		Connection dbConnection = null;
		HashMap<Integer,String> configurationFiles = new HashMap<Integer,String>(); 

		try {							
			String configUrlQuery = "SELECT config_url, id from plnmonitor.pln";

			dbConnection = getDBConnection();

			Statement stmt = dbConnection.createStatement();
			ResultSet rs = stmt.executeQuery(configUrlQuery);
			while (rs.next()) {
				configurationFiles.put(rs.getInt("id"),rs.getString("config_url"));
			}

		} catch (SQLException e) {
			System.out.println(e.getMessage());
		} finally {
			if (dbConnection != null) {
				dbConnection.close();
			}
		}
		return (configurationFiles);
	}


	//loadPLNConfiguration
	// read content of lockss.xml 
	// put all pln members IP address in plnMembers
	public List<String> loadPLNConfiguration(Integer plnID, String configUrl){
		List<String> plnMembers=new  ArrayList<String>();

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		try{
			DocumentBuilder db = factory.newDocumentBuilder();

			Document doc = db.parse(new URL(configUrl).openStream());
			doc.getDocumentElement().normalize();
			NodeList propertyList = doc.getElementsByTagName("property");
			for (int temp = 0; temp < propertyList.getLength(); temp++) {
				Node propertyNode = propertyList.item(temp);
				if (propertyNode.getNodeType() == Node.ELEMENT_NODE) {

					Element eElement = (Element) propertyNode;

					if (eElement.getAttribute("name").contains("id.initialV3PeerList")) {
						NodeList valuesList = eElement.getElementsByTagName("value");
						for (int i=0; i<valuesList.getLength(); i++) {
							System.out.println("Value : " + valuesList.item(i).getTextContent());
							Pattern p = Pattern.compile(IPV4_PATTERN);
							Matcher m = p.matcher(valuesList.item(i).getTextContent());
							while (m.find()) {
								plnMembers.add(m.group()) ;
							}
						}
					}
				}
			}

		}
		catch(Exception e){
			System.out.println(e.toString());

		}
		return (plnMembers);
	}

	// loadDaemonStatus: get the status from a LOCKSS box identified by its IP address boxIpAddress
	
	public void loadDaemonStatus(Integer plnID, String boxIpAddress) throws SQLException{
		Connection dbConnection = null;
		PreparedStatement preparedStatement=null;
		Service service = null;
		List<AuWsResult> ausFromCurrentBox = null;                  
		List<RepositorySpaceWsResult> repositoryBox = null;
		List<PeerWsResult> peersBox = null;
		List<RepositoryWsResult> repo = null;
		PlatformConfigurationWsResult boxConfiguration = null;
		Integer boxId=null;

		try {
			// Call the service and get the results of the query.
			// Store AUs results for each server in a Hashmap (server name, list of Aus)
			this.authenticate(); //basic authentication (inline)

			String serviceAddress=prefixDSS+boxIpAddress+postfixDSS; 

			try {
				service = Service.create(new URL(serviceAddress), new QName(
						TARGET_NAMESPACE, SERVICE_NAME));
				
			}

			catch (WebServiceException e) {
				System.out.println(e.toString());
				System.out.println("Trying secure connnection..." + prefixSDSS);
			
				try {
					if (boxIpAddress.matches("157.193.230.142")) {
						boxIpAddress = "shaw.ugent.be";
					}
					this.formAuthenticate(boxIpAddress);

					service = Service.create(new URL(prefixSDSS+boxIpAddress+postfixDSS), new QName(
							TARGET_NAMESPACE, SERVICE_NAME));
				
				}
				catch (WebServiceException ex) {
					System.out.println(ex.toString());
					System.out.println("Nothing do... connection lost");
				}
			}

			try {
				if (service != null) {  //if service available, get all data from the LOCKSS box
					boxConfiguration =service.getPort(DaemonStatusService.class).getPlatformConfiguration();
					repositoryBox = service.getPort(DaemonStatusService.class).queryRepositorySpaces("select *");
					ausFromCurrentBox = service.getPort(DaemonStatusService.class).queryAus(QUERY);
					peersBox = service.getPort(DaemonStatusService.class).queryPeers("select *");
					repo = service.getPort(DaemonStatusService.class).queryRepositories("select *");
				}
			}
			catch (WebServiceException e) {
				System.out.println(e.toString());
			}

			// if data from plaftorm configuration is available, update the LOCKSS box table accordingly in the database
			if (boxConfiguration!=null){
				//update LOCKSS box config in the LOCKSS_box database
				//upsert: if box date identified by (ipaddress+pln id) is already in the database, update entry otherwise insert 
				try {
					
					String insertTableSQL = 
							"WITH upsert AS " +
						    "(UPDATE plnmonitor.lockss_box " +
							"SET uiport = ?, " +
							"groups = ?, " +
							"v3identity = ?, " +
							"uptime = ?, " +
							"admin_email = ?, " +
							"disks = ?, " +
							"\"current_time\" = ?, " +
							"daemon_full_version = ?, " +
							"java_version = ?, " +
							"platform = ? " +
							"WHERE ipaddress=? and pln=? RETURNING *), " +
							
							"inserted AS ("+
							"INSERT INTO plnmonitor.lockss_box " +
							"(ipaddress,uiport,pln,groups,v3identity,uptime,admin_email,disks,\"current_time\", daemon_full_version, java_version, platform) "+
							"SELECT ?,?,?,?,?,?,?,?,?,?,?,? WHERE NOT EXISTS "+
							"(SELECT * FROM upsert) "+
							"RETURNING *) "+
							"SELECT * " +
							"FROM upsert " +
							"union all " +
							"SELECT * " +
							"FROM inserted";

					dbConnection = getDBConnection();
					preparedStatement = dbConnection.prepareStatement(insertTableSQL, Statement.KEEP_CURRENT_RESULT);
					preparedStatement.setString(1, "8081");
					preparedStatement.setString(2, boxConfiguration.getGroups().get(0).replaceAll("\\[|\\]", ""));
					preparedStatement.setString(3, boxConfiguration.getV3Identity().replaceAll("\\[|\\]", ""));
					preparedStatement.setLong(4, boxConfiguration.getUptime());
					preparedStatement.setString(5, boxConfiguration.getAdminEmail().replaceAll("\\[|\\]", ""));
					preparedStatement.setString(6, boxConfiguration.getDisks().get(0).replaceAll("\\[|\\]", ""));
					preparedStatement.setLong(7, boxConfiguration.getCurrentTime());
					preparedStatement.setString(8, boxConfiguration.getDaemonVersion().toString().replaceAll("\\[|\\]", ""));
					preparedStatement.setString(9, boxConfiguration.getJavaVersion().toString().replaceAll("\\[|\\]", ""));
					preparedStatement.setString(10, boxConfiguration.getPlatform().toString().replaceAll("\\[|\\]", ""));
					
					preparedStatement.setString(11, boxIpAddress);
					preparedStatement.setInt(12, plnID);
					
					preparedStatement.setString(13, boxIpAddress);
					preparedStatement.setString(14, "8081");
					preparedStatement.setInt(15, plnID);
					preparedStatement.setString(16, boxConfiguration.getGroups().get(0).replaceAll("\\[|\\]", ""));
					preparedStatement.setString(17, boxConfiguration.getV3Identity().replaceAll("\\[|\\]", ""));
					preparedStatement.setLong(18, boxConfiguration.getUptime());
					preparedStatement.setString(19, boxConfiguration.getAdminEmail().replaceAll("\\[|\\]", ""));
					preparedStatement.setString(20, boxConfiguration.getDisks().get(0).replaceAll("\\[|\\]", ""));
					preparedStatement.setLong(21, boxConfiguration.getCurrentTime());
					preparedStatement.setString(22, boxConfiguration.getDaemonVersion().toString().replaceAll("\\[|\\]", ""));
					preparedStatement.setString(23, boxConfiguration.getJavaVersion().toString().replaceAll("\\[|\\]", ""));
					preparedStatement.setString(24, boxConfiguration.getPlatform().toString().replaceAll("\\[|\\]", ""));
					System.out.println(preparedStatement.toString());
					ResultSet rs=preparedStatement.executeQuery();
					//ResultSet rs = preparedStatement.getResultSet();
					if (rs.next()) {
						boxId = rs.getInt("id");
					}
					System.out.println("Entry for pln: "+ plnID + " with IP address "+boxIpAddress + " ----- " + boxConfiguration.getIpAddress() + "V3 identity:" +  boxConfiguration.getV3Identity() + " is inserted/updated into LOCKSS_BOX table at position "+ boxId);
					
				} catch (SQLException e) {
					System.out.println(e.getMessage());

				} finally {
					if (preparedStatement != null) {
						preparedStatement.close();
					}
					if (dbConnection != null) {
						dbConnection.close();
					}
				}	
			}
			
			
			
			
			// if repository box data is collected for the current LOCKSS Box identified by box id and repository_space_lockss_id
			// insert the results in the table lockss_box_data_current
			
			if (repositoryBox != null) {
					for (RepositorySpaceWsResult currentBoxResult : repositoryBox) {
						try {							
							String insertTableSQL = "WITH upsert AS (UPDATE plnmonitor.lockss_box_data_current " +
									"SET used = ?, " +
									"size = ?, " +
									"free = ?, " +
									"percentage = ?, " +
									"active_aus = ?, " +
									"deleted_aus = ?, " +
									"inactive_aus = ?, " +
									"orphaned_aus = ? " +
									"WHERE box=? and repository_space_lockss_id=? RETURNING *)" +
									"INSERT INTO plnmonitor.lockss_box_data_current" +
									"(box,used,size,free,percentage,active_aus, repository_space_lockss_id, deleted_aus, inactive_aus, orphaned_aus) "+
									"SELECT ?,?,?,?,?,?,?,?,?,? WHERE NOT EXISTS "+
									"(SELECT * FROM upsert)";
							
							dbConnection = getDBConnection();
							preparedStatement = dbConnection.prepareStatement(insertTableSQL);
							preparedStatement.setLong(1, currentBoxResult.getUsed());
							preparedStatement.setLong(2, currentBoxResult.getSize());
							preparedStatement.setLong(3, currentBoxResult.getFree());
							preparedStatement.setDouble(4, currentBoxResult.getPercentageFull());
							preparedStatement.setLong(5, currentBoxResult.getActiveCount());
							preparedStatement.setLong(6, currentBoxResult.getDeletedCount());
							preparedStatement.setLong(7, currentBoxResult.getInactiveCount());
							preparedStatement.setLong(8, currentBoxResult.getOrphanedCount());
							
							preparedStatement.setLong(9, boxId );
							preparedStatement.setString(10, currentBoxResult.getRepositorySpaceId());
							
							preparedStatement.setLong(11, boxId );
							preparedStatement.setLong(12, currentBoxResult.getUsed());
							preparedStatement.setLong(13, currentBoxResult.getSize());
							preparedStatement.setLong(14, currentBoxResult.getFree());
							preparedStatement.setDouble(15, currentBoxResult.getPercentageFull());
							preparedStatement.setLong(16, currentBoxResult.getActiveCount());
							preparedStatement.setString(17, currentBoxResult.getRepositorySpaceId());
							preparedStatement.setLong(18,currentBoxResult.getDeletedCount());
							preparedStatement.setLong(19,currentBoxResult.getInactiveCount());
							preparedStatement.setLong(20, currentBoxResult.getOrphanedCount());

							System.out.println(preparedStatement.toString());
							preparedStatement.executeUpdate();

							System.out.println("Record is inserted and updated into database table LOCKSS_box_data_current for boxId" + boxId + " Repository Id: " + currentBoxResult.getRepositorySpaceId());

						} catch (SQLException e) {

							System.out.println(e.getMessage());

						} finally {

							if (preparedStatement != null) {
								preparedStatement.close();
							}

							if (dbConnection != null) {
								dbConnection.close();
							}

						}

					}

	
	// if peers box data is collected from the LOCKSS Box, insert the results in the table Peers

					if (peersBox != null) {

						for (PeerWsResult currentPeer : peersBox) {
						//DateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
						//format.setTimeZone(TimeZone.getTimeZone("Etc/UTC"));
						//							System.out.println("Peer Id: "+currentPeer.getPeerId() + "<br>");
						//							System.out.println("Last Poll: "+format.format(currentPeer.getLastPoll()) + "<br>");
						//							System.out.println("Polls Called: "+currentPeer.getPollsCalled() + "<br>");
						//							System.out.println("Nak Reason: "+currentPeer.getNakReason() + "<br>");
						//						    System.out.println("Last invitation: "+ format.format(currentPeer.getLastInvitation())+ "<br>");
						//							System.out.println("Message type: "+currentPeer.getMessageType() + "<br>");
						//							System.out.println("Last vote: "+currentPeer.getLastVote() + "<br> <br>");

						try {							
							String insertTableSQL = "WITH upsert AS (UPDATE plnmonitor.peer " +
									"SET box = ?, " +
									"last_poll = ?, " +
									"polls_called = ?, " +
									"last_invitation = ?, " +
									"last_vote = ?, " +
									"peer_lockss_id = ?, " +
									"last_message = ?, " +
									"invitation_count = ?, " +
									"message_count = ?, " +
									"message_type = ?, " +
									"polls_rejected = ?, " +
									"votes_cast = ? " + 
									"WHERE box=? and peer_lockss_id=? RETURNING *)" +
									"INSERT INTO plnmonitor.peer " +
									"(box,last_poll,polls_called,last_invitation,last_vote,peer_lockss_id,last_message, invitation_count, message_count, message_type, polls_rejected, votes_cast) "+
									"SELECT " +
									"?,?,?,?,? ,?,?,?,?,? ,?,? WHERE NOT EXISTS "+
									"(SELECT * FROM upsert)";

							dbConnection = getDBConnection();
							preparedStatement = dbConnection.prepareStatement(insertTableSQL);
							preparedStatement.setLong(1, boxId);
							preparedStatement.setLong(2, currentPeer.getLastPoll());
							preparedStatement.setLong(3, currentPeer.getPollsCalled());
							preparedStatement.setLong(4, currentPeer.getLastInvitation());
							preparedStatement.setLong(5, currentPeer.getLastVote());
							preparedStatement.setString(6, currentPeer.getPeerId());
							preparedStatement.setLong(7, currentPeer.getLastMessage());
							preparedStatement.setLong(8, currentPeer.getInvitationCount());
							preparedStatement.setLong(9, currentPeer.getMessageCount());
							preparedStatement.setString(10, currentPeer.getMessageType());
							preparedStatement.setLong(11, currentPeer.getPollsRejected());
							preparedStatement.setLong(12, currentPeer.getVotesCast() );
							
							preparedStatement.setLong(13, boxId );
							preparedStatement.setString(14, currentPeer.getPeerId() );
							
							preparedStatement.setLong(15, boxId);
							preparedStatement.setLong(16, currentPeer.getLastPoll());
							preparedStatement.setLong(17, currentPeer.getPollsCalled());
							preparedStatement.setLong(18, currentPeer.getLastInvitation());
							preparedStatement.setLong(19, currentPeer.getLastVote());
							preparedStatement.setString(20, currentPeer.getPeerId());
							preparedStatement.setLong(21, currentPeer.getLastMessage());
							preparedStatement.setLong(22, currentPeer.getInvitationCount());
							preparedStatement.setLong(23, currentPeer.getMessageCount());
							preparedStatement.setString(24, currentPeer.getMessageType());
							preparedStatement.setLong(25, currentPeer.getPollsRejected());
							preparedStatement.setLong(26, currentPeer.getVotesCast() );
							//preparedStatement.setInt(28, currentPeer.getPeerId().hashCode() );

							System.out.println(preparedStatement.toString());
							preparedStatement.executeUpdate();

							System.out.println("Record is inserted into Peers table!");

						} catch (SQLException e) {

							System.out.println(e.getMessage());

						} finally {

							if (preparedStatement != null) {
								preparedStatement.close();
							}

							if (dbConnection != null) {
								dbConnection.close();
							}

						}
					}

						// if AUs box data is collected from the LOCKSS Box, insert the results in the table AU_current
						
						if (ausFromCurrentBox != null) {
							for (AuWsResult currentAU :  ausFromCurrentBox) {
								try {							
									String insertTableSQL = "WITH upsert AS (UPDATE plnmonitor.au_current " +
											"SET box = ?, " +
											"name = ?, " +
											"plugin_name = ?, " +
											"tdb_year = ?, " +
											"access_type = ?, " +
											"content_size = ?, " +
											"recent_poll_agreement = ?, " +
											"creation_time = ?, " +
											"au_lockss_id = ?, " +
											"tdb_publisher = ?, " +
											"volume = ?, " + 
											"disk_usage = ?, " + 
											"last_completed_crawl = ?, " + 
											"last_completed_poll = ?, " + 
											"last_crawl = ?, " + 
											"last_poll = ?, " + 
											"crawl_pool = ?, " + 
											"crawl_proxy = ? ," + 
											"crawl_window = ?, " + 
											"last_crawl_result = ?, " + 
											"last_poll_result = ?, " + 
											"publishing_platform = ?, " + 
											"repository_path = ?, " + 
											"subscription_status = ?, " +
											"substance_state = ?, " + 
											"available_from_publisher = ? " + 
											"WHERE box=? and au_lockss_id=? RETURNING *)" +
											"INSERT INTO plnmonitor.au_current " +
											"(box,name,plugin_name,tdb_year,access_type,content_size,recent_poll_agreement,creation_time,au_lockss_id,tdb_publisher,volume,disk_usage,last_completed_crawl,last_completed_poll,last_crawl,last_poll,crawl_pool,crawl_proxy,crawl_window,last_crawl_result,last_poll_result,publishing_platform,repository_path,subscription_status,substance_state,available_from_publisher)" + 
											"SELECT " +
											"?,?,?,?,? ,?,?,?,?,? ,?,?,?,?,?, ?,?,?,?,?, ?,?,?,?,?, ? WHERE NOT EXISTS "+
											"(SELECT * FROM upsert)";

									dbConnection = getDBConnection();
									preparedStatement = dbConnection.prepareStatement(insertTableSQL);
									preparedStatement.setLong(1, boxId);
									preparedStatement.setString(2, currentAU.getName());
									preparedStatement.setString(3, currentAU.getPluginName());
									preparedStatement.setString(4, currentAU.getTdbYear());
									preparedStatement.setString(5, currentAU.getAccessType());
									preparedStatement.setLong(6, currentAU.getContentSize());
									preparedStatement.setDouble(7, (currentAU.getRecentPollAgreement()!=null)?currentAU.getRecentPollAgreement():0);
									preparedStatement.setLong(8, currentAU.getCreationTime());
									preparedStatement.setString(9, currentAU.getAuId());
									preparedStatement.setString(10, currentAU.getTdbPublisher());
									preparedStatement.setString(11, currentAU.getVolume());
									preparedStatement.setLong(12, (currentAU.getDiskUsage()!=null)?currentAU.getDiskUsage():0);
									preparedStatement.setLong(13, (currentAU.getLastCompletedCrawl()!=null)?currentAU.getLastCompletedCrawl():0);
									preparedStatement.setLong(14, (currentAU.getLastCompletedPoll()!=null)? currentAU.getLastCompletedPoll():0);
									preparedStatement.setLong(15, (currentAU.getLastCrawl()!=null)? currentAU.getLastCrawl():0);
									preparedStatement.setLong(16, (currentAU.getLastPoll()!= null)?currentAU.getLastPoll():0);
									preparedStatement.setString(17, currentAU.getCrawlPool());
									preparedStatement.setString(18, (currentAU.getCrawlProxy()!=null)?currentAU.getCrawlProxy():"");
									preparedStatement.setString(19, (currentAU.getCrawlWindow()!=null)?currentAU.getCrawlWindow():"");
									preparedStatement.setString(20, currentAU.getLastCrawlResult());
									preparedStatement.setString(21, currentAU.getLastPollResult());
									preparedStatement.setString(22, currentAU.getPublishingPlatform());
									preparedStatement.setString(23, (currentAU.getRepositoryPath()!=null)?currentAU.getRepositoryPath():"");
									preparedStatement.setString(24, (currentAU.getSubscriptionStatus()!=null)?currentAU.getSubscriptionStatus(): "");
									preparedStatement.setString(25, currentAU.getSubstanceState());
									preparedStatement.setBoolean(26, currentAU.getAvailableFromPublisher());
									
									preparedStatement.setLong(27, boxId);
									preparedStatement.setString(28, currentAU.getAuId());
									
									preparedStatement.setLong(29, boxId);
									preparedStatement.setString(30, currentAU.getName());
									preparedStatement.setString(31, currentAU.getPluginName());
									preparedStatement.setString(32, currentAU.getTdbYear());
									preparedStatement.setString(33, currentAU.getAccessType());
									preparedStatement.setLong(34, currentAU.getContentSize());
									preparedStatement.setDouble(35, (currentAU.getRecentPollAgreement()!=null)?currentAU.getRecentPollAgreement():0);
									preparedStatement.setLong(36, currentAU.getCreationTime());
									preparedStatement.setString(37, currentAU.getAuId());
									preparedStatement.setString(38, currentAU.getTdbPublisher());
									preparedStatement.setString(39, currentAU.getVolume());
									preparedStatement.setLong(40, currentAU.getDiskUsage());
									preparedStatement.setLong(41, currentAU.getLastCompletedCrawl());
									preparedStatement.setLong(42, (currentAU.getLastCompletedPoll()!=null)? currentAU.getLastCompletedPoll():0);
									preparedStatement.setLong(43, currentAU.getLastCrawl());
									preparedStatement.setLong(44, (currentAU.getLastPoll()!= null)?currentAU.getLastPoll():0);
									preparedStatement.setString(45, currentAU.getCrawlPool());
									preparedStatement.setString(46, (currentAU.getCrawlProxy()!=null)?currentAU.getCrawlProxy():"");
									preparedStatement.setString(47, (currentAU.getCrawlWindow()!=null)?currentAU.getCrawlWindow():"");
									preparedStatement.setString(48, currentAU.getLastCrawlResult());
									preparedStatement.setString(49, currentAU.getLastPollResult());
									preparedStatement.setString(50, currentAU.getPublishingPlatform());
									preparedStatement.setString(51, (currentAU.getRepositoryPath()!=null)?currentAU.getRepositoryPath():"");
									preparedStatement.setString(52, (currentAU.getSubscriptionStatus()!=null)?currentAU.getSubscriptionStatus(): "");
									preparedStatement.setString(53, currentAU.getSubstanceState());
									preparedStatement.setBoolean(54, currentAU.getAvailableFromPublisher());
									
									preparedStatement.executeUpdate();

									System.out.println("Record "+ currentAU.getName() + "is inserted into AU_current table!");

								} catch (SQLException e) {

									System.out.println(e.getMessage());

								} finally {

									if (preparedStatement != null) {
										preparedStatement.close();
									}

									if (dbConnection != null) {
										dbConnection.close();
									}

								}
							}
						}
						
					// Archival Units 

					// print column names
					//						for (int i=0; i< Categories.length; i++) {
					//							System.out.println("<th class=\"tg-031e\">"+ Categories[i]+"</th>");
					//						}
					//						System.out.println("</tr>");

					// print values for each AU
					/*						for (AuWsResult auResult : auResults.getValue()) {
							System.out.println("<td class=\"tg-031e\">"+auResult.getName()+"</td>");
							//				System.out.println("<td class=\"tg-031e\">"+auResult.getVolume()+"</td>");
							//System.out.println("<td class=\"tg-031e\">"+auResult.getPluginName()+"</td>");
							System.out.println("<td class=\"tg-031e\">"+auResult.getTdbYear()+"</td>");
							System.out.println("<td class=\"tg-031e\">"+auResult.getAccessType()+"</td>");
							System.out.println("<td class=\"tg-031e\">"+FileUtils.byteCountToDisplaySize(auResult.getContentSize())+"</td>");
							//System.out.println("<td class=\"tg-031e\">"+auResult.getDiskUsage()+"</td>");
							String pollAgreementStr;
							if (auResult.getRecentPollAgreement() ==null) {
								pollAgreementStr="100 %";
							}
							else {
								pollAgreementStr= String.format("%.2f%%", auResult.getRecentPollAgreement()*100);
							}
							//Double pollAgreement=new Double(auResult.getRecentPollAgreement());
							//String pollAgreementStr= String.format("%.2f", pollAgreement*100);
							//System.out.println(pollAgreementStr);
							System.out.println("<td class=\""+ (pollAgreementStr.equals("100 %")? "greened" :"redded") +"\">"+ pollAgreementStr +"</td>");
							//System.out.println("<td class=\"tg-031e\">"+auResult.getPublishingPlatform()+"</td>");
							System.out.println("<td class=\"tg-031e\">"+auResult.getAvailableFromPublisher().toString()+"</td>");
							//System.out.println("<td class=\"tg-031e\">"+auResult.getSubstanceState()+"</td>");
							Date date = new Date(auResult.getCreationTime());
							DateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
							format.setTimeZone(TimeZone.getTimeZone("Etc/UTC"));
							System.out.println("<td class=\"tg-031e\">"+format.format(date)+"</td>");
							//System.out.println("<td class=\"tg-031e\">"+auResult.getCrawlProxy()+"</td>");
							//System.out.println("<td class=\"tg-031e\">"+auResult.getCrawlWindow()+"</td>");
							//System.out.println("<td class=\"tg-031e\">"+auResult.getCrawlPool()+"</td>");
							date = new Date(auResult.getLastCompletedCrawl());
							format.setTimeZone(TimeZone.getTimeZone("Etc/UTC"));
							System.out.println("<td class=\"tg-031e\">"+format.format(date)+"</td>");
							System.out.println("<td class=\"tg-031e\">"+auResult.getLastPollResult()+"</td>");
							//System.out.println("<td class=\"tg-031e\">"+auResult.getAuConfiguration()+"</td>");
							//System.out.println("<td class=\"tg-031e\">"+auResult.getPeerAgreements().toString()+"</td>");
						}*/

				}

			}


		}
		catch (Exception e)
		{
			e.printStackTrace() ;
		}

	}



	private static Connection getDBConnection() {

		Connection dbConnection = null;

		try {

			Class.forName(DB_DRIVER);

		} catch (ClassNotFoundException e) {

			System.out.println(e.getMessage());

		}

		try {

			dbConnection = DriverManager.getConnection(
					DB_CONNECTION, DB_USER,DB_PASSWORD);
			return dbConnection;

		} catch (SQLException e) {

			System.out.println(e.getMessage());

		}

		return dbConnection;

	}

	private static java.sql.Timestamp getCurrentTimeStamp() {

		java.util.Date today = new java.util.Date();
		return new java.sql.Timestamp(today.getTime());

	}

	/**
	 * Sets the authenticator that will be used by the networking code when the
	 * HTTP server asks for authentication.
	 */
	private void authenticate() {
		Authenticator.setDefault(new Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(USER_NAME, PASSWORD.toCharArray());
			}
		});
	}

	/**
	 * Sets the authenticator that will be used by the networking code when the
	 * HTTP server asks for authentication.
	 */
	private void formAuthenticate(String server ) throws Exception{
		  CredentialsProvider credsProvider = new BasicCredentialsProvider();
	        credsProvider.setCredentials(
	                new AuthScope(server, 8081),
	                new UsernamePasswordCredentials("debug", "debuglockss"));
	        CloseableHttpClient httpclient = HttpClients.custom()
	                .setDefaultCredentialsProvider(credsProvider)
	                .build();
	        try {
	            HttpGet httpget = new HttpGet("https://"+ server +":8081/Home");

	            System.out.println("Executing request " + httpget.getRequestLine());
	            CloseableHttpResponse response = httpclient.execute(httpget);
	            try {
	                System.out.println("----------------------------------------");
	                System.out.println(response.getStatusLine());
	                System.out.println(EntityUtils.toString(response.getEntity()));
	            } finally {
	                response.close();
	            }
	        } finally {
	            httpclient.close();
	        }
	}
	
}
