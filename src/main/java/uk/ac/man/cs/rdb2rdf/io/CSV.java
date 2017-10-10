package uk.ac.man.cs.rdb2rdf.io;

import static uk.ac.man.cs.rdb2rdf.poc.CSV2OWLConverter.ENTITY_DELIMITER;

/**
 * Created by slava on 04/09/17.
 */
public interface CSV {

    public static final String NULL_VALUE = "null";
    public static final String DELIMITER = ",";
    public static final String NEW_LINE_SEPARATOR = "\n";


    public static String processCell(String str) {
        return str.replace(" ", "").replace("\"", "")
                .replace("\'", "").replace("\n", "")
                .replace("\t", "").replace("\r", "")
                .replace("\\", ENTITY_DELIMITER)
                .replace("/", ENTITY_DELIMITER)
                .replace("%", "").replace("(", "").replace(")", "");
    }

}
