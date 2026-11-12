# TalkCtrl protocol notes

What TalkCtrl knows about how Cardo units (Packtalk, Freecom X, Spirit and related "CAIP64" units) are
controlled, and where each fact comes from. Everything here was read from the Cardo Connect Android app
(`com.cardo.smartset`) for interoperability with your own unit. Only class and method names are given;
no Cardo code is copied. TalkCtrl's implementation is written from these notes.

Marks:

- **verified-in-code**: read directly in the named class/method (byte layout, constant or call order).
- **inferred**: deduced from surrounding code, a constant sequence or the standard Qualcomm protocol, but
  not visible as a single clear piece of code. TalkCtrl either keeps these off ("Needs confirming on your
  unit") or says so in the UI.

Nothing in this file has been tested against a real unit yet.

## 1. Transport

| Item | Value | Where | Status |
|---|---|---|---|
| CAIP GATT service (classic+BLE units) | `CD007F80-8B0B-11E6-AE22-56B6B6499611` | `caip64_bluetooth.ble.UUIDService` (CAIP_UUID) | verified-in-code |
| CAIP GATT service on BLE-only units | `CD007F83-8B0B-11E6-AE22-56B6B6499611` | `UUIDService` (CAIP_UUID_BLE_ONLY); `BLEService.updateCachedDeviceByUUID` uses it to mark a unit "BLE only" | verified-in-code |
| Write characteristic (phone to unit) | `CD007F81-8B0B-11E6-AE22-56B6B6499611` | `UUIDService` (UUID_WRITE), `BLEService.sendMessageToDevice` (writeCharacteristic) | verified-in-code |
| Notify characteristic (unit to phone) | `CD007F82-8B0B-11E6-AE22-56B6B6499611` | `UUIDService` (UUID_READ), `BLEService.setupNotification` | verified-in-code |
| MTU requested | 64 | `BLEService` (MTU_SIZE), `setupNotification` (requestMtu) | verified-in-code |
| One notification = one message | yes; each notification is handed to the parser as is | `BLEService.setupNotification$lambda$4$0`, `CAIP64MessagesHandler.handleReadMessages` | verified-in-code |
| One write = one message, no extra header or checksum on BLE | yes; `byteArrayToSend()` is written unchanged | `BLEService.sendMessageToDevice`, `CAIP64MessagesHandler.sendPacket` | verified-in-code |
| Write retry | one retry after 1 s on failure | `BLEService.sendMessageToDevice` | verified-in-code |
| Reconnect | retry every 5 s | `BLEService.connect$lambda$3$0` | verified-in-code |
| RFCOMM fallback socket | insecure RFCOMM; the app first tries the hidden `createRfcommSocket(12)` (channel 12), else `createInsecureRfcommSocketToServiceRecord(CD007F85-8B0B-11E6-AE22-56B6B6499611)`; waits 5 s before connecting | `caip64_bluetooth.BluetoothService$ConnectThread` | verified-in-code |
| RFCOMM message splitting | a read is split into messages of `data[i+1] + 3` bytes (byte 1 read as a length) | `BluetoothService$ConnectedThread.checkForMultipleMessages` | verified-in-code (rule) / inferred (meaning: service messages have no length byte, so this rule does not fit every message) |
| Which transport is used | BLE when the unit offers it, classic RFCOMM otherwise | `bluetooth.CAIPConnectionHandler` (chatServiceBLE / chatServiceClassic) | verified-in-code |
| How the app finds the unit | bonded or A2DP-connected devices whose address starts with `00:0A:9B`, `00:02:5B` or `F8:23:87` | `caip64_bluetooth.DeviceConnection` (allowedAddresses, isCardoDevice) | verified-in-code |

TalkCtrl uses BLE (GATT) by default and offers RFCOMM (public UUID API only, no hidden channel-12 call) as
a fallback in Settings, marked "Needs confirming on your unit".

## 2. CAIP64 framing

All multi-byte values are big-endian.

| Message kind | Layout | Where | Status |
|---|---|---|---|
| Plain packet | `[opcode][payload…]` | `caip64_bluetooth.packet.BluetoothPacket.byteArrayToSend` | verified-in-code |
| Control (event) packet | `[0x30][event hi][event lo][extra length][extra…]` | `packet.BluetoothPacketControl.byteArrayToSend` | verified-in-code |
| Get config | `[0x00][count][type id…]` | `settings.messages.GetConfigParams.getPayloadData` | verified-in-code |
| Set config | `[0x10][total length][record…]`; a record is `[type id][data…]` | `settings.messages.SetParamsConfigs.getPayloadData`, `settings.ConfigsParam.dataToSend` | verified-in-code |
| Config update from unit | `[0x40][length][record…]`, records parsed from offset 2, each record's size fixed per type | `services.UpdateConfigParameters` (constructor) | verified-in-code |
| Service updates from unit | `[opcode][fields…]` (no length byte) | `services.UpdateBatteryService`, `UpdateStateService`, `UpdateMusicSharingService` | verified-in-code |
| Opcode position | byte 0 | `CAIP64MessagesHandler.handleReadMessages` (OPCODE_OFFSET is 0) | verified-in-code |

## 3. Opcodes

From `caip64_bluetooth.packet.Opcode` (verified-in-code unless marked). Some values were shown by the
decompiler as named constants of other classes; those were resolved from the constant's definition
(`OpCodes.Enum`) or, where marked, from the surrounding sequence.

| Opcode | Name | Use in TalkCtrl |
|---|---|---|
| 0x00 | GET_PARAM | read config |
| 0x01 | GET_SPEED_DIAL | read speed dial |
| 0x02 | GET_SN | read serial |
| 0x03 | GET_VER | first message after connecting, payload `00 00` (`settings.messages.GetVersion`) |
| 0x04 | GET_FRIENDLY_NAME | read name |
| 0x10 | SET_PARAM | write config |
| 0x11 | SET_SPEED_DIAL | `[0x11][len][ASCII digits]` (`SetSpeedDial`) |
| 0x12 | SET_FRIENDLY_NAME | `[0x12][len][UTF-8]` (`SetFriendlyName`) |
| 0x21 | SET_PSKEY | not used (value from `OpCodes.Enum.UPGRADE_SILENT_COMMIT_SUPPORTED_CFM` = 33) |
| 0x22 | SERVICE_SET (subscribe) | `[0x22][agnostic flags][specific hi][specific lo]` (`services.SubscribeToServices`); value from `OpCodes.Enum.UPGRADE_SILENT_COMMIT_CFM` = 34 |
| 0x30 | CONTROL | events, see section 5 (value `TarConstants.LF_NORMAL` = '0') — inferred from the constant name, consistent with the event packets |
| 0x31 | CONNECTIVITY_ACK_REQ | not used (inferred, '1') |
| 0x40 | UPDATE_PARAM | config records from the unit |
| 0x41 | UPDATE_SPEED_DIAL | `[0x41][len][digits]` (`UpdateSpeedDial`) |
| 0x42 | UPDATE_FRIENDLY_NAME | `[0x42][len][UTF-8]` (`UpdateFriendlyName`); 0x42 inferred from the sequence 0x40…0x48 |
| 0x43 | UPDATE_SERIAL_NUMBER | `[0x43][len][serial ASCII][Bluetooth address…]` (`UpdateSerialNumber`) |
| 0x50 | SRVC_STATE | `[0x50][state][call bits][FM bits][freq hi][freq lo][DMC bits]…`: FM state = (byte3 & 0xF0) >> 4, preset index = byte3 & 0x0F, frequency = word / 100 MHz (`UpdateStateService`) |
| 0x51 | SRVC_BATTERY | `[0x51][charging(bit7) + percent(bits 6-0)][side]` (`UpdateBatteryService`) |
| 0x69 | SRVC_MUSIC_SHARING | `[0x69][state bits 2-0: 0 idle, 1 outgoing, 2 incoming]` (`UpdateMusicSharingService`) |
| 0xA2 | OTA_START | "device ready to update" request, no payload (`settings.messages.DeviceReadyToUpdate`) |
| 0xA3 | OTA_READY | unit's answer (OTAState.READY_FOR_UPDATE in `DeviceReadyToUpdateOperation`, awaited by `SettingsUpdatingFWViewModel.prepareUpgrade`) |
| 0xD1…0xDC | DMC/intercom extended messages | not used (sequence numbers and member address lists; see section 7) |
| 0xB0…0xBF | crash detection | out of scope |

Connection sequence (`bluetooth.CAIPConnectionHandler.stateConnected`, `CAIP64MessagesHandler.handleGetVersionResponse`,
`handleSerialNumberUpdate`, `handleFriendlyNameUpdate`, `handleSpeedDialUpdate`, `handleConfigParamsResponse`; verified-in-code):

1. `GET_VER` `03 00 00`. The next notification is taken as the version answer (whatever its opcode); its
   byte 0 is the CAIP version, 0x40 = "PRO mesh" (`packet.ProtocolVersion`, `GetVersionResponse` with
   version_offset 0).
2. After 500 ms gaps: GetRidersInfo, `GET_SN` `02`, GetAccessoryState.
3. On the serial: `GET_FRIENDLY_NAME` `04`; on the name: `GET_SPEED_DIAL` `01`; on the speed dial:
   `GET_PARAM` with the config types listed below.
4. On the first config update: subscribe to all services:
   `22 F1 FF 80` (agnostic bits 15,14,13,12,11,10,7,6,5,4,0 truncated to one byte = 0xF1; specific bits
   15…7 = 0xFF80) (`AgnosticsServiceFlags`, `SpecificServiceFlags`, `ExtansionKt.bitfieldsToInt`).

TalkCtrl sends GET_VER, GET_SN, GET_FRIENDLY_NAME, GET_SPEED_DIAL, GET_PARAM and SERVICE_SET in that order
(it skips the rider and accessory requests).

## 4. Config records (`settings.configs.*`, `settings.structures.ConfigType`)

Bit numbers: 0 = least significant (`extensions.ExtansionKt.getBitBoolMap`, `bitfieldsToByte`). Record size
includes the type byte. All verified-in-code.

| Type | Name | Size | Layout |
|---|---|---|---|
| 0x00 | USER_CONFIG | 4 | b1: bits 7-4 language, bit 3 voice commands (ASR), bit 2 voice prompts, bit 1 FM region (0 worldwide, 1 Japan), bit 0 RDS. b2: bit 7 phone/intercom mixing (HFP mixing), bits 6-5 mic (VOX) sensitivity (0 medium, 1 low, 2 high), bits 4-1 automatic volume (0 off, 1 low, 2 medium, 3 high), bit 0 noise gate. b3: bit 7 eco mode, bit 6 DMC mode, bits 5-0 EQ profile (`UserConfigParam`) |
| 0x01 | HW_CONFIG | 2 | b1: bits 3-0 TX power, bit 4 swap left/right speakers (`HardwareConfigParam`) |
| 0x02 | VOLUMES | 7 | b1 volume id (0 = whole record), b2: hi nibble announcements/standby, lo nibble intercom; b3: hi phone 1, lo phone 2; b4: hi FM, lo music 1; b5: hi music 2, lo music-under-intercom level (9=10 %, 1=20 % … 8=90 %, 0=100 %); b6: intercom mix (0 none, 1 full, 2 partial/75 %, 3 50 %, 4 25 %) (`VolumesConfigParam`). Single-volume form `[0x02][volume id][value]` with ids 1 standby, 2 DMC, 3 AG1, 4 AG2, 5 FM, 6 level dependent, 7 self hearing, 8 A2DP1, 9 A2DP2, 10 mix (`SetSingleVolumeStationConfig`, `structures.Volume`) — the app writes the whole record (`operations.settings.SetVolumesParamsOperation`) |
| 0x03 | HS_TYPE | 7 | headset type (2), firmware major (2), minor (2) (`PublicSWVersionParamConfig`) |
| 0x04 | SW_VERSION | 9 | device letter (2), version (2), sub version (2), revision (2) (`SWInternalVersionConfigParam`) |
| 0x05 | REGION_LANGUAGE_LIST | 3 | 16-bit mask of supported languages, bit = language code (`SupportedLanguageListConfigParam`) |
| 0x06 | USER_CONFIG_2 | 4 | b1: bit 0 DMC AGC, 1 advanced MMI, 2 redial by voice, 3 radio-on by voice, 4 auto on/off, 5 private DMC open, 6 public DMC open, 7 ambient mode; b2: bit 0 ANC, bits 2-1 power saver, 3 brake light, 4 LED UI (`UserConfigParam2`) |
| 0x07 | PROTOCOL | 3 | capabilities (`CaipProtocolCapabilities`) |
| 0x80 | FM_SINGLE_STATION | 14 | b1 bits 2-0 station to update (0 = all), then 6 × 2-byte frequency × 100 (`FMStationParamConfig`); single station write `[0x80][index << 5][freq hi][freq lo]` (`SetSingleFmStationConfig`) |
| 0x81 | ASR_THRESHOLD | 5 | (`ASRThresholdConfigParam`) |
| 0x82 | DEVICE_CAPABILITIES | 3 | b1: bit 7 automatic volume, 6 music sharing, 5 Bluetooth intercom, 4 HFP mixing, 3 voice commands, 2 DMC, bits 1-0 intercom channels; b2: bit 7 dynamic IC, 6 FM, 5 OTA, 4 private chat, 3 eco mode, 2 mobile bridge, 1 L/R speakers, 0 IC-DMC bridge (`DeviceCapabilitiesConfigParam`) |
| 0x83 | ACCESSORIES_ACT | 2 | |
| 0x84 | EQ_PROFILES | 5 | two 16-bit masks of available EQ profiles (bit n = profile n) (`EqualizerProfilesConfigParam`) |
| 0x86 | PAID_FEATURES | 3 | out of scope |
| 0x87 | DEVICE_CAP_2 | 4 | |
| 0x88, 0x89 | custom key short/long press | variable | not requested by TalkCtrl |
| 0x8A, 0x8C | front LED mode / brightness | 2 | |
| 0x8E | DEVICE_TYPE_GTS_GT | 2 | |
| 0x8F | HEALTH_INDICATORS | 4 | |

Languages (`settings.structures.Language`, value64): 0 English US, 1 English UK, 2 Spanish, 3 French,
4 German, 5 Italian, 6 Portuguese, 7 Dutch, 8 Russian, 9 Japanese, 10 Chinese, 11 Hebrew, 12 Korean.
Firmware URL language keys: en_us, en_uk, sp, fr, de, it, pt, (Dutch empty), ru, ja, ch, he, ko.

EQ profiles (`structures.EqualizerProfile`): 0 high volume, 1 bass boost, 2 vocal, 3 off, 8–10 Rockford,
16–19 JBL, 20–25 Cardo Labs, 29–30 Spirit.

Writes in the Cardo app are read-modify-write of the whole USER_CONFIG record
(`operations.settings.SetAGCStatusParamOperation`, `SetVOXSensitivityParamOperation`,
`SetASRAndVOXParamsOperation`, `SetCurrentLanguageParamsOperation`, `SetFMBandParamsOperation`,
`SetNoiseGateStatusParamOperation`, `SetHFPMixStatusParamOperation`; verified-in-code). TalkCtrl keeps the
unit's raw bytes and only flips the bits it changes.

VOX on/off: on CAIP64 units the app's VOX switch only exists for older CAIP9 units (`SetVOXActivityParamOperation`
has no CAIP64 packet). TalkCtrl shows it as "Needs confirming on your unit".

## 5. Control events (`packet.CAIPCommands`, sent as `30 hi lo len extra`)

All verified-in-code (value, and the packet class that sends it).

| Event | Value | Extra | Packet class |
|---|---|---|---|
| Factory reset | 0x0555 | – | `settings.messages.ResetToFactory` |
| FM on | 0x2000 | none, or `[preset]`, or `[freq hi][freq lo]` | `fm.FMTurnOn` |
| FM off | 0x2001 | – | `fm.FMTurnOff` |
| FM seek down / up | 0x2002 / 0x2003 | – | `fm.FMSeekDown`, `FMSeekUp` |
| FM scan forward / backward | 0x2004 / 0x2005 | – | `fm.FMScanForward`, `FMScanBackward` |
| FM auto tune | 0x2006 | – | `fm.FMAutoTune` |
| FM play preset | 0x2007 | `[1…6]` | `fm.FMPlayStation` |
| FM set frequency | 0x2008 | `[freq×100 hi][lo]` | `fm.FMSetFrequency` |
| FM stop scan | 0x2009 | – | `fm.FMStopScan` |
| Group: join (start member) | 0x2100 | – | `dmc.DMCStartMember` |
| Group: create (start master) | 0x2101 | – | `dmc.DMCStartMaster` |
| Group: delete | 0x2102 | – | `dmc.DMCDeleteGroup` |
| Group: cancel grouping | 0x2103 | – | `dmc.DMCCancelGrouping` |
| Group: add riders | 0x2104 | – | `dmc.DMCAddMoreRiders` |
| DMC mobile bridge | 0x2200 | – | (`MobileBridgeToggleOperation`) |
| DMC mute | 0x2201 | `[1 mute / 0 unmute]` | `dmc.SetDMCMute`, `MuteState` |
| DMC ↔ Bluetooth intercom bridge | 0x2202 | `[1 on / 0 off]` | `dmc.SetDMCtoIntercomBridge`, `BridgeSetState` |
| DMC private chat toggle | 0x2204 | unclear | not used (Needs confirming) |
| Intercom call A/B/C | 0x2250/51/52 | – | `ic.StartICCall` |
| Intercom end A/B/C | 0x2253/54/55 | – | `ic.StopICCall` |
| Intercom end all | 0x2256 | – | `ic.StopICCallAll` |
| Intercom pair A/B/C | 0x2257/58/59 | – | `ic.StartICPairing` |
| Intercom passive pairing | 0x225A | – | `ic.StartICPassivePairing` |
| Intercom stop pairing | 0x225B | – | `ic.StopICPairing` |
| Conference start / stop | 0x225C / 0x225D | – | `ic.StartICConference`, `StopICConference` |
| Reset intercom riders | 0x225E | – | `ic.ResetICRiders` |
| Music sharing start / stop | 0x2300 / 0x2301 | – | `music.MusicSharingToggle` (sends stop when sharing is active) |
| Speed dial call | 0x4005 | – | `phone.PhoneCallSpeedDial` |
| Phone pair 1 / 2 | 0x0000 / 0x0001 | – | `phone.PairToHFP1/2` (not used) |

## 6. FM

Frequency word = MHz × 100, big-endian (`extensions.ExtansionKt.toFMStationInt`,
`utils.FMStationParser.twoDecToHexAndThenToDouble`). The Cardo app builds it from the decimal string
(101.5 → 101×100 + 5×10); TalkCtrl rounds MHz × 100 and only allows 0.05/0.1 MHz steps, which gives the same
words for normal frequencies. Presets 1–6 (`FMPlayStation` only sends 1…6). Region and RDS live in
USER_CONFIG b1. Verified-in-code.

## 7. Intercom groups (DMC)

Create / join / add riders / cancel / delete / mute / bridges are plain control events (section 5,
verified-in-code). Private chat (`dmc.DmcSetPrivateChatPlus`, opcode 0xDA) needs a sequence number and the
6-byte Bluetooth addresses of the members from the group member list (`DmcGroupMemberList`), so TalkCtrl
does not offer it ("Needs confirming on your unit").

## 8. Firmware update server

| Item | Value | Where | Status |
|---|---|---|---|
| Base URL | `https://update.cardosystems.com/` | string resource `prod_storage_url`, `di.NetworkModuleKt.provideStorageUrl` | verified-in-code |
| Firmware folder | `firmware/` | `NetworkModuleKt` (FW_STORAGE_SUFFIX_RELEASE), `provideFirmwareStorageUrl` | verified-in-code |
| Version info | `GET {base}firmware/{deviceFolder}/fw_info.json` | `data.datasource.network.api.FirmwareVersionApi.getFirmwareInfo` | verified-in-code |
| Release notes | `GET {base}firmware/{deviceFolder}/fw_release_notes.json` (keys en, de, es, fr, it, ja, ko, pt, ru, zh: lists of lines) | `FirmwareVersionApi.getReleaseNotes`, `FirmwareReleaseNotesResponse` | verified-in-code |
| fw_info.json fields | `fw_version` ("major.minor"), `fw_url`, `isEnableByOTA`, `internal_fw_version`, `beta_build_number`, guide URLs | `domain.models.firmware.FirmwareVersion` | verified-in-code |
| `{language}` in fw_url | replaced by the language URL key; `http://` upgraded to `https://` | `data.repository.network.firmware.FirmwareInfoRepositoryImpl.fetchLatestOTAUrl` | verified-in-code |
| fw_url form | e.g. `https://update.cardosystems.com/firmware/FW_vers/<build>/{language}_ver.tar.gz` (same host); first file in the archive is the firmware | one request to `Packtalk_Edge/fw_info.json` during development (returned fw_version 5.4); `SettingsUpdatingFWViewModel.extractTarGZ` | observed / verified-in-code |
| Device folders | e.g. Packtalk_Edge, Packtalk_Neo, Packtalk_Pro, Freecom4X, Freecom2X, Spirit, SpiritHD, Packtalk_Outdoor_Consumer, … (full list in `TalkCtrl/app/.../protocol/Models.kt`) | `device.services.configs.device_type.DeviceTypeCardo` | verified-in-code |
| Serial prefix → folder | only partly readable: PT Packtalk_Edge, PD Packtalk_Edge_Ducati, PH Packtalk_Edge_Honda, KP Packtalk_Edge_KTM, SI Packtalk_Edge_Simpson, OR Packtalk_ORV, LC UCS_Lite, DD Flow_Lite, PI/PL Packtalk_Neo_Louis (the decompiler could not restore the rest) | `DeviceTypeConverter$Base.fromCAIP64` | verified-in-code (partial) |

TalkCtrl asks you to pick the model folder (pre-selected from the serial prefix where known). The check
sends only that folder name in the URL path; no account, token, serial or device id, a fixed
`User-Agent: TalkCtrl`, no cookies.

## 9. GAIA upgrade

Cardo bundles two Qualcomm GAIA clients, and picks one per model (`di.AppModulesKt`, verified-in-code):

- **Every unit except Flow Lite** (Packtalk, Freecom X, Spirit, …): `smartset.services.ota.gaia.GAIAUpgradeManager`
  with the older library `ota_framework.ota` (`UpgradeGaiaManager`, `services.GAIAGATTBLEService`,
  `vmupgradelibrary.UpgradeManager`): **GAIA v1/v2 over BLE GATT**, vendor `0x000A`.
- **Flow Lite** (`DeviceTypeFamilyCardo.isSupportOTAFrameworkV3`): `GAIAUpgradeManagerV3` with `ota_framework_v3`:
  **GAIA v3 over BLE** (`ConnectionRequest(address, BluetoothType.LOW_ENERGY)`), vendor `0x001D`.
- Horn-family units (Lazer VeloVox, Cardo Sync, UCS Lite) use a third, unrelated updater (`OTAUpgradeService`);
  TalkCtrl doesn't support updating those.

Both use the same upgrade messages (the "VM upgrade" protocol, same opcodes in `vmupgradelibrary.codes.OpCodes`
and `upgradelibrary.messages.OpCodes`). Before the transfer the app sends CAIP `OTA_START` (0xA2), waits for
`OTA_READY` (0xA3), waits 1 s more, then starts GAIA (`mvp.settings.ota.update.updating.SettingsUpdatingFWViewModel.prepareUpgrade`,
`callbackForDeviceReadyToUpdate`; verified-in-code). The firmware on the server is a `.tar.gz`; the app sends the
**first file inside** it (`SettingsUpdatingFWViewModel.extractTarGZ`, `parseFile`; `.bin`/`.fot` are sent as
they are; verified-in-code).

GAIA v1/v2 over GATT (all units but Flow Lite), verified-in-code in `ota_framework.ota.UpgradeGaiaManager`,
`gaialibrary.packets.GaiaPacket`, `GaiaPacketBLE`, `gaialibrary.GAIA`:

| Item | Value |
|---|---|
| Packet | `[vendor 00 0A][command hi][command lo][payload]`, big-endian |
| Acknowledgement | command \| 0x8000, payload `[status][…]`, status 0 = success |
| Register for upgrade notifications | 0x4001 `[0x12]`; cancel 0x4002 `[0x12]` |
| Upgrade connect / disconnect / control | 0x0640 / 0x0641 / 0x0642 `[upgrade message]` |
| Upgrade data from the unit | event notification 0x4003 `[0x12][upgrade message]`; the app acknowledges it with `[00 0A C0 03 00]` (`receiveEventNotification`, `createAcknowledgmentRequest(…, 0, …)`) |
| Data endpoint (RWCP) | 0x022E set / 0x02AE get; the Cardo app turns RWCP on (`GAIAUpgradeManager.gaiaReady` → `enableRWCP(true)`) |
| Payload size | ATT MTU − 3 − 4 (`GAIAGATTBLEService.onMtuChanged`, `setPacketMaximumSize`); data per DATA message = payload − 4 |
| MTU | requested from Cardo's remote config (`getOtauConfig().getMtuSize()`); value not in the app — TalkCtrl asks for 256 |

GAIA v3 (Flow Lite) details:

| Item | Value | Where | Status |
|---|---|---|---|
| GAIA GATT service | `00001100-D102-11E1-9B23-00025B00A5A5` | `ota_framework_v3.bluetooth.client.legatt.ServiceUuid.Gaia` | verified-in-code |
| Command characteristic (write) | `00001101-D102-11E1-9B23-00025B00A5A5` | `CharacteristicUuid.GaiaCommand` | verified-in-code |
| Response characteristic (notify) | `00001102-D102-11E1-9B23-00025B00A5A5` | `CharacteristicUuid.GaiaResponse` | verified-in-code |
| Data characteristic (RWCP) | `00001103-D102-11E1-9B23-00025B00A5A5` | `CharacteristicUuid.GaiaData` | verified-in-code |
| GAIA RFCOMM UUIDs | SPP `00001101-0000-1000-8000-00805F9B34FB` (default), legacy GAIA `00001107-D102-11E1-9B23-00025B00A5A5` | `bluetooth.client.rfcomm.RfcommClient$UUIDS` | verified-in-code |
| Vendor ID | V3: `0x001D`; V1/V2: `0x000A` | `gaia.qtil.vendors.QTILVendorIDs` | verified-in-code |
| V3 packet | `[vendor hi][vendor lo][command hi][command lo][payload]`, command = feature << 9 \| type << 7 \| id; types 0 command, 1 notification, 2 response, 3 error | `gaia.core.GaiaPacket.getBytes`, `core.v3.packets.V3Command`, `V3PacketType`, `V3PacketFactory`, `utils.BytesUtils` (big-endian) | verified-in-code |
| BLE framing | none: the V3 packet is the characteristic value; payload limit = ATT size − 7 | `core.transport.GaiaFormatter.LeGatt` | verified-in-code |
| RFCOMM framing | `[0xFF][version][flags][len (1, or 2 with flag 0x02 on v4+)][vendor][command][payload][XOR checksum if flag 0x01]`; len counts the payload only | `core.transport.TransportProtocol.Rfcomm.Frame.format`, `Flag` | verified-in-code |
| Upgrade feature | 6 | `gaia.qtil.data.QTILFeature` | verified-in-code |
| Register notification | Basic feature (0) command 7, payload `[6]` | `qtil.plugins.v3.V3BasicPlugin.registerNotification` | verified-in-code |
| Get GAIA version | Basic command 0 | `V3BasicPlugin` | verified-in-code |
| Upgrade commands | 0 UPGRADE_CONNECT, 1 UPGRADE_DISCONNECT, 2 UPGRADE_CONTROL (payload = upgrade message), 4 SET_DATA_ENDPOINT_MODE `[1]` (RWCP only) | `qtil.plugins.v3.V3UpgradePlugin` (COMMANDS, setUpgradeModeOn/Off, sendUpgradeMessage) | verified-in-code |
| Upgrade notifications | 0 UPGRADE_DATA (upgrade message), 1 stop request, 2 start request | `V3UpgradePlugin.onNotification` | verified-in-code |
| V1/V2 commands | 0x0640 connect, 0x0641 disconnect, 0x0642 control, event 0x12 VMU packet, 0x0300 API version | `qtil.plugins.v1v2.V1V2QTILPlugin` | verified-in-code |
| Upgrade message | `[opcode][length hi][length lo][content]` | `upgradelibrary.messages.UpgradeMessage` | verified-in-code |
| Opcodes | 0x01 START_REQ, 0x02 START_CFM, 0x03 DATA_BYTES_REQ, 0x04 DATA, 0x07 ABORT_REQ, 0x08 ABORT_CFM, 0x0B TRANSFER_COMPLETE_IND, 0x0C TRANSFER_COMPLETE_RES, 0x0E PROCEED_TO_COMMIT, 0x0F COMMIT_REQ, 0x10 COMMIT_CFM, 0x11 ERROR_IND, 0x12 COMPLETE_IND, 0x13 SYNC_REQ, 0x14 SYNC_CFM, 0x15 START_DATA_REQ, 0x16 IS_VALIDATION_DONE_REQ, 0x17 IS_VALIDATION_DONE_CFM, 0x1F ERROR_RES, 0x20/0x21 SILENT_COMMIT_SUPPORTED_REQ/CFM, 0x22 SILENT_COMMIT_CFM, 0x25 COMPLETE_IND_WITH_STATUS | `upgradelibrary.messages.OpCodes` | verified-in-code |
| SYNC_REQ | content = last 4 bytes of the file's MD5 | `UpgradeManagerImpl.sendSyncReq`, `upgrade.UpgradeHelperImpl.startUpgrade` (BytesUtils.getMD5FromUri) | verified-in-code |
| SYNC_CFM | `[resume point][id (4)][protocol version]`; protocol > 6 is refused | `UpgradeManagerImpl.receiveSyncCfm` | verified-in-code |
| Resume points | 0 start, 1 pre-validate, 2 pre-reboot, 3 post-reboot, 4 commit, 5 post-commit | `upgradelibrary.data.ResumePoint` | verified-in-code |
| START_CFM | `[status][battery (2)]`; status 9 = not ready: retry START_REQ after 2 s, up to 5 times | `UpgradeManagerImpl.receiveStartCfm` | verified-in-code |
| After START_CFM | start → START_DATA_REQ; pre-validate → IS_VALIDATION_DONE_REQ; pre-reboot → transfer-complete confirmation; post-reboot → PROCEED_TO_COMMIT `[0]`; commit → COMMIT_CFM `[0]` | `receiveStartCfm` | verified-in-code |
| DATA_BYTES_REQ | `[bytes wanted (4)][offset skip (4)]`; skip moves forward from the current position | `receiveDataBytesReq`, `upgradelibrary.data.DataReader.set` | verified-in-code |
| DATA | `[last (1)][file bytes]` | `UpgradeManagerImpl.sendData` | verified-in-code |
| Chunk size | GAIA payload − 4 (− 1 more with RWCP) | `UpgradeHelperImpl.getChunkSizeFromPayloadSize` | verified-in-code |
| After the last chunk | IS_VALIDATION_DONE_REQ; CFM `[wait ms (2)]` → ask again after that time | `sendNextDataMessage` lambda, `receiveIsValidationDoneCfm` | verified-in-code |
| TRANSFER_COMPLETE_IND | protocol < 4: answer TRANSFER_COMPLETE_RES `[0]` (continue, interactive commit); ≥ 4: SILENT_COMMIT_SUPPORTED_REQ first, then the same answer | `receiveTransferCompleteInd`, `receiveSilentCommitSupportedCfm`, `sendTransferCompleteRes`, `data.ConfirmationOptions` (CONFIRM 0, ABORT 1, SILENT 2) | verified-in-code |
| Unit reboots, then | reconnect, SYNC_REQ again → resume point post-reboot | `GAIAUpgradeManagerV3` reconnection, `receiveStartCfm` | verified-in-code |
| COMMIT_REQ → COMMIT_CFM `[0]` → COMPLETE_IND | done | `receiveCommitRes`, `sendCommitCfm`, `receiveCompleteInd` | verified-in-code |
| ERROR_IND `[code (2)]` | answer ERROR_RES with the same 2 bytes; 0x21 battery low and 0x81 "sync id is different" ask the user, everything else aborts | `receiveErrorInd`, `messages.ErrorCodes` | verified-in-code |
| ABORT_REQ / ABORT_CFM | abort | `UpgradeManagerImpl.abort`, `receiveAbortCfm` | verified-in-code |
| Acknowledged upload | each UPGRADE_CONTROL waits for its GAIA response when RWCP is off | `UpgradeHelperImpl.startUpgrade` (isUploadAcknowledged && !useRwcp), `V3UpgradePlugin.onResponse` | verified-in-code |
| CAIP "ready to update" before GAIA | 0xA2, answer 0xA3, then 1 s | `SettingsUpdatingFWViewModel.prepareUpgrade`, `DeviceReadyToUpdateOperation` | verified-in-code |

Cardo uses RWCP (unacknowledged writes on the data characteristic with its own windowing) on both paths.
TalkCtrl implements the simpler acknowledged mode instead (each upgrade message waits for its GAIA
acknowledgement before the next is sent), which both Qualcomm libraries support and use when RWCP is off
(`UpgradeGaiaManager.sendUpgradeControl` without RWCP, `UpgradeHelperImpl` with `useRwcp = false`). Whether
Cardo firmware accepts it is **inferred**: it is the libraries' non-RWCP mode, but only a real unit can
confirm it. TalkCtrl picks GAIA v1/v2 or v3 from the model you choose on the update screen, as the Cardo app does.

## 10. Out of scope

Live Intercom / cloud voice (VoIP opcodes 0xC0–0xC3), accounts, paid feature unlocks (opcode 0x06/0x45,
config 0x86), crash detection and SOS (opcodes 0xB0–0xBF), ride data collection (0x07/0x13/0x70–0x75),
Shimano integration (0xF1–0xF4).
