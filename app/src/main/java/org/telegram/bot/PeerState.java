package org.telegram.bot;

/**
 * Created by ex3ndr on 13.01.14.
 */
public class PeerState {
    private int id;
    private boolean isUser;
    private int messagesSent;
    private long lastMessageSentTime;
    private boolean isForwardingEnabled;
    private boolean isSpamEnabled;
    private int messageSendDelay;

    public PeerState(int id, boolean isUser) {
        this.id = id;
        this.isUser = isUser;
    }

    public int getId() {
        return id;
    }

    public boolean isUser() {
        return isUser;
    }

    public boolean isForwardingEnabled() {
        return isForwardingEnabled;
    }

    public void setForwardingEnabled(boolean isForwardingEnabled) {
        this.isForwardingEnabled = isForwardingEnabled;
    }

    public boolean isSpamEnabled() {
        return isSpamEnabled;
    }

    public void setSpamEnabled(boolean isSpamEnabled) {
        this.isSpamEnabled = isSpamEnabled;
    }

    public int getMessageSendDelay() {
        return messageSendDelay;
    }

    public void setMessageSendDelay(int messageSendDelay) {
        this.messageSendDelay = messageSendDelay;
    }

    public int getMessagesSent() {
        return messagesSent;
    }

    public void setMessagesSent(int messagesSent) {
        this.messagesSent = messagesSent;
    }

    public long getLastMessageSentTime() {
        return lastMessageSentTime;
    }

    public void setLastMessageSentTime(long lastMessageSentTime) {
        this.lastMessageSentTime = lastMessageSentTime;
    }
}
