package be.ulb.plnmonitordaemon;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Class plnmonitordaemon.
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

	/**
	 * The main method.
	 *
	 * @param args the arguments
	 */
	public static void main(String[] args) {
		new WorkerThread().start();

//		try {
//			Thread.sleep(86400000);
//		} 
//		catch (InterruptedException e) {
//		}
//		System.out.println("Main Thread ending");
	}

}

class WorkerThread extends Thread {

	public WorkerThread() {
		setDaemon(false); 
		// When false, (i.e. when it's a user thread),
		// the Worker thread continues to run.
		// When true, (i.e. when it's a daemon thread),
		// the Worker thread terminates when the main
		// thread terminates.
	}

	public void run() {
		DaemonStatusWebService dsws = new DaemonStatusWebService();
		//while (true) {
			System.out.println("Updating PLN status " );
			try {
				HashMap<Integer, String> configFiles = dsws.getPLNConfigurationFiles();
				for (Map.Entry<Integer, String> entry : configFiles.entrySet()) {
					Integer plnID = entry.getKey();
					String configFile = entry.getValue();

					System.out.println("Loading lockss.xml configuration file from: " + configFile);
					List<String> boxIpAddresses = dsws.loadPLNConfiguration(plnID, configFile);
					for (String boxIpAddress : boxIpAddresses) {
						System.out.println("Loading configuration of: " + boxIpAddress);
						dsws.loadDaemonStatus(plnID, boxIpAddress);
						
					}
				}
		//sleep(86400000);
			} catch (Exception e) {
				e.printStackTrace();
			}
		//}
	}
}