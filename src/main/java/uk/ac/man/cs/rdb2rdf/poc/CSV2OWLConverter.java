package uk.ac.man.cs.rdb2rdf.poc;

import com.opencsv.CSVReader;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import uk.ac.man.cs.rdb2rdf.io.Out;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static uk.ac.man.cs.rdb2rdf.io.CSV.DELIMITER;
import static uk.ac.man.cs.rdb2rdf.io.CSV.processCell;

/**
 * Created by slava on 01/03/17.
 */
public class CSV2OWLConverter {

    public static final String IRI_NAME = "http://owl.cs.manchester.ac.uk/healthefacts";

    public static final String IRI_DELIMITER = "#";
    public static final String ENTITY_DELIMITER = "-";
    public static final String IND_SUFFIX = "i";

    public static final String TOP_CLASS = "DomainConcept";

    public static final String TOP_MEDICINE = "Drug";
    public static final String TOP_DISEASE = "Disease";
    public static final String TOP_PATIENT = "Patient";
    public static final String TOP_ORDER = "Order";

    public static final String TOP_LAB = "Lab";
    public static final String TOP_ENCOUNTER = "Encounter";

    public static final String TOP_PLAUSIBILITY = "Plausibility";
    public static final String LOW_PLAUSIBILITY = "Low";
    public static final String MEDIUM_PLAUSIBILITY = "Medium";
    public static final String HIGH_PLAUSIBILITY = "High";

    public static final String YEAR = "year";
    public static final String MONTH = "month";
    public static final String DAY = "day";


    public static final String TOP_PROPERTY = "domainObjectProperty";



    private OWLOntologyManager manager;
    private OWLOntology ontology;
    private OWLDataFactory factory;

    private Map<String, OWLClass> icd9Map;


    public CSV2OWLConverter() throws OWLOntologyCreationException {
        // create an ontology
        IRI iri = IRI.create(IRI_NAME);
        manager = OWLManager.createOWLOntologyManager();
        ontology = manager.createOntology(iri);
        factory = manager.getOWLDataFactory();
    }


    public static void main(String args[])
            throws OWLOntologyCreationException,
            IOException, OWLOntologyStorageException {
        createUsingICD9(new File(args[0]), new File(args[1]), new File(args[2]));
//        create(new File(args[0]), new File(args[1]));
    }


    private static void create(File file0, File file1)
            throws OWLOntologyCreationException, OWLOntologyStorageException, IOException {
        CSV2OWLConverter converter = new CSV2OWLConverter();
        converter.createOntology(file0, file1);
    }


    private static void createUsingICD9(File file0, File file1, File file2)
            throws OWLOntologyCreationException, IOException, OWLOntologyStorageException {
        CSV2OWLConverter converter = new CSV2OWLConverter();
        converter.addICD9Ontology(file2);
        converter.createOntology(file0, file1);
    }


    private void createOntology(File csvFile, File ontFile)
            throws OWLOntologyCreationException,
            IOException, OWLOntologyStorageException {
        // populate the ontology
        CSVReader reader = new CSVReader(new FileReader(csvFile));
        reader.readNext();
        String[] row = reader.readNext();
        int count = 0;
        while (row != null) {
            processRowAsMedicineLabDiagnosisTime(row);
            row = reader.readNext();
            if (++count % 10000 == 0) {
                Out.p("Reading CSV: " + count + " lines");
            }
        }

        // save the ontology
        if (!ontFile.exists()) {
            ontFile.createNewFile();
        }

        // filter temporal information
//        removeMultipleAge();

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
        return icd9Map.get(code);
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


    // see join_medicine_lab_diagnosis.sql
    private void processRowAsMedicineLabDiagnosisTime(String[] row) {
        // object properties
        IRI prescribedIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "prescribedDrug");
        OWLObjectProperty prescribedProp = factory.getOWLObjectProperty(prescribedIRI);
        IRI orderedLabIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "orderedLab");
        OWLObjectProperty orderedLabProp = factory.getOWLObjectProperty(orderedLabIRI);

        // annotation properties
        OWLAnnotationProperty labelProp = factory.getRDFSLabel();
        IRI yearPropIRI = IRI.create(IRI_NAME + IRI_DELIMITER + YEAR);
        OWLAnnotationProperty yearProp = factory.getOWLAnnotationProperty(yearPropIRI);
        IRI monthPropIRI = IRI.create(IRI_NAME + IRI_DELIMITER + MONTH);
        OWLAnnotationProperty monthProp = factory.getOWLAnnotationProperty(monthPropIRI);
        IRI dayPropIRI = IRI.create(IRI_NAME + IRI_DELIMITER + DAY);
        OWLAnnotationProperty dayProp = factory.getOWLAnnotationProperty(dayPropIRI);

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
        // time
        String medDateStr = row[6];
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime medDateTime = LocalDateTime.from(dtf.parse(medDateStr));
        Set<OWLAnnotation> medAnnots = new HashSet<>();
        medAnnots.add(factory.getOWLAnnotation(yearProp, factory.getOWLLiteral(medDateTime.getYear())));
        medAnnots.add(factory.getOWLAnnotation(monthProp, factory.getOWLLiteral(medDateTime.getMonthValue())));
        medAnnots.add(factory.getOWLAnnotation(dayProp, factory.getOWLLiteral(medDateTime.getDayOfMonth())));
        OWLAxiom annPrescrAxiom = prescrAxiom.getAnnotatedAxiom(medAnnots);
        axioms.add(annPrescrAxiom);

        // top lab
        IRI labTopIRI = IRI.create(IRI_NAME + IRI_DELIMITER + TOP_LAB);
        OWLClass labTopClass = factory.getOWLClass(labTopIRI);
        // lab
        String labStr = processCell(row[4]);
        IRI labIndIRI = IRI.create(IRI_NAME + IRI_DELIMITER + labStr);
        OWLNamedIndividual labInd = factory.getOWLNamedIndividual(labIndIRI);
        IRI labIRI = IRI.create(IRI_NAME + IRI_DELIMITER + labStr + IND_SUFFIX);
        OWLClass labClass = factory.getOWLClass(labIRI);
        // lab name
        String labNameStr = row[5];
        axioms.add(factory.getOWLAnnotationAssertionAxiom(labelProp, labInd.getIRI(),
                factory.getOWLLiteral(labNameStr)));
        axioms.add(factory.getOWLAnnotationAssertionAxiom(labelProp, labClass.getIRI(),
                factory.getOWLLiteral(labNameStr)));
        axioms.add(factory.getOWLClassAssertionAxiom(labClass, labInd));
        axioms.add(factory.getOWLSubClassOfAxiom(labClass, labTopClass));
        OWLAxiom labAxiom = factory.getOWLObjectPropertyAssertionAxiom(orderedLabProp, encInd, labInd);
        // time
        String labDateStr = row[7];
        LocalDateTime labDateTime = LocalDateTime.from(dtf.parse(labDateStr));
        Set<OWLAnnotation> labAnnots = new HashSet<>();
        labAnnots.add(factory.getOWLAnnotation(yearProp, factory.getOWLLiteral(labDateTime.getYear())));
        labAnnots.add(factory.getOWLAnnotation(monthProp, factory.getOWLLiteral(labDateTime.getMonthValue())));
        labAnnots.add(factory.getOWLAnnotation(dayProp, factory.getOWLLiteral(labDateTime.getDayOfMonth())));
        OWLAxiom annLabAxiom = labAxiom.getAnnotatedAxiom(labAnnots);
        axioms.add(annLabAxiom);

        // condition
        String condStr = processCell(row[8]);
        OWLClass condClass = findICD9Class(condStr);
        if (condClass == null) {
            return;
        }
        IRI condIndIRI = IRI.create(IRI_NAME + IRI_DELIMITER + condStr + IND_SUFFIX);
        OWLNamedIndividual condInd = factory.getOWLNamedIndividual(condIndIRI);
        String condNameStr = row[9];
        axioms.add(factory.getOWLAnnotationAssertionAxiom(labelProp, condInd.getIRI(),
                factory.getOWLLiteral(condNameStr)));
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
        // time
        String condDateStr = row[10];
        LocalDateTime condDateTime = LocalDateTime.from(dtf.parse(condDateStr));
        Set<OWLAnnotation> condAnnots = new HashSet<>();
        condAnnots.add(factory.getOWLAnnotation(yearProp, factory.getOWLLiteral(condDateTime.getYear())));
        condAnnots.add(factory.getOWLAnnotation(monthProp, factory.getOWLLiteral(condDateTime.getMonthValue())));
        condAnnots.add(factory.getOWLAnnotation(dayProp, factory.getOWLLiteral(condDateTime.getDayOfMonth())));
        OWLAxiom annCondAxiom = condAxiom.getAnnotatedAxiom(condAnnots);
        axioms.add(annCondAxiom);

        // axioms
        manager.addAxioms(ontology, axioms);
    }


    // see join_medicine_lab.sql
    private void processRowAsMedicineLabTime(String[] row) {
        // object properties
        IRI prescribedIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "prescribedDrug");
        OWLObjectProperty prescribedProp = factory.getOWLObjectProperty(prescribedIRI);
        IRI orderedLabIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "orderedLab");
        OWLObjectProperty orderedLabProp = factory.getOWLObjectProperty(orderedLabIRI);

        // annotation properties
        OWLAnnotationProperty labelProp = factory.getRDFSLabel();
        IRI yearPropIRI = IRI.create(IRI_NAME + IRI_DELIMITER + YEAR);
        OWLAnnotationProperty yearProp = factory.getOWLAnnotationProperty(yearPropIRI);
        IRI monthPropIRI = IRI.create(IRI_NAME + IRI_DELIMITER + MONTH);
        OWLAnnotationProperty monthProp = factory.getOWLAnnotationProperty(monthPropIRI);
        IRI dayPropIRI = IRI.create(IRI_NAME + IRI_DELIMITER + DAY);
        OWLAnnotationProperty dayProp = factory.getOWLAnnotationProperty(dayPropIRI);

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
        String medicineBrandStr = processCell(row[2]);
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
        // annotation
        String medDateStr = row[6];
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime medDateTime = LocalDateTime.from(dtf.parse(medDateStr));
        Set<OWLAnnotation> medAnnots = new HashSet<>();
        medAnnots.add(factory.getOWLAnnotation(yearProp, factory.getOWLLiteral(medDateTime.getYear())));
        medAnnots.add(factory.getOWLAnnotation(monthProp, factory.getOWLLiteral(medDateTime.getMonthValue())));
        medAnnots.add(factory.getOWLAnnotation(dayProp, factory.getOWLLiteral(medDateTime.getDayOfMonth())));
        OWLAxiom annPrescrAxiom = prescrAxiom.getAnnotatedAxiom(medAnnots);
        axioms.add(annPrescrAxiom);

        // top lab
        IRI labTopIRI = IRI.create(IRI_NAME + IRI_DELIMITER + TOP_LAB);
        OWLClass labTopClass = factory.getOWLClass(labTopIRI);
        // lab
        String labStr = processCell(row[4]);
        IRI labIndIRI = IRI.create(IRI_NAME + IRI_DELIMITER + labStr);
        OWLNamedIndividual labInd = factory.getOWLNamedIndividual(labIndIRI);
        IRI labIRI = IRI.create(IRI_NAME + IRI_DELIMITER + labStr + ENTITY_DELIMITER + IND_SUFFIX);
        OWLClass labClass = factory.getOWLClass(labIRI);
        // lab name
        String labNameStr = processCell(row[5]);
        axioms.add(factory.getOWLAnnotationAssertionAxiom(labelProp, labInd.getIRI(),
                factory.getOWLLiteral(labNameStr)));
        axioms.add(factory.getOWLAnnotationAssertionAxiom(labelProp, labClass.getIRI(),
                factory.getOWLLiteral(labNameStr)));
        axioms.add(factory.getOWLClassAssertionAxiom(labClass, labInd));
        axioms.add(factory.getOWLSubClassOfAxiom(labClass, labTopClass));
        OWLAxiom labAxiom = factory.getOWLObjectPropertyAssertionAxiom(orderedLabProp, encInd, labInd);
        // annotation
        String labDateStr = row[7];
        LocalDateTime labDateTime = LocalDateTime.from(dtf.parse(labDateStr));
        Set<OWLAnnotation> labAnnots = new HashSet<>();
        labAnnots.add(factory.getOWLAnnotation(yearProp, factory.getOWLLiteral(labDateTime.getYear())));
        labAnnots.add(factory.getOWLAnnotation(monthProp, factory.getOWLLiteral(labDateTime.getMonthValue())));
        labAnnots.add(factory.getOWLAnnotation(dayProp, factory.getOWLLiteral(labDateTime.getDayOfMonth())));
        OWLAxiom annLabAxiom = labAxiom.getAnnotatedAxiom(labAnnots);
        axioms.add(annLabAxiom);

        // axioms
        manager.addAxioms(ontology, axioms);
    }


    // see join_medicine_diagnosis.sql
    private void processRowAsMedicineDiagnosisICD9(String[] row) {
        // encounter
        String encStr = processCell(row[0]);
        IRI encIRI = IRI.create(IRI_NAME + IRI_DELIMITER + encStr);
        OWLNamedIndividual encInd = factory.getOWLNamedIndividual(encIRI);
        // top medicine
        IRI medicineTopIRI = IRI.create(IRI_NAME + IRI_DELIMITER + TOP_MEDICINE);
        OWLClass medicineTopClass = factory.getOWLClass(medicineTopIRI);
        // medicine
        String medicineStr = processCell(row[1]);
        IRI medicineIRI = IRI.create(IRI_NAME + IRI_DELIMITER + medicineStr);
        OWLClass medicineClass = factory.getOWLClass(medicineIRI);
        // medical conditions
        String condStr = processCell(row[2]);
        OWLClass condClass = findICD9Class(condStr);
        // axioms
        Set<OWLAxiom> axioms = new HashSet<>();
        axioms.add(factory.getOWLClassAssertionAxiom(condClass, encInd));
        axioms.add(factory.getOWLClassAssertionAxiom(medicineClass, encInd));
        axioms.add(factory.getOWLSubClassOfAxiom(medicineClass, medicineTopClass));
        manager.addAxioms(ontology, axioms);
    }


    // see join_medicine_diagnosis.sql
    private void processRowAsMedicineDiagnosisICD9Rich(String[] row) {
        // encounter
        String encStr = processCell(row[0]);
        IRI encIRI = IRI.create(IRI_NAME + IRI_DELIMITER + encStr);
        OWLNamedIndividual encInd = factory.getOWLNamedIndividual(encIRI);

        // top medicine
        IRI medicineTopIRI = IRI.create(IRI_NAME + IRI_DELIMITER + TOP_MEDICINE);
        OWLClass medicineTopClass = factory.getOWLClass(medicineTopIRI);

        // medicine
        String medicineStr = processCell(row[1]);
        IRI medicineIRI = IRI.create(IRI_NAME + IRI_DELIMITER + medicineStr);
        OWLClass medicineClass = factory.getOWLClass(medicineIRI);
        IRI medicineIndIRI = IRI.create(IRI_NAME + IRI_DELIMITER + medicineStr + IND_SUFFIX);
        OWLNamedIndividual medicineInd = factory.getOWLNamedIndividual(medicineIndIRI);
        IRI prescribedIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "prescribed");
        OWLObjectProperty prescribedProp = factory.getOWLObjectProperty(prescribedIRI);


        // diagnoses
        String condStr = processCell(row[2]);
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


        // axioms
        Set<OWLAxiom> axioms = new HashSet<>();
        axioms.add(factory.getOWLSubClassOfAxiom(medicineClass, medicineTopClass));
        axioms.add(factory.getOWLClassAssertionAxiom(medicineClass, medicineInd));
        axioms.add(factory.getOWLClassAssertionAxiom(condClass, condInd));
        axioms.add(factory.getOWLObjectPropertyAssertionAxiom(prescribedProp, encInd, medicineInd));
        axioms.add(factory.getOWLObjectPropertyAssertionAxiom(diagnosedExperiencedProp, encInd, condInd));

        manager.addAxioms(ontology, axioms);
    }


    // see diagnosis.sql
    private void processRowAsDiagnosisICD9Rich(String[] row) {
        // encounter
        String encStr = processCell(row[1]);
        IRI encIRI = IRI.create(IRI_NAME + IRI_DELIMITER + encStr);
        OWLNamedIndividual encInd = factory.getOWLNamedIndividual(encIRI);

        // diagnoses
        String condStr = processCell(row[2]);
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

        // axioms
        Set<OWLAxiom> axioms = new HashSet<>();
        axioms.add(factory.getOWLClassAssertionAxiom(condClass, condInd));
        axioms.add(factory.getOWLObjectPropertyAssertionAxiom(diagnosedExperiencedProp, encInd, condInd));

        manager.addAxioms(ontology, axioms);
    }




    // see join_medicine_diagnosis_demographics.sql
    private void processRowAsMedicineDiagnosisICD9DemographicsRich(String[] row) {
        // encounter
        String encStr = processCell(row[0]);
        IRI encIRI = IRI.create(IRI_NAME + IRI_DELIMITER + encStr);
        OWLNamedIndividual encInd = factory.getOWLNamedIndividual(encIRI);

        // top medicine
        IRI medicineTopIRI = IRI.create(IRI_NAME + IRI_DELIMITER + TOP_MEDICINE);
        OWLClass medicineTopClass = factory.getOWLClass(medicineTopIRI);

        // medicine
        String medicineStr = processCell(row[1]);
        IRI medicineIRI = IRI.create(IRI_NAME + IRI_DELIMITER + medicineStr);
        OWLClass medicineClass = factory.getOWLClass(medicineIRI);
        IRI medicineIndIRI = IRI.create(IRI_NAME + IRI_DELIMITER + medicineStr + IND_SUFFIX);
        OWLNamedIndividual medicineInd = factory.getOWLNamedIndividual(medicineIndIRI);
        IRI prescribedIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "prescribed");
        OWLObjectProperty prescribedProp = factory.getOWLObjectProperty(prescribedIRI);

        // diagnoses
        String condStr = processCell(row[2]);
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

        // demographics
        // age
        String ageStr = processCell(row[3]);
        OWLClass ageClass = getAgeClass(ageStr);
        // gender
        String genderStr = processCell(row[4]);
        IRI genderIRI = IRI.create(IRI_NAME + IRI_DELIMITER + genderStr);
        OWLClass genderClass = factory.getOWLClass(genderIRI);
        // race
        String raceStr = processCell(row[5]);
        IRI raceIRI = IRI.create(IRI_NAME + IRI_DELIMITER + raceStr);
        OWLClass raceClass = factory.getOWLClass(raceIRI);

        // axioms
        Set<OWLAxiom> axioms = new HashSet<>();
        axioms.add(factory.getOWLSubClassOfAxiom(medicineClass, medicineTopClass));
        axioms.add(factory.getOWLClassAssertionAxiom(medicineClass, medicineInd));
        axioms.add(factory.getOWLClassAssertionAxiom(condClass, condInd));
        axioms.add(factory.getOWLObjectPropertyAssertionAxiom(prescribedProp, encInd, medicineInd));
        axioms.add(factory.getOWLObjectPropertyAssertionAxiom(diagnosedExperiencedProp, encInd, condInd));
        // demographics
        axioms.add(factory.getOWLClassAssertionAxiom(genderClass, encInd));
        axioms.add(factory.getOWLClassAssertionAxiom(raceClass, encInd));
        axioms.add(factory.getOWLClassAssertionAxiom(ageClass, encInd));

        manager.addAxioms(ontology, axioms);
    }


    public static boolean isDiagnosis(String condStr) {
        if (condStr.indexOf("E") > -1 || condStr.indexOf("V") > -1) {
            return false;
        }
        if (condStr.indexOf("-") == 3 || condStr.indexOf(".") == 3) {
            return true;
        }
        if (condStr.indexOf("-") == -1 && condStr.indexOf(".") == -1 && condStr.length() == 3) {
            return true;
        }
        return false;
    }


    private OWLClass getAgeClass(String ageStr) {
        int age;
        try {
            // years
            age = Integer.parseInt(ageStr);
        } catch (NumberFormatException e) {
            // months
            age = (int) Math.round(Double.parseDouble(ageStr)/12);
        }
        // find a range
        String ageRangeStr = "Age";
        if (age <= 10) {
            ageRangeStr += "<=10";
        } else if (age <= 20) {
            ageRangeStr += "(10-20]";
        } else if (age <= 30) {
            ageRangeStr += "(20-30]";
        } else if (age <= 40) {
            ageRangeStr += "(30-40]";
        } else if (age <= 50) {
            ageRangeStr += "(40-50]";
        } else if (age <= 60) {
            ageRangeStr += "(50-60]";
        } else if (age <= 70) {
            ageRangeStr += "(60-70]";
        } else if (age <= 80) {
            ageRangeStr += "(70-80]";
        } else if (age <= 90) {
            ageRangeStr += "(80-90]";
        } else if (age <= 100) {
            ageRangeStr += "(90-100]";
        } else {
            ageRangeStr += ">100";
        }
        IRI ageRangeIRI = IRI.create(IRI_NAME + IRI_DELIMITER + ageRangeStr);
        return factory.getOWLClass(ageRangeIRI);
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





    // see orders.sql
    private void processRowAsOrders(String[] row) {
        // encounter
        String encStr = processCell(row[0]);
        IRI encIRI = IRI.create(IRI_NAME + IRI_DELIMITER + encStr);
        OWLNamedIndividual encInd = factory.getOWLNamedIndividual(encIRI);

        // diagnoses
        String condStr = processCell(row[1]);
        OWLClass condClass = findICD9Class(condStr);
        if (condClass == null) {
            return;
        }
        IRI condIndIRI = IRI.create(IRI_NAME + IRI_DELIMITER + condStr);
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



        // top medicine
        IRI medicineTopIRI = IRI.create(IRI_NAME + IRI_DELIMITER + TOP_MEDICINE);
        OWLClass medicineTopClass = factory.getOWLClass(medicineTopIRI);

        // medicine
        String medicineStr = processCell(row[5]);
        String startTimeStr = processCell(row[7]);
        IRI medicineIRI = IRI.create(IRI_NAME + IRI_DELIMITER + medicineStr);
        OWLClass medicineClass = factory.getOWLClass(medicineIRI);
        IRI medicineIndIRI = IRI.create(IRI_NAME + IRI_DELIMITER + encStr
                + ENTITY_DELIMITER + medicineStr
                + ENTITY_DELIMITER + startTimeStr);
        OWLNamedIndividual medicineInd = factory.getOWLNamedIndividual(medicineIndIRI);
        IRI prescribedIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "orders");
        OWLObjectProperty prescribedProp = factory.getOWLObjectProperty(prescribedIRI);

        String strengthStr = processCell(row[6]);
        IRI strengthIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "strength");
        OWLDataProperty strengthProp = factory.getOWLDataProperty(strengthIRI);

        IRI startIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "start");
        OWLDataProperty startProp = factory.getOWLDataProperty(startIRI);

        String endTimeStr = processCell(row[8]);
        IRI endIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "end");
        OWLDataProperty endProp = factory.getOWLDataProperty(endIRI);

        IRI ndcIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "ndc");
        OWLDataProperty ndcProp = factory.getOWLDataProperty(ndcIRI);


        // axioms
        Set<OWLAxiom> axioms = new HashSet<>();
        axioms.add(factory.getOWLSubClassOfAxiom(medicineClass, medicineTopClass));
        axioms.add(factory.getOWLClassAssertionAxiom(medicineClass, medicineInd));
        axioms.add(factory.getOWLClassAssertionAxiom(condClass, condInd));
        axioms.add(factory.getOWLObjectPropertyAssertionAxiom(prescribedProp, encInd, medicineInd));
        axioms.add(factory.getOWLObjectPropertyAssertionAxiom(diagnosedExperiencedProp, encInd, condInd));
        axioms.add(factory.getOWLDataPropertyAssertionAxiom(strengthProp, medicineInd, strengthStr));
        axioms.add(factory.getOWLDataPropertyAssertionAxiom(startProp, medicineInd, startTimeStr));
        axioms.add(factory.getOWLDataPropertyAssertionAxiom(endProp, medicineInd, endTimeStr));
        axioms.add(factory.getOWLDataPropertyAssertionAxiom(ndcProp, medicineInd, medicineStr));


        manager.addAxioms(ontology, axioms);
    }






    private void addICD9Classes(File file) throws OWLOntologyCreationException {
        Out.p("Loading ICD9 terminology");
        OWLOntology icd9Ontology = manager.loadOntologyFromOntologyDocument(file);
        // adding annotations
        addAnnotations(icd9Ontology);
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
