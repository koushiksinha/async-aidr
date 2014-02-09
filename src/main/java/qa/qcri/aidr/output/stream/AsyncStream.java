package qa.qcri.aidr.output.stream;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import org.glassfish.jersey.server.ChunkedOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qa.qcri.aidr.output.utils.JedisConnectionObject;
import qa.qcri.aidr.output.utils.JsonDataFormatter;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.exceptions.JedisConnectionException;

@Path("/stream")
public class AsyncStream implements ServletContextListener {
	// Time-out constants
	private static final int REDIS_CALLBACK_TIMEOUT = 5 * 60 * 1000;		// in ms
	private static final int SUBSCRIPTION_MAX_DURATION = -1;			//default = no expiry

	// Pertaining to JEDIS - establishing connection with a REDIS DB
	// Currently using ssh tunneling:: ssh -f -L 1978:localhost:6379 scd1.qcri.org -N
	// Channel(s) being used for testing:
	// 		a) aidr_predict.clex_20131201
	//		

	//subscription related
	private static final String CHANNEL_PREFIX_CODE = "aidr_predict.";
	private boolean patternSubscriptionFlag;
	private final boolean rejectNullFlag = false;
	private String redisChannel = "*";							// channel to subscribe to		
	private static final String redisHost = "localhost";		// Current assumption: REDIS running on same m/c

	private static final int redisPort = 6379;					
	
	// Jedis related
	public static JedisConnectionObject jedisConn;
	public Jedis subscriberJedis = null;
	public RedisSubscriber aidrSubscriber = null;
	private boolean isConnected = false;
	private boolean isSubscribed =false;

	// Related to Async Thread management
	public static ExecutorService executorServicePool;
	private ChunkedOutput<String> responseWriter = null;

	// Debugging
	private static Logger logger = LoggerFactory.getLogger(AsyncStream.class);

	/////////////////////////////////////////////////////////////////////////////

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		// TODO Auto-generated method stub
		// For now: set up a simple configuration that logs on the console
		//PropertyConfigurator.configure("log4j.properties");		// where to place the properties file?
		//BasicConfigurator.configure();							// basic configuration for log4j logging
		System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "INFO");		// set logging level for slf4j
		jedisConn = new JedisConnectionObject(redisHost, redisPort); 
		executorServicePool = Executors.newFixedThreadPool(200);		// max number of threads
		// Most important action - setup channel buffering thread
		logger.info("Context Initialized");
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		if (!responseWriter.isClosed()) {
			try {
				responseWriter.close();
				logger.info("'contextDetstroyed] closed open ChunkedOutput stream.");
			} catch (IOException e) {
				logger.error("[contextDestroyed] Error in closing ChunkedOutput");
			}
		}
		logger.info("[finalize] Taking down all channel buffers and threads");
		jedisConn.finalize();
		jedisConn = null;
		logger.info("Jedis resources released");
		shutdownAndAwaitTermination(executorServicePool);
		logger.info("Context destroyed");
	}

	public boolean initRedisConnection() { 
		try {
			isConnected = jedisConn.connectToRedis();
			subscriberJedis = jedisConn.getJedis();
		} catch (JedisConnectionException e) {
			logger.error("Fatal error! Couldn't establish connection to REDIS!");
			e.printStackTrace();
			System.exit(1);
		}
		if (subscriberJedis != null) {
			logger.info("[initRedisConnection] subscriberJedis = " + subscriberJedis);
			return true;
		}
		return false;
	}

	// Stop subscription of this subscribed thread and return resources to the JEDIS thread pool
	private void stopSubscription(final RedisSubscriber sub, final Jedis jedis) {
		if (sub != null && sub.getSubscribedChannels() > 0) {
			if (!patternSubscriptionFlag) { 
				sub.unsubscribe();				
			}
			else {
				sub.punsubscribe();
			}
		}
		jedisConn.returnJedis();
		logger.info("[stopSubscription] Subscription ended for Channel=" + redisChannel);
	}


	// Create a subscription to specified REDIS channel: spawn a new thread
	private void subscribeToChannel(final RedisSubscriber sub, final Jedis jedis, String channel) throws Exception {
		redisChannel = channel;
		logger.info("[subscribeToChannel] Going for subscribe thread creation, executorServicePool: " + executorServicePool);
		executorServicePool.submit(new Runnable() {
			public void run() {
				try {
					logger.info("[subscribeToChannel] patternSubscriptionFlag = " + patternSubscriptionFlag);
					if (!patternSubscriptionFlag) { 
						logger.info("[subscribeToChannel] Attempting subscription for " + redisHost + ":" + redisPort + "/" + redisChannel);
						jedis.subscribe(sub, redisChannel);
					} 
					else {
						logger.info("[subscribeToChannel] Attempting pSubscription for " + redisHost + ":" + redisPort + "/" + redisChannel);
						jedis.psubscribe(sub, redisChannel);
					}
				} catch (Exception e) {
					logger.error("[subscribeToChannel] AIDR Predict Channel Subscribing failed");
					stopSubscription(sub, jedis);
				} finally {
					try {
						stopSubscription(sub, jedis);
					} catch (Exception e) {
						logger.error("[subscribeToChannel] Exception occurred attempting stopSubscription: " + e.toString());
						e.printStackTrace();
						System.exit(1);
					}
				}
			}
		}); 
	}

	private boolean isPattern(String channelName) {
		// We consider only the wildcards * and ?
		if (channelName.contains("*") || channelName.contains("?")) {
			patternSubscriptionFlag = true;
			return true;
		}
		else {
			patternSubscriptionFlag = false;
			return false;
		}
	}

	public String setFullyQualifiedChannelName(final String channelPrefixCode, final String channelCode) {
		if (isPattern(channelCode)) {
			patternSubscriptionFlag = true;
			return channelCode;			// already fully qualified name
		}
		//Otherwise concatenate to form the fully qualified channel name
		String channelName = channelPrefixCode.concat(channelCode);
		patternSubscriptionFlag = false;
		return channelName;
	}

	/**
	 * 
	 * @param callbackName  JSONP callback name
	 * @param count  number of buffered messages to fetch
	 * @return returns the 'count' number of buffered messages from requested channel as jsonp data 
	 * @throws IOException 
	 */
	@GET
	@Path("/channel/{crisisCode}")
	@Produces("application/json")
	public ChunkedOutput<String> streamChunkedResponse(
			@PathParam("crisisCode") String channelCode,
			@QueryParam("callback") final String callbackName,
			@DefaultValue("-1") @QueryParam("rate") Float rate,
			@DefaultValue("0") @QueryParam("duration") String duration) throws IOException {
		
		if (channelCode != null) {
			// TODO: Handle client refresh of web-page in same session				
			if (initRedisConnection()) {
				responseWriter = new ChunkedOutput<String>(String.class);
				
				// Get callback function name, if any
				String channel = setFullyQualifiedChannelName(CHANNEL_PREFIX_CODE, channelCode);
				aidrSubscriber = new RedisSubscriber(subscriberJedis, responseWriter, channel, callbackName, rate, duration);
				try {
					logger.info("subscriberJedis = " + subscriberJedis + ", aidrSubscriber = " + aidrSubscriber);
					subscribeToChannel(aidrSubscriber, subscriberJedis, channel);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					logger.error("[streamChunkedResponse] Fatal exception occurred attempting subscription: " + e.toString());
					e.printStackTrace();
					System.exit(1);
				}
				logger.info("[streamChunkedResponse] Attempting async response thread");
				executorServicePool.execute(aidrSubscriber);	// alternatively, use: asyncContext.start(aidrSubscriber);
			}
		} 
		else {
			// No crisisCode provided...
			StringBuilder errorMessageString = new StringBuilder();
			if (callbackName != null) {
				errorMessageString.append(callbackName).append("(");
			}
			errorMessageString.append("{\"crisisCode\":\"null\"");
			errorMessageString.append("\"streaming status\":\"error\"}");
			if (callbackName != null) {
				errorMessageString.append(")");
			}
			responseWriter = new ChunkedOutput<String>(String.class);
			responseWriter.write(errorMessageString.toString());
		}
		logger.info("[streamChunkedResponse] Reached end of function");
		return responseWriter;
	}

	// cleanup all threads 
	void shutdownAndAwaitTermination(ExecutorService threadPool) {
		threadPool.shutdown(); // Disable new tasks from being submitted
		try {
			// Wait a while for existing tasks to terminate
			if (!threadPool.awaitTermination(1, TimeUnit.SECONDS)) {
				threadPool.shutdownNow(); 			// Cancel currently executing tasks
				// Wait a while for tasks to respond to being cancelled
				if (!threadPool.awaitTermination(1, TimeUnit.SECONDS))
					logger.error("[shutdownAndAwaitTermination] Pool did not terminate");
				System.err.println("[shutdownAndAwaitTermination] Pool did not terminate");
			}
		} catch (InterruptedException ie) {
			// (Re-)Cancel if current thread also interrupted
			threadPool.shutdownNow();
			// Preserve interrupt status
			Thread.currentThread().interrupt();
		}
	}

	public class RedisSubscriber extends JedisPubSub implements AsyncListener, Runnable {
		//////////////////////////////////////////////////////////////////////////////////////////////////
		// The inner class that handles both Asynchronous Servlet Thread and Redis Threaded Subscription
		//////////////////////////////////////////////////////////////////////////////////////////////////
		// Redis/Jedis related
		private String channel = null;
		private String callbackName = null;
		private Jedis jedis;

		// Async execution related 
		private final ChunkedOutput<String> responseWriter;
		private boolean runFlag = true;
		private boolean error = false;
		private boolean timeout = false;
		private long subscriptionDuration = SUBSCRIPTION_MAX_DURATION;

		// rate control related 
		private static final int DEFAULT_SLEEP_TIME = 0;		// in msec
		private float messageRate = 0;							// default: <= 0 implies no rate control
		private int sleepTime = DEFAULT_SLEEP_TIME;

		// Share data structure between Jedis and Async threads
		private List<String> messageList = Collections.synchronizedList(new ArrayList<String>());


		public RedisSubscriber(final Jedis jedis, final ChunkedOutput<String> responseWriter, 
							   final String channel, final String callbackName, 
							   final Float rate, final String duration) throws IOException {
			this.channel = channel;
			this.callbackName = callbackName;
			this.jedis = jedis;
			this.setRunFlag(true);		
			this.responseWriter = responseWriter;
			if (duration != null) {
				subscriptionDuration = parseTime(duration);
			}
			logger.info("Client requested subscription for duration = " + subscriptionDuration);
			if (rate != -1) {
				messageRate = rate;			// specified as messages/min (NOTE: upper-bound)
				if (messageRate > 0) {		// otherwise, use default rate
					sleepTime = Math.max(0, Math.round(60 * 1000 / messageRate));		// time to sleep between sends (in msecs)
				}
			}
		}

		private long parseTime(String timeString) {
			long duration = 0;
			final int maxDuration = SUBSCRIPTION_MAX_DURATION > 0 ? SUBSCRIPTION_MAX_DURATION : Integer.MAX_VALUE;
			float value = Float.parseFloat(timeString.substring(0, timeString.length()-1));
			if (value > 0) {
				String suffix = timeString.substring(timeString.length() - 1, timeString.length());
				if (suffix.equalsIgnoreCase("s"))
					duration = Math.min(maxDuration, Math.round(value * 1000));
				if (suffix.equalsIgnoreCase("m"))
					duration = Math.min(maxDuration, Math.round(value * 1000 * 60));
				if (suffix.equalsIgnoreCase("h"))
					duration = Math.min(maxDuration, Math.round(value * 1000 * 60 * 60));
				if (suffix.equalsIgnoreCase("d"))
					duration = Math.min(maxDuration, Math.round(value * 1000 * 60 * 60 * 24));
			}
			return duration;
		}

		@Override
		public void onMessage(String channel, String message) {
			final int DEFAULT_COUNT = 1;
			synchronized (messageList) {
				if (messageList.size() < DEFAULT_COUNT) messageList.add(message);
			}
		}

		@Override
		public void onPMessage(String pattern, String channel, String message) {
			synchronized (messageList) {
				if (messageList.size() <  1) messageList.add(message);
			}
		}

		@Override
		public void onPSubscribe(String pattern, int subscribedChannels) {
			isSubscribed = true;
			logger.info("[onPSubscribe] Started pattern subscription...");
		}

		@Override
		public void onPUnsubscribe(String pattern, int subscribedChannels) {
			isSubscribed = false;
			logger.info("[onPUnsubscribe] Unsubscribed from pattern subscription...");
		}

		@Override
		public void onSubscribe(String channel, int subscribedChannels) {
			isSubscribed = true;
			logger.info("[onSubscribe] Started channel subscription...");
		}

		@Override
		public void onUnsubscribe(String channel, int subscribedChannels) {
			isSubscribed = false;
			logger.info("[onUnsubscribe] Unusbscribed from channel " + channel);
		}

		///////////////////////////////////
		// Now to implement Async methods
		///////////////////////////////////
		public boolean isThreadTimeout(long startTime) {
			if ((subscriptionDuration > 0) && (new Date().getTime() - startTime) > subscriptionDuration) {
				logger.info("[isThreadTimeout] Exceeded Thread timeout = " + subscriptionDuration + "msec");
				return true;
			}
			return false;
		}

		public void run() {
			// Time-out related local variables
			long startTime = new Date().getTime();			// start time of the thread execution
			long lastAccessedTime = startTime; 

			setRunFlag(true);
			while (getRunFlag() && !isThreadTimeout(startTime)) {
				// Here we poll a non blocking resource for updates
				if (messageList != null && !messageList.isEmpty()) {
					// There are updates, send these to the waiting client
					if (!error && !timeout) {
						// Send updates response as JSON
						List<String> latestMsg = null; 
						synchronized (messageList) {
							latestMsg = new ArrayList<String>();
							latestMsg.addAll(messageList);
						}
						JsonDataFormatter taggerOutput = new JsonDataFormatter(callbackName);	// Tagger specific JSONP output formatter
						StringBuilder jsonDataList = taggerOutput.createList(latestMsg, latestMsg.size(), rejectNullFlag);
						int count = taggerOutput.getMessageCount();
						try {
							//logger.info("[run] Formatted jsonDataList: " + jsonDataList.toString());
							if (!responseWriter.isClosed()) {
								responseWriter.write(jsonDataList.toString());
								responseWriter.write("\n");
								logger.info("[run] sent jsonp data, count = " + count);
							}
							else {
								logger.info("Possible client disconnect...");
								break;
							}
						} catch (Exception e) {
							logger.info("Error in write attempt - possible client disconnect");
							setRunFlag(false);
						} 
						synchronized (messageList) {
							if (count != 0)									// we did not just send an empty JSONP message
								lastAccessedTime = new Date().getTime();	// approx. time when message last received from REDIS

							// Reset the messageList buffer and cleanup
							messageList.clear();	// remove the sent message from list
							latestMsg.clear();
							latestMsg = null;
							jsonDataList = null;
						}
						// Now sleep for a short time before going for next message - easy to read on screen
						try {
							Thread.sleep(sleepTime);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							//e.printStackTrace();
						}
					}
					else {
						setRunFlag(false);
					}
				}
				else {
					// messageList is empty --> no message received 
					// from REDIS. Wait for some more time before giving up.
					long currentTime = new Date().getTime();
					long elapsed = currentTime - lastAccessedTime;
					if (elapsed > REDIS_CALLBACK_TIMEOUT) {
						logger.error("[run] exceeded REDIS timeout = " + REDIS_CALLBACK_TIMEOUT + "msec");
						setRunFlag(false);
					}	
					else {
						try {
							Thread.sleep(100);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
				// check if the client is up - indirectly through whether the write succeeded or failed
				if (responseWriter.isClosed()) {
					logger.info("[run] Client side error - possible client disconnect..." + new Date());
					setRunFlag(false);
				}
			}	// end-while

			// clean-up and exit thread
			if (!error && !timeout) {
				if (messageList != null) {
					messageList.clear();
					messageList = null;
				}
				if (!responseWriter.isClosed()) {
					try {
						responseWriter.close();
					} catch (IOException e) {
						logger.error("[run] Error attempting closing ChunkedOutput.");
					}
				}
				try {
					stopSubscription(this, this.jedis);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					logger.error("[run] Attempting clean-up. Exception occurred attempting stopSubscription: " + e.toString());
					e.printStackTrace();
				}
			}
		}

		public void setRunFlag(final boolean val) {
			runFlag = val;
		}

		public boolean getRunFlag() {
			return runFlag;
		}

		@Override
		public void onError(AsyncEvent event) throws IOException {
			setRunFlag(false);
			error = true;
			logger.error("[onError] An error occured while executing task for client ");
		}

		@Override
		public void onTimeout(AsyncEvent event) throws IOException {
			setRunFlag(false);
			timeout = true;
			logger.warn("[onTimeout] Timed out while executing task for client");
		}

		@Override
		public void onStartAsync(AsyncEvent event) throws IOException {}

		@Override
		public void onComplete(AsyncEvent event) throws IOException {
			logger.info("[run] Async thread complete...");
		}
	}

}
