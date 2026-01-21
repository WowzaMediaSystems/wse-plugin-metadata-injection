package com.wowza.wms.plugin.metadatainjection.module.pdt;

import com.wowza.util.*;
import com.wowza.wms.amf.AMFPacket;
import com.wowza.wms.application.*;
import com.wowza.wms.httpstreamer.cupertinostreaming.livestreampacketizer.*;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.media.mp3.model.idtags.*;
import com.wowza.wms.plugin.metadatainjection.module.IHTTPStreamerCupertinoLivePacketizerMultiDataHandler;
import com.wowza.wms.stream.IMediaStream;
import org.apache.commons.lang.time.FastDateFormat;

import java.util.*;

public class PDTLiveStreamPacketizerDataHandler implements IHTTPStreamerCupertinoLivePacketizerMultiDataHandler
{

	public static final String MODULE_NAME = "ModuleCupertinoProgramDateTime";
	public static final String PROPNAME_TRACKER = "ModuleCupertinoProgramDateTime.ProgramDateTimeTracker";
	public static final String EXTDATEFORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"; // The date/time representation is ISO/IEC 8601:2004 - 2010-02-19T14:54:23.031+08:00
	public static final String ID3DATEFORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"; // The date/time representation is ISO/IEC 8601:2004 - 2010-02-19T14:54:23.031+08:00

	private IApplicationInstance appInstance = null;
	private FastDateFormat extDateString = FastDateFormat.getInstance(EXTDATEFORMAT, SystemUtils.gmtTimeZone, Locale.US);
	private FastDateFormat id3DateString = FastDateFormat.getInstance(ID3DATEFORMAT, SystemUtils.gmtTimeZone, Locale.US);

	private boolean enableProgramDateTime = false;
	private boolean enableId3ProgramDateTime = false;
	private long cupertinoProgramDateTimeOffset = 0;
	private String streamName = null;
	private LiveStreamPacketizerCupertino packetizer = null;

	public PDTLiveStreamPacketizerDataHandler(IApplicationInstance appInstance,
			LiveStreamPacketizerCupertino liveStreamPacketizer, String streamName)
	{
		this.appInstance = appInstance;
		this.streamName = streamName;
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
		WMSLoggerFactory.getLogger(PDTLiveStreamPacketizerDataHandler.class)
				.info(MODULE_NAME + "Running with cupertinoEnableProgramDateTime:" + enableProgramDateTime + " cupertinoEnableId3ProgramDateTime:" + enableId3ProgramDateTime);

	}

	public ProgramDateTimeTracker getTracker()
	{
		ProgramDateTimeTracker tracker = null;
		while (true)
		{
			IMediaStream stream = appInstance.getStreams().getStream(this.streamName);
			if (stream == null)
				break;

			WMSProperties props = stream.getProperties();

			synchronized(props)
			{
				tracker = (ProgramDateTimeTracker)props.getProperty(PROPNAME_TRACKER);
				if (tracker == null)
				{
					tracker = new ProgramDateTimeTracker(stream, cupertinoProgramDateTimeOffset);
					props.put(PROPNAME_TRACKER, tracker);
				}
			}
			break;
		}

		return tracker;
	}

	@Override
	public void onFillChunkStart(LiveStreamPacketizerCupertinoChunk chunk)
	{
		if (chunk != null && (enableProgramDateTime || enableId3ProgramDateTime))
		{
			ProgramDateTimeTracker tracker = getTracker();
			if (tracker != null)
			{
				ElapsedTimer elapsedTime = tracker.stream.getElapsedTime();

				long createTime = elapsedTime.getDate().getTime() + tracker.timeOffset;

				//EXT-X-PROGRAM-DATE-TIME
				if (enableProgramDateTime)
				{
					String extProgramDateTimeStr = extDateString.format(new Date(createTime));
					chunk.setProgramDateTime(extProgramDateTimeStr);
				}

				//ID3 Tag
				if (enableId3ProgramDateTime)
				{
					ID3Frames idsHeader = this.packetizer.getID3FramesHeader(chunk.getRendition());
					if (idsHeader != null)
					{
						ID3V2FrameTextInformationUserDefined comment = new ID3V2FrameTextInformationUserDefined();
						comment.setDescription("programDateTime");
						String id3ProgramDateTimeStr = id3DateString.format(new Date(createTime));
						comment.setValue(id3ProgramDateTimeStr);
						idsHeader.putFrame(comment);
					}
				}
			}
		}
	}

	@Override
	public void onFillChunkEnd(LiveStreamPacketizerCupertinoChunk chunk, long timecode)
	{
		if (chunk != null && (enableProgramDateTime || enableId3ProgramDateTime))
		{
			ProgramDateTimeTracker tracker = getTracker();
			if (tracker != null)
			{
				tracker.timeOffset += chunk.getDuration();
			}
		}
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

	class ProgramDateTimeTracker
	{
		long timeOffset = 0;
		IMediaStream stream = null;

		public ProgramDateTimeTracker(IMediaStream stream, long timeOffset)
		{
			this.stream = stream;
			this.timeOffset = timeOffset;
		}
	}

	@Override
	public boolean isEnabled()
	{
		return this.enableId3ProgramDateTime || this.enableProgramDateTime;
	}

}
