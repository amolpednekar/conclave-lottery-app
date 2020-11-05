package com.r3.conclave.sample.host;

import com.r3.conclave.common.EnclaveInstanceInfo;
import com.r3.conclave.common.EnclaveMode;
import com.r3.conclave.common.OpaqueBytes;
import com.r3.conclave.host.EnclaveHost;
import com.r3.conclave.host.EnclaveLoadException;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * This class demonstrates how to load an enclave and exchange byte arrays with it.
 */
public class LotteryHost {
    public static void main(String[] args) throws EnclaveLoadException, IOException {
        final String enclaveClassName = "com.example.conclave.LotteryEnclave";
        System.out.println("Initializing LotteryHost");

        // Report whether the platform supports hardware enclaves.
        try {
            EnclaveHost.checkPlatformSupportsEnclaves(true);
            System.out.println("This platform supports enclaves in simulation, debug and release mode.");
        } catch (EnclaveLoadException e) {
            System.out.println("This platform currently only supports enclaves in simulation mode: " + e.getMessage());
        }

        // Let's open a TCP socket and implement a trivial protocol that lets a remote client use it.
        int port = 9999;
        System.out.println("Listening on port " + port + ". Use the client app to request for lottery numbers.");
        ServerSocket acceptor = new ServerSocket(port);
        Socket connection = acceptor.accept();

        // Create a output stream to communicate with the client
        DataOutputStream output = new DataOutputStream(connection.getOutputStream());

        // We start by loading the enclave using EnclaveHost, and passing the class name of the Enclave subclass
        // that we defined in our enclave module. This will start the sub-JVM and initialise the Enclave subclass
        EnclaveHost enclave = EnclaveHost.load(enclaveClassName);

        // SPID & Attestation keys required for non-simulation modes
        if (enclave.getEnclaveMode() != EnclaveMode.SIMULATION && args.length != 2) {
            throw new IllegalArgumentException("You need to provide the SPID and attestation key as arguments for " +
                    enclave.getEnclaveMode() + " mode.");
        }
        OpaqueBytes spid = enclave.getEnclaveMode() != EnclaveMode.SIMULATION ? OpaqueBytes.parse(args[0]) : null;
        String attestationKey = enclave.getEnclaveMode() != EnclaveMode.SIMULATION ? args[1] : null;


        // Start the enclave
        enclave.start(spid, attestationKey, new EnclaveHost.MailCallbacks() {
            @Override
            public void postMail(byte[] encryptedBytes, String routingHint) {
                try {
                    sendArray(output, encryptedBytes);
                } catch (IOException e) {
                    System.err.println("Failed to send reply to client.");
                    e.printStackTrace();
                }
            }
        });

        // The attestation data must be provided to the client of the enclave, via whatever mechanism you like.
        final EnclaveInstanceInfo attestation = enclave.getEnclaveInstanceInfo();
        final byte[] attestationBytes = attestation.serialize();
        // It has a useful toString method.
        System.out.println(EnclaveInstanceInfo.deserialize(attestationBytes));
        // Just send the attestation straight to the client. It's signed so that is MITM-safe.
        sendArray(output, attestationBytes);

        // Lottery Application
        // The client will request the enclave to register 6-digit lottery numbers of its choosing
        // We read the encrypted numbers via the socket connection
        for(int i=0;i<5;i++){
            DataInputStream input = new DataInputStream(connection.getInputStream());
            byte[] mailBytes = new byte[input.readInt()];
            input.readFully(mailBytes);
            // Deliver the data to the enclave.
            // The enclave will give us the encrypted reply in the callback we provided to enclave.start function
            enclave.deliverMail(1, mailBytes);
        }

        // Lottery numbers allocation is complete, now its time to draw a winner
        // This example requires the host to send a "DECLARE" command to calcuate the winner to the enclave
        // This could also be sent by a client, e.g. The lottery commission

        System.out.println();
        final Charset utf8 = StandardCharsets.UTF_8;
        byte[] winningLotteryBytes = enclave.callEnclave("DECLARE".getBytes(utf8));
        String winningLottery = new String(winningLotteryBytes, utf8);
        System.out.println("[Host] Declaring Lottery!: " + winningLottery);
        System.out.println();

        // Closing the output stream closes the connection. Different clients will block each other but this
        // is just a hello world sample.
        output.close();
    }

    private static void sendArray(DataOutputStream stream, byte[] bytes) throws IOException {
        stream.writeInt(bytes.length);
        stream.write(bytes);
        stream.flush();
    }
}
