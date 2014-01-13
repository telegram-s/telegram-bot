package org.telegram.bot;

import org.telegram.api.*;
import org.telegram.api.auth.TLAuthorization;
import org.telegram.api.auth.TLSentCode;
import org.telegram.api.engine.*;
import org.telegram.api.requests.*;
import org.telegram.api.updates.TLState;
import org.telegram.bot.engine.MemoryApiState;
import org.telegram.mtproto.log.LogInterface;
import org.telegram.mtproto.log.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

/**
 * Created by ex3ndr on 13.01.14.
 */
public class Application {
    private static HashMap<Integer, PeerState> userStates = new HashMap<Integer, PeerState>();
    private static HashMap<Integer, PeerState> chatStates = new HashMap<Integer, PeerState>();
    private static MemoryApiState apiState;
    private static TelegramApi api;
    private static Random rnd = new Random();
    private static long lastOnline = System.currentTimeMillis();

    public static void main(String[] args) throws IOException {
        disableLogging();
        createApi();
        login();
        workLoop();
    }

    private static synchronized String generateRandomString(int size) {
        String res = "";
        for (int i = 0; i < size; i++) {
            res += (char) ('a' + rnd.nextInt('z' - 'a'));
        }
        return res;
    }

    private static synchronized PeerState[] getAllSpamPeers() {
        ArrayList<PeerState> peerStates = new ArrayList<PeerState>();
        for (PeerState state : userStates.values()) {
            if (state.isSpamEnabled()) {
                peerStates.add(state);
            }
        }
        for (PeerState state : chatStates.values()) {
            if (state.isSpamEnabled()) {
                peerStates.add(state);
            }
        }
        return peerStates.toArray(new PeerState[0]);
    }

    private static synchronized PeerState getUserPeer(int uid) {
        if (!userStates.containsKey(uid)) {
            userStates.put(uid, new PeerState(uid, true));
        }

        return userStates.get(uid);
    }

    private static synchronized PeerState getChatPeer(int chatId) {
        if (!chatStates.containsKey(chatId)) {
            chatStates.put(chatId, new PeerState(chatId, false));
        }

        return chatStates.get(chatId);
    }

    private static void sendMessage(PeerState peerState, String message) {
        if (peerState.isUser()) {
            sendMessageUser(peerState.getId(), message);
        } else {
            sendMessageChat(peerState.getId(), message);
        }
    }

    private static void sendMessageChat(int chatId, String message) {
        api.doRpcCallWeak(new TLRequestMessagesSendMessage(new TLInputPeerChat(chatId), message, rnd.nextInt()));
    }

    private static void sendMessageUser(int uid, String message) {
        api.doRpcCallWeak(new TLRequestMessagesSendMessage(new TLInputPeerContact(uid), message, rnd.nextInt()));
    }

    private static void onIncomingMessageUser(int uid, String message) {
        System.out.println("Incoming message from user #" + uid + ": " + message);
        PeerState peerState = getUserPeer(uid);
        if (message.startsWith("bot")) {
            sendMessageUser(uid, "Received: " + message);
            processCommand(message.trim().substring(3).trim(), peerState);
        } else {
            if (peerState.isForwardingEnabled()) {
                sendMessageUser(uid, "FW: " + message);
            }
        }
    }

    private static void onIncomingMessageChat(int chatId, String message) {
        System.out.println("Incoming message from in chat #" + chatId + ": " + message);
        PeerState peerState = getChatPeer(chatId);
        if (message.startsWith("bot")) {
            processCommand(message.trim().substring(3).trim(), getChatPeer(chatId));
        } else {
            if (peerState.isForwardingEnabled()) {
                sendMessageChat(chatId, "FW: " + message);
            }
        }
    }

    private static void processCommand(String message, PeerState peerState) {
        String[] args = message.split(" ");
        if (args.length == 0) {
            sendMessage(peerState, "Unknown command");
        }
        String command = args[0].trim().toLowerCase();
        if (command.equals("enable_forward")) {
            sendMessage(peerState, "Forwarding enabled");
            peerState.setForwardingEnabled(true);
        } else if (command.equals("disable_forward")) {
            peerState.setForwardingEnabled(false);
            sendMessage(peerState, "Forwarding disabled");
        } else if (command.equals("random")) {
            if (args.length == 2) {
                int count = Integer.parseInt(args[1].trim());
                sendMessage(peerState, "Random: " + (generateRandomString(count)));
            } else {
                sendMessage(peerState, "Random: " + (generateRandomString(32)));
            }
        } else if (command.equals("start_spam")) {
            int delay = 15;
            if (args.length == 2) {
                delay = Integer.parseInt(args[1].trim());
            }
            peerState.setMessageSendDelay(delay);
            peerState.setSpamEnabled(true);
            peerState.setLastMessageSentTime(0);
            sendMessage(peerState, "Spam enabled with delay " + delay + " sec");
        } else if (command.equals("stop_spam")) {
            peerState.setSpamEnabled(false);
            sendMessage(peerState, "Spam disabled");
        } else {
            sendMessage(peerState, "Unknown command '" + args[0] + "'");
        }
    }

    private static void workLoop() {
        while (true) {
            try {
                PeerState[] states = getAllSpamPeers();
                for (PeerState state : states) {
                    if (state.isSpamEnabled()) {
                        if (System.currentTimeMillis() - state.getLastMessageSentTime() > state.getMessageSendDelay() * 1000L) {
                            int messageId = state.getMessagesSent() + 1;
                            state.setMessagesSent(messageId);
                            sendMessage(state, "Spam #" + messageId + ": " + generateRandomString(32));
                            state.setLastMessageSentTime(System.currentTimeMillis());
                        }
                    }
                }
                if (System.currentTimeMillis() - lastOnline > 60 * 1000) {
                    api.doRpcCallWeak(new TLRequestAccountUpdateStatus(false));
                    lastOnline = System.currentTimeMillis();
                }
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static void disableLogging() {
        Logger.registerInterface(new LogInterface() {
            @Override
            public void w(String tag, String message) {

            }

            @Override
            public void d(String tag, String message) {

            }

            @Override
            public void e(String tag, Throwable t) {

            }
        });
        org.telegram.api.engine.Logger.registerInterface(new LoggerInterface() {
            @Override
            public void w(String tag, String message) {

            }

            @Override
            public void d(String tag, String message) {

            }

            @Override
            public void e(String tag, Throwable t) {

            }
        });
    }

    private static void createApi() {
        apiState = new MemoryApiState();
        api = new TelegramApi(apiState, new AppInfo(5, "console", "???", "???", "en"), new ApiCallback() {

            @Override
            public void onAuthCancelled(TelegramApi api) {

            }

            @Override
            public void onUpdatesInvalidated(TelegramApi api) {

            }

            @Override
            public void onUpdate(TLAbsUpdates updates) {
                if (updates instanceof TLUpdateShortMessage) {
                    onIncomingMessageUser(((TLUpdateShortMessage) updates).getFromId(), ((TLUpdateShortMessage) updates).getMessage());
                } else if (updates instanceof TLUpdateShortChatMessage) {
                    onIncomingMessageChat(((TLUpdateShortChatMessage) updates).getChatId(), ((TLUpdateShortChatMessage) updates).getMessage());
                }
            }
        });
    }

    private static void login() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Loading fresh DC list...");
        TLConfig config = api.doRpcCallNonAuth(new TLRequestHelpGetConfig());
        apiState.updateSettings(config);
        System.out.println("completed.");
        System.out.print("Phone number for bot:");
        String phone = reader.readLine();
        System.out.print("Sending sms to phone " + phone + "...");
        TLSentCode sentCode;
        try {
            sentCode = api.doRpcCallNonAuth(new TLRequestAuthSendCode(phone, 0, 5, "1c5c96d5edd401b1ed40db3fb5633e2d", "en"));
        } catch (RpcException e) {
            if (e.getErrorCode() == 303) {
                int destDC;
                if (e.getErrorTag().startsWith("NETWORK_MIGRATE_")) {
                    destDC = Integer.parseInt(e.getErrorTag().substring("NETWORK_MIGRATE_".length()));
                } else if (e.getErrorTag().startsWith("PHONE_MIGRATE_")) {
                    destDC = Integer.parseInt(e.getErrorTag().substring("PHONE_MIGRATE_".length()));
                } else if (e.getErrorTag().startsWith("USER_MIGRATE_")) {
                    destDC = Integer.parseInt(e.getErrorTag().substring("USER_MIGRATE_".length()));
                } else {
                    throw e;
                }
                api.switchToDc(destDC);
                sentCode = api.doRpcCallNonAuth(new TLRequestAuthSendCode(phone, 0, 5, "1c5c96d5edd401b1ed40db3fb5633e2d", "en"));
            } else {
                throw e;
            }
        }
        System.out.println("sent.");
        System.out.print("Activation code:");
        String code = reader.readLine();
        TLAuthorization auth = api.doRpcCallNonAuth(new TLRequestAuthSignIn(phone, sentCode.getPhoneCodeHash(), code));
        apiState.setAuthenticated(apiState.getPrimaryDc(), true);
        System.out.println("Activation complete.");
        System.out.print("Loading initial state...");
        TLState state = api.doRpcCall(new TLRequestUpdatesGetState());
        System.out.println("loaded.");
    }
}
