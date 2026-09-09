package com.wowza.wms.plugin.metadatainjection.module;

import com.wowza.wms.amf.AMFPacket;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.httpstreamer.cmafstreaming.livestreampacketizer.LiveStreamPacketizerCmaf;
import com.wowza.wms.httpstreamer.cupertinostreaming.livestreampacketizer.CupertinoPacketHolder;
import com.wowza.wms.httpstreamer.cupertinostreaming.livestreampacketizer.IHTTPStreamerCupertinoLivePacketizerDataHandler2;
import com.wowza.wms.httpstreamer.cupertinostreaming.livestreampacketizer.LiveStreamPacketizerCupertino;
import com.wowza.wms.httpstreamer.cupertinostreaming.livestreampacketizer.LiveStreamPacketizerCupertinoChunk;
import com.wowza.wms.httpstreamer.model.LiveStreamPacketizerPacketHolder;
import com.wowza.wms.httpstreamer.mpegdashstreaming.file.InbandEventStreams;
import com.wowza.wms.httpstreamer.mpegdashstreaming.livestreampacketizer.IHTTPStreamerMPEGDashLivePacketizerDataHandler;
import com.wowza.wms.media.mp3.model.idtags.ID3Frames;
import com.wowza.wms.module.ModuleBase;
import com.wowza.wms.plugin.metadatainjection.ReleaseInfo;
import com.wowza.wms.plugin.metadatainjection.datahandler.cmaf.AMFToID3CmafLiveStreamPacketizerDataHandler;
import com.wowza.wms.plugin.metadatainjection.datahandler.cmaf.IHTTPStreamerMPEGDashLivePacketizerMultiDataHandler;
import com.wowza.wms.plugin.metadatainjection.datahandler.cmaf.PDTCmafLiveStreamPacketizerDataHandler;
import com.wowza.wms.plugin.metadatainjection.datahandler.cupertino.AMFToID3CupertinoLiveStreamPacketizerDataHandler;
import com.wowza.wms.plugin.metadatainjection.datahandler.cupertino.IHTTPStreamerCupertinoLivePacketizerMultiDataHandler;
import com.wowza.wms.plugin.metadatainjection.datahandler.cupertino.PDTCupertinoLiveStreamPacketizerDataHandler;
import com.wowza.wms.plugin.metadatainjection.datahandler.id3.AMFToID3ApplicationsManager;
import com.wowza.wms.plugin.metadatainjection.datahandler.id3.AMFToID3ConverterStreamController;
import com.wowza.wms.stream.livepacketizer.ILiveStreamPacketizer;
import com.wowza.wms.stream.livepacketizer.LiveStreamPacketizerActionNotifyBase;

/**
 * Attaches ID3 and program-date-time data handlers to the live stream packetizers of an application.
 * <ul>
 * <li>Cupertino (HLS/TS): ID3 tags written into the transport stream.</li>
 * <li>CMAF (HLS/DASH fMP4): ID3 tags wrapped in emsg boxes (scheme https://aomedia.org/emsg/ID3).</li>
 * </ul>
 */
public class ID3AndPDTInjectionModule extends ModuleBase
{
	public static final Class<ID3AndPDTInjectionModule> CLASS = ID3AndPDTInjectionModule.class;
	public static String MODULE_NAME = CLASS.getSimpleName();
	public static final String MODULE_VERSION = ReleaseInfo.getVersion();

	public static final String PROPNAME_STREAM_NAME = "ID3AndPDTInjectionModule.streamName";
	public static final String PROPNAME_DATA_HANDLER = "ID3AndPDTInjectionModule.dataHandler";

	private static AMFToID3ApplicationsManager appsManager = AMFToID3ApplicationsManager.getAppsManager();
	private LiveStreamPacketizerListener listener;

	private IApplicationInstance appInstance;

	/**
	 * Composite handler for the Cupertino (HLS/TS) packetizer.
	 */
	class LiveStreamPacketizerDataHandler implements IHTTPStreamerCupertinoLivePacketizerDataHandler2
	{
		private LiveStreamPacketizerCupertino packetizer = null;
		private IHTTPStreamerCupertinoLivePacketizerMultiDataHandler pdt = null;
		private AMFToID3CupertinoLiveStreamPacketizerDataHandler amfToID3 = null;

		public LiveStreamPacketizerDataHandler(LiveStreamPacketizerCupertino packetizer, String streamName)
		{
			this.packetizer = packetizer;

			pdt = new PDTCupertinoLiveStreamPacketizerDataHandler(appInstance, packetizer, streamName);
			amfToID3 = new AMFToID3CupertinoLiveStreamPacketizerDataHandler(appInstance, packetizer, streamName);
		}

		public void dispose()
		{
			if (amfToID3 != null)
				amfToID3.dispose();
		}

		@Override
		public void onFillChunkStart(LiveStreamPacketizerCupertinoChunk chunk)
		{
			ID3Frames idsHeader = this.packetizer.getID3FramesHeader(chunk.getRendition());
			if (idsHeader != null)
			{
				idsHeader.clear();
			}

			if (pdt != null && pdt.isEnabled())
				pdt.onFillChunkStart(chunk);
			if (amfToID3 != null && amfToID3.isEnabled())
				amfToID3.onFillChunkStart(chunk);

		}

		@Override
		public void onFillChunkEnd(LiveStreamPacketizerCupertinoChunk chunk, long timecode)
		{
			if (pdt != null && pdt.isEnabled())
				pdt.onFillChunkEnd(chunk, timecode);
			if (amfToID3 != null && amfToID3.isEnabled())
				amfToID3.onFillChunkEnd(chunk, timecode);

		}

		@Override
		public void onFillChunkMediaPacket(LiveStreamPacketizerCupertinoChunk chunk, CupertinoPacketHolder holder,
				AMFPacket packet)
		{
			if (pdt != null && pdt.isEnabled())
				pdt.onFillChunkMediaPacket(chunk, holder, packet);
			if (amfToID3 != null && amfToID3.isEnabled())
				amfToID3.onFillChunkMediaPacket(chunk, holder, packet);

		}

		@Override
		public void onFillChunkDataPacket(LiveStreamPacketizerCupertinoChunk chunk, CupertinoPacketHolder holder,
				AMFPacket packet, ID3Frames id3Frames)
		{
			if (pdt != null && pdt.isEnabled())
				pdt.onFillChunkDataPacket(chunk, holder, packet, id3Frames);
			if (amfToID3 != null && amfToID3.isEnabled())
				amfToID3.onFillChunkDataPacket(chunk, holder, packet, id3Frames);

		}
	}

	/**
	 * Composite handler for the CMAF packetizer. ID3 frames are delivered as emsg boxes in each segment.
	 */
	class CmafLiveStreamPacketizerDataHandler implements IHTTPStreamerMPEGDashLivePacketizerDataHandler
	{
		private IHTTPStreamerMPEGDashLivePacketizerMultiDataHandler pdt = null;
		private AMFToID3CmafLiveStreamPacketizerDataHandler amfToID3 = null;

		public CmafLiveStreamPacketizerDataHandler(LiveStreamPacketizerCmaf packetizer, String streamName)
		{
			pdt = new PDTCmafLiveStreamPacketizerDataHandler(appInstance, packetizer, streamName);
			amfToID3 = new AMFToID3CmafLiveStreamPacketizerDataHandler(appInstance, packetizer, streamName);
		}

		public void dispose()
		{
			if (amfToID3 != null)
				amfToID3.dispose();
		}

		@Override
		public void onFillSegmentStart(long startTimecode, long endTimecode, InbandEventStreams inbandEventStreams)
		{
			if (pdt != null && pdt.isEnabled())
				pdt.onFillSegmentStart(startTimecode, endTimecode, inbandEventStreams);
			if (amfToID3 != null && amfToID3.isEnabled())
				amfToID3.onFillSegmentStart(startTimecode, endTimecode, inbandEventStreams);
		}

		@Override
		public void onFillSegmentEnd(long startTimecode, long endTimecode, InbandEventStreams inbandEventStreams)
		{
			if (pdt != null && pdt.isEnabled())
				pdt.onFillSegmentEnd(startTimecode, endTimecode, inbandEventStreams);
			if (amfToID3 != null && amfToID3.isEnabled())
				amfToID3.onFillSegmentEnd(startTimecode, endTimecode, inbandEventStreams);
		}

		@Override
		public void onFillSegmentDataPacket(LiveStreamPacketizerPacketHolder holder, AMFPacket packet, InbandEventStreams inbandEventStreams)
		{
			if (pdt != null && pdt.isEnabled())
				pdt.onFillSegmentDataPacket(holder, packet, inbandEventStreams);
			if (amfToID3 != null && amfToID3.isEnabled())
				amfToID3.onFillSegmentDataPacket(holder, packet, inbandEventStreams);
		}

		@Override
		public void onFillSegmentMediaPacket(LiveStreamPacketizerPacketHolder holder, AMFPacket packet)
		{
			if (pdt != null && pdt.isEnabled())
				pdt.onFillSegmentMediaPacket(holder, packet);
			if (amfToID3 != null && amfToID3.isEnabled())
				amfToID3.onFillSegmentMediaPacket(holder, packet);
		}
	}

	class LiveStreamPacketizerListener extends LiveStreamPacketizerActionNotifyBase
	{
		IApplicationInstance appInstance = null;

		public LiveStreamPacketizerListener(IApplicationInstance appInstance)
		{
			this.appInstance = appInstance;
		}

		@Override
		public void onLiveStreamPacketizerCreate(ILiveStreamPacketizer packetizer, String streamName)
		{
			if (packetizer instanceof LiveStreamPacketizerCupertino cupertino)
			{
				LiveStreamPacketizerDataHandler handler = new LiveStreamPacketizerDataHandler(cupertino, streamName);
				cupertino.getProperties().setProperty(PROPNAME_STREAM_NAME, streamName);
				cupertino.getProperties().setProperty(PROPNAME_DATA_HANDLER, handler);
				cupertino.setDataHandler(handler);
				getLogger().info(MODULE_NAME + "#LiveStreamPacketizerListener.onLiveStreamPacketizerCreate[" + cupertino.getContextStr() + "] cupertino");
			}
			else if (packetizer instanceof LiveStreamPacketizerCmaf cmaf)
			{
				CmafLiveStreamPacketizerDataHandler handler = new CmafLiveStreamPacketizerDataHandler(cmaf, streamName);
				cmaf.getProperties().setProperty(PROPNAME_STREAM_NAME, streamName);
				cmaf.getProperties().setProperty(PROPNAME_DATA_HANDLER, handler);
				cmaf.setDataHandler(handler);
				getLogger().info(MODULE_NAME + "#LiveStreamPacketizerListener.onLiveStreamPacketizerCreate[" + cmaf.getContextStr() + "] cmaf");
			}
		}

		@Override
		public void onLiveStreamPacketizerDestroy(ILiveStreamPacketizer liveStreamPacketizer)
		{
			String streamName = liveStreamPacketizer.getProperties().getPropertyStr(PROPNAME_STREAM_NAME);
			Object handler = liveStreamPacketizer.getProperties().getProperty(PROPNAME_DATA_HANDLER);

			if (handler instanceof LiveStreamPacketizerDataHandler cupertinoHandler)
				cupertinoHandler.dispose();
			else if (handler instanceof CmafLiveStreamPacketizerDataHandler cmafHandler)
				cmafHandler.dispose();

			// Only drop the controller once no packetizer (cupertino or cmaf) for this stream references it
			if (streamName != null)
			{
				AMFToID3ConverterStreamController controller = appsManager.getController(appInstance, streamName);
				if (controller == null || !controller.hasDataHandlers())
					appsManager.removeController(appInstance, streamName);
			}

			if (liveStreamPacketizer instanceof LiveStreamPacketizerCupertino cupertino)
				getLogger().info(MODULE_NAME + "#LiveStreamPacketizerListener.onLiveStreamPacketizerDestroy[" + cupertino.getContextStr() + "] cupertino");
			else if (liveStreamPacketizer instanceof LiveStreamPacketizerCmaf cmaf)
				getLogger().info(MODULE_NAME + "#LiveStreamPacketizerListener.onLiveStreamPacketizerDestroy[" + cmaf.getContextStr() + "] cmaf");

			super.onLiveStreamPacketizerDestroy(liveStreamPacketizer);
		}

	}

	public void onAppStart(IApplicationInstance appInstance)
	{
		this.appInstance = appInstance;
		getLogger().info(
				"ID3AndPDTInjectionModule.onAppStart[" + appInstance.getContextStr() + "] MetadataInjection v" + MODULE_VERSION);

		listener = new LiveStreamPacketizerListener(appInstance);
		appInstance.addLiveStreamPacketizerListener(listener);
	}

	public void onAppStop(IApplicationInstance appInstance)
	{
		appInstance.removeLiveStreamPacketizerListener(listener);

		getLogger().info("ID3AndPDTInjectionModule.onAppStop[" + appInstance.getContextStr() + "]");

	}

}
