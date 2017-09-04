package uk.ac.man.cs.rdb2rdf.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static uk.ac.man.cs.rdb2rdf.main.CSV.DELIMITER;
import static uk.ac.man.cs.rdb2rdf.main.CSV2OWLConverter.ENTITY_DELIMITER;

/**
 * Created by slava on 01/03/17.
 */
public class CSVReader {



    public static List<String[]> read(File file, boolean hasHeader) {
        BufferedReader reader = null;
        List<String[]> result = new ArrayList<>();
        try {
            if (file.exists()) {
                reader = new BufferedReader(new FileReader(file));
                String line;
                if (hasHeader) {
                    line = reader.readLine();
                }
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


    public static String processCell(String str) {
        return str.replace(" ", "").replace("\"", "")
                .replace("\'", "").replace("\n", "")
                .replace("\t", "").replace("\r", "")
                .replace("\\", ENTITY_DELIMITER)
                .replace("/", ENTITY_DELIMITER)
                .replace("%", "").replace("(", "").replace(")", "");
    }

}
