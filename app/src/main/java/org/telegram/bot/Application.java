package org.telegram.bot;

import org.telegram.api.*;
import org.telegram.api.auth.TLAuthorization;
import org.telegram.api.auth.TLSentCode;
import org.telegram.api.engine.*;
import org.telegram.api.messages.TLAbsSentMessage;
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
        api.doRpcCall(new TLRequestMessagesSendMessage(new TLInputPeerChat(chatId), message, rnd.nextInt()),
                15 * 60000,
                new RpcCallbackEx<TLAbsSentMessage>() {
                    @Override
                    public void onConfirmed() {

                    }

                    @Override
                    public void onResult(TLAbsSentMessage result) {

                    }

                    @Override
                    public void onError(int errorCode, String message) {
                    }
                });
    }

    private static void sendMessageUser(int uid, String message) {
        api.doRpcCall(new TLRequestMessagesSendMessage(new TLInputPeerContact(uid), message, rnd.nextInt()),
                15 * 60000,
                new RpcCallbackEx<TLAbsSentMessage>() {
                    @Override
                    public void onConfirmed() {

                    }

                    @Override
                    public void onResult(TLAbsSentMessage result) {

                    }

                    @Override
                    public void onError(int errorCode, String message) {

                    }
                });
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

    private static String getWalkerString(int len, int position) {
        int realPosition = position % len * 2;
        if (realPosition > len) {
            realPosition = len - (realPosition - len);
        }
        String res = "|";
        for (int i = 0; i < realPosition; i++) {
            res += ".";
        }
        res += "\uD83D\uDEB6";
        for (int i = realPosition + 1; i < len; i++) {
            res += ".";
        }
        return res + "|";
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
                if (count <= 0) {
                    count = 32;
                }
                if (count > 4 * 1024) {
                    sendMessage(peerState, WarAndPeace.ANGRY);
                } else {
                    sendMessage(peerState, "Random: " + (generateRandomString(count)));
                }
            } else {
                sendMessage(peerState, "Random: " + (generateRandomString(32)));
            }
        } else if (command.equals("start_flood")) {
            int delay = 15;
            if (args.length == 2) {
                delay = Integer.parseInt(args[1].trim());
            }
            peerState.setMessageSendDelay(delay);
            peerState.setSpamEnabled(true);
            peerState.setLastMessageSentTime(0);
            sendMessage(peerState, "Flood enabled with delay " + delay + " sec");
        } else if (command.equals("stop_flood")) {
            peerState.setSpamEnabled(false);
            sendMessage(peerState, "Flood disabled");
        } else if (command.equals("ping")) {
            for (int i = 0; i < 50; i++) {
                sendMessage(peerState, "pong " + getWalkerString(10, i) + " #" + i);
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } else if (command.equals("war_ping")) {
            for (int i = 0; i < 50; i++) {
                sendMessage(peerState, WarAndPeace.TEXT2);
            }
        } else if (command.equals("war")) {
            sendMessage(peerState, WarAndPeace.TEXT2);
        } else if (command.equals("war2")) {
            sendMessage(peerState, WarAndPeace.TEXT);
        } else if (command.equals("help")) {
            sendMessage(peerState, "Bot commands:\n" +
                    "bot enable_forward/disable_forward - forwarding of incoming messages\n" +
                    "bot start_flood [delay] - Start flood with [delay] sec (default = 15)\n" +
                    "bot stop_flood - Stop flood\n" +
                    "bot random [len] - Write random string of length [len] (default = 32)\n" +
                    "bot ping - ping with 50 pongs\n" +
                    "bot war - war and peace fragment\n" +
                    "bot war2 - alternative war and peace fragment (currently unable to send)\n" +
                    "bot war_ping - ping with 50 war and peace fragments\n");
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
                            sendMessage(state, "Flood " + getWalkerString(10, messageId) + " #" + messageId);
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

    private static void createApi() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Use test DC? (write test for test servers): ");
        String res = reader.readLine();
        boolean useTest = res.equals("test");
        if (!useTest) {
            System.out.println("Using production servers");
        } else {
            System.out.println("Using test servers");
        }
        apiState = new MemoryApiState(useTest);
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
