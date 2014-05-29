/*
 * Copyright (C) 2014 The OmniROM Project <http://www.omnirom.org>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony;

import static com.android.internal.telephony.RILConstants.*;
import android.content.Intent;
import android.content.Context;
import android.os.AsyncResult;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.SignalStrength;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccController;
import android.telephony.SmsMessage;
import com.android.internal.telephony.cdma.CdmaInformationRecords;
import com.android.internal.telephony.dataconnection.DataCallResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MediaTekRIL extends RIL implements CommandsInterface {
	// TODO: Support multiSIM
	// Sim IDs are 0 / 1
	final int mSimId = 1;
	private static boolean mInitialRadioStateChange = false;

	public MediaTekRIL(Context context, int networkMode, int cdmaSubscription) {
		super(context, networkMode, cdmaSubscription, null);
	}

	public MediaTekRIL(Context context, int networkMode, int cdmaSubscription,
			Integer instanceId) {
		super(context, networkMode, cdmaSubscription, instanceId);
	}

	@Override
	protected void initializeInstanceId() {
		Rlog.d(LOG_TAG, "MediaTekRIL mInstanceId: " + mInstanceId);
		if (null == mInstanceId) {
			int telMode = SystemProperties.getInt("ril.telephony.mode", 0);
			switch (telMode) {
			case 1:
				if (1 == mSimId)
					mInstanceId = 3;
				break;
			case 3:
				break;
			case 2:
			case 4:
				if (1 == mSimId)
					mInstanceId = 3;
				else
					mInstanceId = 4;
				break;
			}
			Rlog.d(LOG_TAG, "MediaTekRIL Change mInstanceId to " + mInstanceId
					+ " base on ril.telephony.mode:" + telMode + " mSimId: "
					+ mSimId);
		}
	}

	public static byte[] hexStringToBytes(String s) {
		byte[] ret;

		if (s == null)
			return null;

		int len = s.length();
		ret = new byte[len / 2];

		for (int i = 0; i < len; i += 2) {
			ret[i / 2] = (byte) ((hexCharToInt(s.charAt(i)) << 4) | hexCharToInt(s
					.charAt(i + 1)));
		}

		return ret;
	}

	static int hexCharToInt(char c) {
		if (c >= '0' && c <= '9')
			return (c - '0');
		if (c >= 'A' && c <= 'F')
			return (c - 'A' + 10);
		if (c >= 'a' && c <= 'f')
			return (c - 'a' + 10);

		throw new RuntimeException("invalid hex char '" + c + "'");
	}

	protected Object responseOperatorInfos(Parcel p) {
		String strings[] = (String[]) responseStrings(p);
		ArrayList<OperatorInfo> ret;

		if (strings.length % 5 != 0) {
			throw new RuntimeException(
					"RIL_REQUEST_QUERY_AVAILABLE_NETWORKS: invalid response. Got "
							+ strings.length
							+ " strings, expected multible of 5");
		}

		String lacStr = SystemProperties.get("gsm.cops.lac");
		boolean lacValid = false;
		int lacIndex = 0;

		Rlog.d(LOG_TAG,
				"lacStr = " + lacStr + " lacStr.length=" + lacStr.length()
						+ " strings.length=" + strings.length);
		if ((lacStr.length() > 0) && (lacStr.length() % 4 == 0)
				&& ((lacStr.length() / 4) == (strings.length / 5))) {
			Rlog.d(LOG_TAG, "lacValid set to true");
			lacValid = true;
		}

		SystemProperties.set("gsm.cops.lac", "");

		ret = new ArrayList<OperatorInfo>(strings.length / 5);

		for (int i = 0; i < strings.length; i += 5) {
			if ((strings[i] != null) && (strings[i].startsWith("uCs2") == true)) {
				Rlog.d(RILJ_LOG_TAG,
						"responseOperatorInfos handling UCS2 format name");

				try {
					strings[i] = new String(
							hexStringToBytes(strings[i].substring(4)), "UTF-16");
				} catch (UnsupportedEncodingException ex) {
					Rlog.e(RILJ_LOG_TAG,
							"responseOperatorInfos UnsupportedEncodingException");
				}
			}

			if ((lacValid == true) && (strings[i] != null)) {
				UiccController uiccController = UiccController.getInstance();
				IccRecords iccRecords = uiccController
						.getIccRecords(UiccController.APP_FAM_3GPP);
				int lacValue = -1;
				String sEons = null;
				String lac = lacStr.substring(lacIndex, lacIndex + 4);
				Rlog.d(LOG_TAG, "lacIndex=" + lacIndex + " lacValue="
						+ lacValue + " lac=" + lac + " plmn numeric="
						+ strings[i + 2] + " plmn name" + strings[i + 0]);

				if (lac != "") {
					lacValue = Integer.parseInt(lac, 16);
					lacIndex += 4;
					if (lacValue != 0xfffe) {
						/*
						 * sEons =
						 * iccRecords.getEonsIfExist(strings[i+2],lacValue
						 * ,true); if(sEons != null) { strings[i] = sEons;
						 * Rlog.d(LOG_TAG,
						 * "plmn name update to Eons: "+strings[i]); }
						 */
					} else {
						Rlog.d(LOG_TAG, "invalid lac ignored");
					}
				}
			}

			if (strings[i] != null
					&& (strings[i].equals("") || strings[i]
							.equals(strings[i + 2]))) {
				Operators init = new Operators();
				String temp = init.unOptimizedOperatorReplace(strings[i + 2]);
				Rlog.d(RILJ_LOG_TAG, "lookup RIL responseOperatorInfos() "
						+ strings[i + 2] + " gave " + temp);
				strings[i] = temp;
				strings[i + 1] = temp;
			}

			// 1, 2 = 2G
			// > 2 = 3G
			String property_name = "gsm.baseband.capability";
			if (mSimId > 0) {
				property_name = property_name + (mSimId + 1);
			}

			int basebandCapability = SystemProperties.getInt(property_name, 3);
			Rlog.d(LOG_TAG, "property_name=" + property_name
					+ ", basebandCapability=" + basebandCapability);
			if (3 < basebandCapability) {
				strings[i] = strings[i].concat(" " + strings[i + 4]);
				strings[i + 1] = strings[i + 1].concat(" " + strings[i + 4]);
			}

			ret.add(new OperatorInfo(strings[i], strings[i + 1],
					strings[i + 2], strings[i + 3]));
		}

		return ret;
	}

	private Object responseCrssNotification(Parcel p) {
		/*
		 * SuppCrssNotification notification = new SuppCrssNotification();
		 * notification.code = p.readInt(); notification.type = p.readInt();
		 * notification.number = p.readString(); notification.alphaid =
		 * p.readString(); notification.cli_validity = p.readInt(); return
		 * notification;
		 */

		Rlog.e(LOG_TAG, "NOT PROCESSING CRSS NOTIFICATION");
		return null;
	}

	private Object responseEtwsNotification(Parcel p) {
		/*
		 * EtwsNotification response = new EtwsNotification();
		 * response.warningType = p.readInt(); response.messageId = p.readInt();
		 * response.serialNumber = p.readInt(); response.plmnId =
		 * p.readString(); response.securityInfo = p.readString(); return
		 * response;
		 */
		Rlog.e(LOG_TAG, "NOT PROCESSING ETWS NOTIFICATION");

		return null;
	}

	// all that C&P just for responseOperator overriding?
	@Override
	protected RILRequest processSolicited(Parcel p) {
		int serial, error;
		boolean found = false;

		serial = p.readInt();
		error = p.readInt();

		RILRequest rr;

		rr = findAndRemoveRequestFromList(serial);

		if (rr == null) {
			Rlog.w(LOG_TAG, "Unexpected solicited response! sn: " + serial
					+ " error: " + error);
			return null;
		}

		Object ret = null;

		if (error == 0 || p.dataAvail() > 0) {

			/* Convert RIL_REQUEST_GET_MODEM_VERSION back */
			if (SystemProperties.get("ro.cm.device").indexOf("e73") == 0
					&& rr.mRequest == 220) {
				rr.mRequest = RIL_REQUEST_BASEBAND_VERSION;
			}

			// either command succeeds or command fails but with data payload
			try {
				switch (rr.mRequest) {
				/*
				 * cat hardware/ril/libril/ril_commands.h | grep "^ *{RIL_" |
				 * sed -re 's/\{([^,]+),[^,]+,([^}]+).+/case \1: ret =
				 * \2(p);break;/'
				 */
				case RIL_REQUEST_GET_SIM_STATUS:
					ret = responseIccCardStatus(p);
					break;
				case RIL_REQUEST_ENTER_SIM_PIN:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_ENTER_SIM_PUK:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_ENTER_SIM_PIN2:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_ENTER_SIM_PUK2:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_CHANGE_SIM_PIN:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_CHANGE_SIM_PIN2:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_ENTER_DEPERSONALIZATION_CODE:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_GET_CURRENT_CALLS:
					ret = responseCallList(p);
					break;
				case RIL_REQUEST_DIAL:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_GET_IMSI:
					ret = responseString(p);
					break;
				case RIL_REQUEST_HANGUP:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_HANGUP_WAITING_OR_BACKGROUND:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_HANGUP_FOREGROUND_RESUME_BACKGROUND:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_SWITCH_WAITING_OR_HOLDING_AND_ACTIVE:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_CONFERENCE:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_UDUB:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_LAST_CALL_FAIL_CAUSE:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_SIGNAL_STRENGTH:
					ret = responseSignalStrength(p);
					break;
				case RIL_REQUEST_VOICE_REGISTRATION_STATE:
					ret = responseStrings(p);
					break;
				case RIL_REQUEST_DATA_REGISTRATION_STATE:
					ret = responseStrings(p);
					break;
				case RIL_REQUEST_OPERATOR:
					ret = responseOperator(p);
					break;
				case RIL_REQUEST_RADIO_POWER:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_DTMF:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_SEND_SMS:
					ret = responseSMS(p);
					break;
				case RIL_REQUEST_SEND_SMS_EXPECT_MORE:
					ret = responseSMS(p);
					break;
				case RIL_REQUEST_SETUP_DATA_CALL:
					ret = responseSetupDataCall(p);
					break;
				case RIL_REQUEST_SIM_IO:
					ret = responseICC_IO(p);
					break;
				case RIL_REQUEST_SEND_USSD:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_CANCEL_USSD:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_GET_CLIR:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_SET_CLIR:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_QUERY_CALL_FORWARD_STATUS:
					ret = responseCallForward(p);
					break;
				case RIL_REQUEST_SET_CALL_FORWARD:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_QUERY_CALL_WAITING:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_SET_CALL_WAITING:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_SMS_ACKNOWLEDGE:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_GET_IMEI:
					ret = responseString(p);
					break;
				case RIL_REQUEST_GET_IMEISV:
					ret = responseString(p);
					break;
				case RIL_REQUEST_ANSWER:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_DEACTIVATE_DATA_CALL:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_QUERY_FACILITY_LOCK:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_SET_FACILITY_LOCK:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_CHANGE_BARRING_PASSWORD:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_QUERY_NETWORK_SELECTION_MODE:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_SET_NETWORK_SELECTION_AUTOMATIC:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_SET_NETWORK_SELECTION_MANUAL:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_QUERY_AVAILABLE_NETWORKS:
					ret = responseOperatorInfos(p);
					break;
				case RIL_REQUEST_DTMF_START:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_DTMF_STOP:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_BASEBAND_VERSION:
					ret = responseString(p);
					break;
				case RIL_REQUEST_SEPARATE_CONNECTION:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_SET_MUTE:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_GET_MUTE:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_QUERY_CLIP:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_LAST_DATA_CALL_FAIL_CAUSE:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_DATA_CALL_LIST:
					ret = responseDataCallList(p);
					break;
				case RIL_REQUEST_RESET_RADIO:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_OEM_HOOK_RAW:
					ret = responseRaw(p);
					break;
				case RIL_REQUEST_OEM_HOOK_STRINGS:
					ret = responseStrings(p);
					break;
				case RIL_REQUEST_SCREEN_STATE:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_SET_SUPP_SVC_NOTIFICATION:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_WRITE_SMS_TO_SIM:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_DELETE_SMS_ON_SIM:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_SET_BAND_MODE:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_QUERY_AVAILABLE_BAND_MODE:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_STK_GET_PROFILE:
					ret = responseString(p);
					break;
				case RIL_REQUEST_STK_SET_PROFILE:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_STK_SEND_ENVELOPE_COMMAND:
					ret = responseString(p);
					break;
				case RIL_REQUEST_STK_SEND_TERMINAL_RESPONSE:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_STK_HANDLE_CALL_SETUP_REQUESTED_FROM_SIM:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_EXPLICIT_CALL_TRANSFER:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_SET_PREFERRED_NETWORK_TYPE:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_GET_PREFERRED_NETWORK_TYPE:
					ret = responseGetPreferredNetworkType(p);
					break;
				case RIL_REQUEST_GET_NEIGHBORING_CELL_IDS:
					ret = responseCellList(p);
					break;
				case RIL_REQUEST_SET_LOCATION_UPDATES:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_CDMA_SET_SUBSCRIPTION_SOURCE:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_CDMA_SET_ROAMING_PREFERENCE:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_CDMA_QUERY_ROAMING_PREFERENCE:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_SET_TTY_MODE:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_QUERY_TTY_MODE:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_CDMA_SET_PREFERRED_VOICE_PRIVACY_MODE:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_CDMA_QUERY_PREFERRED_VOICE_PRIVACY_MODE:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_CDMA_FLASH:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_CDMA_BURST_DTMF:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_CDMA_VALIDATE_AND_WRITE_AKEY:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_CDMA_SEND_SMS:
					ret = responseSMS(p);
					break;
				case RIL_REQUEST_CDMA_SMS_ACKNOWLEDGE:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_GSM_GET_BROADCAST_CONFIG:
					ret = responseGmsBroadcastConfig(p);
					break;
				case RIL_REQUEST_GSM_SET_BROADCAST_CONFIG:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_GSM_BROADCAST_ACTIVATION:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_CDMA_GET_BROADCAST_CONFIG:
					ret = responseCdmaBroadcastConfig(p);
					break;
				case RIL_REQUEST_CDMA_SET_BROADCAST_CONFIG:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_CDMA_BROADCAST_ACTIVATION:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_CDMA_SUBSCRIPTION:
					ret = responseStrings(p);
					break;
				case RIL_REQUEST_CDMA_WRITE_SMS_TO_RUIM:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_CDMA_DELETE_SMS_ON_RUIM:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_DEVICE_IDENTITY:
					ret = responseStrings(p);
					break;
				case RIL_REQUEST_EXIT_EMERGENCY_CALLBACK_MODE:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_GET_SMSC_ADDRESS:
					ret = responseString(p);
					break;
				case RIL_REQUEST_SET_SMSC_ADDRESS:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_REPORT_SMS_MEMORY_STATUS:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_REPORT_STK_SERVICE_IS_RUNNING:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_CDMA_GET_SUBSCRIPTION_SOURCE:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_ISIM_AUTHENTICATION:
					ret = responseString(p);
					break;
				case RIL_REQUEST_ACKNOWLEDGE_INCOMING_GSM_SMS_WITH_PDU:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_STK_SEND_ENVELOPE_WITH_STATUS:
					ret = responseICC_IO(p);
					break;
				case RIL_REQUEST_VOICE_RADIO_TECH:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_GET_NITZ_TIME:
					ret = responseGetNitzTime(p);
					break;
				case RIL_REQUEST_QUERY_UIM_INSERTED:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_HANGUP_ALL:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_GET_COLP:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_SET_COLP:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_GET_COLR:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_GET_CCM:
					ret = responseString(p);
					break;
				case RIL_REQUEST_GET_ACM:
					ret = responseString(p);
					break;
				case RIL_REQUEST_GET_ACMMAX:
					ret = responseString(p);
					break;
				case RIL_REQUEST_GET_PPU_AND_CURRENCY:
					ret = responseStrings(p);
					break;
				case RIL_REQUEST_SET_ACMMAX:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_RESET_ACM:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_SET_PPU_AND_CURRENCY:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_RADIO_POWEROFF:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_DUAL_SIM_MODE_SWITCH:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_QUERY_PHB_STORAGE_INFO:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_WRITE_PHB_ENTRY:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_READ_PHB_ENTRY:
					ret = responsePhbEntries(p);
					break;
				case RIL_REQUEST_SET_GPRS_CONNECT_TYPE:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_SET_GPRS_TRANSFER_TYPE:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_MOBILEREVISION_AND_IMEI:
					ret = responseString(p);
					break;
				case RIL_REQUEST_QUERY_SIM_NETWORK_LOCK:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_SET_SIM_NETWORK_LOCK:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_SET_SCRI:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_VT_DIAL:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_BTSIM_CONNECT:
					ret = responseString(p);
					break;
				case RIL_REQUEST_BTSIM_DISCONNECT_OR_POWEROFF:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_BTSIM_POWERON_OR_RESETSIM:
					ret = responseString(p);
					break;
				case RIL_REQUEST_BTSIM_TRANSFERAPDU:
					ret = responseString(p);
					break;
				case RIL_REQUEST_EMERGENCY_DIAL:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_SET_NETWORK_SELECTION_MANUAL_WITH_ACT:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_QUERY_ICCID:
					ret = responseString(p);
					break;
				case RIL_REQUEST_SIM_AUTHENTICATION:
					ret = responseString(p);
					break;
				case RIL_REQUEST_USIM_AUTHENTICATION:
					ret = responseString(p);
					break;
				case RIL_REQUEST_VOICE_ACCEPT:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_RADIO_POWERON:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_GET_SMS_SIM_MEM_STATUS:
					ret = responseSimSmsMemoryStatus(p);
					break;
				case RIL_REQUEST_FORCE_RELEASE_CALL:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_SET_CALL_INDICATION:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_REPLACE_VT_CALL:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_GET_3G_CAPABILITY:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_SET_3G_CAPABILITY:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_GET_POL_CAPABILITY:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_GET_POL_LIST:
					ret = responseNetworkInfoWithActs(p);
					break;
				case RIL_REQUEST_SET_POL_ENTRY:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_QUERY_UPB_CAPABILITY:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_EDIT_UPB_ENTRY:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_DELETE_UPB_ENTRY:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_READ_UPB_GAS_LIST:
					ret = responseStrings(p);
					break;
				case RIL_REQUEST_READ_UPB_GRP:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_WRITE_UPB_GRP:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_DISABLE_VT_CAPABILITY:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_HANGUP_ALL_EX:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_SET_SIM_RECOVERY_ON:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_GET_SIM_RECOVERY_ON:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_SET_TRM:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_DETECT_SIM_MISSING:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_GET_CALIBRATION_DATA:
					ret = responseString(p);
					break;
				case RIL_REQUEST_GET_PHB_STRING_LENGTH:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_GET_PHB_MEM_STORAGE:
					ret = responseGetPhbMemStorage(p);
					break;
				case RIL_REQUEST_SET_PHB_MEM_STORAGE:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_READ_PHB_ENTRY_EXT:
					ret = responseReadPhbEntryExt(p);
					break;
				case RIL_REQUEST_WRITE_PHB_ENTRY_EXT:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_GET_SMS_PARAMS:
					ret = responseSmsParams(p);
					break;
				case RIL_REQUEST_SET_SMS_PARAMS:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_SIM_TRANSMIT_BASIC:
					ret = responseICC_IO(p);
					break;
				case RIL_REQUEST_SIM_OPEN_CHANNEL:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_SIM_CLOSE_CHANNEL:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_SIM_TRANSMIT_CHANNEL:
					ret = responseICC_IO(p);
					break;
				case RIL_REQUEST_SIM_GET_ATR:
					ret = responseString(p);
					break;
				case RIL_REQUEST_SET_CB_CHANNEL_CONFIG_INFO:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_SET_CB_LANGUAGE_CONFIG_INFO:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_GET_CB_CONFIG_INFO:
					ret = responseCbConfig(p);
					break;
				case RIL_REQUEST_SET_ALL_CB_LANGUAGE_ON:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_SET_ETWS:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_SET_FD_MODE:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_SIM_OPEN_CHANNEL_WITH_SW:
					ret = responseICC_IO(p);
					break;
				case RIL_REQUEST_GET_CELL_INFO_LIST:
					ret = responseCellInfoList(p);
					break;
				case RIL_REQUEST_SET_UNSOL_CELL_INFO_LIST_RATE:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_SET_INITIAL_ATTACH_APN:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_IMS_REGISTRATION_STATE:
					ret = responseInts(p);
					break;
				case RIL_REQUEST_IMS_SEND_SMS:
					ret = responseSMS(p);
					break;
				case RIL_REQUEST_SET_UICC_SUBSCRIPTION:
					ret = responseVoid(p);
					break;
				case RIL_REQUEST_SET_DATA_SUBSCRIPTION:
					ret = responseVoid(p);
					break;
				default:
					throw new RuntimeException(
							"Unrecognized solicited response: " + rr.mRequest);
					// break;
				}
			} catch (Throwable tr) {
				// Exceptions here usually mean invalid RIL responses

				Rlog.w(LOG_TAG, rr.serialString() + "< "
						+ requestToString(rr.mRequest)
						+ " exception, possible invalid RIL response", tr);

				if (rr.mResult != null) {
					AsyncResult.forMessage(rr.mResult, null, tr);
					rr.mResult.sendToTarget();
				}
				return rr;
			}
		}

		if (error != 0) {
			rr.onError(error, ret);
			return rr;
		}

		Rlog.d(RILJ_LOG_TAG,
				rr.serialString() + "< " + requestToString(rr.mRequest) + " "
						+ retToString(rr.mRequest, ret));

		if (rr.mResult != null) {
			AsyncResult.forMessage(rr.mResult, ret, null);
			rr.mResult.sendToTarget();
		}

		return rr;
	}

	@Override
    protected void processUnsolicited(Parcel p) {
        Object ret;
        int dataPosition = p.dataPosition(); // save off position within the
                                             // Parcel
        int response = p.readInt();

        switch (response) {
            case RIL_UNSOL_NEIGHBORING_CELL_INFO:
                ret = responseStrings(p);
                break;
            case RIL_UNSOL_NETWORK_INFO:
                ret = responseStrings(p);
                break;
            case RIL_UNSOL_CALL_FORWARDING:
                ret = responseInts(p);
                break;
            case RIL_UNSOL_CRSS_NOTIFICATION:
                ret = responseCrssNotification(p);
                break;
            case RIL_UNSOL_CALL_PROGRESS_INFO:
                ret = responseStrings(p);
                break;
            case RIL_UNSOL_PHB_READY_NOTIFICATION:
                ret = responseVoid(p);
                break;
            case RIL_UNSOL_SPEECH_INFO:
                ret = responseInts(p);
                break;
            case RIL_UNSOL_SIM_INSERTED_STATUS:
                ret = responseInts(p);
                break;
            case RIL_UNSOL_RADIO_TEMPORARILY_UNAVAILABLE:
                ret = responseInts(p);
                break;
            case RIL_UNSOL_ME_SMS_STORAGE_FULL:
                ret = responseVoid(p);
                break;
            case RIL_UNSOL_SMS_READY_NOTIFICATION:
                ret = responseVoid(p);
                break;
            case RIL_UNSOL_SCRI_RESULT:
                ret = responseInts(p);
                break;
            case RIL_UNSOL_VT_STATUS_INFO:
                ret = responseInts(p);
                break;
            case RIL_UNSOL_VT_RING_INFO:
                ret = responseVoid(p);
                break;
            case RIL_UNSOL_INCOMING_CALL_INDICATION:
                ret = responseStrings(p);
                break;
            case RIL_UNSOL_SIM_MISSING:
                ret = responseInts(p);
                break;
            case RIL_UNSOL_GPRS_DETACH:
                ret = responseVoid(p);
                break;
            case RIL_UNSOL_SIM_RECOVERY:
                ret = responseInts(p);
                break;
            case RIL_UNSOL_VIRTUAL_SIM_ON:
                ret = responseInts(p);
                break;
            case RIL_UNSOL_VIRTUAL_SIM_OFF:
                ret = responseInts(p);
                break;
            case RIL_UNSOL_INVALID_SIM:
                ret = responseStrings(p);
                break;
            case RIL_UNSOL_RESPONSE_PS_NETWORK_STATE_CHANGED:
                ret = responseVoid(p);
                break;
            case RIL_UNSOL_RESPONSE_ACMT:
                ret = responseInts(p);
                break;
            case RIL_UNSOL_EF_CSP_PLMN_MODE_BIT:
                ret = responseInts(p);
                break;
            case RIL_UNSOL_IMEI_LOCK:
                ret = responseVoid(p);
                break;
            case RIL_UNSOL_RESPONSE_MMRR_STATUS_CHANGED:
                ret = responseInts(p);
                break;
            case RIL_UNSOL_SIM_PLUG_OUT:
                ret = responseInts(p);
                break;
            case RIL_UNSOL_SIM_PLUG_IN:
                ret = responseInts(p);
                break;
            case RIL_UNSOL_RESPONSE_ETWS_NOTIFICATION:
                ret = responseEtwsNotification(p);
                break;
            case RIL_UNSOL_CNAP:
                ret = responseStrings(p);
                break;
            case RIL_UNSOL_STK_EVDL_CALL:
                ret = responseInts(p);
                break;
            default:
                // Rewind the Parcel
                p.setDataPosition(dataPosition);

                // Forward responses that we are not overriding to the super
                // class
                super.processUnsolicited(p);
                return;
        }

        // To avoid duplicating code from RIL.java, we rewrite some response
        // codes to fit
        // AOSP's one (when they do the same effect)
        boolean rewindAndReplace = false;
        int newResponseCode = 0;

        switch (response) {
            case RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED:
                if (RILJ_LOGD) unsljLogRet(response, ret);
                // intercept and send GPRS_TRANSFER_TYPE and
                // GPRS_CONNECT_TYPE to RIL
                setRadioStateFromRILInt(p.readInt());
                rewindAndReplace = true;
                newResponseCode = RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED;
                break;
            case RIL_UNSOL_NEIGHBORING_CELL_INFO:
                if (RILJ_LOGD) unsljLogRet(response, ret);
                Rlog.e(LOG_TAG, "TODO  RIL_UNSOL_NEIGHBORING_CELL_INFO");
                break;
            case RIL_UNSOL_NETWORK_INFO:
                Rlog.e(LOG_TAG, "TODO  RIL_UNSOL_NETWORK_INFO");
                if (RILJ_LOGD) unsljLogRet(response, ret);
                break;
            case RIL_UNSOL_CALL_FORWARDING:
                Rlog.e(LOG_TAG, "TODO  RIL_UNSOL_CALL_FORWARDING");
                if (RILJ_LOGD) unsljLogRet(response, ret);
                break;
            case RIL_UNSOL_CRSS_NOTIFICATION:
                Rlog.e(LOG_TAG, "TODO  RIL_UNSOL_CRSS_NOTIFICATION");
                if (RILJ_LOGD) unsljLogRet(response, ret);
                break;
            case RIL_UNSOL_CALL_PROGRESS_INFO:
                if (RILJ_LOGD) unsljLogRet(response, ret);
                rewindAndReplace = true;
                newResponseCode = RIL_UNSOL_RESPONSE_CALL_STATE_CHANGED;
                break;
            case RIL_UNSOL_PHB_READY_NOTIFICATION:
                Rlog.e(LOG_TAG, "TODO  RIL_UNSOL_PHB_READY_NOTIFICATION");
                if (RILJ_LOGD) unsljLogRet(response, ret);
                break;
            case RIL_UNSOL_SPEECH_INFO:
                Rlog.e(LOG_TAG, "TODO  RIL_UNSOL_SPEECH_INFO");
                if (RILJ_LOGD) unsljLogRet(response, ret);
                break;
            case RIL_UNSOL_SIM_INSERTED_STATUS:
                if (RILJ_LOGD) unsljLogRet(response, ret);
                rewindAndReplace = true;
                newResponseCode = RIL_UNSOL_RESPONSE_SIM_STATUS_CHANGED;
                break;/*
            case RIL_UNSOL_RADIO_TEMPORARILY_UNAVAILABLE:
                if (RILJ_LOGD) unsljLogRet(response, ret);
                rewindAndReplace = true;
                newResponseCode = RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED;
                break;*/
            case RIL_UNSOL_ME_SMS_STORAGE_FULL:
                if (RILJ_LOGD) unsljLogRet(response, ret);
                Rlog.e(LOG_TAG, "TODO  RIL_UNSOL_ME_SMS_STORAGE_FULL");
                break;
            case RIL_UNSOL_SMS_READY_NOTIFICATION:
                if (RILJ_LOGD) unsljLogRet(response, ret);
                rewindAndReplace = true;
                newResponseCode = RIL_UNSOL_RESPONSE_NEW_SMS_STATUS_REPORT;
                break;
            case RIL_UNSOL_SCRI_RESULT:
                Rlog.e(LOG_TAG, "TODO  RIL_UNSOL_SCRI_RESULT");
                if (RILJ_LOGD) unsljLogRet(response, ret);
                break;
            case RIL_UNSOL_VT_STATUS_INFO:
                Rlog.e(LOG_TAG, "TODO  RIL_UNSOL_VT_STATUS_INFO");
                if (RILJ_LOGD) unsljLogRet(response, ret);
                break;
            case RIL_UNSOL_VT_RING_INFO:
                Rlog.e(LOG_TAG, "TODO  RIL_UNSOL_VT_RING_INFO");
                if (RILJ_LOGD) unsljLogRet(response, ret);
                break;
            case RIL_UNSOL_INCOMING_CALL_INDICATION:
                if (RILJ_LOGD) unsljLogRet(response, ret);
                setCallIndication((String[]) ret);
                rewindAndReplace = true;
                newResponseCode = RIL_UNSOL_RESPONSE_CALL_STATE_CHANGED;
                break;
            case RIL_UNSOL_SIM_MISSING:
                if (RILJ_LOGD) unsljLogRet(response, ret);
                rewindAndReplace = true;
                newResponseCode = RIL_UNSOL_RESPONSE_SIM_STATUS_CHANGED;
                break;
            case RIL_UNSOL_GPRS_DETACH:
                Rlog.e(LOG_TAG, "TODO  RIL_UNSOL_GPRS_DETACH");
                if (RILJ_LOGD) unsljLogRet(response, ret);
                break;
            case RIL_UNSOL_SIM_RECOVERY:
                Rlog.e(LOG_TAG, "TODO  RIL_UNSOL_SIM_RECOVERY");
                if (RILJ_LOGD) unsljLogRet(response, ret);
                break;
            case RIL_UNSOL_VIRTUAL_SIM_ON:
                Rlog.e(LOG_TAG, "TODO  RIL_UNSOL_VIRTUAL_SIM_ON");
                if (RILJ_LOGD) unsljLogRet(response, ret);
                break;
            case RIL_UNSOL_VIRTUAL_SIM_OFF:
                Rlog.e(LOG_TAG, "TODO  RIL_UNSOL_VIRTUAL_SIM_OFF");
                if (RILJ_LOGD) unsljLogRet(response, ret);
                break;
            case RIL_UNSOL_INVALID_SIM:
                Rlog.e(LOG_TAG, "TODO  RIL_UNSOL_INVALID_SIM");
                if (RILJ_LOGD) unsljLogRet(response, ret);
                break;
            case RIL_UNSOL_RESPONSE_PS_NETWORK_STATE_CHANGED:
                if (RILJ_LOGD) unsljLogRet(response, ret);
                rewindAndReplace = true;
                newResponseCode = RIL_UNSOL_RESPONSE_VOICE_NETWORK_STATE_CHANGED;
                break;
            case RIL_UNSOL_RESPONSE_ACMT:
                Rlog.e(LOG_TAG, "TODO  RIL_UNSOL_RESPONSE_ACMT");
                if (RILJ_LOGD) unsljLogRet(response, ret);
                break;
            case RIL_UNSOL_EF_CSP_PLMN_MODE_BIT:
                Rlog.e(LOG_TAG, "TODO  RIL_UNSOL_EF_CSP_PLMN_MODE_BIT");
                if (RILJ_LOGD) unsljLogRet(response, ret);
                break;
            case RIL_UNSOL_IMEI_LOCK:
                Rlog.e(LOG_TAG, "TODO  RIL_UNSOL_IMEI_LOCK");
                if (RILJ_LOGD) unsljLogRet(response, ret);
                break;
            case RIL_UNSOL_RESPONSE_MMRR_STATUS_CHANGED:
                Rlog.e(LOG_TAG, "TODO  RIL_UNSOL_RESPONSE_MMRR_STATUS_CHANGED");
                if (RILJ_LOGD) unsljLogRet(response, ret);
                break;
            case RIL_UNSOL_SIM_PLUG_OUT:
                if (RILJ_LOGD) unsljLogRet(response, ret);
                rewindAndReplace = true;
                newResponseCode = RIL_UNSOL_RESPONSE_SIM_STATUS_CHANGED;
                break;
            case RIL_UNSOL_SIM_PLUG_IN:
                if (RILJ_LOGD) unsljLogRet(response, ret);
                rewindAndReplace = true;
                newResponseCode = RIL_UNSOL_RESPONSE_SIM_STATUS_CHANGED;
                break;
            case RIL_UNSOL_RESPONSE_ETWS_NOTIFICATION:
                Rlog.e(LOG_TAG, "TODO  RIL_UNSOL_RESPONSE_ETWS_NOTIFICATION");
                if (RILJ_LOGD) unsljLogRet(response, ret);
                break;
            default:
                Rlog.e(LOG_TAG, "Unprocessed unsolicited known MTK response: "
                        + response);
        }

        if (rewindAndReplace) {
            Rlog.w(LOG_TAG, "Rewriting MTK unsolicited response to "
                    + newResponseCode);

            // Rewrite
            p.setDataPosition(dataPosition);
            p.writeInt(newResponseCode);

            // And rewind again in front
            p.setDataPosition(dataPosition);

            super.processUnsolicited(p);
        }
    }

	private Object responseOperator(Parcel p) {
		int num;
		String response[] = p.readStringArray();

		if (false) {
			num = p.readInt();

			response = new String[num];
			for (int i = 0; i < num; i++) {
				response[i] = p.readString();
			}
		}

		if ((response[0] != null) && (response[0].startsWith("uCs2") == true)) {
			Rlog.d(RILJ_LOG_TAG, "responseOperator handling UCS2 format name");
			try {
				response[0] = new String(
						hexStringToBytes(response[0].substring(4)), "UTF-16");
			} catch (UnsupportedEncodingException ex) {
				riljLog("responseOperatorInfos UnsupportedEncodingException");
			}
		}

		if (response[0] != null
				&& (response[0].equals("") || response[0].equals(response[2]))) {
			Operators init = new Operators();
			String temp = init.unOptimizedOperatorReplace(response[2]);
			Rlog.d(RILJ_LOG_TAG, "lookup RIL responseOperator() " + response[2]
					+ " gave " + temp + " was " + response[0] + "/"
					+ response[1] + " before.");
			response[0] = temp;
			response[1] = temp;
		}

		return response;
	}

	private void setCallIndication(String[] incomingCallInfo) {
		RILRequest rr = RILRequest
				.obtain(RIL_REQUEST_SET_CALL_INDICATION, null);

		int callId = Integer.parseInt(incomingCallInfo[0]);
		int callMode = Integer.parseInt(incomingCallInfo[3]);
		int seqNumber = Integer.parseInt(incomingCallInfo[4]);

		// some guess work is needed here, for now, just 0
		callMode = 0;

		rr.mParcel.writeInt(3);

		rr.mParcel.writeInt(callMode);
		rr.mParcel.writeInt(callId);
		rr.mParcel.writeInt(seqNumber);

		Rlog.d(RILJ_LOG_TAG, rr.serialString() + "> "
				+ requestToString(rr.mRequest) + " " + callMode + " " + callId
				+ " " + seqNumber);

		send(rr);
	}

	// Override setupDataCall as the MTK RIL needs 8th param CID (hardwired to
	// 1?)
	@Override
	public void setupDataCall(String radioTechnology, String profile,
			String apn, String user, String password, String authType,
			String protocol, Message result) {
		RILRequest rr = RILRequest.obtain(RIL_REQUEST_SETUP_DATA_CALL, result);

		rr.mParcel.writeInt(8);

		rr.mParcel.writeString(radioTechnology);
		rr.mParcel.writeString(profile);
		rr.mParcel.writeString(apn);
		rr.mParcel.writeString(user);
		rr.mParcel.writeString(password);
		rr.mParcel.writeString(authType);
		rr.mParcel.writeString(protocol);
		rr.mParcel.writeString("1");

		Rlog.d(LOG_TAG, rr.serialString() + "> "
				+ requestToString(rr.mRequest) + " " + radioTechnology + " "
				+ profile + " " + apn + " " + user + " " + password + " "
				+ authType + " " + protocol + "1");

		send(rr);
	}

	protected Object responseSignalStrength(Parcel p) {
		return new SignalStrength(p);
	}

	private void setRadioStateFromRILInt(int stateCode) {
		switch (stateCode) {
			case 0: // RADIO_OFF
			    Rlog.d(LOG_TAG, "Set Radio State to RADIO_OFF");
			    break;
			case 1: // RADIO_UNAVAILABLE
			    Rlog.d(LOG_TAG, "Set Radio State to RADIO_UNAVAILABLE");
			    break;
		  case 2: // SIM_NOT_READY
		  		Rlog.d(LOG_TAG, "Set Radio State to SIM_NOT_READY");
			    break;
			case 3: // SIM_LOCKED_OR_ABSENT;
			    Rlog.d(LOG_TAG, "Set Radio State to SIM_LOCKED_OR_ABSENT");
			    setGprsTransferType(1, null);
			    setGprsConnType(1, null);
			    break;
	    case 4: // SIM_READY;
	        Rlog.d(LOG_TAG, "Set Radio State to SIM_READY");
	        setGprsTransferType(1, null);
			    setGprsConnType(1, null);
				break;
	    case 5: // RUIM_NOT_READY;
	        Rlog.d(LOG_TAG, "Set Radio State to RUIM_NOT_READY");
			    break;
	    case 6: // RUIM_READY;
	        Rlog.d(LOG_TAG, "Set Radio State to RUIM_READY");
			    break;
	    case 7: // RUIM_LOCKED_OR_ABSENT;
	        Rlog.d(LOG_TAG, "Set Radio State to RUIM_LOCKED_OR_ABSENT");
			    break;
	    case 8: // NV_NOT_READY;
	        Rlog.d(LOG_TAG, "Set Radio State to NV_NOT_READY");
			    break;
	    case 9: // NV_READY;
	        Rlog.d(LOG_TAG, "Set Radio State to NV_READY");
			    break;
	    case 10: // RADIO_ON;
	      Rlog.d(LOG_TAG, "Set Radio State to RADIO_ON");
	    	break;
			default: 
				Rlog.e(LOG_TAG, "Unrecognized Radio State: " + stateCode);
		}
	}

	@Override
	public void setRadioPower(boolean on, Message result) {
		boolean allow = SystemProperties.getBoolean("persist.ril.enable", true);
		if (!allow) {
			return;
		}

		if (!this.mState.isOn()) {
      setPreferredNetworkType(mPreferredNetworkType, null);
		}

    RILRequest rr = RILRequest.obtain(RIL_REQUEST_RADIO_POWER, result);

    rr.mParcel.writeInt(1);
    rr.mParcel.writeInt(on ? 1 : 0);

    if (RILJ_LOGD) {
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                + (on ? " on" : " off"));
    }

    send(rr);
    /*
    RILRequest rr = RILRequest.obtain(RIL_REQUEST_DUAL_SIM_MODE_SWITCH, result);

    if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

    rr.mParcel.writeInt(1);
    rr.mParcel.writeInt(on ? 3 : 0); // SIM1 | SIM2 ?

    send(rr);
		*/
	}

	@Override
	public void setUiccSubscription(int slotId, int appIndex, int subId,
			int subStatus, Message result) {
		Rlog.d(LOG_TAG, "setUiccSubscription" + slotId + " " + appIndex
				+ " " + subId + " " + subStatus);

		// Fake response (note: should be sent before
		// mSubscriptionStatusRegistrants or
		// SubscriptionManager might not set the readiness correctly)
		AsyncResult.forMessage(result, 0, null);
		result.sendToTarget();

		// TODO: Actually turn off/on the radio (and don't fight with the
		// ServiceStateTracker)
		if (subStatus == 1 /* ACTIVATE */) {
			// Subscription changed: enabled
			if (mSubscriptionStatusRegistrants != null) {
				mSubscriptionStatusRegistrants
						.notifyRegistrants(new AsyncResult(null,
								new int[] { 1 }, null));
			}
		} else if (subStatus == 0 /* DEACTIVATE */) {
			// Subscription changed: disabled
			if (mSubscriptionStatusRegistrants != null) {
				mSubscriptionStatusRegistrants
						.notifyRegistrants(new AsyncResult(null,
								new int[] { 0 }, null));
			}
		}
	}

	public void setDataSubscription(Message response) {
		int simId = mInstanceId == null ? 0 : mInstanceId;
		Rlog.d(LOG_TAG, "Setting data subscription to " + simId
				+ " ignored on MTK");
		AsyncResult.forMessage(response, 0, null);
		response.sendToTarget();
	}

	public void setDefaultVoiceSub(int subIndex, Message response) {
		// No need to inform the RIL on MTK
		Rlog.d(LOG_TAG, "Setting defaultvoice subscription to "
				+ mInstanceId + " ignored on MTK");
		AsyncResult.forMessage(response, 0, null);
		response.sendToTarget();
	}

	private Object responsePhbEntries(Parcel p) {
		Rlog.e(LOG_TAG, "TODO responsePhbEntries");
		return null;
	}

	private Object responseSimSmsMemoryStatus(Parcel p) {
		Rlog.e(LOG_TAG, "TODO responsePhbEntries");
		return null;
	}

	private Object responseNetworkInfoWithActs(Parcel p) {
		Rlog.e(LOG_TAG, "TODO responsePhbEntries");
		return null;
	}

	private Object responseGetPhbMemStorage(Parcel p) {
		Rlog.e(LOG_TAG, "TODO responsePhbEntries");
		return null;
	}

	private Object responseReadPhbEntryExt(Parcel p) {
		Rlog.e(LOG_TAG, "TODO responsePhbEntries");
		return null;
	}

	private Object responseSmsParams(Parcel p) {
		Rlog.e(LOG_TAG, "TODO responsePhbEntries");
		return null;
	}

	private Object responseCbConfig(Parcel p) {
		Rlog.e(LOG_TAG, "TODO responsePhbEntries");
		return null;
	}

	private Object responseGetNitzTime(Parcel p) {
		Object[] arrayOfObject = new Object[2];
		String result = p.readString();
		long response = p.readLong();
		arrayOfObject[0] = result;
		arrayOfObject[1] = Long.valueOf(response);
		return arrayOfObject;
	}

	@Override
  public void setPreferredNetworkType(int networkType , Message response) {
      RILRequest rr = RILRequest.obtain(
              RILConstants.RIL_REQUEST_SET_PREFERRED_NETWORK_TYPE, response);

      rr.mParcel.writeInt(1);
      rr.mParcel.writeInt(networkType + 100);

      mSetPreferredNetworkType = networkType;
      mPreferredNetworkType = networkType;

      if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
              + " : " + networkType);

      send(rr);
  }

  @Override
  protected void switchToRadioState(RadioState newState) {
      if (newState.isOn()) {
      	//setRadioMode(0, null);
        disableVTCapability();
      } 
      Rlog.i(LOG_TAG, "Radio switch state to " + newState);
      setRadioState(newState);
  }

  private void disableVTCapability() {
    RILRequest rr = RILRequest.obtain(RIL_REQUEST_DISABLE_VT_CAPABILITY, null);
    riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
    send(rr);
  }
  
  public void setRadioMode(int mode, Message result) {
    RILRequest rr = RILRequest.obtain(RIL_REQUEST_DUAL_SIM_MODE_SWITCH,
				result);

		riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

		rr.mParcel.writeInt(1);
		rr.mParcel.writeInt(mode);

		send(rr);
  }
  
   public void setGprsTransferType(int type, Message result) {
    RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_GPRS_TRANSFER_TYPE, result);
    riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
    rr.mParcel.writeInt(1);
    rr.mParcel.writeInt(type);
    Intent intent = new Intent("android.intent.action.GPRS_TRANSFER_TYPE");
    intent.putExtra("gemini.gprs.transfer.type", type);
    this.mContext.sendStickyBroadcast(intent);
    riljLog("Broadcast: ACTION_GPRS_CONNECTION_TYPE_SELECT");
    send(rr);
  }

  public void setGprsConnType(int type, Message result)  {
    RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_GPRS_CONNECT_TYPE, result);
    riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
    rr.mParcel.writeInt(1);
    rr.mParcel.writeInt(type);
    send(rr);
  }
}
