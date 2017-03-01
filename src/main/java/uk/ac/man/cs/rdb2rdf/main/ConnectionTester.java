package uk.ac.man.cs.rdb2rdf.main;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

/**
 * Created by slava on 27/02/17.
 */
public class ConnectionTester {
    public static void main(String args[]) {
        Connection c = null;
        try {
            String url = "jdbc:postgresql://therealjasmina.cs.man.ac.uk:5432/HealtheFacts";
            Properties props = new Properties();
            props.setProperty("user", "slava");
            props.setProperty("password", "");
            props.setProperty("sslmode", "verify-full");
            props.setProperty("sslrootcert", "/home/slava/.postgresql/root.crt");
            props.setProperty("sslcert", "/home/slava/.postgresql/slava.crt");
            props.setProperty("sslkey", "/home/slava/.postgresql/slava.key");

            // get a connection
            c = DriverManager.getConnection(url, props);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(e.getClass().getName() + ": " + e.getMessage());
            System.exit(0);
        }
        System.out.println("Opened database successfully");
    }
}