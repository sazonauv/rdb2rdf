package uk.ac.man.cs.rdb2rdf.poc;

import com.opencsv.CSVReader;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import uk.ac.man.cs.rdb2rdf.io.Out;

import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static uk.ac.man.cs.rdb2rdf.io.CSV.processCell;
import static uk.ac.man.cs.rdb2rdf.poc.CSV2OWLConverter.*;

/**
 * Created by slava on 12/10/17.
 */
public class OWLABoxBuilder {


    private OWLOntologyManager manager;
    private OWLOntology ontology;
    private OWLDataFactory factory;

    private Map<String, OWLClass> icd9Map;

    public OWLABoxBuilder() throws OWLOntologyCreationException {
        // create an ontology
        IRI iri = IRI.create(IRI_NAME);
        manager = OWLManager.createOWLOntologyManager();
        ontology = manager.createOntology(iri);
        factory = manager.getOWLDataFactory();
    }


    public static void main(String args[])
            throws OWLOntologyCreationException,
            IOException, OWLOntologyStorageException {
        createUsingICD9(new File(args[0]), new File(args[1]), new File(args[2]),
                new File(args[3]), new File(args[4]));
    }

    private static void createUsingICD9(File file0, File file1, File file2, File file3, File file4)
            throws OWLOntologyCreationException, IOException, OWLOntologyStorageException {
        OWLABoxBuilder builder = new OWLABoxBuilder();
        builder.addICD9Ontology(file4);
        builder.createOntology(file0, file1, file2, file3);
    }

    private void createOntology(File diagCSV, File medCSV, File labCSV, File ontFile)
            throws OWLOntologyStorageException, IOException {

        readConditions(diagCSV);
        readMedicines(medCSV);
        readLabs(labCSV);

        FileOutputStream outputStream = new FileOutputStream(ontFile);
        BufferedOutputStream buffOutputStream = new BufferedOutputStream(outputStream);
        manager.saveOntology(ontology, buffOutputStream);
    }




    private void readConditions(File csvFile) throws IOException {
        CSVReader reader = new CSVReader(new FileReader(csvFile));
        String[] row = reader.readNext();
        while (row != null) {
            processConditionLine(row);
            row = reader.readNext();
        }
    }


    private void readMedicines(File csvFile) throws IOException {
        CSVReader reader = new CSVReader(new FileReader(csvFile));
        String[] row = reader.readNext();
        while (row != null) {
            processMedicineLine(row);
            row = reader.readNext();
        }
    }




    private void readLabs(File csvFile) throws IOException {
        CSVReader reader = new CSVReader(new FileReader(csvFile));
        String[] row = reader.readNext();
        while (row != null) {
            processLabLine(row);
            row = reader.readNext();
        }
    }




    private void processConditionLine(String[] row) {
        Set<OWLAxiom> axioms = new HashSet<>();

        // top encounter
        IRI encTopIRI = IRI.create(IRI_NAME + IRI_DELIMITER + TOP_ENCOUNTER);
        OWLClass encTopClass = factory.getOWLClass(encTopIRI);
        // encounter
        String encStr = processCell(row[0]);
        IRI encIRI = IRI.create(IRI_NAME + IRI_DELIMITER + encStr);
        OWLNamedIndividual encInd = factory.getOWLNamedIndividual(encIRI);
        axioms.add(factory.getOWLClassAssertionAxiom(encTopClass, encInd));

        // condition
        String condStr = processCell(row[1]);
        OWLClass condClass = findICD9Class(condStr);
        if (condClass == null) {
            return;
        }
        IRI condIndIRI = IRI.create(IRI_NAME + IRI_DELIMITER + condStr + IND_SUFFIX);
        OWLNamedIndividual condInd = factory.getOWLNamedIndividual(condIndIRI);


        // determine whether it is a diagnosis or procedure
        OWLObjectProperty diagnosedExperiencedProp;
        if (isDiagnosis(condStr)) {
            IRI diagnosedIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "diagnosed");
            diagnosedExperiencedProp = factory.getOWLObjectProperty(diagnosedIRI);
        } else {
            IRI experiencedIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "experienced");
            diagnosedExperiencedProp = factory.getOWLObjectProperty(experiencedIRI);
        }
        axioms.add(factory.getOWLClassAssertionAxiom(condClass, condInd));
        OWLAxiom condAxiom = factory.getOWLObjectPropertyAssertionAxiom(diagnosedExperiencedProp, encInd, condInd);
        axioms.add(condAxiom);

        // axioms
        manager.addAxioms(ontology, axioms);
    }



    private void processMedicineLine(String[] row) {
        OWLAnnotationProperty labelProp = factory.getRDFSLabel();
        IRI prescribedIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "prescribedDrug");
        OWLObjectProperty prescribedProp = factory.getOWLObjectProperty(prescribedIRI);

        Set<OWLAxiom> axioms = new HashSet<>();

        // top encounter
        IRI encTopIRI = IRI.create(IRI_NAME + IRI_DELIMITER + TOP_ENCOUNTER);
        OWLClass encTopClass = factory.getOWLClass(encTopIRI);
        // encounter
        String encStr = processCell(row[0]);
        IRI encIRI = IRI.create(IRI_NAME + IRI_DELIMITER + encStr);
        OWLNamedIndividual encInd = factory.getOWLNamedIndividual(encIRI);
        axioms.add(factory.getOWLClassAssertionAxiom(encTopClass, encInd));

        // top medicine
        IRI medicineTopIRI = IRI.create(IRI_NAME + IRI_DELIMITER + TOP_MEDICINE);
        OWLClass medicineTopClass = factory.getOWLClass(medicineTopIRI);
        // medicine
        String medicineCodeStr = processCell(row[1]);
        String medicineBrandStr = row[2];
        String medicineClassStr = processCell(row[3]);
        IRI medicineCodeIRI = IRI.create(IRI_NAME + IRI_DELIMITER + medicineCodeStr);
        OWLNamedIndividual medicineInd = factory.getOWLNamedIndividual(medicineCodeIRI);
        IRI medicineClassIRI = IRI.create(IRI_NAME + IRI_DELIMITER + medicineClassStr);
        OWLClass medicineClass = factory.getOWLClass(medicineClassIRI);

        // medicine name
        axioms.add(factory.getOWLAnnotationAssertionAxiom(labelProp, medicineInd.getIRI(),
                factory.getOWLLiteral(medicineBrandStr)));
        axioms.add(factory.getOWLClassAssertionAxiom(medicineClass, medicineInd));
        axioms.add(factory.getOWLSubClassOfAxiom(medicineClass, medicineTopClass));
        OWLAxiom prescrAxiom = factory.getOWLObjectPropertyAssertionAxiom(prescribedProp, encInd, medicineInd);
        axioms.add(prescrAxiom);

        // axioms
        manager.addAxioms(ontology, axioms);
    }



    private void processLabLine(String[] row) {
        OWLAnnotationProperty labelProp = factory.getRDFSLabel();
        IRI orderedLabIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "orderedLab");
        OWLObjectProperty orderedLabProp = factory.getOWLObjectProperty(orderedLabIRI);

        Set<OWLAxiom> axioms = new HashSet<>();

        // top encounter
        IRI encTopIRI = IRI.create(IRI_NAME + IRI_DELIMITER + TOP_ENCOUNTER);
        OWLClass encTopClass = factory.getOWLClass(encTopIRI);
        // encounter
        String encStr = processCell(row[0]);
        IRI encIRI = IRI.create(IRI_NAME + IRI_DELIMITER + encStr);
        OWLNamedIndividual encInd = factory.getOWLNamedIndividual(encIRI);
        axioms.add(factory.getOWLClassAssertionAxiom(encTopClass, encInd));

        // top lab
        IRI labTopIRI = IRI.create(IRI_NAME + IRI_DELIMITER + TOP_LAB);
        OWLClass labTopClass = factory.getOWLClass(labTopIRI);
        // lab
        String labStr = processCell(row[1]);
        IRI labIndIRI = IRI.create(IRI_NAME + IRI_DELIMITER + labStr);
        OWLNamedIndividual labInd = factory.getOWLNamedIndividual(labIndIRI);
        IRI labIRI = IRI.create(IRI_NAME + IRI_DELIMITER + labStr + IND_SUFFIX);
        OWLClass labClass = factory.getOWLClass(labIRI);
        // lab name
        String labNameStr = row[2];
        axioms.add(factory.getOWLAnnotationAssertionAxiom(labelProp, labInd.getIRI(),
                factory.getOWLLiteral(labNameStr)));
        axioms.add(factory.getOWLAnnotationAssertionAxiom(labelProp, labClass.getIRI(),
                factory.getOWLLiteral(labNameStr)));
        axioms.add(factory.getOWLClassAssertionAxiom(labClass, labInd));
        axioms.add(factory.getOWLSubClassOfAxiom(labClass, labTopClass));
        OWLAxiom labAxiom = factory.getOWLObjectPropertyAssertionAxiom(orderedLabProp, encInd, labInd);
        axioms.add(labAxiom);

        // axioms
        manager.addAxioms(ontology, axioms);
    }





    private OWLClass findICD9Class(String code) {
        return icd9Map.get(code);
    }





    private void addICD9Ontology(File file) throws OWLOntologyCreationException {
        Out.p("Loading ICD9 terminology");
        OWLOntology icd9Ontology = manager.loadOntologyFromOntologyDocument(file);
        // get class hierarchy
        Out.p("Adding class hierarchy");
        Set<OWLSubClassOfAxiom> classAxioms = new HashSet<>();
        for (OWLAxiom axiom : icd9Ontology.getAxioms()) {
            if (axiom instanceof OWLSubClassOfAxiom) {
                OWLSubClassOfAxiom clAxiom = (OWLSubClassOfAxiom) axiom;
                classAxioms.add(clAxiom);
            }
        }
        manager.addAxioms(ontology, classAxioms);
        Out.p("Class axioms are added: " + classAxioms.size());
        // adding annotations
        addAnnotations(icd9Ontology);
    }


    private void addAnnotations(OWLOntology icd9Ontology) {
        // get annotations
        Out.p("Adding annotations");
        Set<OWLClass> cls = icd9Ontology.getClassesInSignature();
        Set<OWLAnnotationAssertionAxiom> labelAnnots = new HashSet<>();
        for (OWLClass cl : cls) {
            Set<OWLAnnotationAssertionAxiom> annots = icd9Ontology.getAnnotationAssertionAxioms(cl.getIRI());
            for (OWLAnnotationAssertionAxiom annot : annots) {
                if (annot.getProperty().getIRI().toString().contains("prefLabel")) {
                    labelAnnots.add(annot);
                }
            }
        }
        Out.p("Annotations are added: " + labelAnnots.size());
        manager.addAxioms(ontology, labelAnnots);
        // create mapping
        icd9Map = new HashMap<>();
        Out.p("Creating ICD9 code-class mappings");
        for (OWLClass cl : cls) {
            String clCode = cl.getIRI().toString().replace("http://purl.bioontology.org/ontology/ICD9CM/", "");
            icd9Map.put(clCode, cl);
        }
    }

}
