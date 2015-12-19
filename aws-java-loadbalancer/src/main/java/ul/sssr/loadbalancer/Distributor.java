package ul.sssr.loadbalancer;

import java.net.*;
import java.util.List;
import java.io.*;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.PurgeQueueRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;

public class Distributor {

	static int portNumber = 8080;
	static int k;
	static String myQRequestUrl;
	static AmazonSQS sqs;

	public static void main(String[] args) throws IOException {

		/*
		 * The ProfileCredentialsProvider will return your [default] credential
		 * profile by reading from the credentials file located at
		 * (~/.aws/credentials).
		 */

		AWSCredentials credentials = null;
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
			while (listening) {
				new ServerThread(socket.accept()).start();
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
}
