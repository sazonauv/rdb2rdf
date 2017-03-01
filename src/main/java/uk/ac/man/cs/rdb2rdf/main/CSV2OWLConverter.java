package uk.ac.man.cs.rdb2rdf.main;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import java.io.*;
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


    private List<String[]> rows;
    private String[] header;

    private OWLOntologyManager manager;
    private OWLOntology ontology;
    private OWLDataFactory factory;


    public CSV2OWLConverter(File csvFile) {
        init(csvFile);
    }

    private void init(File csvFile) {
        // read the CSV file
        rows = CSVReader.read(csvFile);
        header = rows.get(0);
        rows.remove(0);
    }


    private void createOntology() throws OWLOntologyCreationException {
        // create an ontology
        IRI iri = IRI.create(IRI_NAME);
        manager = OWLManager.createOWLOntologyManager();
        ontology = manager.createOntology(iri);
        factory = manager.getOWLDataFactory();

        // populate the ontology
        int i = 0;
        for (String[] row : rows) {
            i++;
            // encounter
            String encId = row[0];
            encId = encId.replaceAll("\"", "");
            IRI encIRI = IRI.create(IRI_NAME + IRI_DELIMITER + encId + ENTITY_DELIMITER + i);
            OWLNamedIndividual encInd = factory.getOWLNamedIndividual(encIRI);
            // gender
            String gender = row[2];
            gender = gender.replaceAll("\"", "");
            IRI genderIRI = IRI.create(IRI_NAME + IRI_DELIMITER + gender);
            OWLClass genderClass = factory.getOWLClass(genderIRI);
            // measurements
            String meas = row[3].replaceAll(" ", "");
            meas = meas.replaceAll("\"", "");
            IRI measIRI = IRI.create(IRI_NAME + IRI_DELIMITER + meas);
            OWLObjectProperty measProp = factory.getOWLObjectProperty(measIRI);
            // results of measurements
            String measRes = row[4].replaceAll(" ", "");
            measRes = measRes.replaceAll("\"", "");
            IRI measResIRI = IRI.create(IRI_NAME + IRI_DELIMITER + measRes);
            OWLClass measResClass = factory.getOWLClass(measResIRI);
            IRI measResIndIRI = IRI.create(IRI_NAME + IRI_DELIMITER + measRes + ENTITY_DELIMITER + "i");
            OWLNamedIndividual measResInd = factory.getOWLNamedIndividual(measResIndIRI);
            // medical conditions
            String cond = row[5].replaceAll(" ", "");
            cond = cond.replaceAll("\"", "");
            IRI condIRI = IRI.create(IRI_NAME + IRI_DELIMITER + cond);
            OWLClass condClass = factory.getOWLClass(condIRI);
            // axioms
            Set<OWLAxiom> axioms = new HashSet<>();
            axioms.add(factory.getOWLClassAssertionAxiom(genderClass, encInd));
            axioms.add(factory.getOWLClassAssertionAxiom(condClass, encInd));
            axioms.add(factory.getOWLClassAssertionAxiom(measResClass, measResInd));
            axioms.add(factory.getOWLObjectPropertyAssertionAxiom(measProp, encInd, measResInd));
            manager.addAxioms(ontology, axioms);
        }

    }



    private void saveOntology(File ontFile) throws IOException, OWLOntologyStorageException {
        if (!ontFile.exists()) {
            ontFile.createNewFile();
        }
        FileOutputStream outputStream = new FileOutputStream(ontFile);
        BufferedOutputStream buffOutputStream = new BufferedOutputStream(outputStream);
        manager.saveOntology(ontology, buffOutputStream);
    }


    public static void main(String args[])
            throws OWLOntologyCreationException,
            IOException, OWLOntologyStorageException {
        File csvFile = new File(args[0]);
        CSV2OWLConverter converter = new CSV2OWLConverter(csvFile);
        converter.createOntology();
        // save the ontology
        File ontFile = new File(args[1]);
        converter.saveOntology(ontFile);
    }




}
