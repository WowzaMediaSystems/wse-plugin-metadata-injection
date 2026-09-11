package com.wowza.wms.plugin.metadatainjection.datahandler.cmaf;

import com.wowza.wms.httpstreamer.mpegdashstreaming.file.InbandEventStream;
import com.wowza.wms.httpstreamer.mpegdashstreaming.file.InbandEventStreams;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.media.metadata.emsg.EmsgBuilder;
import com.wowza.wms.media.metadata.emsg.EmsgFrames;
import com.wowza.wms.media.metadata.emsg.IEmsgFrame;
import com.wowza.wms.media.mp3.model.idtags.ID3Frames;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wraps serialized ID3v2 tags in emsg (DASH Event Message) boxes for CMAF segments, following
 * the "ID3 Timed Metadata in CMAF" scheme ({@value #ID3_EMSG_SCHEME_URI}) that HLS.js, dash.js,
 * Shaka and Apple players recognise.
 *
 * See https://aomediacodec.github.io/id3-emsg/
 * <p>
 * Players such as dash.js de-duplicate emsg boxes on (scheme_id_uri, value, id), so every emsg
 * emitted under this scheme for a given packetizer must carry a unique id. Share a single
 * instance between all handlers attached to the same packetizer so they draw from one counter.
 */
public class ID3EmsgUtils
{
	public static final String ID3_EMSG_SCHEME_URI = "https://aomedia.org/emsg/ID3";
	public static final String ID3_EMSG_SCHEME_VALUE = "";
	public static final int ID3_EMSG_VERSION = IEmsgFrame.VERSION_1;
	public static final int ID3_EMSG_TIMESCALE = 1000; // WSE packetizer timecodes are in milliseconds
	public static final int ID3_EMSG_DURATION = 0;

	private final String contextStr;
	private final AtomicInteger nextId = new AtomicInteger(0);

	public ID3EmsgUtils(String contextStr)
	{
		this.contextStr = contextStr;
	}

	/**
	 * Register the ID3 inband event stream on the given set so the packetizer advertises it
	 * (e.g. an InbandEventStream element in a DASH MPD).
	 * <p>
	 * A CMAF packetizer owns one {@link InbandEventStreams} per writer handler (audio-only,
	 * video-only, separate tracks), so this must be evaluated for every set passed in rather than
	 * once per packetizer. The registered-check makes repeated calls on the same set a no-op.
	 */
	public void registerEventStream(InbandEventStreams inbandEventStreams)
	{
		if (inbandEventStreams == null)
			return;

		if (inbandEventStreams.getRegisteredEventStream(ID3_EMSG_SCHEME_URI) == null)
			inbandEventStreams.registerEventStream(new InbandEventStream(ID3_EMSG_SCHEME_URI, ID3_EMSG_SCHEME_VALUE));
	}

	/**
	 * Serialize the ID3 frames and add them to the segment as an emsg box.
	 *
	 * @param inbandEventStreams the segment's inband event streams supplied by the packetizer
	 * @param id3Frames          frames to serialize (ID3v2.4, sync-safe sizes, no footer)
	 * @param timecode           presentation time in milliseconds
	 * @return the emsg frame added, or null if nothing was added
	 */
	public IEmsgFrame addID3Frames(InbandEventStreams inbandEventStreams, ID3Frames id3Frames, long timecode)
	{
		if (inbandEventStreams == null || id3Frames == null || id3Frames.getFrames().isEmpty())
			return null;

		registerEventStream(inbandEventStreams);

		EmsgFrames emsgFrames = inbandEventStreams.getEmsgFrames();
		if (emsgFrames == null)
		{
			WMSLoggerFactory.getLogger(ID3EmsgUtils.class).warn("ID3EmsgUtils.addID3Frames[" + contextStr + "]: no EmsgFrames available on segment");
			return null;
		}

		byte[] id3Bytes = id3Frames.serialize(true, false, ID3Frames.ID3HEADERFLAGS_DEFAULT);
		// EmsgBuilder takes the message payload as a String and serializes it back to bytes; the ID3v2.4
		// header and frame headers are 7-bit sync-safe and the TXXX text is UTF-8, so this round-trips.
		String id3String = new String(id3Bytes, StandardCharsets.UTF_8);

		IEmsgFrame emsg = new EmsgBuilder(ID3_EMSG_VERSION, ID3_EMSG_SCHEME_URI, ID3_EMSG_SCHEME_VALUE)
				.setId(nextId.getAndIncrement())
				.setTimescale(ID3_EMSG_TIMESCALE)
				.setTime(timecode)
				.setEventDuration(ID3_EMSG_DURATION)
				.setMessage(id3String)
				.build();

		emsgFrames.addFrame(emsg);
		return emsg;
	}

}
