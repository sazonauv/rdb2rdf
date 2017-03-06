package uk.ac.man.cs.rdb2rdf.main;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import javax.xml.namespace.QName;
import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by slava on 01/03/17.
 */
public class CSV2OWLConverter {

    private static final String IRI_NAME = "http://owl.cs.manchester.ac.uk/healthefacts";
    private static final String IRI_DELIMITER = "#";
    private static final String ENTITY_DELIMITER = "-";
    private static final String CSV_DELIMITER = ",";



    private void createOntology(File csvFile, File ontFile)
            throws OWLOntologyCreationException,
            IOException, OWLOntologyStorageException {

        // create an ontology
        IRI iri = IRI.create(IRI_NAME);
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = manager.createOntology(iri);
        OWLDataFactory factory = manager.getOWLDataFactory();

        // populate the ontology
        BufferedReader reader = null;
        try {
            if (csvFile.exists()) {
                reader = new BufferedReader(new FileReader(csvFile));
                // read the header
                String line = reader.readLine();
                // read the file line by line
                int i = 0;
                while ((line = reader.readLine()) != null) {
                    // get all tokens in the line
                    String[] row = line.split(CSV_DELIMITER);
                    // debug
                    i++;
                    if (i % 1e6 == 0) {
                        System.out.println(i + " lines are processed");
                    }
                    // encounter
                    String encId = processCell(row[0]);
                    IRI encIRI = IRI.create(IRI_NAME + IRI_DELIMITER + encId);
                    OWLNamedIndividual encInd = factory.getOWLNamedIndividual(encIRI);
                    // measurements
                    String meas = processCell(row[1]);
                    IRI measIRI = IRI.create(IRI_NAME + IRI_DELIMITER + meas);
                    if (measIRI.toString().contains("http://owl.cs.manchester.ac.uk/healthefacts#Temperature")) {
                        continue;
                    }
                    OWLObjectProperty measProp = factory.getOWLObjectProperty(measIRI);
                    // results of measurements
                    String measRes = processCell(row[2]);
                    IRI measResIRI = IRI.create(IRI_NAME + IRI_DELIMITER + measRes);
                    OWLClass measResClass = factory.getOWLClass(measResIRI);
                    IRI measResIndIRI = IRI.create(IRI_NAME + IRI_DELIMITER + measRes + ENTITY_DELIMITER + "i");
                    OWLNamedIndividual measResInd = factory.getOWLNamedIndividual(measResIndIRI);
                    // medical conditions
                    String cond = processCell(row[3]);
                    IRI condIRI = IRI.create(IRI_NAME + IRI_DELIMITER + cond);
                    OWLClass condClass = factory.getOWLClass(condIRI);
                    // patient types
                    String ptype = processCell(row[4]);
                    IRI ptypeIRI = IRI.create(IRI_NAME + IRI_DELIMITER + ptype);
                    OWLClass ptypeClass = factory.getOWLClass(ptypeIRI);
                    // medications
                    String med = processCell(row[5]);
                    IRI medIRI = IRI.create(IRI_NAME + IRI_DELIMITER + med);
                    OWLClass medClass = factory.getOWLClass(medIRI);
                    // axioms
                    Set<OWLAxiom> axioms = new HashSet<>();
                    axioms.add(factory.getOWLClassAssertionAxiom(condClass, encInd));
                    axioms.add(factory.getOWLClassAssertionAxiom(measResClass, measResInd));
                    axioms.add(factory.getOWLObjectPropertyAssertionAxiom(measProp, encInd, measResInd));
                    axioms.add(factory.getOWLClassAssertionAxiom(ptypeClass, encInd));
                    axioms.add(factory.getOWLClassAssertionAxiom(medClass, encInd));
                    manager.addAxioms(ontology, axioms);
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

        // save the ontology
        if (!ontFile.exists()) {
            ontFile.createNewFile();
        }
        FileOutputStream outputStream = new FileOutputStream(ontFile);
        BufferedOutputStream buffOutputStream = new BufferedOutputStream(outputStream);
        manager.saveOntology(ontology, buffOutputStream);
    }

    private static String processCell(String str) {
        return str.replace(" ", "").replace("\"", "")
                .replace("\'", "").replace("\n", "")
                .replace("\t", "").replace("\r", "")
                .replace("\\", ENTITY_DELIMITER)
                .replace("/", ENTITY_DELIMITER);
    }



    public static void main(String args[])
            throws OWLOntologyCreationException,
            IOException, OWLOntologyStorageException {
        CSV2OWLConverter converter = new CSV2OWLConverter();
        converter.createOntology(new File(args[0]), new File(args[1]));
    }




}
