import java.net.*;
import java.io.*;
import java.util.Base64;
public class UDPclient {
    private static final int TIMEOUT = 1000; 
    private static final int MAX_RETRIES = 5; 
    private static final int MAX_BLOCK_SIZE = 1000; 

    public static void main(String[] args) throws IOException {
      
        if (args.length != 3) {
            System.err.println("Usage: java UDPclient <hostname> <port> <files.txt>");
            System.exit(1);
        }

        String hostname = args[0];
        int port = Integer.parseInt(args[1]);
        String fileList = args[2];

        BufferedReader fileReader;
        try {
            fileReader = new BufferedReader(new FileReader(fileList));
        } catch (FileNotFoundException e) {
            System.err.println("Error: File list " + fileList + " not found");
            return;
        }

     
        DatagramSocket socket = new DatagramSocket();
        InetAddress serverAddress = InetAddress.getByName(hostname);

       
        String filename;
        while ((filename = fileReader.readLine()) != null) {
            filename = filename.trim();
            if (filename.isEmpty()) continue;
            System.out.print("Downloading " + filename + ": ");
            downloadFile(socket, serverAddress, port, filename);
        }
        fileReader.close();
        socket.close();
    }
  
    private static void downloadFile(DatagramSocket socket, InetAddress serverAddress, int port, String filename) throws IOException {
    
        String response = sendAndReceive(socket, serverAddress, port, "DOWNLOAD " + filename);
        if (response == null || response.startsWith("ERR")) {
            System.out.println("\nError: " + (response != null ? response : "No response from server"));
            return;
        }

       
        String[] parts = response.trim().split("\\s+");
        if (parts.length != 6 || !parts[0].equals("OK")) {
            System.out.println("\nInvalid response: " + response + ", parts: " + Arrays.toString(parts));
            return;
        }

        long fileSize = Long.parseLong(parts[3]);
        int filePort = Integer.parseInt(parts[5]);
        System.out.println("Size: " + fileSize + " bytes, Port: " + filePort);
      
        RandomAccessFile raf = new RandomAccessFile(filename, "rw");

        long currentByte = 0;
        while (currentByte < fileSize) {
            long endByte = Math.min(currentByte + MAX_BLOCK_SIZE - 1, fileSize - 1);
            String request = String.format("FILE %s GET START %d END %d", filename, currentByte, endByte);
            response = sendAndReceive(socket, serverAddress, filePort, request);

            if (response == null || !response.startsWith("FILE " + filename + " OK")) {
                System.out.println("\nError receiving block: " + (response != null ? response : "No response"));
                raf.close();
                return;
            }
                       
            String[] dataParts = response.trim().split("\\s+");
            if (dataParts.length < 7 || !dataParts[0].equals("FILE") || !dataParts[1].equals(filename) ||
                !dataParts[2].equals("OK") || !dataParts[3].equals("START") || !dataParts[5].equals("END")) {
                System.out.println("\nInvalid block response: " + response + ", parts: " + Arrays.toString(dataParts));
                raf.close();
                return;
            }

            int dataIndex = response.indexOf("DATA");
            if (dataIndex == -1 || dataIndex + 5 >= response.length()) {
                System.out.println("\nInvalid block response: No DATA field in " + response);
                raf.close();
                return;
            }
            String base64Data = response.substring(dataIndex + 5).trim();
            byte[] data;
            try {
                data = Base64.getDecoder().decode(base64Data);
            } catch (IllegalArgumentException e) {
                System.out.println("\nBase64 decoding error: " + e.getMessage() + ", data: " + base64Data);
                raf.close();
                return;
            }
            long startByte = Long.parseLong(dataParts[4]);
            raf.seek(startByte);
            raf.write(data);
            currentByte = startByte + data.length;
            System.out.print("*");
        }
        // Send CLOSE request
        response = sendAndReceive(socket, serverAddress, filePort, "FILE " + filename + " CLOSE");
        if (response == null || !response.equals("FILE " + filename + " CLOSE_OK")) {
            System.out.println("\nError closing file: " + (response != null ? response : "No response"));
        } else {
            System.out.println("\nDownload completed: " + filename);
        }
        raf.close();
    }
    private static String sendAndReceive(DatagramSocket socket, InetAddress address, int port, String request) throws IOException {
        byte[] sendData = request.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, address, port);
        
        int retries = 0;
        int currentTimeout = TIMEOUT;
        while (retries < MAX_RETRIES) {
            socket.send(sendPacket);
            socket.setSoTimeout(currentTimeout);

            try {
                socket.receive(receivePacket);
                String response = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();
                System.out.println("Received: <" + response + ">"); // Debug output
                return response;
            } catch (SocketTimeoutException e) {
                retries++;
                System.out.println("Timeout, retrying (" + retries + "/" + MAX_RETRIES + ") for request: " + request);
                currentTimeout *= 2; // Double timeout
                if (retries == MAX_RETRIES) {
                    System.out.println("Failed after " + MAX_RETRIES + " retries for request: " + request);
                    return null;
                }
            }
        }
        return null;
    }
}