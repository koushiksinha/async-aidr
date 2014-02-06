package qa.qcri.aidr.output.stream;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;

<<<<<<< HEAD
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
=======
>>>>>>> 1b539dfda1e61bf0767ddc8ad6b093abc94cb041
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.glassfish.jersey.server.ChunkedOutput;
<<<<<<< HEAD
=======

>>>>>>> 1b539dfda1e61bf0767ddc8ad6b093abc94cb041
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


<<<<<<< HEAD
@Path("/test/chunk")
public class TestChunkedOutput {
	
	// Related to Async Thread management
	//public static ExecutorService executorServicePool;

	// Debugging
	private static Logger logger = LoggerFactory.getLogger(TestChunkedOutput.class);
	private static int sendCount = 0;
	//private ContainerResponse responseContext;
	
	/////////////////////////////////////////////////////////////////////////////

	@GET
	@Path("/ping")
	@Produces(MediaType.APPLICATION_JSON)
	public ChunkedOutput<String> getChunkedResponse() throws Throwable {
		final ChunkedOutput<String> output = new ChunkedOutput<String>(String.class);
        new Thread() {
            public void run() {
                logger.info("Received GET request...");
            	try {
                    String chunk = getNextString();
                    while (chunk != null) {
                        if (!output.isClosed()) {
                        	if (!output.isClosed()) output.write(chunk);
                        	if (!output.isClosed()) output.write("\n");
                        }
                        else {
                        	logger.info("Possible client disconnect...");
                        	break;
                        }
                        try {
							Thread.sleep(3000);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							//e.printStackTrace();
						}
                        chunk = getNextString();
                        logger.info("received from generator: " + chunk);
                    }
                } catch (Exception e) {
                    // IOException thrown when writing the
                    // chunks of response: should be handled
                	logger.info("Error in write attempt");
                } 
            	logger.info("Done with async thread - joining with master now");
            	for (int i = 0;i < 10;i++) {
            		System.out.println("value = " + i);
            	}
            }
        }.start();
        logger.info("End of async main thread");
        return output;
    }
 
    protected String getNextString() {
        // ... long running operation that returns
        //     next string or null if no other string is accessible
    	/*
=======
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
>>>>>>> 1b539dfda1e61bf0767ddc8ad6b093abc94cb041
    	Scanner in = new Scanner(System.in);
    	if (in.hasNext()) {
    		final String msg = in.next();
    		return msg;
<<<<<<< HEAD
    	}*/
    	

    	if (TestChunkedOutput.sendCount < 10) {
    		StringBuilder str = new StringBuilder();
    		str.append("{").append("\"count\":").append(Integer.toString(TestChunkedOutput.sendCount)).append("}");
    		logger.info("returning to caller = " + str);
    		++TestChunkedOutput.sendCount;
    		return str.toString();
    	}
    	sendCount = 0;
    	return null;
    }
    
    /*
	@Override
	public void contextDestroyed(ServletContextEvent arg0) {
		// TODO Auto-generated method stub
		logger.info("Context destroyed");
		
	}

	@Override
	public void contextInitialized(ServletContextEvent arg0) {
		// TODO Auto-generated method stub
		System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "INFO");		// set logging level for slf4j
		logger.info("Context initialized");
		
	}
	*/	
=======
    	}
    	return null;
    }	
>>>>>>> 1b539dfda1e61bf0767ddc8ad6b093abc94cb041
}
