/** 
 * Handles the actual writing of data to the client. If keepAlive
 * flag is set then the responsibility of closing the write is
 * on the caller.
 */

package qa.qcri.aidr.output.utils;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qa.qcri.aidr.output.getdata.ChannelBufferManager;

public class WriteResponse {
	public final static String DEFAULT_MIME_TYPE = "application/json";
	public HttpServletResponse response;
	public boolean keepAlive;
	public PrintWriter responseWriter = null;
	
	private static Logger logger = LoggerFactory.getLogger(WriteResponse.class);

	public WriteResponse(HttpServletResponse response, boolean keepAlive) {
		//BasicConfigurator.configure();		// configuration for log4j logging
		System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "INFO");		// set logging level for slf4j
		this.response = response;
		this.keepAlive = keepAlive;	
	}
	
	public boolean initWriter(String mimeType) {
		if (mimeType.equals("application/json")) {
			response.setContentType("application/json");
		}
		if (mimeType.equals("text/html")) {
			response.setContentType("text/html");
		}
		response.setCharacterEncoding("UTF-8");
		try {
			responseWriter = response.getWriter();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			logger.error(this + "@[run] Error initializing PrintWriter", e);
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	public boolean checkError() {
		return this.responseWriter.checkError();	
	}
	
	public void close() {
		this.responseWriter.flush();
		this.responseWriter.close();
	}
	
	public void writeJsonData(StringBuilder jsonDataList, int count) {
		if (jsonDataList.length() > 0) { 
			responseWriter.println(jsonDataList);		// change made at home
			responseWriter.flush();
		}
		// check if the write succeeded
		if (responseWriter.checkError()) {
			logger.error("[writeJsonData] Client side error - possible client disconnect...");
		}	
		if (!keepAlive)
			responseWriter.close();
	}

	public void writeHtmlData(StringBuilder htmlDataList, int count) {
		// Allocate a output writer to write the response message into the network socket
		if (htmlDataList.length() > 0) { 
			responseWriter.println(htmlDataList);		// change made at home
			responseWriter.flush();
		}
		// check if the write succeeded
		if (responseWriter.checkError()) {
			logger.error("[writeHtmlData] Client side error - possible client disconnect...");
		}	
		if (!keepAlive)
			responseWriter.close();
	}


	protected void writeErrorMessage(Set<String> channelList, String CHANNEL_PREFIX_STRING) {
		try {
			// Allocate a output writer to write the response message into the network socket
			responseWriter.println("<!DOCTYPE html>");
			responseWriter.println("<html>");
			responseWriter.println("<head><title>REDIS PUBSUB Channel Data Output Service</title></head>");
			responseWriter.println("<body>");
			responseWriter.println("<h1>Invalid/No CrisisCode Provided! </h1>");
			responseWriter.println("<h2>Can not initiate REDIS channel subscription!</h2>");
			responseWriter.println("<p><big>Available active channels: </big></p>");
			responseWriter.println("<ul>"); 
			if (channelList != null) {
				Iterator<String> itr = channelList.iterator();
				while (itr.hasNext()) {
					responseWriter.println("<li>" + itr.next().substring(CHANNEL_PREFIX_STRING.length()) + "</li>");
				}
			}
			responseWriter.println("</body></html>");
			if (responseWriter.checkError()) {
				logger.error("[writeErrorMessage] Client side error - possible client disconnect...");
			}	
		} finally {
			responseWriter.flush();
			responseWriter.close();  // Always close the output writer
		}
	}
}
