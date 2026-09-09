package com.wowza.wms.plugin.metadatainjection.datahandler.cmaf;

import com.wowza.wms.amf.AMFDataList;
import com.wowza.wms.amf.AMFPacket;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.httpstreamer.cmafstreaming.livestreampacketizer.LiveStreamPacketizerCmaf;
import com.wowza.wms.httpstreamer.model.LiveStreamPacketizerPacketHolder;
import com.wowza.wms.httpstreamer.mpegdashstreaming.file.InbandEventStreams;
import com.wowza.wms.httpstreamer.mpegdashstreaming.livestreampacketizer.IHTTPStreamerMPEGDashLivePacketizerDataHandler;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.media.metadata.emsg.IEmsgFrame;
import com.wowza.wms.media.mp3.model.idtags.ID3Frames;
import com.wowza.wms.plugin.metadatainjection.amf.AMFToID3ApplicationsManager;
import com.wowza.wms.plugin.metadatainjection.amf.AMFToID3Converter;
import com.wowza.wms.plugin.metadatainjection.amf.AMFToID3ConverterContext;
import com.wowza.wms.plugin.metadatainjection.amf.AMFToID3ConverterStreamController;
import com.wowza.wms.plugin.metadatainjection.amf.IAMFToID3DataHandler;
import com.wowza.wms.plugin.metadatainjection.datahandler.cupertino.AMFToID3CupertinoLiveStreamPacketizerDataHandler;

/**
 * Converts injected AMF data events into ID3 frames and delivers them in CMAF segments as
 * emsg boxes (scheme {@code https://aomedia.org/emsg/ID3}). This is the CMAF counterpart of
 * {@link AMFToID3CupertinoLiveStreamPacketizerDataHandler} and shares the same converters, controller,
 * and application properties.
 */
public class AMFToID3CmafLiveStreamPacketizerDataHandler implements IHTTPStreamerMPEGDashLivePacketizerDataHandler, IAMFToID3DataHandler
{
	private final IApplicationInstance appInstance;
	private final LiveStreamPacketizerCmaf packetizer;
	private final String streamName;

	protected AMFToID3Converter converter;
	private int maxFailedConversionMessages = 20;
	private int maxVerboseConversionMessages = 20;
	private final AMFToID3ConverterContext context;
	private final ID3EmsgUtils emsgUtils;

	private volatile boolean enabled = false;

	public AMFToID3CmafLiveStreamPacketizerDataHandler(IApplicationInstance appInstance, LiveStreamPacketizerCmaf liveStreamPacketizer, String streamName)
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

		this.emsgUtils = new ID3EmsgUtils(this.packetizer.getContextStr());

		// We have controller, but need to set the packetizer
		packetizer.getProperties().setProperty("ID3AndPDTInjectionModule.streamName", streamName);

		AMFToID3ApplicationsManager appsManager = AMFToID3ApplicationsManager.getAppsManager();
		AMFToID3ConverterStreamController controller = appsManager.getController(appInstance, streamName);
		if (controller == null)
			controller = appsManager.createController(appInstance, streamName);
		controller.addDataHandler(this);
	}

	/**
	 * Unregister from the stream controller. Call when the owning packetizer is destroyed.
	 */
	public void dispose()
	{
		AMFToID3ConverterStreamController controller = AMFToID3ApplicationsManager.getAppsManager().getController(appInstance, streamName);
		if (controller != null)
			controller.removeDataHandler(this);
	}

	@Override
	public void onFillSegmentStart(long startTimecode, long endTimecode, InbandEventStreams inbandEventStreams)
	{
		emsgUtils.registerEventStream(inbandEventStreams);
	}

	@Override
	public void onFillSegmentEnd(long startTimecode, long endTimecode, InbandEventStreams inbandEventStreams)
	{
		// no-op
	}

	@Override
	public void onFillSegmentDataPacket(LiveStreamPacketizerPacketHolder holder, AMFPacket packet, InbandEventStreams inbandEventStreams)
	{
		try
		{
			byte[] buffer = packet.getData();
			if (buffer == null)
				return;

			if (packet.getSize() <= 2)
				return;

			int offset = 0;
			if (buffer[0] == 0)
				offset++;

			AMFDataList amfList = new AMFDataList(buffer, offset, buffer.length - offset);

			context.setContextString(this.packetizer.getContextStr());

			ID3Frames id3Frames = new ID3Frames();
			this.converter.convertAMFDataListToID3(amfList, context, id3Frames);

			if (id3Frames.getFrames().isEmpty())
				return;

			// Insert into media segment as an emsg box
			IEmsgFrame emsg = emsgUtils.addID3Frames(inbandEventStreams, id3Frames, packet.getAbsTimecode());
			if (emsg != null)
			{
				WMSLoggerFactory.getLogger(AMFToID3CmafLiveStreamPacketizerDataHandler.class)
						.debug("AMFToID3CmafLiveStreamPacketizerDataHandler[" + context.getContextString() + "] emsg: [id: " + emsg.getId() + ", time: " + emsg.getTime() + "]");
			}
		}
		catch (Exception e)
		{
			WMSLoggerFactory.getLogger(AMFToID3CmafLiveStreamPacketizerDataHandler.class).error("AMFToID3CmafLiveStreamPacketizerDataHandler.onFillSegmentDataPacket[" + context.getContextString() + "]", e);
		}
	}

	@Override
	public void onFillSegmentMediaPacket(LiveStreamPacketizerPacketHolder holder, AMFPacket packet)
	{
		// no-op
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
