package ul.sssr.loadbalancer;

import java.net.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.io.*;

import org.apache.commons.codec.binary.Base64;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.PurgeQueueRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;

public class Distributor {

	static int portNumber = 8080;
	static AWSCredentials credentials;
	static int k;
	static String myQRequestUrl;
	static AmazonSQS sqs;
	static Queue<AmazonEC2> fifo = new LinkedList<AmazonEC2>();

	public static void main(String[] args) throws IOException {

		/*
		 * The ProfileCredentialsProvider will return your [default] credential
		 * profile by reading from the credentials file located at
		 * (~/.aws/credentials).
		 */
		try {
			credentials = new ProfileCredentialsProvider().getCredentials();
		} catch (Exception e) {
			throw new AmazonClientException(
					"Cannot load the credentials from the credential profiles file. "
							+ "Please make sure that your credentials file is at the correct "
							+ "location (~/.aws/credentials), and is in valid format.",
					e);
		}

		sqs = new AmazonSQSClient(credentials);
		Region euCentral1 = Region.getRegion(Regions.EU_CENTRAL_1);
		sqs.setRegion(euCentral1);

		System.out.println("===========================================");
		System.out.println("Getting Started with Amazon SQS");
		System.out.println("===========================================\n");

		try {
			// Create a queue

			System.out
					.println("Creating a new SQS queue called arif-QRequest.\n");

			CreateQueueRequest createQueueRequest = new CreateQueueRequest(
					"arif-QRequest");
			myQRequestUrl = sqs.createQueue(createQueueRequest).getQueueUrl();

			// Purge a queue
			System.out
					.println("Purge SQS queue called arif-QRequest if it exists \n");

			PurgeQueueRequest purgeQueueRequest = new PurgeQueueRequest(
					myQRequestUrl);
			sqs.purgeQueue(purgeQueueRequest);

		} catch (AmazonServiceException ase) {
			System.out
					.println("Caught an AmazonServiceException, which means your request made it "
							+ "to Amazon SQS, but was rejected with an error response for some reason.");
			System.out.println("Error Message:    " + ase.getMessage());
			System.out.println("HTTP Status Code: " + ase.getStatusCode());
			System.out.println("AWS Error Code:   " + ase.getErrorCode());
			System.out.println("Error Type:       " + ase.getErrorType());
			System.out.println("Request ID:       " + ase.getRequestId());
		} catch (AmazonClientException ace) {
			System.out
					.println("Caught an AmazonClientException, which means the client encountered "
							+ "a serious internal problem while trying to communicate with SQS, such as not "
							+ "being able to access the network.");
			System.out.println("Error Message: " + ace.getMessage());
		}

		// Initialisation du compteur de requête

		System.out.println("Initializing the counter query\n");
		k = 0;

		ServerSocket socket;
		boolean listening = true;

		try {
			socket = new ServerSocket(portNumber);
			
			int max = 1;
			while (listening) {
				new ServerThread(socket.accept()).start();
				GetQueueAttributesRequest request = new GetQueueAttributesRequest(myQRequestUrl).withAttributeNames("ApproximateNumberOfMessages");
				Map<String, String> attrs = sqs.getQueueAttributes(request).getAttributes();
				
				// get the approximate number of messages in the queue
	            int messages = Integer.parseInt(attrs.get("ApproximateNumberOfMessages"));
	            // compare with max, the user's choice for maximum number of messages
	            if (messages > max) {
	                // if number of messages exceeds maximum, 
	            	fifo.add(createWorker());
	                System.out.println("Creation new worker succeed !");
	            }
			}
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static class ServerThread extends Thread {
		private Socket socket = null;

		public ServerThread(Socket socket) {
			super("ServerThread");
			this.socket = socket;
		}

		public void run() {
			InputStream sis;
			String val = "";
			try {
				sis = socket.getInputStream();
				BufferedReader br = new BufferedReader(new InputStreamReader(
						sis));
				String request = br.readLine();
				System.out.println("La requête est : "+request);
				if (request != null) {
					String[] requestParam = request.split(" ");
					String[] requestParam1 = requestParam[1].split("/");
					if (isInt(requestParam1[1])) {
						val = requestParam1[1];
						if(Integer.parseInt(val) > 40){
							fifo.add(createWorker());
							  System.out.println("Creation new worker succeed > 40 !");
						}
						int numRequete = k;

						// incrémente nombre rêquete
						k++;

						// create queue response
						CreateQueueRequest createQueueRequest = new CreateQueueRequest(
								"arif-QResponse-" + numRequete);
						String myQResponseUrl = sqs.createQueue(
								createQueueRequest).getQueueUrl();
						
						// Send a message
						sqs.sendMessage(new SendMessageRequest(myQRequestUrl,
								val + " " + numRequete));

						
						boolean fini = true;
						while (fini) {
							List<Message> msgs = sqs.receiveMessage(
									new ReceiveMessageRequest(myQResponseUrl)
											.withMaxNumberOfMessages(1))
									.getMessages();

							if (msgs.size() > 0) {
								Message message = msgs.get(0);
								String data = message.getBody();
								System.out
										.println("Le résultat de fibonnaci de"+val+" est "
												+ data);
								PrintWriter out = new PrintWriter(
										socket.getOutputStream(), true);
								out.println("Le résultat de fibonnaci de "+val+" est "
										+ data);
								sqs.deleteQueue(myQResponseUrl);
								fini = false;
							}
						}
					}
				}
				socket.close();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

		}
	}

	public static boolean isInt(String chaine) {
		boolean valeur = true;
		char[] tab = chaine.toCharArray();

		for (char carac : tab) {
			if (!Character.isDigit(carac) && valeur) {
				valeur = false;
			}
		}
		return valeur;
	}
	
	public static AmazonEC2 createWorker(){
		AmazonEC2 ec2 = new AmazonEC2AsyncClient(credentials);
		ec2.setEndpoint("ec2.eu-central-1.amazonaws.com");

		// CREATE EC2 INSTANCES
		RunInstancesRequest runInstancesRequest = new RunInstancesRequest()
		    .withInstanceType("t2.micro")
		    .withImageId("ami-e5574889")
		    .withMinCount(1)
		    .withMaxCount(1)
		    .withSecurityGroupIds("bachir_SG_frankfurt")
		    .withKeyName("bachir-key-pair")
		    .withUserData(getUserDataScript());
		CreateTagsRequest createTagsRequest = new CreateTagsRequest();
		createTagsRequest.withResources((ec2.runInstances(runInstancesRequest).getReservation().getInstances().get(0).getInstanceId()))
			.withTags(new Tag("Name", "Worker"));
		ec2.createTags(createTagsRequest);
		return ec2;		
	}
	
	public static void stopWorker(){
		
	}
	
	private static String getUserDataScript(){
        ArrayList<String> lines = new ArrayList<String>();
        lines.add("#! /bin/bash");
        lines.add("cd /home/ec2-user");
        lines.add("./java-server_launch");
        String str = new String(Base64.encodeBase64(join(lines, "\n").getBytes()));
        return str;
    }
	 
	private static String join(Collection<String> s, String delimiter) {
	        StringBuilder builder = new StringBuilder();
	        Iterator<String> iter = s.iterator();
	        while (iter.hasNext()) {
	            builder.append(iter.next());
	            if (!iter.hasNext()) {
	                break;
	            }
	            builder.append(delimiter);
	        }
	        return builder.toString();
	    }
	
}
