package com.wowza.wms.plugin.metadatainjection.datachannel;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wowza.wms.amf.AMFDataList;
import com.wowza.wms.amf.AMFPacket;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.application.WMSProperties;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.plugin.metadatainjection.HTTPProviderMetadataInjection;
import com.wowza.wms.plugin.metadatainjection.amf.AMFToID3Converter;
import com.wowza.wms.stream.IMediaStream;
import com.wowza.wms.stream.IMediaStreamLivePacketNotify;
import com.wowza.wms.vhost.IVHost;
import com.wowza.wms.webrtc.datachannel.IWebRTCDataChannel;
import com.wowza.wms.webrtc.datachannel.IWebRTCDataChannelSession;

/**
 * Delivers the data events that become ID3 tags to WebRTC viewers over a data channel, and
 * re-creates WSE's broadcast routing, which the required {@code webrtcDataChannelMode=MODULE}
 * turns off.
 * <p>
 * This is the only class that references {@code com.wowza.wms.webrtc.datachannel} (WSE 4.12+).
 * {@code MetadataInjectionModule} only creates it once it has found that API, and only calls the
 * {@code Object}-typed entry points, so the module still loads on older WSE versions.
 */
public class MetadataDataChannelSupport implements IMediaStreamLivePacketNotify
{
	private static final Class<MetadataDataChannelSupport> CLASS = MetadataDataChannelSupport.class;
	private static final String CLASS_NAME = CLASS.getSimpleName();

	public static final String PROPNAME_ENABLED = "metadataDataChannelEnabled";
	public static final String PROPNAME_OUTPUT_LABEL = "metadataDataChannelOutputLabel";
	public static final String PROPNAME_INPUT_LABEL = "metadataDataChannelInputLabel";
	public static final String PROPNAME_RELAY = "metadataDataChannelRelay";
	public static final String DEFAULT_LABEL = "metadata";

	private static final String PROPNAME_WEBRTC_ENABLE_DATA_CHANNELS = "webrtcEnableDataChannels";
	private static final String PROPNAME_WEBRTC_DATA_CHANNEL_MODE = "webrtcDataChannelMode";
	private static final String WEBRTC_DATA_CHANNEL_MODE_MODULE = "module";
	// Set by WSE on each simulcast rendition stream; names the stream the publisher signaled
	private static final String PROPNAME_RENDITION_PUBLISHER_STREAM = "webRTCRenditionPublisherStreamName";
	private static final String PROPNAME_SESSION_ROOM = "MetadataInjectionModule.dataChannelRoom";

	/**
	 * The sessions of one (application instance, stream): the publisher, its viewers and the labels
	 * the publisher currently has open.
	 */
	private static class Room
	{
		final Set<IWebRTCDataChannelSession> viewers = ConcurrentHashMap.newKeySet();
		final Set<String> publisherLabels = ConcurrentHashMap.newKeySet();
		volatile IWebRTCDataChannelSession publisher;

		boolean isEmpty()
		{
			return publisher == null && viewers.isEmpty();
		}
	}

	private final WMSLogger logger;
	private final IApplicationInstance appInstance;
	private final String outputLabel;
	// Null when publishers cannot inject over a data channel
	private final String inputLabel;
	private final ObjectMapper jsonMapper = new ObjectMapper();
	private final AtomicInteger countRejectedInputMessages = new AtomicInteger();
	private final boolean relay;
	private final AMFToID3Converter converter;
	private final int maxFailedSendMessages;
	private final AtomicInteger countFailedSendMessages = new AtomicInteger();
	private final ConcurrentMap<String, Room> rooms = new ConcurrentHashMap<>();

	public MetadataDataChannelSupport(IApplicationInstance appInstance)
	{
		this.appInstance = appInstance;
		this.logger = WMSLoggerFactory.getLoggerObj(CLASS, appInstance);

		WMSProperties props = appInstance.getProperties();
		this.outputLabel = props.getPropertyStr(PROPNAME_OUTPUT_LABEL, DEFAULT_LABEL);
		String configuredInputLabel = props.getPropertyStr(PROPNAME_INPUT_LABEL, "").trim();
		this.inputLabel = configuredInputLabel.isEmpty() ? null : configuredInputLabel;
		this.relay = props.getPropertyBoolean(PROPNAME_RELAY, true);
		this.maxFailedSendMessages = props.getPropertyInt("amfToID3ConversionFailedMaximum", 5);

		this.converter = new AMFToID3Converter(appInstance);
		this.converter.setMaxFailedConversionMessages(this.maxFailedSendMessages);
		this.converter.setMaxVerboseConversionMessages(props.getPropertyInt("amfToID3ConversionVerboseMaximum", 5));

		logger.info(CLASS_NAME + "[" + appInstance.getContextStr() + "] " + PROPNAME_OUTPUT_LABEL + ":" + this.outputLabel + " " + PROPNAME_INPUT_LABEL + ":" + (this.inputLabel != null ? this.inputLabel : "[off]") + " " + PROPNAME_RELAY + ":" + this.relay);

		WMSProperties webrtcProps = appInstance.getWebRTCProperties();
		boolean channelsEnabled = webrtcProps != null && webrtcProps.getPropertyBoolean(PROPNAME_WEBRTC_ENABLE_DATA_CHANNELS, false);
		String mode = webrtcProps != null ? webrtcProps.getPropertyStr(PROPNAME_WEBRTC_DATA_CHANNEL_MODE, "") : "";
		if (!channelsEnabled || !WEBRTC_DATA_CHANNEL_MODE_MODULE.equalsIgnoreCase(mode.trim()))
			logger.warn(CLASS_NAME + "[" + appInstance.getContextStr() + "] " + PROPNAME_ENABLED + " is set but nothing will be delivered: WebRTC properties "
					+ PROPNAME_WEBRTC_ENABLE_DATA_CHANNELS + "=true and " + PROPNAME_WEBRTC_DATA_CHANNEL_MODE + "=MODULE are both required (found "
					+ PROPNAME_WEBRTC_ENABLE_DATA_CHANNELS + "=" + channelsEnabled + ", " + PROPNAME_WEBRTC_DATA_CHANNEL_MODE + "=" + mode + ")");
	}

	// ---- stream tap ----

	@Override
	public void onLivePacket(IMediaStream stream, AMFPacket packet)
	{
		int type = packet.getType();
		if (type != IVHost.CONTENTTYPE_DATA0 && type != IVHost.CONTENTTYPE_DATA3)
			return;

		Room room = rooms.get(stream.getName());
		if (room == null || room.viewers.isEmpty())
			return;

		try
		{
			AMFDataList amfList = toAMFDataList(packet);
			if (amfList == null)
				return;

			String payload = converter.convertAMFDataListToPayload(amfList);
			if (payload == null)
				return;

			for (IWebRTCDataChannelSession viewer : room.viewers)
			{
				// No channel yet means the viewer is still connecting
				IWebRTCDataChannel channel = findOpenChannel(viewer, outputLabel);
				if (channel != null && !channel.sendString(payload))
					logFailedSend(viewer, payload.length());
			}
		}
		catch (Exception e)
		{
			logger.error(CLASS_NAME + ".onLivePacket[" + stream.getContextStr() + "]", e);
		}
	}

	private static AMFDataList toAMFDataList(AMFPacket packet)
	{
		byte[] buffer = packet.getData();
		if (buffer == null || packet.getSize() <= 2)
			return null;

		int offset = 0;
		if (buffer[0] == 0)
			offset++;

		int len = Math.min(packet.getSize(), buffer.length) - offset;
		if (len <= 0)
			return null;

		return new AMFDataList(buffer, offset, len);
	}

	private void logFailedSend(IWebRTCDataChannelSession session, int size)
	{
		if (countFailedSendMessages.incrementAndGet() < maxFailedSendMessages)
			logger.warn(CLASS_NAME + "[" + describe(session) + "] metadata not delivered (" + size
					+ " chars): send refused (channel closed, message at or above the 64 KB limit, or outbound buffer full)");
	}

	// ---- data-channel callbacks, Object-typed for MetadataInjectionModule ----

	public void onSessionCreate(Object sessionObj)
	{
		IWebRTCDataChannelSession session = (IWebRTCDataChannelSession)sessionObj;
		String roomName = roomNameFor(session);
		session.getProperties().setProperty(PROPNAME_SESSION_ROOM, roomName);

		rooms.compute(roomName, (key, room) -> {
			if (room == null)
				room = new Room();
			join(room, session);
			return room;
		});
		logger.info(CLASS_NAME + ".onSessionCreate[" + describe(session) + "] room:" + roomName);
	}

	public void onSessionDestroy(Object sessionObj)
	{
		IWebRTCDataChannelSession session = (IWebRTCDataChannelSession)sessionObj;
		String roomName = session.getProperties().getPropertyStr(PROPNAME_SESSION_ROOM);
		if (roomName == null)
			return;

		rooms.computeIfPresent(roomName, (key, room) -> {
			leave(room, session);
			return room.isEmpty() ? null : room;
		});
		logger.info(CLASS_NAME + ".onSessionDestroy[" + describe(session) + "] room:" + roomName);
	}

	public void onChannelOpen(Object channelObj)
	{
		IWebRTCDataChannel channel = (IWebRTCDataChannel)channelObj;
		IWebRTCDataChannelSession session = channel.getSession();
		Room room = roomOf(session);
		if (!isRelayed(room, session, channel))
			return;

		room.publisherLabels.add(channel.getLabel());
		for (IWebRTCDataChannelSession viewer : room.viewers)
			ensureChannel(viewer, channel.getLabel());
	}

	public void onChannelClose(Object channelObj)
	{
		IWebRTCDataChannel channel = (IWebRTCDataChannel)channelObj;
		IWebRTCDataChannelSession session = channel.getSession();
		Room room = roomOf(session);
		if (!isRelayed(room, session, channel))
			return;

		room.publisherLabels.remove(channel.getLabel());
		for (IWebRTCDataChannelSession viewer : room.viewers)
			closeChannels(viewer, channel.getLabel());
	}

	public void onStringMessage(Object channelObj, String message)
	{
		IWebRTCDataChannel channel = (IWebRTCDataChannel)channelObj;
		IWebRTCDataChannelSession session = channel.getSession();
		if (isInputChannel(channel))
		{
			injectFromChannel(session, channel, message);
			return;
		}

		Room room = roomOf(session);
		if (!isRelayed(room, null, channel))
			return;

		if (session == room.publisher)
		{
			for (IWebRTCDataChannelSession viewer : room.viewers)
			{
				IWebRTCDataChannel target = findOpenChannel(viewer, channel.getLabel());
				if (target != null)
					target.sendString(message);
			}
		}
		else if (!session.isPublisher())
		{
			IWebRTCDataChannel target = findOpenChannel(room.publisher, channel.getLabel());
			if (target != null)
				target.sendString(message);
		}
	}

	public void onBinaryMessage(Object channelObj, byte[] message)
	{
		IWebRTCDataChannel channel = (IWebRTCDataChannel)channelObj;
		IWebRTCDataChannelSession session = channel.getSession();
		Room room = roomOf(session);
		if (!isRelayed(room, null, channel))
			return;

		if (session == room.publisher)
		{
			for (IWebRTCDataChannelSession viewer : room.viewers)
			{
				IWebRTCDataChannel target = findOpenChannel(viewer, channel.getLabel());
				if (target != null)
					target.sendBinary(message);
			}
		}
		else if (!session.isPublisher())
		{
			IWebRTCDataChannel target = findOpenChannel(room.publisher, channel.getLabel());
			if (target != null)
				target.sendBinary(message);
		}
	}

	// ---- metadata input ----

	private boolean isInputChannel(IWebRTCDataChannel channel)
	{
		return inputLabel != null && inputLabel.equals(channel.getLabel());
	}

	/**
	 * Injects a message the stream's publisher sent on the input channel, exactly as the REST API
	 * injects a POST body: the message is the same JSON, and the reply sent back on the channel is
	 * the JSON the API would answer with. Only the publisher may inject; viewers are refused.
	 */
	private void injectFromChannel(IWebRTCDataChannelSession session, IWebRTCDataChannel channel, String message)
	{
		String reason = null;
		JsonNode payload = null;
		IMediaStream stream = null;

		if (!session.isPublisher())
			reason = "only the publisher of the stream can inject metadata";
		else
		{
			try
			{
				payload = jsonMapper.readTree(message);
			}
			catch (Exception e)
			{
				reason = "invalid json";
			}
			if (reason == null && (payload == null || !payload.isObject() || !payload.hasNonNull("event")))
				reason = "payload does not contain event";
			if (reason == null)
			{
				stream = appInstance.getStreams().getStream(session.getStreamName());
				if (stream == null)
					reason = "stream not found: " + session.getStreamName();
			}
		}

		if (reason != null)
		{
			if (countRejectedInputMessages.incrementAndGet() < maxFailedSendMessages)
				logger.warn(CLASS_NAME + "[" + describe(session) + "] metadata not injected from channel " + channel.getLabel() + ": " + reason);
			channel.sendString("{\"status\":\"failed\", \"reason\":" + jsonMapper.valueToTree(reason) + "}");
			return;
		}

		// Never on the data channel thread: an injection can wait out a delay and repeats
		String guid = HTTPProviderMetadataInjection.injectMetadataAsync(appInstance, stream, payload);
		channel.sendString("{\"status\":\"success\", \"guid\":\"" + guid + "\"}");
	}

	// ---- rooms ----

	private void join(Room room, IWebRTCDataChannelSession session)
	{
		if (session.isPublisher())
		{
			IWebRTCDataChannelSession previous = room.publisher;
			if (previous != null && previous != session)
			{
				logger.warn(CLASS_NAME + "[" + describe(session) + "] replacing publisher " + previous.getConnectionId());
				clearPublisherLabels(room);
			}
			room.publisher = session;
			return;
		}

		room.viewers.add(session);
		ensureChannel(session, outputLabel);
		for (String publisherLabel : room.publisherLabels)
			ensureChannel(session, publisherLabel);
	}

	private void leave(Room room, IWebRTCDataChannelSession session)
	{
		if (room.publisher == session)
		{
			room.publisher = null;
			clearPublisherLabels(room);
		}
		else
			room.viewers.remove(session);
	}

	private void clearPublisherLabels(Room room)
	{
		for (String publisherLabel : room.publisherLabels)
		{
			for (IWebRTCDataChannelSession viewer : room.viewers)
				closeChannels(viewer, publisherLabel);
		}
		room.publisherLabels.clear();
	}

	private Room roomOf(IWebRTCDataChannelSession session)
	{
		String roomName = session.getProperties().getPropertyStr(PROPNAME_SESSION_ROOM);
		return roomName != null ? rooms.get(roomName) : null;
	}

	/**
	 * True when traffic on this channel is ours to route: the relay is on, the session is in a room
	 * and the channel is neither the metadata output channel nor the metadata input channel. With a session, also requires it to be the room's
	 * current publisher (label open/close mirroring).
	 */
	private boolean isRelayed(Room room, IWebRTCDataChannelSession publisher, IWebRTCDataChannel channel)
	{
		if (!relay || room == null || outputLabel.equals(channel.getLabel()) || isInputChannel(channel))
			return false;
		return publisher == null || publisher == room.publisher;
	}

	/**
	 * Same scoping as WSE's broadcast mode: the signaled stream name, except that a player of a
	 * simulcast rendition joins its publisher's room.
	 */
	private String roomNameFor(IWebRTCDataChannelSession session)
	{
		String streamName = session.getStreamName();
		if (session.isPublisher())
			return streamName;

		IMediaStream stream = appInstance.getStreams().getStream(streamName);
		if (stream == null)
			return streamName;

		return stream.getProperties().getPropertyStr(PROPNAME_RENDITION_PUBLISHER_STREAM, streamName);
	}

	// ---- channels ----

	private static void ensureChannel(IWebRTCDataChannelSession session, String channelLabel)
	{
		if (findOpenChannel(session, channelLabel) == null)
			session.openChannel(channelLabel);
	}

	private static void closeChannels(IWebRTCDataChannelSession session, String channelLabel)
	{
		for (IWebRTCDataChannel channel : session.getChannels())
		{
			// A label the viewer opened itself is the viewer's to close
			if (channelLabel.equals(channel.getLabel()) && !channel.isRemotelyOpened())
				channel.close();
		}
	}

	/**
	 * The session's open channel with this label, preferring one the client opened itself since
	 * that is the one it is certain to be listening on.
	 */
	private static IWebRTCDataChannel findOpenChannel(IWebRTCDataChannelSession session, String channelLabel)
	{
		if (session == null)
			return null;

		IWebRTCDataChannel found = null;
		for (IWebRTCDataChannel channel : session.getChannels())
		{
			if (!channel.isOpen() || !channelLabel.equals(channel.getLabel()))
				continue;
			if (channel.isRemotelyOpened())
				return channel;
			if (found == null)
				found = channel;
		}
		return found;
	}

	private static String describe(IWebRTCDataChannelSession session)
	{
		return session.getStreamName() + "/" + session.getConnectionId() + (session.isPublisher() ? " publisher" : " viewer");
	}
}
