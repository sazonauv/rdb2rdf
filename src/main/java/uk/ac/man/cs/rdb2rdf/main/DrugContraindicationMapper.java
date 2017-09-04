package uk.ac.man.cs.rdb2rdf.main;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static uk.ac.man.cs.rdb2rdf.main.CSV2OWLConverter.IND_SUFFIX;
import static uk.ac.man.cs.rdb2rdf.main.CSV2OWLConverter.IRI_DELIMITER;
import static uk.ac.man.cs.rdb2rdf.main.CSVReader.processCell;


/**
 * Created by slava on 04/09/17.
 */
public class DrugContraindicationMapper {

    private static final String IRI_NAME = "http://owl.cs.manchester.ac.uk/contraindication";

    private OWLOntologyManager manager;
    private OWLOntology ontology;
    private OWLDataFactory factory;

//    private Map<String, String> drug2cat;
    private Map<String, String> drug2cond;
//    private Map<String, String> drug2ndc;


    public DrugContraindicationMapper(File drug2cond)
            throws OWLOntologyCreationException {
        // create an ontology
        IRI iri = IRI.create(IRI_NAME);
        manager = OWLManager.createOWLOntologyManager();
        ontology = manager.createOntology(iri);
        factory = manager.getOWLDataFactory();
        init(drug2cond);
    }

    private void init(File drug2condFile) {
//        final List<String[]> drug2catRows = CSVReader.read(drug2catFile);
        final List<String[]> drug2condRows = CSVReader.read(drug2condFile, true);
//        final List<String[]> drug2ndcRows = CSVReader.read(drug2ndcFile);
//        drug2cat = new HashMap<>();
        drug2cond = new HashMap<>();
//        drug2ndc = new HashMap<>();
//        for (String[] row : drug2catRows) {
//            drug2cat.put(row[0], row[3]);
//        }
        for (String[] row : drug2condRows) {
            String condStr = processCell(row[4].replace("No Interactions Found", ""));
            if (condStr.isEmpty()) {
                continue;
            }
            drug2cond.put(processCell(row[2]), condStr);
        }
//        for (String[] row : drug2ndcRows) {
//            drug2ndc.put(row[2], row[0]);
//        }
    }

    public static void main(String args[])
            throws OWLOntologyCreationException, IOException, OWLOntologyStorageException {
        DrugContraindicationMapper converter = new DrugContraindicationMapper(new File(args[0]));
        converter.createOntology(new File(args[1]));
    }

    private void createOntology(File ontFile) throws IOException, OWLOntologyStorageException {
        // populate the ontology
        IRI drugClassIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "Drug");
        OWLClass drugClass = factory.getOWLClass(drugClassIRI);
        IRI condClassIRI =  IRI.create(IRI_NAME + IRI_DELIMITER + "Condition");
        OWLClass condClass = factory.getOWLClass(condClassIRI);
        for (String drug : drug2cond.keySet()) {
            String condStr = drug2cond.get(drug);
            IRI drugIndIRI = IRI.create(IRI_NAME + IRI_DELIMITER + drug);
            OWLNamedIndividual drugInd = factory.getOWLNamedIndividual(drugIndIRI);
            IRI condIndIRI = IRI.create(IRI_NAME + IRI_DELIMITER + condStr);
            OWLNamedIndividual condInd = factory.getOWLNamedIndividual(condIndIRI);
            IRI contraindicatedIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "contraindicated");
            OWLObjectProperty contraindicatedProp = factory.getOWLObjectProperty(contraindicatedIRI);
            manager.addAxiom(ontology, factory.getOWLClassAssertionAxiom(drugClass, drugInd));
            manager.addAxiom(ontology, factory.getOWLClassAssertionAxiom(condClass, condInd));
            manager.addAxiom(ontology, factory.getOWLObjectPropertyAssertionAxiom(
                    contraindicatedProp, drugInd, condInd));
        }

        // save the ontology
        if (!ontFile.exists()) {
            ontFile.createNewFile();
        }

        FileOutputStream outputStream = new FileOutputStream(ontFile);
        BufferedOutputStream buffOutputStream = new BufferedOutputStream(outputStream);
        manager.saveOntology(ontology, buffOutputStream);
    }




}
