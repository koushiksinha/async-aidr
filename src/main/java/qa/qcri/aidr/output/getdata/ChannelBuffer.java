/** 
 * @author Koushik Sinha
 * Last modified: 06/01/2014
 * 
 * The ChannelBuffer class implements the creation,
 * and deletion of channels as well as retrieval of
 * messages from a specific channel.
 * 
 */

package qa.qcri.aidr.output.getdata;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.apache.commons.collections.Buffer;
import org.apache.commons.collections.BufferUtils;
import org.apache.commons.collections.buffer.CircularFifoBuffer;

//import org.apache.log4j.BasicConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChannelBuffer {
	private static int MAX_BUFFER_SIZE = 2000;		// number of elements the buffer will hold at any time
	private String channelName;
	private long lastAddTime;
	private Buffer messageBuffer;
	private Buffer timestampBuffer;

	private static Logger logger = LoggerFactory.getLogger(ChannelBuffer.class);

	public ChannelBuffer(final String name) {
		//BasicConfigurator.configure();			// setup log4j logging
		System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "INFO");		// set logging level for slf4j
		channelName = name;
	}

	public void createChannelBuffer() {
		messageBuffer = BufferUtils.synchronizedBuffer(new CircularFifoBuffer(MAX_BUFFER_SIZE));
		timestampBuffer = BufferUtils.synchronizedBuffer(new CircularFifoBuffer(MAX_BUFFER_SIZE));
	}

	public void createChannelBuffer(final int bufferSize) {
		messageBuffer = BufferUtils.synchronizedBuffer(new CircularFifoBuffer(bufferSize));
		timestampBuffer = BufferUtils.synchronizedBuffer(new CircularFifoBuffer(bufferSize));
	}

	@SuppressWarnings("unchecked")
	public void addMessage(String msg) {
		if (msg != null) {
			messageBuffer.add(msg);
			lastAddTime = new Date().getTime();
			timestampBuffer.add(Long.toString(lastAddTime));
		}
	}

	@SuppressWarnings("unchecked")
	public void addAllMessages(ArrayList<String> msgList) {
		if (msgList != null) {
			messageBuffer.addAll(msgList);
			lastAddTime = new Date().getTime();

			for (String msg:msgList)
				timestampBuffer.add(Long.toString(lastAddTime));	// rationale: all messages received simultaneously
		}
	}
	
	@SuppressWarnings("unchecked")
	public void addAllMessages(ArrayList<String> msgList, ArrayList<String>timestampList) {
		if (msgList != null && timestampList != null) {
			messageBuffer.addAll(msgList);
			timestampBuffer.addAll(timestampList);	
		}
	}

	@SuppressWarnings("unchecked")
	/**
	 * @param msgCount: number of messages to return
	 * @return Returns a list of messages sorted in ascending order of timestamp
	 */
	public List<String> getMessages(int msgCount) {
		List<String> msgList = new ArrayList<String>(); 
		synchronized(messageBuffer) {
			Iterator<String>itr = messageBuffer.iterator();
			int count = 0;
			while (itr.hasNext()) {
				msgList.add(itr.next());
				++count;
			}
		}
		return msgList;
	}

	@SuppressWarnings("unchecked")
	/**
	 * @param msgCount: number of messages to return
	 * @return Returns a list of messages sorted in descending order of timestamp
	 */
	public List<String> getLIFOMessages(int msgCount) {
		List<String> msgList = new ArrayList<String>();

		List<String> tempList = new ArrayList<String>();
		tempList.addAll(getMessages(MAX_BUFFER_SIZE));
		int count = 0;
		ListIterator<String>itr = tempList.listIterator(tempList.size()-1);
		while (itr.hasPrevious() && count < msgCount) {
			msgList.add(itr.next());
			++count;
		}
		return msgList;
	}

	public void deleteBuffer() {
		channelName = null;
		if (messageBuffer != null) { 
			messageBuffer.clear();
			messageBuffer = null;
		}
	}

	public void setChannelName(String name) {
		channelName = name;
	}

	public String getChannelName() {
		return channelName;
	}

	public long getLastAddTime() {
		return lastAddTime;
	}
	
	@SuppressWarnings("unchecked")
	public long getMsgAddTime(String msg) {
		if (messageBuffer != null && timestampBuffer != null) {
			synchronized(messageBuffer) {
				Iterator<String>itr = messageBuffer.iterator();
				Iterator<String>timeItr = timestampBuffer.iterator();
				while (itr.hasNext() && timeItr.hasNext()) {
					String temp = itr.next();
					if (temp.equals(msg)) {
						long timestamp = Long.parseLong(timeItr.next());
						return timestamp;
					}
				}
			}
		}
		return -1;
	}
	
	@SuppressWarnings("unchecked")
	public List<Long> getMsgTimeStamps(int msgCount) {
		List<Long>timestampList = new ArrayList<Long>();
		if (timestampBuffer != null) {
			synchronized(timestampBuffer) {
				Iterator<String>itr = timestampBuffer.iterator();
				int count = 0;
				while (itr.hasNext() && count < msgCount) {
					timestampList.add(Long.parseLong(itr.next()));
					++count;
				}
			}
			return timestampList;
		}
		return null;
	}
}