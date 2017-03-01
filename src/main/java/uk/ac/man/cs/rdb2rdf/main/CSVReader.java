package uk.ac.man.cs.rdb2rdf.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by slava on 01/03/17.
 */
public class CSVReader {

    public static final String DELIMITER = ",";

    public static List<String[]> read(File file) {
        BufferedReader reader = null;
        List<String[]> result = new ArrayList<>();
        try {
            if (file.exists()) {
                reader = new BufferedReader(new FileReader(file));
                String line;
                // read the file line by line
                while ((line = reader.readLine()) != null) {
                    // get all tokens available in line
                    String[] tokens = line.split(DELIMITER);
                    result.add(tokens);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

}
