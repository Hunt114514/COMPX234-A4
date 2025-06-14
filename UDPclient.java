import java.net.*;
import java.io.*;
import java.util.Base64;
public class UDPclient {
    private static final int TIMEOUT = 1000; // Initial timeout (milliseconds)
    private static final int MAX_RETRIES = 5; // Maximum retry attempts
    private static final int MAX_BLOCK_SIZE = 1000; // Maximum block size in bytes

    public static void main(String[] args) throws IOException {
        // Verify command line arguments
        if (args.length != 3) {
            System.err.println("Usage: java UDPclient <hostname> <port> <files.txt>");
            System.exit(1);
        }

        String hostname = args[0];
        int port = Integer.parseInt(args[1]);
        String fileList = args[2];

        // Open file list
        BufferedReader fileReader;
        try {
            fileReader = new BufferedReader(new FileReader(fileList));
        } catch (FileNotFoundException e) {
            System.err.println("Error: File list " + fileList + " not found");
            return;
        }

        // Create UDP socket
        DatagramSocket socket = new DatagramSocket();
        InetAddress serverAddress = InetAddress.getByName(hostname);

        // Process files in the list sequentially
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