import java.net.*;
import java.io.*;
import java.util.Base64;
import java.util.Random;

public class UDPserver {
    private static final int MIN_PORT = 50000;
    private static final int MAX_PORT = 51000;
    private static final int MAX_BLOCK_SIZE = 1000;

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java UDPserver <port>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        DatagramSocket welcomeSocket = new DatagramSocket(port);
        System.out.println("Server started on port " + port);

        while (true) {
            byte[] receiveData = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            welcomeSocket.receive(receivePacket);

            String request = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();
            System.out.println("Received: <" + request + ">"); // Debug output
            if (!request.startsWith("DOWNLOAD ")) {
                continue;
            }

            String filename = request.substring(9).trim();
            new Thread(() -> handleFileTransmission(receivePacket, filename)).start();
        }
    }