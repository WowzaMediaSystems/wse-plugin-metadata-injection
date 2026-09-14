package com.wowza.wms.plugin.metadatainjection.datahandler.cupertino;

import java.util.Date;
import java.util.Locale;

import org.apache.commons.lang.time.FastDateFormat;

import com.wowza.util.SystemUtils;
import com.wowza.wms.amf.AMFPacket;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.application.WMSProperties;
import com.wowza.wms.httpstreamer.cupertinostreaming.livestreampacketizer.CupertinoPacketHolder;
import com.wowza.wms.httpstreamer.cupertinostreaming.livestreampacketizer.IHTTPStreamerCupertinoLivePacketizerDataHandler2;
import com.wowza.wms.httpstreamer.cupertinostreaming.livestreampacketizer.LiveStreamPacketizerCupertino;
import com.wowza.wms.httpstreamer.cupertinostreaming.livestreampacketizer.LiveStreamPacketizerCupertinoChunk;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.media.mp3.model.idtags.ID3Frames;
import com.wowza.wms.media.mp3.model.idtags.ID3V2FrameTextInformationUserDefined;

public class PDTCupertinoLiveStreamPacketizerDataHandler implements IHTTPStreamerCupertinoLivePacketizerDataHandler2
{

	public static final String MODULE_NAME = "ModuleCupertinoProgramDateTime";
	// Same pattern WSE uses for EXT-X-PROGRAM-DATE-TIME (ISO/IEC 8601:2004, GMT) - 2010-02-19T14:54:23.031+00:00
	public static final String EXTDATEFORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'+00:00'";
	public static final String ID3DATEFORMAT = EXTDATEFORMAT;

	private FastDateFormat extDateString = FastDateFormat.getInstance(EXTDATEFORMAT, SystemUtils.gmtTimeZone, Locale.US);

	private boolean enableProgramDateTime = false;
	private boolean enableId3ProgramDateTime = true;
	private long cupertinoProgramDateTimeOffset = 0;
	private LiveStreamPacketizerCupertino packetizer = null;

	public PDTCupertinoLiveStreamPacketizerDataHandler(IApplicationInstance appInstance,
			LiveStreamPacketizerCupertino liveStreamPacketizer, String streamName)
	{
		this.packetizer = liveStreamPacketizer;

		WMSProperties httpProps = appInstance.getHTTPStreamerProperties();
		WMSProperties props = appInstance.getProperties();

		enableProgramDateTime = httpProps.getPropertyBoolean("cupertinoEnableProgramDateTime", enableProgramDateTime);
		enableProgramDateTime = props.getPropertyBoolean("cupertinoEnableProgramDateTime", enableProgramDateTime);
		enableId3ProgramDateTime = httpProps.getPropertyBoolean("cupertinoEnableId3ProgramDateTime", enableId3ProgramDateTime);
		enableId3ProgramDateTime = props.getPropertyBoolean("cupertinoEnableId3ProgramDateTime", enableId3ProgramDateTime);
		cupertinoProgramDateTimeOffset = httpProps.getPropertyLong("cupertinoProgramDateTimeOffset",
				cupertinoProgramDateTimeOffset);
		cupertinoProgramDateTimeOffset = props.getPropertyLong("cupertinoProgramDateTimeOffset", cupertinoProgramDateTimeOffset);
		WMSLoggerFactory.getLogger(PDTCupertinoLiveStreamPacketizerDataHandler.class)
				.info(MODULE_NAME + "[" + liveStreamPacketizer.getContextStr() + "] Running with cupertinoEnableProgramDateTime:" + enableProgramDateTime + " cupertinoEnableId3ProgramDateTime:" + enableId3ProgramDateTime + " cupertinoProgramDateTimeOffset:" + cupertinoProgramDateTimeOffset);

	}

	@Override
	public void onFillChunkStart(LiveStreamPacketizerCupertinoChunk chunk)
	{
		if (chunk != null && (enableProgramDateTime || enableId3ProgramDateTime))
		{
			// WSE 4.11+ stamps the chunk with the wall clock time it was created before this callback
			// fires. Keep that value when present; otherwise compute it the same way WSE does.
			String programDateTime = chunk.getProgramDateTime();
			if (programDateTime == null)
			{
				long createTime = System.currentTimeMillis() + cupertinoProgramDateTimeOffset;
				programDateTime = extDateString.format(new Date(createTime));

				//EXT-X-PROGRAM-DATE-TIME
				if (enableProgramDateTime)
					chunk.setProgramDateTime(programDateTime);
			}

			//ID3 Tag
			if (enableId3ProgramDateTime)
			{
				ID3Frames idsHeader = this.packetizer.getID3FramesHeader(chunk.getRendition());
				if (idsHeader != null)
				{
					ID3V2FrameTextInformationUserDefined comment = new ID3V2FrameTextInformationUserDefined();
					comment.setDescription("programDateTime");
					comment.setValue(programDateTime);
					idsHeader.putFrame(comment);
				}
			}
		}
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
		// no-op
	}

	@Override
	public void onFillChunkMediaPacket(LiveStreamPacketizerCupertinoChunk chunk, CupertinoPacketHolder holder, AMFPacket packet)
	{
		// no-op
	}

	public boolean isEnabled()
	{
		return this.enableId3ProgramDateTime || this.enableProgramDateTime;
	}

}
