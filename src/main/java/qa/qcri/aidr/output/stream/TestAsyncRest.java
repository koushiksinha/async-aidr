package qa.qcri.aidr.output.stream;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.CompletionCallback;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.container.ConnectionCallback;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/test/print")
public class TestAsyncRest {
	private static int numberOfSuccessResponses = 0;
	private static int numberOfFailures = 0;
	private static Throwable lastException = null;

	// Debugging
	private static Logger logger = LoggerFactory.getLogger(TestAsyncRest.class);
	private static int sendCount = 0;

	@GET
	@Path("/ping")
	@Produces(MediaType.APPLICATION_JSON)
	public void asyncGetWithTimeout(@Suspended final AsyncResponse asyncResponse, 
			@Context final HttpServletResponse response) {

		asyncResponse.register(new ConnectionCallback() {
			@Override
			public void onDisconnect(AsyncResponse disconnected) {
				logger.info("client disconnect...");
			}
		});
		asyncResponse.register(new CompletionCallback() {
			@Override
			public void onComplete(Throwable throwable) {
				if (throwable == null) {
					// no throwable - the processing ended successfully
					// (response already written to the client)
					numberOfSuccessResponses++;
				} else {
					numberOfFailures++;
					lastException = throwable;
				}
			}
		});

		new Thread(new Runnable() {
			@Override
			public void run() {
				logger.info("Received GET request...");
				
				response.setBufferSize(5);
				PrintWriter output = null;
				try {
					//HttpServletResponse response = (HttpServletResponse) asyncContext.getResponse();
					output = response.getWriter();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					logger.error(this + "@[run] Error initializing PrintWriter", e);	
				}

				String chunk = getNextString();
				while (chunk != null) {
					if (!output.checkError()) {
						output.write(chunk);
						output.write("\n");
						output.flush();
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
				if (chunk != null) { 
					output.close();
					asyncResponse.cancel();//asyncResponse.resume(chunk);	
				}
				else {
					output.close();
					asyncResponse.cancel();
				}
			}
		}).start();
	}

	protected String getNextString() {
		if (sendCount < 10) {
			StringBuilder str = new StringBuilder();
			str.append("{").append("\"count\":").append(Integer.toString(sendCount)).append("}");
			logger.info("returning to caller = " + str);
			++sendCount;
			return str.toString();
		}
		return null;
	}
}
