package ul.sssr.loadbalancer;

import java.net.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.io.*;
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
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
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
	static Queue<String> fifo = new LinkedList<String>();

	public static void main(String[] args) throws IOException {

		/*
		 * The ProfileCredentialsProvider will return your [default] credential
		 * profile by reading from the credentials file located at
		 * (/root/.aws/credentials).
		 */
		try {
			credentials = new ProfileCredentialsProvider().getCredentials();
		} catch (Exception e) {
			throw new AmazonClientException(
					"Cannot load the credentials from the credential profiles file. "
							+ "Please make sure that your credentials file is at the correct "
							+ "location (/root/.aws/credentials), and is in valid format.",
					e);
		}

		sqs = new AmazonSQSClient(credentials);
		Region euCentral1 = Region.getRegion(Regions.EU_CENTRAL_1);
		sqs.setRegion(euCentral1);

		try {
			CreateQueueRequest createQueueRequest = new CreateQueueRequest("arif-QRequest");
			myQRequestUrl = sqs.createQueue(createQueueRequest).getQueueUrl();
			PurgeQueueRequest purgeQueueRequest = new PurgeQueueRequest(myQRequestUrl);
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
		k = 0;
		ServerSocket socket;
		boolean listening = true;
		try {
			socket = new ServerSocket(portNumber);
			//Ajout d'un worker tout les 25 messages 
			int max = 50;
			while (listening) {
				new ServerThread(socket.accept()).start();
				GetQueueAttributesRequest request = new GetQueueAttributesRequest(myQRequestUrl)
						.withAttributeNames("ApproximateNumberOfMessages");
				Map<String, String> attrs = sqs.getQueueAttributes(request).getAttributes();
				// get the approximate number of messages in the queue
				int messages = Integer.parseInt(attrs.get("ApproximateNumberOfMessages"));
				if (messages > max) 
					fifo.add(createWorker());
				stopWorker(messages);
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
				BufferedReader br = new BufferedReader(new InputStreamReader(sis));
				String request = br.readLine();
				if (request != null) {
					String[] requestParam = request.split(" ");
					String[] requestParam1 = requestParam[1].split("/");
					if (isInt(requestParam1[1])) {
						val = requestParam1[1];
						int numRequete = k;
						k++;
						CreateQueueRequest createQueueRequest = new CreateQueueRequest("arif-QResponse-" + numRequete);
						String myQResponseUrl = sqs.createQueue(createQueueRequest).getQueueUrl();
						sqs.sendMessage(new SendMessageRequest(myQRequestUrl,val + " " + numRequete));
						boolean fini = true;
						while (fini) {
							List<Message> msgs = sqs.receiveMessage(new ReceiveMessageRequest(myQResponseUrl).withMaxNumberOfMessages(1))
									.getMessages();
							if (msgs.size() > 0) {
								Message message = msgs.get(0);
								String data = message.getBody();
								PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
								out.println("Le résultat de fibonnaci de "+ val + " est " + data+".");
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
			if (!Character.isDigit(carac) && valeur) 
				valeur = false;
		}
		return valeur;
	}

	public static String createWorker() {
		AmazonEC2 ec2 = new AmazonEC2AsyncClient(credentials);
		ec2.setEndpoint("ec2.eu-central-1.amazonaws.com");

		// CREATE EC2 INSTANCES
		RunInstancesRequest runInstancesRequest = new RunInstancesRequest()
				.withInstanceType("t2.micro").withImageId("ami-e5574889")
				.withMinCount(1).withMaxCount(1)
				.withSecurityGroupIds("bachir_SG_frankfurt")
				.withKeyName("bachir-key-pair");
		CreateTagsRequest createTagsRequest = new CreateTagsRequest();
		String instanceId = ec2.runInstances(runInstancesRequest).getReservation()
				.getInstances().get(0).getInstanceId();
		createTagsRequest.withResources(
				(instanceId)).withTags(
				new Tag("Name", "Worker"));
		ec2.createTags(createTagsRequest);

		return instanceId;
	}

	public static void stopWorker(int messages){
		AmazonEC2 ec2 = new AmazonEC2AsyncClient(credentials);
		ec2.setEndpoint("ec2.eu-central-1.amazonaws.com");
		int nbWorkers = messages/25;
		if (nbWorkers < fifo.size() ){	    
		    List<String> ids = new ArrayList<String>();
		    ids.add(fifo.peek());
		    try {
		        // Terminate instances.
		        TerminateInstancesRequest terminateRequest = new TerminateInstancesRequest(ids);
		        ec2.terminateInstances(terminateRequest);
		        fifo.poll();
		    } catch (AmazonServiceException e) {
		        // Write out any exceptions that may have occurred.
		        System.out.println("Error terminating instances");
		        System.out.println("Caught Exception: " + e.getMessage());
		        System.out.println("Reponse Status Code: " + e.getStatusCode());
		        System.out.println("Error Code: " + e.getErrorCode());
		        System.out.println("Request ID: " + e.getRequestId());
		    }		    
		}
	}
}
