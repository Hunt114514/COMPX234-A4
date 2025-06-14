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
            System.out.println("Received: <" + request + ">"); 
            if (!request.startsWith("DOWNLOAD ")) {
                continue;
            }

            String filename = request.substring(9).trim();
            new Thread(() -> handleFileTransmission(receivePacket, filename)).start();
        }
    }
    private static void handleFileTransmission(DatagramPacket clientPacket, String filename) {
        try {
            File file = new File(filename);
            if (!file.exists()) {
                sendResponse(clientPacket, "ERR " + filename + " NOT_FOUND");
                return;
            }

            Random random = new Random();
            int filePort = MIN_PORT + random.nextInt(MAX_PORT - MIN_PORT + 1);
            DatagramSocket fileSocket = new DatagramSocket(filePort);

            long fileSize = file.length();
            String response = String.format("OK %s SIZE %d PORT %d", filename, fileSize, filePort);
            sendResponse(clientPacket, response);

            RandomAccessFile raf = new RandomAccessFile(file, "r");
            byte[] receiveData = new byte[2048];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            while (true) {
                fileSocket.receive(receivePacket);
                String request = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();
                System.out.println("Received: <" + request + ">");

                String[] parts = request.split("\\s+");
                if (parts.length < 2 || !parts[0].equals("FILE") || !parts[1].equals(filename)) {
                    continue;
                }

                if (request.equals("FILE " + filename + " CLOSE")) {
                    response = "FILE " + filename + " CLOSE_OK";
                    sendResponse(receivePacket, response);
                    break;
                } else if (parts.length == 7 && parts[2].equals("GET") && parts[3].equals("START") && parts[5].equals("END")) {
                    long start = Long.parseLong(parts[4]);
                    long end = Long.parseLong(parts[6]);
                    if (end >= fileSize || start > end) {
                        continue;
                    }

                    int length = (int) (end - start + 1);
                    byte[] data = new byte[length];
                    raf.seek(start);
                    raf.read(data);

                    String base64Data = Base64.getEncoder().withoutPadding().encodeToString(data).replaceAll("\\r?\\n", "");
                    StringBuilder responseBuilder = new StringBuilder();
                    responseBuilder.append("FILE ").append(filename).append(" OK START ").append(start)
                                  .append(" END ").append(end).append(" DATA ").append(base64Data);
                    response = responseBuilder.toString();
                    System.out.println("Sending: <" + response + "> (Base64 length: " + base64Data.length() + ")"); 
                    DatagramPacket sendPacket = new DatagramPacket(response.getBytes(), response.length(),
                            receivePacket.getAddress(), receivePacket.getPort());
                    fileSocket.send(sendPacket);
                }
            }

            raf.close();
            fileSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private static void sendResponse(DatagramPacket clientPacket, String response) throws IOException {
        DatagramPacket sendPacket = new DatagramPacket(response.getBytes(), response.length(),
                clientPacket.getAddress(), clientPacket.getPort());
        DatagramSocket socket = new DatagramSocket();
        socket.send(sendPacket);
        socket.close();
        System.out.println("Sent: <" + response + ">"); 
    }
}