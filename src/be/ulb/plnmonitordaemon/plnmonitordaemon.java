package be.ulb.plnmonitordaemon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class plnmonitordaemon {

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
		int count = 0;
		DaemonStatusWebService dsws = new DaemonStatusWebService();
		//while (true) {
			System.out.println("Updating PLN status " + count++);
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
	//			sleep(86400000);
			} catch (Exception e) {
				e.printStackTrace();
			}
	//	}
	}
}