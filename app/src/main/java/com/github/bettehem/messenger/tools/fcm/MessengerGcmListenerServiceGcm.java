package com.github.bettehem.messenger.tools.fcm;

import android.content.Intent;
import android.os.Handler;

import com.github.bettehem.androidtools.Preferences;
import com.github.bettehem.androidtools.misc.Time;
import com.github.bettehem.messenger.MainActivity;
import com.github.bettehem.messenger.tools.background.ReceivedMessage;
import com.github.bettehem.messenger.tools.background.RequestResponse;
import com.github.bettehem.messenger.tools.items.MessageItem;
import com.github.bettehem.messenger.tools.listeners.GcmReceivedListener;
import com.github.bettehem.messenger.tools.listeners.MessageItemListener;
import com.github.bettehem.messenger.tools.managers.ChatsManager;
import com.github.bettehem.messenger.tools.managers.EncryptionManager;
import com.github.bettehem.messenger.tools.users.Sender;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Calendar;
import java.util.Map;

import static com.github.bettehem.messenger.tools.ui.CustomNotificationKt.notification;

public class MessengerGcmListenerServiceGcm extends FirebaseMessagingService implements GcmReceivedListener {
    private static MessageItemListener messageItemListener;

    @Override
    public void onMessageReceived(RemoteMessage message){
        final Map data = message.getData();

        String type = (String) data.get("type");
        Handler mainHandler = new Handler(getApplication().getMainLooper());
        Runnable myRunnable;

        if (type != null){

            //TODO: Check if a chat has been started with the sender already and if so,
            // ignore message types: chatrequest, requestresponse, startChat
            switch (type.toLowerCase()){

                case "notification":
                    notification(getApplicationContext(), (String)  data.get("title"), (String) data.get("message"), false);
                    break;

                case "chatrequest":
                    final String requestSender = getSender((String) data.get("sender"));
                    myRunnable = () -> ChatsManager.handleChatRequest(getApplicationContext(), requestSender, (String) data.get("key"), (String) data.get("iv"));
                    mainHandler.post(myRunnable);
                    break;

                case "message":
                    ReceivedMessage receivedMessage = new ReceivedMessage(getApplicationContext(), true, data.get("sender") + ChatsManager.SPLITTER + data.get("isSecretMessage"), (String) data.get("message"), (String) data.get("messageId"));
                    receivedMessage.setMessageListener(this);
                    receivedMessage.getMessage();
                    break;

                case "requestresponse":
                    String responseSender = getSender((String) data.get("sender"));
                    boolean requestAccepted = Boolean.valueOf((String) data.get("requestAccepted"));
                    String password = (String) data.get("password");
                    RequestResponse requestResponse = new RequestResponse(getApplicationContext(), requestAccepted, responseSender, password);
                    requestResponse.handleResponse();
                    break;

                case "startchat":
                    //TODO: change to use hash of encrypted username for "sender" parameter
                    String sender = getSender((String) data.get("sender"));

                    //check if right sender
                    if (data.get("hash") != null && EncryptionManager.createHash(Preferences.loadString(getApplicationContext(), "iv", sender)).contentEquals((String) data.get("hash"))){
                        final String chatStartSender = sender;
                        boolean correctPassword = Boolean.valueOf((String) data.get("correctPassword"));
                        if (correctPassword){
                            myRunnable = () -> ChatsManager.startNormalChat(getApplication(), chatStartSender, null);
                            mainHandler.post(myRunnable);

                        }else {
                            //TODO: Tell user that their password was incorrect
                        }
                    }
                    break;

                case "deliveryreport":
                    String reportSender = getSender((String) data.get("sender"));
                    String messageId = (String) data.get("messageId");
                    //save report
                    MessageItem item = ChatsManager.saveDeliveryReport(getApplicationContext(), reportSender, messageId);
                    if (messageItemListener != null && Preferences.loadBoolean(getApplicationContext(), "appVisible")){
                        messageItemListener.onMessageItemUpdated(item);
                    }
                    break;

            }
        }
    }

    private String getSender(String sender){
        return sender.contains(ChatsManager.SPLITTER) ? sender.replace(ChatsManager.SPLITTER, " ") : sender;
    }

    public static void setMessageItemListener(MessageItemListener messageItemListener){
        MessengerGcmListenerServiceGcm.messageItemListener = messageItemListener;
    }

    @Override
    public void onMessageReceived(Sender senderData, String message, String messageId) {
        //set an intent for the notification
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.putExtra("type", "message");
        intent.putExtra("username", senderData.userName);
        //show a notification
        notification(getApplicationContext(), "Messenger - " + senderData.userName, message, senderData.isSecretMessage, intent);
        //save message
        if (messageId != null){
            ChatsManager.saveMessage(getApplicationContext(), senderData.userName, new MessageItem(message, messageId, new Time(Calendar.getInstance()), false));
        }
        if (messageItemListener != null){
            messageItemListener.onMessageListUpdated(getApplicationContext());
        }

        //send delivery report
        ChatsManager.sendDeliveryReport(getApplicationContext(), senderData.userName, messageId);
    }
}
