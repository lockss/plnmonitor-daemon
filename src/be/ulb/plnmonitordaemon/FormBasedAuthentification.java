package be.ulb.plnmonitordaemon;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;


/**
 * The Class FormBasedAuthentification.
 * 
 * Unsuccessful attempt to use form-based authentification for boxes with SSL enabled
 * form-based authentication
 */
public class FormBasedAuthentification {

		    /**
    		 * The main method.
    		 *
    		 * @param agrs the arguments
    		 * @throws Exception the exception
    		 */
    		public static void main(String[] agrs) throws Exception{
		        String host = "164.15.1.87";
		        int port = 8081;
		        String protocol = "https";
		 
		        //Protocol myhttps = new Protocol("https", new MySSLSocketFactory(), 443);
		    
		        CredentialsProvider credsProvider = new BasicCredentialsProvider();
		        credsProvider.setCredentials(
		                new AuthScope("164.15.1.87", 8081),
		                new UsernamePasswordCredentials("debug", "debuglockss"));
		        CloseableHttpClient httpclient = HttpClients.custom()
		                .setDefaultCredentialsProvider(credsProvider)
		                .build();
		        try {
		            HttpGet httpget = new HttpGet("https://164.15.1.87:8081/Home");

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
