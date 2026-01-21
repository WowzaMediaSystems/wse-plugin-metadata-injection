package com.wowza.wms.plugin.metadatainjection.module;

import com.wowza.wms.plugin.metadatainjection.*;
import com.wowza.wms.plugin.metadatainjection.datahandler.*;
import com.wowza.wms.plugin.metadatainjection.httpprovider.*;
import com.wowza.wms.plugin.metadatainjection.datahandler.pdt.*;
import com.wowza.wms.plugin.metadatainjection.datahandler.id3.*;

import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.amf.AMFPacket;
import com.wowza.wms.httpstreamer.cupertinostreaming.livestreampacketizer.*;
import com.wowza.wms.media.mp3.model.idtags.ID3Frames;
import com.wowza.wms.module.ModuleBase;
import com.wowza.wms.stream.livepacketizer.*;

public class ID3AndPDTInjectionModule extends ModuleBase
{
	public static final Class<ID3AndPDTInjectionModule> CLASS = ID3AndPDTInjectionModule.class;
	public static String MODULE_NAME = CLASS.getSimpleName();
	public static final String MODULE_VERSION = ReleaseInfo.getVersion();

	private static AMFToID3ApplicationsManager appsManager = new AMFToID3ApplicationsManager();
	private LiveStreamPacketizerListener listener;

	private IApplicationInstance appInstance;

	class LiveStreamPacketizerDataHandler implements IHTTPStreamerCupertinoLivePacketizerDataHandler2
	{
		private LiveStreamPacketizerCupertino packetizer = null;
		private String streamName = null;
		private IHTTPStreamerCupertinoLivePacketizerMultiDataHandler pdt = null;
		private IHTTPStreamerCupertinoLivePacketizerMultiDataHandler amfToID3 = null;

		public LiveStreamPacketizerDataHandler(LiveStreamPacketizerCupertino packetizer, String streamName)
		{
			this.packetizer = packetizer;
			this.streamName = streamName;

			pdt = new PDTLiveStreamPacketizerDataHandler(appInstance, packetizer, streamName);
			amfToID3 = new AMFToID3LiveStreamPacketizerDataHandler(appInstance, packetizer, streamName);
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
			if (packetizer instanceof LiveStreamPacketizerCupertino)
			{
				((LiveStreamPacketizerCupertino)packetizer).setDataHandler(
						new LiveStreamPacketizerDataHandler((LiveStreamPacketizerCupertino)packetizer, streamName));
				getLogger().info(
						"ID3AndPDTInjectionModule#LiveStreamPacketizerListener.onLiveStreamPacketizerCreate[" + ((LiveStreamPacketizerCupertino)packetizer).getContextStr() + "]");
			}
		}

		@Override
		public void onLiveStreamPacketizerDestroy(ILiveStreamPacketizer liveStreamPacketizer)
		{
			String streamName = liveStreamPacketizer.getProperties()
					.getPropertyStr("ID3AndPDTInjectionModule.streamName");

			appsManager.removeController(appInstance, streamName);
			if (liveStreamPacketizer instanceof LiveStreamPacketizerCupertino)
			{
				getLogger().info(
						"ID3AndPDTInjectionModule#LiveStreamPacketizerListener.onLiveStreamPacketizerDestroy[" + ((LiveStreamPacketizerCupertino)liveStreamPacketizer).getContextStr() + "]");
			}

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
