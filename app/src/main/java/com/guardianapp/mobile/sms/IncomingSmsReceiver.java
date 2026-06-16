package com.guardianapp.mobile.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

public class IncomingSmsReceiver extends BroadcastReceiver {

    private static final String TAG = "IncomingSmsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }
        String action = intent.getAction();
        if (!Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(action)
                && !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(action)) {
            return;
        }

        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null || messages.length == 0) {
            return;
        }

        StringBuilder body = new StringBuilder();
        String sender = null;
        for (SmsMessage message : messages) {
            if (message == null) {
                continue;
            }
            if (sender == null || sender.isBlank()) {
                sender = message.getDisplayOriginatingAddress();
            }
            String part = message.getDisplayMessageBody();
            if (part != null) {
                body.append(part);
            }
        }

        final String finalSender = sender;
        final String finalBody = body.toString();
        PendingResult pendingResult = goAsync();
        SmsThreatProcessor.processIncomingMessage(
                context.getApplicationContext(),
                finalSender,
                finalBody,
                null,
                new SmsThreatProcessor.ResultCallback() {
                    @Override
                    public void onProcessed(SmsThreatProcessor.ProcessResult result) {
                        SmsNotificationHelper.showIncomingSmsNotification(
                                context.getApplicationContext(),
                                result.getItem()
                        );
                        pendingResult.finish();
                    }

                    @Override
                    public void onError(Throwable error) {
                        Log.e(TAG, "SMS analysis failed", error);
                        SmsNotificationHelper.showAnalysisErrorNotification(
                                context.getApplicationContext(),
                                finalSender,
                                finalBody
                        );
                        pendingResult.finish();
                    }
                }
        );
    }
}
