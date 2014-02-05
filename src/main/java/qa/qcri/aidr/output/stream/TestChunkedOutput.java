package qa.qcri.aidr.output.stream;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.glassfish.jersey.server.ChunkedOutput;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Path("/test")
public class TestChunkedOutput {
	
	// Related to Async Thread management
	public static ExecutorService executorServicePool;

	// Debugging
	private static Logger logger = LoggerFactory.getLogger(TestChunkedOutput.class);

	/////////////////////////////////////////////////////////////////////////////

	@GET
	@Produces(MediaType.TEXT_PLAIN)
	public ChunkedOutput<String> getChunkedResponse() {
        final ChunkedOutput<String> output = new ChunkedOutput<String>(String.class);
 
        new Thread() {
            public void run() {
                try {
                    String chunk;
 
                    while ((chunk = getNextString()).equalsIgnoreCase("done")) {
                        output.write(chunk);
                    }
                } catch (IOException e) {
                    // IOException thrown when writing the
                    // chunks of response: should be handled
                } finally {
                    try {
						output.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
                        // simplified: IOException thrown from
                        // this close() should be handled here...
                }
            }
        }.start();
 
        // the output will be probably returned even before
        // a first chunk is written by the new thread
        return output;
    }
 
    private String getNextString() {
        // ... long running operation that returns
        //     next string or null if no other string is accessible
    	Scanner in = new Scanner(System.in);
    	if (in.hasNext()) {
    		final String msg = in.next();
    		return msg;
    	}
    	return null;
    }	
}
