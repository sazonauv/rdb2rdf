package uk.ac.man.cs.rdb2rdf.main;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Created by slava on 04/09/17.
 */
public class CSVWriter {

    private FileWriter fileWriter;


    public CSVWriter(File file, boolean append) {
        // If the file doesn't exist then create it
        try {
            File parent = file.getParentFile();
            if (!parent.exists()) {
                parent.mkdirs();
            }
            if (!file.exists()) {
                file.createNewFile();
            }
            fileWriter = new FileWriter(file, append);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }




    public void append(String[] entry) {
        try {
            for (int j=0; j<entry.length; j++) {
                fileWriter.append(entry[j]);
                fileWriter.append(CSV.DELIMITER);
            }
            fileWriter.append(CSV.NEW_LINE_SEPARATOR);
        } catch (Exception e) {
            e.printStackTrace();
            close();
        }
    }



    public void append(List<String[]> entries) {
        try {
            for (String[] entry : entries) {
                for (int j=0; j<entry.length; j++) {
                    fileWriter.append(entry[j]);
                    fileWriter.append(CSV.DELIMITER);
                }
                fileWriter.append(CSV.NEW_LINE_SEPARATOR);
            }

        } catch (Exception e) {
            e.printStackTrace();
            close();
        }
    }



    public void close() {
        try {
            fileWriter.flush();
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}