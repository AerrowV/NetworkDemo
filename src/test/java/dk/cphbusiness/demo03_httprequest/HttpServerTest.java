package dk.cphbusiness.demo03_httprequest;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

class HttpServerTest {

    private static final int PORT = 9090;
    private static final String IP = "127.0.0.1";

    private static HttpServer httpServer = new HttpServer();

    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private String response = "";

    @BeforeAll
    public static void setup() {
        System.out.println("setup");
    }

    @BeforeEach
    public void setupEach() {
        System.out.println("setupEach");
        new Thread(()->httpServer.startConnection(PORT)).start();
    }

    @Test
    void startConnection() {
        //Arrange
        StringBuilder actual = new StringBuilder();
        try {
            Socket clientSocket = new Socket(IP, PORT);
            PrintWriter out;
            BufferedReader in;
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out.println("GET / HTTP/1.1");
            String responseLine = "";

            while((responseLine = in.readLine()) != null) {
                actual.append(responseLine);
                actual.append(System.lineSeparator());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        //Act
        String expected = "HTTP/1.1 200 OK" + System.lineSeparator() +
                "Date: Mon, 23 May 2022 22:38:34 GMT" + System.lineSeparator() +
                "Server: Apache/2.4.1 (Unix)\n" +
                "Content-Type: text/html; charset=UTF-8" + System.lineSeparator() +
                "Content-Length: 87" + System.lineSeparator() +
                "Connection: close" + System.lineSeparator() +
                System.lineSeparator() +
                "\"<html><head><title>hello world</title></head><body><h1>Hello World</h1></body></html>";

        //Assert
        assertEquals(expected, actual);

    }
}