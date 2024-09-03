package dk.cphbusiness.demo06_mywebserver;

import dk.cphbusiness.demo05_fileserver.RequestFileServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class MyWebServer {

    private static final int PORT = 9090;

    public static void main(String[] args) {
        RequestFileServer server = new RequestFileServer();
        server.startConnection(PORT);
    }

    public void startConnection(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server is listening on port " + port);

            while (true) {  // Keep the server running
                Socket clientSocket = serverSocket.accept(); // Blocking call
                System.out.println("New client connected");

                try (PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                     BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {

                    // Read the request from the client
                    RequestFileServer.RequestDTO requestDTO = generateRequestObject(in);
                    String requestLine = requestDTO.getRequestLine();
                    String resource = requestLine.split(" ")[1];

                    // Handle special "Login" request
                    if (resource.equals("/login")) {
                        String response = "<html><body><h1>Login Successful</h1></body></html>";
                        String httpResponse = httpResponseWrapper(response);
                        out.println(httpResponse);

                        System.out.println("Login request received. Shutting down the server.");
                        break;  // Stop the server after login request
                    }

                    // Get the file from the resource
                    String response = getFile(resource);
                    String httpResponse = httpResponseWrapper(response);
                    out.println(httpResponse);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getFile(String resource) {
        resource = reformatResource(resource); // Remove leading / and add .html if not present

        String response = "";
        try {
            // Get the URL of the resource using getResource
            URL resourceUrl = RequestFileServer.class.getClassLoader().getResource(resource);

            if (resourceUrl == null) {
                // Resource not found, return a 404 response
                response = "<html><body><h1>404 Not Found</h1></body></html>";
            } else {
                // Convert URL to URI and read the file
                URI resourceUri = resourceUrl.toURI();
                Path resourcePath = Paths.get(resourceUri);

                // Read the content of the resource using Files.readString
                response = Files.readString(resourcePath);
            }

        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
            response = "<html><body><h1>404 Not Found</h1></body></html>";
        }
        return response;
    }


    private String reformatResource(String resource) {
        if (resource.equals("/")) {
            resource = "index.html";
        } else if (!resource.endsWith(".html")) {
            resource += "/index.html";
        }
        if (resource.startsWith("/")) {
            resource = resource.substring(1);
        }
        return resource;
    }

    public RequestFileServer.RequestDTO generateRequestObject(BufferedReader in) {
        String requestLine = null;
        Map<String, String> headers = null;
        Map<String, String> queryParams = null;
        Map<String, String> requestBodyData = new HashMap<>();
        RequestFileServer.RequestDTO requestDTO = new RequestFileServer.RequestDTO();

        try {
            StringBuilder requestBuilder = new StringBuilder();
            requestLine = in.readLine();

            if (requestLine == null || requestLine.isEmpty()) {
                throw new IllegalArgumentException("The request is lacking the request line and is therefore not a valid HTTP request");
            }

            if (!in.ready()) {
                requestDTO.setRequestLine(requestLine);
                return requestDTO;
            }

            String newLine;
            while (in.ready() && (newLine = in.readLine()) != null && !newLine.isEmpty()) {
                requestBuilder.append(newLine).append("\n");
            }

            headers = getHeadersFromRequest(requestBuilder);

            try {
                requestBodyData = getRequestBody(requestLine, requestBuilder);
                System.out.println("Request body: " + requestBodyData.toString());
            } catch (IllegalArgumentException e) {
                System.out.println("Not a POST, PUT or PATCH request");
            }

            queryParams = getQueryParameters(requestLine);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return new RequestFileServer.RequestDTO(requestLine, headers, queryParams, requestBodyData);
    }

    private Map<String, String> getHeadersFromRequest(StringBuilder requestBuilder) {
        Map<String, String> headers = new HashMap<>();
        for (String line : requestBuilder.toString().split("\n")) {
            if (line.isEmpty()) {
                break;
            }
            String[] parts = line.split(":");
            headers.put(parts[0], parts[1]);
        }
        return headers;
    }

    private Map<String, String> getRequestBody(String requestLine, StringBuilder requestBuilder) throws IOException {
        if (!(requestLine.contains("POST") || requestLine.contains("PUT") || requestLine.contains("PATCH"))) {
            throw new IllegalArgumentException("This request contains no body");
        }

        Map<String, String> requestBodyFormParameters = new HashMap<>();
        StringBuilder requestBodyBuilder = new StringBuilder();
        int contentLength = getContentLength(requestBuilder.toString());

        if (contentLength > 0) {
            char[] buffer = new char[contentLength];
            requestBodyBuilder.append(buffer);
        } else {
            throw new IllegalArgumentException("This request contains no body");
        }

        String[] paramStrings = requestBodyBuilder.toString().split("&");
        for (String paramString : paramStrings) {
            String[] parts = paramString.split("=");
            requestBodyFormParameters.put(parts[0], parts[1]);
        }
        return requestBodyFormParameters;
    }

    private static int getContentLength(String request) {
        String[] lines = request.split("\n");
        for (String line : lines) {
            if (line.startsWith("Content-Length:")) {
                return Integer.parseInt(line.substring("Content-Length:".length()).trim());
            }
        }
        return 0;
    }

    private static Map<String, String> getQueryParameters(String requestLine) {
        Map<String, String> queryParams = new HashMap<>();

        if (requestLine.split(" ").length < 2) {
            return queryParams;
        }

        String pathPart = requestLine.split(" ")[1];

        if (!pathPart.contains("?")) {
            return queryParams;
        }

        String queriesPart = pathPart.split("\\?")[1];

        String[] queries = queriesPart.contains("&") ? queriesPart.split("&") : new String[]{queriesPart};
        for (int i = 0; i < queries.length; i++) {
            String[] keyValue = queries[i].split("=");
            queryParams.put(keyValue[0], keyValue[1]);
        }
        return queryParams;
    }

    public static class RequestDTO {
        private String requestLine;
        private Map<String, String> headers;
        private Map<String, String> queryParams;
        private Map<String, String> requestBody;

        public RequestDTO() {
        }

        public RequestDTO(String requestLine, Map<String, String> headers, Map<String, String> queryParams, Map<String, String> requestBody) {
            this.requestLine = requestLine;
            this.headers = headers;
            this.queryParams = queryParams;
            this.requestBody = requestBody;
        }

        public void setRequestLine(String requestLine) {
            this.requestLine = requestLine;
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers = headers;
        }

        public void setQueryParams(Map<String, String> queryParams) {
            this.queryParams = queryParams;
        }

        public void setRequestBody(Map<String, String> requestBody) {
            this.requestBody = requestBody;
        }

        public String getRequestLine() {
            return requestLine;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public Map<String, String> getQueryParams() {
            return queryParams;
        }

        public Map<String, String> getRequestBody() {
            return requestBody;
        }

        @Override
        public String toString() {
            return "requestLine='" + requestLine + '\'' +
                    ", headers=" + headers +
                    ", queryParams=" + queryParams +
                    ", requestBody=" + requestBody;
        }
    }

    private String httpResponseWrapper(String responseBody) {
        String responseHeader = "HTTP/1.1 200 OK" + System.lineSeparator() +
                "Date: Mon, 23 May 2022 22:38:34 GMT" + System.lineSeparator() +
                "Server: Apache/2.4.1 (Unix)\n" +
                "Content-Type: text/html; charset=UTF-8" + System.lineSeparator() +
                "Content-Length: " + responseBody.length() + System.lineSeparator() +
                "Connection: close" + System.lineSeparator();

        return responseHeader + System.lineSeparator() + responseBody;
    }

}


