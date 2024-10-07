// File: SimpleVulnerableApp.java
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class SimpleVulnerableApp {
    
    // Hardcoded credentials (bad practice)
    private static final String DEFAULT_PASSWORD = "password123";

    // Insecure use of system properties
    public static void setSystemProperty(String key, String value) {
        System.setProperty(key, value); // This can potentially override critical system properties
    }

    // Method demonstrating potential null pointer dereference
    public void printMessage(String message) {
        if (message.equals("hello")) {  // Potential NPE if message is null
            System.out.println("Hello, world!");
        } else {
            System.out.println("Hello, user!");
        }
    }

    // Method demonstrating potential resource leak
    public void readFile(String filePath) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(filePath));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Potential issue: not properly closing the reader if an exception occurs
            try {
                if (reader != null) reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        SimpleVulnerableApp app = new SimpleVulnerableApp();
        app.printMessage(null); // Force a NullPointerException
        app.readFile("example.txt"); // Simulate resource management issues
        setSystemProperty("example.key", "exampleValue"); // Simulate insecure system property usage
    }
}
