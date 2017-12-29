package io.github.tiagoshibata.gpsdclient;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.concurrent.ArrayBlockingQueue;

class UdpSensorStream {
    private final String TAG = "UdpSensorStream";

    private class NetworkThread extends Thread {
        private ArrayBlockingQueue<String> messageQueue = new ArrayBlockingQueue<>(30);
        private boolean running = true;
        private SocketAddress address;
        private DatagramSocket udpSocket;

        private NetworkThread(SocketAddress address) throws SocketException {
            this.address = address;
            udpSocket = new DatagramSocket();
        }

        @Override
        public void run() {
            while (running) {
                try {
                    byte[] message = messageQueue.take().getBytes();
                    udpSocket.send(
                            new DatagramPacket(message, message.length, address)
                    );
                } catch (InterruptedException e) {
                    // Ignored (will check "running" variable at end of loop)
                } catch (IOException e) {
                    Log.w(TAG, e.toString());
                }
            }
            udpSocket.close();
        }

        private void stopThread() {
            running = false;
            interrupt();
        }
    }
    private NetworkThread networkThread;

    UdpSensorStream(SocketAddress address) throws SocketException {
        networkThread = new NetworkThread(address);
        networkThread.start();
    }

    /**
     * Queue data to the networking thread.
     * <p>
     * The offer method is used, which is non-blocking, to avoid lockups if
     * called from the UI thread. Note, however, that the message might be
     * discarded if the queue is full (highly unlikely in our scenario, since
     * UDP is used for transport, which won't block for long, and GPS messages
     * have low frequency).
     *
     * @param  data data to be transmitted
     */
    void send(final String data) {
        if (!networkThread.messageQueue.offer(data))
            Log.w(TAG, "Failed to send: network queue full");
    }

    void stop() {
        networkThread.stopThread();
    }
}
