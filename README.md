# Wowza Streaming Engine Metadata Injection Plugin

This module provides a REST API to add metadata to a live stream. This is done by injecting AMFData which can then be converted to ID3 tags.  A GUID is created for each event sent and returned in the HTTP POST request.

This module leverages the WSE classes:
* `HTTPProvider2Base`: support of using a REST API with engine
* `ModuleBase`: support for accessing the LiveStreamPacktizers
* `IHTTPStreamerCupertinoLivePacketizerDataHandler2` to access HLS/TS media segments to add AMFData, convert to ID3, and insert Program Date Time
* `IHTTPStreamerMPEGDashLivePacketizerDataHandler` to access CMAF (fMP4) media segments and deliver the same ID3 tags as `emsg` boxes

### Packetizer support

| Packetizer | Output | Delivery |
| ---------- | ------ | -------- |
| `cupertinostreamingpacketizer` (HLS/TS) | ID3v2 tags in the transport stream | Per-chunk ID3 header (PDT) and timed ID3 frames (AMF data) |
| `cmafstreamingpacketizer` (HLS/DASH fMP4) | ID3v2 tags wrapped in `emsg` boxes, scheme `https://aomedia.org/emsg/ID3` | One `emsg` per injected data event (`presentation_time` = packet timecode, timescale 1000) plus one `programDateTime` `emsg` at the start of each segment |

The CMAF `emsg` scheme follows the [ID3 Timed Metadata in CMAF](https://aomediacodec.github.io/id3-emsg/) spec and is recognised by HLS.js, dash.js, Shaka Player and Apple AVPlayer. The `amfToID3ConversionAddToManifest` option (`#EXT-X-METADATA-EVENT-*` chunklist tags) applies to the Cupertino packetizer only.

## Prerequisites

* Wowza Streaming Engine™ 4.9.4 or later is required

## Build instructions

1. Clone this repository to your local filesystem.
2. Run `./build.sh`  This will build the module/jar file with the `wse-plugin-builder` using docker

## Run the Demo

After building the module, start Wowza Streaming Engine and Wowza Streaming Engine Manager using the docker-compose.yaml file in this repository. It includes a pre-configured Wowza Streaming Engine instance and sample `live` and `simu-live` applications.

1. Run the following command to launch WSE and WSEM:

```bash
docker compose up
```

2. View the stream on the provided [sample player page](http://wse-trial.wowza.com:8088/id3/index.html?src=http://wse-trial.wowza.com/live/myStream/playlist.m3u8).  This will insert ID3 tags and show them as they are received on this HLS stream `http://wse-trial.wowza.com/live/myStream/playlist.m3u8`


## Install on existing WSE instance


* copy wse-plugin-metadata-injection jar to lib folder

### VHost.xml 
add HTTPProvider

```xml
<HTTPProvider>
	<BaseClass>com.wowza.wms.plugin.metadatainjection.HTTPProviderMetadataInjection</BaseClass>
	<RequestFilters>v1/server/plugin/metaDataInjection*</RequestFilters>
	<AuthenticationMethod>admin-basic</AuthenticationMethod>
	<PasswordEncodingScheme>none</PasswordEncodingScheme>
</HTTPProvider>
```  

add Property
						<Property>
							<Name>useMetadataApiKey</Name>
							<Value>true</Value>
						</Property>

add Property, update to either metadata-api-key or authorization
```
<Property>
	<Name>optionsCORSHeadersAddMain</Name>
	<Value>Access-Control-Allow-Headers:metadata-api-key</Value>
	<Type>String</Type>
</Property>	
```
Optional
```
<Property>
	<Name>metadataApiKey</Name>
	<Value>secretkey</Value>
	<Type>String</Type>
</Property>

```
### Application.xml
Need to turn on Program Date Time for HLS by adding the following module:
```
<Module>
	<Name>MetadataInjectionModule</Name>
	<Description>MetadataInjectionModule</Description>
	<Class>com.wowza.wms.plugin.metadatainjection.MetadataInjectionModule</Class>
</Module>
```
> [!Note]
> ### Deprecated class names (upgrading from 1.x)
> Version 2.0.0 moved the module and HTTP provider to the `com.wowza.wms.plugin.metadatainjection` package. The old class names still work but are deprecated and will be removed in a future major release. Update your configuration to the new names:
> | Deprecated class | Replacement | Config file |
> | ---------------- | ----------- | ----------- |
> | `com.wowza.wms.plugin.metadatainjection.module.ID3AndPDTInjectionModule` | `com.wowza.wms.plugin.metadatainjection.MetadataInjectionModule` | `Application.xml` |
> | `com.wowza.wms.plugin.metadatainjection.httpprovider.HTTPProviderMetadataInjection` | `com.wowza.wms.plugin.metadatainjection.HTTPProviderMetadataInjection` | `VHost.xml` |
> The deprecated classes are empty subclasses of their replacements, so behaviour is identical until they are removed.


### Properties:

| Name                  | Type                                           | Description                                                                      |
| -------------------- | ------------------------------------------------- | ---------------------------------------------------------------------------- |
| metadataApiKey             | String   | Can set a authorization key/header with Application.xml property.  This will require the api request to have a header `metadata-api-key`  Overrides property in vhost.xml if exists|
| amfToID3ConversionEnabled | Boolean | convert AMF data to ID3 data.  Default false |
| amfToID3ConversionAddToManifest | Boolean | Adds to HLS Manifest tag `#EXT-X-METADATA-EVENT-*` with guid of event. Default false |
| amfToID3ConversionVerboseMaximum | Integer | How many verbose log messges to log. Default 5| 
| amfToID3ConversionFailedMaximum | Integer | How many failed log messages to log. Default 5


### Properties (HTTPStreamer):
| Name                  | Type                                           | Description                                                                      |
| -------------------- | ------------------------------------------------- | ---------------------------------------------------------------------------- |
| cupertinoEnableProgramDateTime             | Boolean   | Built-in WSE property. Adds `EXT-X-PROGRAM-DATE-TIME` to HLS chunklists (TS and CMAF). Default false. Not required for the ID3 tags below. On WSE versions older than 4.11 this module sets the TS chunk PDT itself when this is enabled. [Wowza Documentation](https://www.wowza.com/docs/how-to-control-display-of-program-date-and-time-headers-in-hls-chunklists-for-live-streams-ext-x-program-date-time) |
| cupertinoEnableId3ProgramDateTime             | Boolean   | Add a `programDateTime` ID3 tag to the ID3 header of each HLS/TS chunk. Default true. The value is the wall clock time the chunk was created and matches the chunk's `EXT-X-PROGRAM-DATE-TIME`; on WSE 4.11+ the server's value is reused as-is. |
| cupertinoProgramDateTimeOffset | Integer | How much to adjust the HLS/TS PDT in milliseconds. Default 0. Only applied when this module computes the PDT itself (WSE older than 4.11); ignored when the server has already set the chunk PDT. |
| cmafEnableId3ProgramDateTime             | Boolean   | Add a `programDateTime` ID3 tag (as an `emsg`) at the start of each CMAF segment. Defaults to the value of `cupertinoEnableId3ProgramDateTime` (true). The value is the wall clock time the segment was created, matching how WSE calculates `EXT-X-PROGRAM-DATE-TIME`; on WSE 4.11+ the server's value is reused as-is. |
| cmafProgramDateTimeOffset | Integer | How much to adjust the CMAF ID3 PDT in milliseconds. Defaults to the value of `cupertinoProgramDateTimeOffset` (0). Only applied when this module computes the time itself; ignored if the packetizer already exposes the segment's native time. |

### Properties (LiveStreamPacketizer):
| Name                  | Type                                           | Description                                                                      |
| -------------------- | ------------------------------------------------- | ---------------------------------------------------------------------------- |
| cmafDataEventsTrackType | String | Built-in WSE property selecting which CMAF track carries `emsg` boxes: `audio` or `video`. WSE defaults to `audio`; this module changes the default to `video` when the property is not set in Application.xml. Set it explicitly under `<Application>/<LiveStreamPacketizer>/<Properties>` to override. audio-only CMAF outputs must set this back to audio |


## API
### API patterns is
* `v1/server/plugin/metaDataInjection/applications/{appName}/streams/{streamName}`
* opitional header value `metadata-api-key` for authorization

### API supports methods/verbs
`GET | POST `

### Payload (JSON)

#### Properties
| Property      | Description                                              |
|:--------------|:---------------------------------------------------------|
| event       | Name of the data event to be triggeed          |
| async   | Return right away from api call (default false)          |
| delay       | Delay injection by seconds (default 0)                                 |
| repeat            | How many times to inject data   (default 1) |
| repeatInterval            | Time between repeated events  (default 0)|
| injectTime             | Include current time in metadata json object (default false)   |
|      id3        | Conver to ID3 tag (default false)|
| data| The json object to be sent|



## curl Examples

```
curl -X GET http://127.0.0.1/v1/server/plugin/metaDataInjection/version
```

```shell
curl -X POST  -H "Content-Type: application/json" -H "metadata-api-key:secretkey" -d '{
	"event": "dataTest",
	"async": true,
	"delay": 5000,
	"repeat": 3,
	"repeatInterval": 500,
	"injectTime": true,
	"id3":true,
	"data": {
		"stringTest": "apple",
		"objsTest": {
			"name": "Gpa",
			"parent": {
				"name": "Mom",
				"children": [
					{"name":"Boy"},
					{"name":"Girl"}
				]
			}
		},
		"intTest":1,
		"boolTest":true,
		"doubleTest":1.123,
		"bitIntTest":12345678901234567890,
		"longTest":1234567890123, 
		"array1Test" : [1,2,3], 
		"array2Test" : [{ "q1" : "one" },{ "q2" : "two" }]
	}}' http://127.0.0.1/v1/server/plugin/metaDataInjection/applications/live/streams/mystream
```

```
curl -X GET http://127.0.0.1/v1/server/plugin/metaDataInjection/injections/{guid}
```