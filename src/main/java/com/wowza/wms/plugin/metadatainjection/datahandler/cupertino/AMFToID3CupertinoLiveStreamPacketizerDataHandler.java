package com.wowza.wms.plugin.metadatainjection.datahandler.cupertino;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wowza.wms.amf.AMFDataList;
import com.wowza.wms.amf.AMFPacket;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.httpstreamer.cupertinostreaming.livestreampacketizer.CupertinoPacketHolder;
import com.wowza.wms.httpstreamer.cupertinostreaming.livestreampacketizer.CupertinoUserManifestHeaders;
import com.wowza.wms.httpstreamer.cupertinostreaming.livestreampacketizer.IHTTPStreamerCupertinoLivePacketizerDataHandler2;
import com.wowza.wms.httpstreamer.cupertinostreaming.livestreampacketizer.LiveStreamPacketizerCupertino;
import com.wowza.wms.httpstreamer.cupertinostreaming.livestreampacketizer.LiveStreamPacketizerCupertinoChunk;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.media.mp3.model.idtags.ID3Frames;
import com.wowza.wms.media.mp3.model.idtags.ID3V2FrameTextInformationUserDefined;
import com.wowza.wms.media.mp3.model.idtags.IID3V2Frame;
import com.wowza.wms.plugin.metadatainjection.amf.AMFToID3ApplicationsManager;
import com.wowza.wms.plugin.metadatainjection.amf.AMFToID3Converter;
import com.wowza.wms.plugin.metadatainjection.amf.AMFToID3ConverterContext;
import com.wowza.wms.plugin.metadatainjection.amf.IAMFToID3DataHandler;

public class AMFToID3CupertinoLiveStreamPacketizerDataHandler implements IHTTPStreamerCupertinoLivePacketizerDataHandler2, IAMFToID3DataHandler
{
	private IApplicationInstance appInstance;
	private LiveStreamPacketizerCupertino packetizer;
	private String streamName;
	private boolean addToManifest = false;

	protected AMFToID3Converter converter;
	private int maxFailedConversionMessages = 20;
	private int maxVerboseConversionMessages = 20;
	private AMFToID3ConverterContext context;

	private boolean enabled = false;
	private String internalTest;

	public AMFToID3CupertinoLiveStreamPacketizerDataHandler(IApplicationInstance appInstance,
			LiveStreamPacketizerCupertino liveStreamPacketizer, String streamName)
	{
		this.appInstance = appInstance;
		this.packetizer = liveStreamPacketizer;
		this.streamName = streamName;

		context = new AMFToID3ConverterContext();
		context.setContextString(this.packetizer.getContextStr());

		this.converter = new AMFToID3Converter();
		converter.setMaxVerboseConversionMessages(maxVerboseConversionMessages);
		converter.setMaxFailedConversionMessages(maxFailedConversionMessages);

		converter.setContextStr(liveStreamPacketizer.getContextStr());

		addToManifest = appInstance.getProperties().getPropertyBoolean("amfToID3ConversionAddToManifest", false);

		// We have controller, but need to set the packetizer
		packetizer.getProperties().setProperty("ID3AndPDTInjectionModule.streamName", streamName);

		// Atomic: creates the controller if needed and attaches this handler in one step
		AMFToID3ApplicationsManager.getAppsManager().registerDataHandler(appInstance, streamName, this);
	}

	@Override
	public void onFillChunkStart(LiveStreamPacketizerCupertinoChunk chunk)
	{
		// no-op
	}

	@Override
	public void onFillChunkEnd(LiveStreamPacketizerCupertinoChunk chunk, long timecode)
	{
		// no-op
	}

	@Override
	public void onFillChunkDataPacket(LiveStreamPacketizerCupertinoChunk chunk, CupertinoPacketHolder holder, AMFPacket packet,
			ID3Frames id3Frames)
	{
		while (true)
		{
			byte[] buffer = packet.getData();
			if (buffer == null)
				break;

			if (packet.getSize() <= 2)
				break;

			int offset = 0;
			if (buffer[0] == 0)
				offset++;

			AMFDataList amfList = new AMFDataList(buffer, offset, buffer.length - offset);

			context.setContextString(this.packetizer.getContextStr());

			//insert into media segment
			this.converter.convertAMFDataListToID3(amfList, context, id3Frames);

			// insert tags into manifest
			// #EXT-X-METADATA-EVENT-TESTEVENT1:f56c068c-ae1f-478e-9148-12d88a4f4f5b
			// many need to look at converting this to EXT-DATERANGE
			// https://tools.ietf.org/html/draft-pantos-http-live-streaming-23#section-4.3.2.7
			// #EXT-DATERANGE:ID="f56c068c-ae1f-478e-9148-12d88a4f4f5b",START-DATE="2021-01-12 12:12",X-EVENT="TESTEVENT1"
			if (addToManifest)
			{
				try
				{
					for (IID3V2Frame id3v2Frame : id3Frames.getFrames())
					{
						if (id3v2Frame.getClass() == ID3V2FrameTextInformationUserDefined.class)
						{
							ID3V2FrameTextInformationUserDefined textInfo = (ID3V2FrameTextInformationUserDefined)id3v2Frame;
							CupertinoUserManifestHeaders userManifestHeaders = chunk.getUserManifestHeaders();
							if (userManifestHeaders != null)
							{
								// Add custom headers to chunklist body for a given chunk
								ObjectMapper mapper = new ObjectMapper();
								JsonNode obj = mapper.readTree(textInfo.getValue());
								String event = null;
								String guid = null;
								if (obj.has("eventType"))
								{
									event = obj.get("eventType").asText();
								}
								if (obj.has("guid"))
								{
									guid = obj.get("guid").asText();
								}
								if (guid != null && !guid.isEmpty() && event != null && !event.isEmpty() && !event.equals("programDateTime"))
								{
									userManifestHeaders.addHeader("EXT-X-METADATA-EVENT-" + event.toUpperCase(), guid);
								}
							}
						}
					}
				}
				catch (Exception e)
				{
					WMSLoggerFactory.getLogger(AMFToID3CupertinoLiveStreamPacketizerDataHandler.class).error(e);
				}
			}
			break;
		}
	}

	@Override
	public void onFillChunkMediaPacket(LiveStreamPacketizerCupertinoChunk chunk, CupertinoPacketHolder holder, AMFPacket packet)
	{
		// no-op\
	}

	/**
	 * Unregister from the stream controller. Call when the owning packetizer is destroyed.
	 * The controller itself is dropped once no handler references it.
	 */
	public void dispose()
	{
		AMFToID3ApplicationsManager.getAppsManager().unregisterDataHandler(appInstance, streamName, this);
	}

	@Override
	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
	}

	@Override
	public boolean isEnabled()
	{
		return enabled;
	}

	@Override
	public void setMaxFailedConversionMessages(int maxFailedConversionMessages)
	{
		this.maxFailedConversionMessages = maxFailedConversionMessages;
		if (converter != null)
			converter.setMaxFailedConversionMessages(maxFailedConversionMessages);

	}

	@Override
	public void setMaxVerboseConversionMessages(int maxVerboseConversionMessages)
	{
		this.maxVerboseConversionMessages = maxVerboseConversionMessages;

		if (converter != null)
			converter.setMaxVerboseConversionMessages(maxVerboseConversionMessages);

	}

}
