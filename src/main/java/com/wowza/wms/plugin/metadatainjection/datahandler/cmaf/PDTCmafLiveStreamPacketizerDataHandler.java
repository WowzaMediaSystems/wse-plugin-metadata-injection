package com.wowza.wms.plugin.metadatainjection.datahandler.cmaf;

import java.util.Date;
import java.util.Locale;

import org.apache.commons.lang.time.FastDateFormat;

import com.wowza.util.ElapsedTimer;
import com.wowza.util.SystemUtils;
import com.wowza.wms.amf.AMFPacket;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.application.WMSProperties;
import com.wowza.wms.httpstreamer.cmafstreaming.livestreampacketizer.LiveStreamPacketizerCmaf;
import com.wowza.wms.httpstreamer.model.LiveStreamPacketizerPacketHolder;
import com.wowza.wms.httpstreamer.mpegdashstreaming.file.InbandEventStreams;
import com.wowza.wms.httpstreamer.mpegdashstreaming.livestreampacketizer.IHTTPStreamerMPEGDashLivePacketizerDataHandler;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.media.mp3.model.idtags.ID3Frames;
import com.wowza.wms.media.mp3.model.idtags.ID3V2FrameTextInformationUserDefined;
import com.wowza.wms.plugin.metadatainjection.datahandler.cupertino.PDTCupertinoLiveStreamPacketizerDataHandler;
import com.wowza.wms.stream.IMediaStream;

/**
 * Adds a "programDateTime" ID3 TXXX frame, wrapped in an emsg box, at the start of each CMAF
 * segment. This is the CMAF counterpart of the ID3 program-date-time tag written by
 * {@link PDTCupertinoLiveStreamPacketizerDataHandler} for HLS/TS chunks.
 * <p>
 * EXT-X-PROGRAM-DATE-TIME for CMAF HLS chunklists is handled natively by Wowza Streaming Engine
 * (cmafEnableProgramDateTime), so this handler only emits the ID3/emsg tag.
 */
public class PDTCmafLiveStreamPacketizerDataHandler implements IHTTPStreamerMPEGDashLivePacketizerDataHandler
{
	public static final String MODULE_NAME = "ModuleCmafProgramDateTime";
	public static final String ID3_DESCRIPTION = "programDateTime";

	private final IApplicationInstance appInstance;
	private final FastDateFormat id3DateString = FastDateFormat.getInstance(PDTCupertinoLiveStreamPacketizerDataHandler.ID3DATEFORMAT, SystemUtils.gmtTimeZone, Locale.US);

	private boolean enableId3ProgramDateTime = true;
	private long programDateTimeOffset = 0;
	private final String streamName;
	private final LiveStreamPacketizerCmaf packetizer;
	private final ID3EmsgUtils emsgUtils;

	// Timecode of the first segment seen; segment start times are relative to it
	private long baseTimecode = Long.MIN_VALUE;

	/**
	 * @param emsgUtils emsg helper shared with every other handler on this packetizer so emsg ids stay unique
	 */
	public PDTCmafLiveStreamPacketizerDataHandler(IApplicationInstance appInstance, LiveStreamPacketizerCmaf liveStreamPacketizer, String streamName, ID3EmsgUtils emsgUtils)
	{
		this.appInstance = appInstance;
		this.streamName = streamName;
		this.packetizer = liveStreamPacketizer;
		this.emsgUtils = emsgUtils;

		WMSProperties httpProps = appInstance.getHTTPStreamerProperties();
		WMSProperties props = appInstance.getProperties();

		// Fall back to the cupertino settings so existing configurations carry over to CMAF
		enableId3ProgramDateTime = httpProps.getPropertyBoolean("cupertinoEnableId3ProgramDateTime", enableId3ProgramDateTime);
		enableId3ProgramDateTime = props.getPropertyBoolean("cupertinoEnableId3ProgramDateTime", enableId3ProgramDateTime);
		enableId3ProgramDateTime = httpProps.getPropertyBoolean("cmafEnableId3ProgramDateTime", enableId3ProgramDateTime);
		enableId3ProgramDateTime = props.getPropertyBoolean("cmafEnableId3ProgramDateTime", enableId3ProgramDateTime);

		programDateTimeOffset = httpProps.getPropertyLong("cupertinoProgramDateTimeOffset", programDateTimeOffset);
		programDateTimeOffset = props.getPropertyLong("cupertinoProgramDateTimeOffset", programDateTimeOffset);
		programDateTimeOffset = httpProps.getPropertyLong("cmafProgramDateTimeOffset", programDateTimeOffset);
		programDateTimeOffset = props.getPropertyLong("cmafProgramDateTimeOffset", programDateTimeOffset);

		WMSLoggerFactory.getLogger(PDTCmafLiveStreamPacketizerDataHandler.class)
				.info(MODULE_NAME + "[" + liveStreamPacketizer.getContextStr() + "] Running with cmafEnableId3ProgramDateTime:" + enableId3ProgramDateTime + " cmafProgramDateTimeOffset:" + programDateTimeOffset);
	}

	@Override
	public void onFillSegmentStart(long startTimecode, long endTimecode, InbandEventStreams inbandEventStreams)
	{
		if (!enableId3ProgramDateTime || inbandEventStreams == null)
			return;

		emsgUtils.registerEventStream(inbandEventStreams);

		IMediaStream stream = appInstance.getStreams().getStream(streamName);
		if (stream == null)
			return;

		ElapsedTimer elapsedTime = stream.getElapsedTime();
		if (elapsedTime == null)
			return;

		synchronized(this)
		{
			if (baseTimecode == Long.MIN_VALUE)
				baseTimecode = startTimecode;
		}

		long createTime = elapsedTime.getDate().getTime() + programDateTimeOffset + (startTimecode - baseTimecode);

		ID3Frames id3Frames = new ID3Frames();
		ID3V2FrameTextInformationUserDefined comment = new ID3V2FrameTextInformationUserDefined();
		comment.setDescription(ID3_DESCRIPTION);
		comment.setValue(id3DateString.format(new Date(createTime)));
		id3Frames.putFrame(comment);

		emsgUtils.addID3Frames(inbandEventStreams, id3Frames, startTimecode);
	}

	@Override
	public void onFillSegmentEnd(long startTimecode, long endTimecode, InbandEventStreams inbandEventStreams)
	{
		// no-op
	}

	@Override
	public void onFillSegmentDataPacket(LiveStreamPacketizerPacketHolder holder, AMFPacket packet, InbandEventStreams inbandEventStreams)
	{
		// no-op
	}

	@Override
	public void onFillSegmentMediaPacket(LiveStreamPacketizerPacketHolder holder, AMFPacket packet)
	{
		// no-op
	}

	public boolean isEnabled()
	{
		return enableId3ProgramDateTime;
	}

	public LiveStreamPacketizerCmaf getPacketizer()
	{
		return packetizer;
	}

}
