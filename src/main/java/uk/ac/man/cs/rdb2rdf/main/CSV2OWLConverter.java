package uk.ac.man.cs.rdb2rdf.main;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by slava on 01/03/17.
 */
public class CSV2OWLConverter {

    private static final String IRI_NAME = "http://owl.cs.manchester.ac.uk/healthefacts";
    private static final String IRI_DELIMITER = "#";
    private static final String ENTITY_DELIMITER = "-";
    private static final String CSV_DELIMITER = ",";


    private OWLOntologyManager manager;
    private OWLOntology ontology;
    private OWLDataFactory factory;


    public CSV2OWLConverter() throws OWLOntologyCreationException {
        // create an ontology
        IRI iri = IRI.create(IRI_NAME);
        manager = OWLManager.createOWLOntologyManager();
        ontology = manager.createOntology(iri);
        factory = manager.getOWLDataFactory();
    }


    private void createOntology(File csvFile, File ontFile)
            throws OWLOntologyCreationException,
            IOException, OWLOntologyStorageException {
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
                    if (i % 1e4 == 0) {
                        System.out.println(i + " lines are processed");
                    }
                    processRowAsPopulationDiagnosisICD9PatientID(row);
//                    processRowAsVitalsDiagnosisICD9(row);
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

        // filter temporal information
        removeMultipleAge();

        FileOutputStream outputStream = new FileOutputStream(ontFile);
        BufferedOutputStream buffOutputStream = new BufferedOutputStream(outputStream);
        manager.saveOntology(ontology, buffOutputStream);
    }


    private void removeMultipleAge() {
        Map<OWLNamedIndividual, Double> indAgeMap = new HashMap<>();
        for (OWLAxiom axiom : ontology.getLogicalAxioms()) {
            if (axiom instanceof OWLDataPropertyAssertionAxiom) {
                OWLDataPropertyAssertionAxiom dpAss = (OWLDataPropertyAssertionAxiom) axiom;
                OWLDataPropertyExpression dp = dpAss.getProperty();
                if (dp.asOWLDataProperty().getIRI().toString().contains("hasAge")) {
                    Double age = dpAss.getObject().parseDouble();
                    OWLNamedIndividual ind = dpAss.getSubject().asOWLNamedIndividual();
                    Double oldAge = indAgeMap.get(ind);
                    if (oldAge == null || oldAge < age) {
                        indAgeMap.put(ind, age);
                    }
                }
            }
        }
        // find axioms to be removed
        Set<OWLAxiom> removals = new HashSet<>();
        for (OWLAxiom axiom : ontology.getLogicalAxioms()) {
            if (axiom instanceof OWLDataPropertyAssertionAxiom) {
                OWLDataPropertyAssertionAxiom dpAss = (OWLDataPropertyAssertionAxiom) axiom;
                OWLDataPropertyExpression dp = dpAss.getProperty();
                if (dp.asOWLDataProperty().getIRI().toString().contains("hasAge")) {
                    Double age = dpAss.getObject().parseDouble();
                    OWLNamedIndividual ind = dpAss.getSubject().asOWLNamedIndividual();
                    Double maxAge = indAgeMap.get(ind);
                    if (age < maxAge) {
                        removals.add(axiom);
                    }
                }
            }
        }
        manager.removeAxioms(ontology, removals);
    }


    // see join_vitals_diagnosis.sql
    private void processRowAsVitalsDiagnosisICD9(String[] row) {
        // encounter
        String encStr = processCell(row[0]);
        IRI encIRI = IRI.create(IRI_NAME + IRI_DELIMITER + encStr);
        OWLNamedIndividual encInd = factory.getOWLNamedIndividual(encIRI);
        // measurements
        String measStr = processCell(row[1]);
        IRI measIRI = IRI.create(IRI_NAME + IRI_DELIMITER + measStr);
        OWLDataProperty measProp = factory.getOWLDataProperty(measIRI);
        // numeric results of measurements
        String measResStr = processCell(row[2]);
        double measRes = Double.parseDouble(measResStr);
        if (measRes == 0) {
            return;
        }
        OWLLiteral measResLit = factory.getOWLLiteral(measRes);
        // medical conditions
        String condStr = processCell(row[3]);
        OWLClass condClass = findICD9Class(condStr);
        // axioms
        Set<OWLAxiom> axioms = new HashSet<>();
        axioms.add(factory.getOWLClassAssertionAxiom(condClass, encInd));
        axioms.add(factory.getOWLDataPropertyAssertionAxiom(measProp, encInd, measResLit));
        manager.addAxioms(ontology, axioms);
    }


    private OWLClass findICD9Class(String code) {
        Set<OWLClass> cls = ontology.getClassesInSignature();
        for (OWLClass cl : cls) {
            IRI clIri = cl.getIRI();
            String clCode = clIri.toString().replace("http://purl.bioontology.org/ontology/ICD9CM/", "");
            if (clCode.equals(code)) {
                return cl;
            }
        }
        return null;
    }


    // see join_vitals_diagnosis.sql
    private void processRowAsVitalsDiagnosis(String[] row) {
        // encounter
        String encStr = processCell(row[0]);
        IRI encIRI = IRI.create(IRI_NAME + IRI_DELIMITER + encStr);
        OWLNamedIndividual encInd = factory.getOWLNamedIndividual(encIRI);
        // measurements
        String measStr = processCell(row[1]);
        IRI measIRI = IRI.create(IRI_NAME + IRI_DELIMITER + measStr);
//        if (measIRI.toString().contains("http://owl.cs.manchester.ac.uk/healthefacts#Temperature")) {
//            return;
//        }
        OWLDataProperty measProp = factory.getOWLDataProperty(measIRI);
        // numeric results of measurements
        String measResStr = processCell(row[2]);
        double measRes = Double.parseDouble(measResStr);
        if (measRes == 0) {
            return;
        }
        OWLLiteral measResLit = factory.getOWLLiteral(measRes);
        // medical conditions
        String condStr = processCell(row[3]);
        IRI condIRI = IRI.create(IRI_NAME + IRI_DELIMITER + condStr);
        OWLClass condClass = factory.getOWLClass(condIRI);
        // axioms
        Set<OWLAxiom> axioms = new HashSet<>();
        axioms.add(factory.getOWLClassAssertionAxiom(condClass, encInd));
        axioms.add(factory.getOWLDataPropertyAssertionAxiom(measProp, encInd, measResLit));
        manager.addAxioms(ontology, axioms);
    }



    // see join_population_diagnosis.sql
    private void processRowAsPopulationDiagnosisICD9PatientID(String[] row) {
        // patient id
        String patId = processCell(row[0]);
        IRI patIRI = IRI.create(IRI_NAME + IRI_DELIMITER + patId);
        OWLNamedIndividual patInd = factory.getOWLNamedIndividual(patIRI);
        // age
        IRI ageIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "hasAge");
        OWLDataProperty ageProp = factory.getOWLDataProperty(ageIRI);
        String ageStr = processCell(row[1]);
        double age = Math.round(Double.parseDouble(ageStr));
        if (age > 100) {
            return;
        }
        OWLLiteral ageLit = factory.getOWLLiteral(age);
        // gender
        String genderStr = processCell(row[2]);
        IRI genderIRI = IRI.create(IRI_NAME + IRI_DELIMITER + genderStr);
        OWLClass genderClass = factory.getOWLClass(genderIRI);
        // race
        String raceStr = processCell(row[3]);
        IRI raceIRI = IRI.create(IRI_NAME + IRI_DELIMITER + raceStr);
        OWLClass raceClass = factory.getOWLClass(raceIRI);
        // medical conditions
        String condStr = processCell(row[4]);
        OWLClass condClass = findICD9Class(condStr);
        // axioms
        Set<OWLAxiom> axioms = new HashSet<>();
        axioms.add(factory.getOWLClassAssertionAxiom(genderClass, patInd));
        axioms.add(factory.getOWLClassAssertionAxiom(raceClass, patInd));
        axioms.add(factory.getOWLClassAssertionAxiom(condClass, patInd));
        axioms.add(factory.getOWLDataPropertyAssertionAxiom(ageProp, patInd, ageLit));
        manager.addAxioms(ontology, axioms);
    }




    // see join_population_diagnosis.sql
    private void processRowAsPopulationDiagnosisICD9(String[] row) {
        // encounter
        String encStr = processCell(row[0]);
        IRI encIRI = IRI.create(IRI_NAME + IRI_DELIMITER + encStr);
        OWLNamedIndividual encInd = factory.getOWLNamedIndividual(encIRI);
        // age
        IRI ageIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "hasAge");
        OWLDataProperty ageProp = factory.getOWLDataProperty(ageIRI);
        String ageStr = processCell(row[1]);
        double age = Math.round(Double.parseDouble(ageStr));
        if (age > 100) {
            return;
        }
        OWLLiteral ageLit = factory.getOWLLiteral(age);
        // gender
        String genderStr = processCell(row[2]);
        IRI genderIRI = IRI.create(IRI_NAME + IRI_DELIMITER + genderStr);
        OWLClass genderClass = factory.getOWLClass(genderIRI);
        // race
        String raceStr = processCell(row[3]);
        IRI raceIRI = IRI.create(IRI_NAME + IRI_DELIMITER + raceStr);
        OWLClass raceClass = factory.getOWLClass(raceIRI);
        // medical conditions
        String condStr = processCell(row[4]);
        OWLClass condClass = findICD9Class(condStr);
        // axioms
        Set<OWLAxiom> axioms = new HashSet<>();
        axioms.add(factory.getOWLClassAssertionAxiom(genderClass, encInd));
        axioms.add(factory.getOWLClassAssertionAxiom(raceClass, encInd));
        axioms.add(factory.getOWLClassAssertionAxiom(condClass, encInd));
        axioms.add(factory.getOWLDataPropertyAssertionAxiom(ageProp, encInd, ageLit));
        manager.addAxioms(ontology, axioms);
    }



    // see join_population_diagnosis.sql
    private void processRowAsPopulationDiagnosis(String[] row) {
        // encounter
        String encStr = processCell(row[0]);
        IRI encIRI = IRI.create(IRI_NAME + IRI_DELIMITER + encStr);
        OWLNamedIndividual encInd = factory.getOWLNamedIndividual(encIRI);
        // age
        IRI ageIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "hasAge");
        OWLDataProperty ageProp = factory.getOWLDataProperty(ageIRI);
        String ageStr = processCell(row[1]);
        double age = Math.round(Double.parseDouble(ageStr));
        if (age > 100) {
            return;
        }
        OWLLiteral ageLit = factory.getOWLLiteral(age);
        // gender
        String genderStr = processCell(row[2]);
        IRI genderIRI = IRI.create(IRI_NAME + IRI_DELIMITER + genderStr);
        OWLClass genderClass = factory.getOWLClass(genderIRI);
        // race
        String raceStr = processCell(row[3]);
        IRI raceIRI = IRI.create(IRI_NAME + IRI_DELIMITER + raceStr);
        OWLClass raceClass = factory.getOWLClass(raceIRI);
        // medical conditions
        String condStr = processCell(row[4]);
        IRI condIRI = IRI.create(IRI_NAME + IRI_DELIMITER + condStr);
        OWLClass condClass = factory.getOWLClass(condIRI);
        // axioms
        Set<OWLAxiom> axioms = new HashSet<>();
        axioms.add(factory.getOWLClassAssertionAxiom(genderClass, encInd));
        axioms.add(factory.getOWLClassAssertionAxiom(raceClass, encInd));
        axioms.add(factory.getOWLClassAssertionAxiom(condClass, encInd));
        axioms.add(factory.getOWLDataPropertyAssertionAxiom(ageProp, encInd, ageLit));
        manager.addAxioms(ontology, axioms);
    }



    // see join_population_diagnosis.sql
    private void processRowAsNonNumericVitalDiagnosisMedicine(String[] row) {
        // encounter
        String encId = processCell(row[0]);
        IRI encIRI = IRI.create(IRI_NAME + IRI_DELIMITER + encId);
        OWLNamedIndividual encInd = factory.getOWLNamedIndividual(encIRI);
        // measurements
        String meas = processCell(row[1]);
        IRI measIRI = IRI.create(IRI_NAME + IRI_DELIMITER + meas);
        if (measIRI.toString().contains("http://owl.cs.manchester.ac.uk/healthefacts#Temperature")) {
            return;
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



    private static String processCell(String str) {
        return str.replace(" ", "").replace("\"", "")
                .replace("\'", "").replace("\n", "")
                .replace("\t", "").replace("\r", "")
                .replace("\\", ENTITY_DELIMITER)
                .replace("/", ENTITY_DELIMITER)
                .replace("%", "").replace("(", "").replace(")", "");
    }



    public static void main(String args[])
            throws OWLOntologyCreationException,
            IOException, OWLOntologyStorageException {
        CSV2OWLConverter converter = new CSV2OWLConverter();
        converter.addICD9Ontology(new File(args[2]));
        converter.createOntology(new File(args[0]), new File(args[1]));
    }



    private void addICD9Ontology(File file) throws OWLOntologyCreationException {
        System.out.println("Loading ICD9 terminology");
        OWLOntology icd9Ontology = manager.loadOntologyFromOntologyDocument(file);
        // get class hierarchy
        System.out.println("Adding class hierarchy");
        Set<OWLSubClassOfAxiom> classAxioms = new HashSet<>();
        Set<OWLClass> cls = new HashSet<>();
        for (OWLAxiom axiom : icd9Ontology.getAxioms()) {
            if (axiom instanceof OWLSubClassOfAxiom) {
                OWLSubClassOfAxiom clAxiom = (OWLSubClassOfAxiom) axiom;
                classAxioms.add(clAxiom);
                cls.addAll(clAxiom.getClassesInSignature());
            }
        }
        System.out.println("Class axioms are added: " + classAxioms.size());
        // get annotations
        System.out.println("Adding annotations");
        Set<OWLAnnotationAssertionAxiom> labelAnnots = new HashSet<>();
        for (OWLClass cl : cls) {
            Set<OWLAnnotationAssertionAxiom> annots = icd9Ontology.getAnnotationAssertionAxioms(cl.getIRI());
            for (OWLAnnotationAssertionAxiom annot : annots) {
                if (annot.getProperty().getIRI().toString().contains("prefLabel")) {
                    labelAnnots.add(annot);
                }
            }
        }
        System.out.println("Annotations are added: " + labelAnnots.size());
        manager.addAxioms(ontology, classAxioms);
        manager.addAxioms(ontology, labelAnnots);
    }


}
