package uk.ac.man.cs.rdb2rdf.main;

import com.opencsv.CSVReader;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import java.io.*;
import java.util.*;

import static uk.ac.man.cs.rdb2rdf.main.CSV.processCell;
import static uk.ac.man.cs.rdb2rdf.main.CSV2OWLConverter.*;


/**
 * Created by slava on 04/09/17.
 */
public class DrugContraindicationMapper {

    private static final String IRI_NAME = //"http://owl.cs.manchester.ac.uk/contraindication";
                                            "http://mekon/demo.owl";
    private static final String TOP_CONTRAINDICATION = "Contraindication";
    private static final String GIVEN_CONTRAINDICATION = "GivenContraindication";
    private static final String INFERRED_CONTRAINDICATION = "InferredContraindication";


    private OWLOntologyManager manager;
    private OWLOntology ontology;
    private OWLDataFactory factory;
    private OWLReasoner reasoner;

    private Map<String, OWLClass> icd9Map;

    private Map<String, Contraindication> drug2ContrMap;

    private Map<String, Indication> drug2IndicMap;

    private double scaleFactor = 1.0;



    public DrugContraindicationMapper(File drug2contrcond, File cond2ICD, File drug2cat,
                                      File drug2indiccond, File drug2id, double scaleFactor)
            throws OWLOntologyCreationException, IOException {
        // create an ontology
        IRI iri = IRI.create(IRI_NAME);
        manager = OWLManager.createOWLOntologyManager();
        ontology = manager.createOntology(iri);
        factory = manager.getOWLDataFactory();
        this.scaleFactor = scaleFactor;
        initContraindications(drug2contrcond, cond2ICD, drug2cat);
        initIndications(drug2indiccond, drug2id, drug2cat);
    }

    private void initIndications(File drug2indiccond, File drug2id, File drug2cat) throws IOException {
        Map<String, String> mcode2codeMap = new HashMap<>();
        Map<String, String> mcode2nameMap = new HashMap<>();
        Map<String, String> mcode2orderMap = new HashMap<>();
        CSVReader reader = new CSVReader(new FileReader(drug2id));
        List<String[]> drug2codeRows = reader.readAll();
        reader.close();
        drug2codeRows.remove(0);
        for (String[] row : drug2codeRows) {
            String code = processCell(row[1]);
            String multumCode = processCell(row[3]);
            String name = processCell(row[4]);
            mcode2codeMap.put(multumCode, code);
            mcode2nameMap.put(multumCode, name);
            // order
            String order = (row[5].isEmpty() ? "" : "form=" + row[5]);
            order += (row[7].isEmpty() ? "" : " | dose=" + row[7]);
            order += row[8];
            order +=(row[11].isEmpty() ? "" : " | quan=" + row[11]);
            order += row[12];
            order += (row[15].isEmpty() ? "" : " | route=" + row[15]);
            order += (row[16].isEmpty() ? "" : " | freq=" + row[16]);
            order += (row[17].isEmpty() ? "" : " | dur=" + row[17]);
            order += row[18];
            mcode2orderMap.put(multumCode, order);
        }

        reader = new CSVReader(new FileReader(drug2indiccond));
        List<String[]> drug2condRows = reader.readAll();
        reader.close();
        drug2condRows.remove(0);

        drug2IndicMap = new HashMap<>();
        for (String[] row : drug2condRows) {
            if (Math.random() > scaleFactor) {
                continue;
            }
            String multumCode = processCell(row[2]);
            String code = mcode2codeMap.get(multumCode);
            if (code == null) {
                continue;
            }
            Indication indic;
            if (drug2IndicMap.containsKey(code)) {
                indic = drug2IndicMap.get(code);
            } else {
                indic = new Indication();
                indic.drugCode = code;
                indic.drugName = mcode2nameMap.get(multumCode);
                indic.conditions = new HashSet<>();
                indic.orders = new HashSet<>();
                drug2IndicMap.put(indic.drugCode, indic);
            }
            String conditionId = processCell(row[0]);
            indic.conditions.add(conditionId);
            indic.orders.add(mcode2orderMap.get(multumCode));
        }

        // map drugs to categories
        reader = new CSVReader(new FileReader(drug2cat));
        List<String[]> drug2catRows = reader.readAll();
        reader.close();
        drug2catRows.remove(0);
        for (String[] row : drug2catRows) {
            String drugCode = processCell(row[0]);
            Indication indic = drug2IndicMap.get(drugCode);
            if (indic != null) {
                indic.drugCategory = processCell(row[3]);
            }
        }
    }

    private void initContraindications(File drug2contrcondFile, File cond2ICDFile, File drug2catFile) throws IOException {
        // map conditions to ICD
        Map<String, Set<String>> cond2ICDMap = new HashMap<>();
        CSVReader reader = new CSVReader(new FileReader(cond2ICDFile));
        List<String[]> cond2ICDRows = reader.readAll();
        reader.close();
        cond2ICDRows.remove(0);
        for (String[] row : cond2ICDRows) {
            String condID = processCell(row[2]);
            Set<String> codes = cond2ICDMap.get(condID);
            if (codes == null) {
                codes = new HashSet<>();
                cond2ICDMap.put(condID, codes);
            }
            String code = processCell(row[0]);
            codes.add(code);
        }

        // map drugs to ICD codes
        reader = new CSVReader(new FileReader(drug2contrcondFile));
        List<String[]> drug2condRows = reader.readAll();
        reader.close();
        drug2condRows.remove(0);
        drug2ContrMap = new HashMap<>();
        for (String[] row : drug2condRows) {
            if (Math.random() > scaleFactor) {
                continue;
            }
            String drugCode = processCell(row[1]);
            Contraindication contr;
            if (drug2ContrMap.containsKey(drugCode)) {
                contr = drug2ContrMap.get(drugCode);
            } else {
                contr = new Contraindication();
                contr.drugCode = drugCode;
                contr.drugName = processCell(row[2]);
                contr.conditions = new HashSet<>();
                drug2ContrMap.put(contr.drugCode, contr);
            }
            String conditionId = processCell(row[3]);
            if (cond2ICDMap.containsKey(conditionId)) {
                contr.conditions.addAll(cond2ICDMap.get(conditionId));
            }
            String severity = processCell(row[7]);
            if (severity.contains("Minimal")) {
                contr.severity = "Minimal";
            } else if (severity.contains("Moderate")) {
                contr.severity = "Moderate";
            } else if (severity.contains("High")) {
                contr.severity = "High";
            } else {
                contr.severity = severity;
            }
        }

        // map drugs to categories
        reader = new CSVReader(new FileReader(drug2catFile));
        List<String[]> drug2catRows = reader.readAll();
        reader.close();
        drug2catRows.remove(0);
        for (String[] row : drug2catRows) {
            String drugCode = processCell(row[0]);
            Contraindication contr = drug2ContrMap.get(drugCode);
            if (contr != null) {
                contr.drugCategory = processCell(row[3]);
            }
        }

    }


    private void addMekonOntology(File file) throws OWLOntologyCreationException {
        Out.p("Loading Mekon template");
        OWLOntology mekonOntology = manager.loadOntologyFromOntologyDocument(file);
        manager.addAxioms(ontology, mekonOntology.getAxioms());
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
                if (!clAxiom.getSuperClass().equals(factory.getOWLThing())) {
                    classAxioms.add(clAxiom);
                }
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


    private void computeClassHierarchy() throws Exception {
        // process the ontology by the reasoner
        Out.p("\nInitialising the reasoner");
        reasoner = ReasonerLoader.initReasoner(ontology);
        reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
    }


    private void createMekonOntology(File ontFile) throws IOException, OWLOntologyStorageException {
        // populate the ontology
        // classes
        IRI topDrugClassIRI = IRI.create(IRI_NAME + IRI_DELIMITER + TOP_MEDICINE);
        OWLClass topDrugClass = factory.getOWLClass(topDrugClassIRI);
        IRI topCondClassIRI = IRI.create(IRI_NAME + IRI_DELIMITER + TOP_DISEASE);
        OWLClass topCondClass = factory.getOWLClass(topCondClassIRI);
        IRI topPatientClassIRI = IRI.create(IRI_NAME + IRI_DELIMITER + TOP_PATIENT);
        OWLClass topPatientClass = factory.getOWLClass(topPatientClassIRI);
        IRI topClassIRI = IRI.create(IRI_NAME + IRI_DELIMITER + TOP_CLASS);
        OWLClass topClass = factory.getOWLClass(topClassIRI);

        manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(topDrugClass, topClass));
        manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(topCondClass, topClass));
        manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(topPatientClass, topClass));

        Set<OWLClass> topCondClasses = reasoner.getSubClasses(
                factory.getOWLThing(), true).getFlattened();
        for (OWLClass cl : topCondClasses) {
            manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(cl, topCondClass));
        }

        // properties
        IRI hasContrDrugIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "hasContraindicatedDrug");
        OWLObjectProperty hasContrDrugProp = factory.getOWLObjectProperty(hasContrDrugIRI);
        IRI hasDrugIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "hasIndicatedDrug");
        OWLObjectProperty hasDrugProp = factory.getOWLObjectProperty(hasDrugIRI);
        IRI hasConditionIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "hasCondition");
        OWLObjectProperty hasConditionProp = factory.getOWLObjectProperty(hasConditionIRI);
        IRI hasOrderIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "hasOrder");
        OWLObjectProperty hasOrderProp = factory.getOWLObjectProperty(hasOrderIRI);
        OWLAnnotationProperty labelProp = factory.getRDFSLabel();

        IRI topPropIRI = IRI.create(IRI_NAME + IRI_DELIMITER + TOP_PROPERTY);
        OWLObjectProperty topProp = factory.getOWLObjectProperty(topPropIRI);
        manager.addAxiom(ontology, factory.getOWLSubObjectPropertyOfAxiom(hasContrDrugProp, topProp));
        manager.addAxiom(ontology, factory.getOWLSubObjectPropertyOfAxiom(hasDrugProp, topProp));
        manager.addAxiom(ontology, factory.getOWLSubObjectPropertyOfAxiom(hasConditionProp, topProp));
        manager.addAxiom(ontology, factory.getOWLSubObjectPropertyOfAxiom(hasOrderProp, topProp));



        OWLClassExpression onlyContrDrugExpr = factory.getOWLObjectAllValuesFrom(hasContrDrugProp, topDrugClass);
        OWLClassExpression onlyDrugExpr = factory.getOWLObjectAllValuesFrom(hasDrugProp, topDrugClass);
        OWLClassExpression onlyCondExpr = factory.getOWLObjectAllValuesFrom(hasConditionProp, topCondClass);
        manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(topPatientClass, onlyContrDrugExpr));
        manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(topPatientClass, onlyDrugExpr));
        manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(topPatientClass, onlyCondExpr));

        // indications
        createIndications(labelProp, topDrugClass, topPatientClass, hasConditionProp,
                hasDrugProp, topClass, hasOrderProp);

        // contraindications
        createContraindications(labelProp, topDrugClass, topPatientClass, hasConditionProp,
                hasContrDrugProp, topClass, topProp);

        // inconsistency checks
        createInconsistencyChecks(topPatientClass, hasDrugProp, hasContrDrugProp);

        // save the ontology
        if (!ontFile.exists()) {
            ontFile.createNewFile();
        }

        FileOutputStream outputStream = new FileOutputStream(ontFile);
        BufferedOutputStream buffOutputStream = new BufferedOutputStream(outputStream);
        manager.saveOntology(ontology, buffOutputStream);
    }



    private void createIndications(OWLAnnotationProperty labelProp, OWLClass topDrugClass,
                                   OWLClass topPatientClass, OWLObjectProperty hasConditionProp,
                                   OWLObjectProperty hasDrugProp, OWLClass topClass,
                                   OWLObjectProperty hasOrderProp) {

        IRI topOrderIRI = IRI.create(IRI_NAME + IRI_DELIMITER + TOP_ORDER);
        OWLClass topOrderClass = factory.getOWLClass(topOrderIRI);
        manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(topOrderClass, topClass));

        for (String drugCode : drug2IndicMap.keySet()) {

            Indication indic = drug2IndicMap.get(drugCode);

            // drug class
            IRI drugClassIRI = IRI.create(IRI_NAME + IRI_DELIMITER + drugCode);
            OWLClass drugClass = factory.getOWLClass(drugClassIRI);

            // drug code
            manager.addAxiom(ontology,
                    factory.getOWLAnnotationAssertionAxiom(
                            labelProp, drugClass.getIRI(), factory.getOWLLiteral(indic.drugName)));

            // categories
            if (indic.drugCategory != null) {
                IRI drugCategoryIRI = IRI.create(IRI_NAME + IRI_DELIMITER + indic.drugCategory);
                OWLClass drugCategoryClass = factory.getOWLClass(drugCategoryIRI);
                manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(drugCategoryClass, topDrugClass));
                manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(drugClass, drugCategoryClass));
            }

            // conditions
            Set<String> condIDs = indic.conditions;
            for (String condID : condIDs) {
                OWLClass condClass = findICD9Class(condID);
                if (condClass == null) {
                    continue;
                }

                // patient
                String condName = null;
                for (OWLAnnotationAssertionAxiom ann : ontology.getAnnotationAssertionAxioms(condClass.getIRI())) {
                    condName = ann.getValue().asLiteral().get().getLiteral();
                    break;
                }
                IRI patientIRI = IRI.create(IRI_NAME + IRI_DELIMITER
                        + TOP_PATIENT + ENTITY_DELIMITER + condClass.getIRI().getShortForm());
                OWLClass patientClass = factory.getOWLClass(patientIRI);
                manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(patientClass, topPatientClass));
                manager.addAxiom(ontology, factory.getOWLAnnotationAssertionAxiom(labelProp, patientClass.getIRI(),
                        factory.getOWLLiteral("Patient with " + condName)));

                OWLClassExpression patientExpr = factory.getOWLObjectIntersectionOf(
                        topPatientClass,
                        factory.getOWLObjectSomeValuesFrom(hasConditionProp, condClass));

                manager.addAxiom(ontology, factory.getOWLEquivalentClassesAxiom(patientClass, patientExpr));

                // orders
                Set<String> orders = indic.orders;
                int count = 0;
                for (String order : orders) {
                    count++;
                    IRI orderIRI = IRI.create(IRI_NAME + IRI_DELIMITER + indic.drugName
                            + ENTITY_DELIMITER + "order" + ENTITY_DELIMITER + count);
                    OWLClass orderClass = factory.getOWLClass(orderIRI);
                    manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(orderClass, topOrderClass));

                    manager.addAxiom(ontology,
                            factory.getOWLAnnotationAssertionAxiom(
                                    labelProp, orderClass.getIRI(), factory.getOWLLiteral(order)));

                    OWLClassExpression orderExpr = factory.getOWLObjectSomeValuesFrom(hasOrderProp, orderClass);
                    OWLClassExpression drugExpr = factory.getOWLObjectSomeValuesFrom(hasDrugProp,
                            factory.getOWLObjectIntersectionOf(drugClass, orderExpr));
                    manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(patientClass, drugExpr));
                }

            }

        }
    }


    private void createContraindications(OWLAnnotationProperty labelProp, OWLClass topDrugClass,
                                         OWLClass topPatientClass, OWLObjectProperty hasConditionProp,
                                         OWLObjectProperty hasContrDrugProp, OWLClass topClass,
                                         OWLObjectProperty topProp
                                         ) {

        IRI topPlausClassIRI = IRI.create(IRI_NAME + IRI_DELIMITER + TOP_PLAUSIBILITY);
        OWLClass topPlausClass = factory.getOWLClass(topPlausClassIRI);
        IRI lowPlausClassIRI = IRI.create(IRI_NAME + IRI_DELIMITER + LOW_PLAUSIBILITY);
        OWLClass lowPlausClass = factory.getOWLClass(lowPlausClassIRI);
        IRI mediumPlausClassIRI = IRI.create(IRI_NAME + IRI_DELIMITER + MEDIUM_PLAUSIBILITY);
        OWLClass mediumPlausClass = factory.getOWLClass(mediumPlausClassIRI);
        IRI highPlausClassIRI = IRI.create(IRI_NAME + IRI_DELIMITER + HIGH_PLAUSIBILITY);
        OWLClass highPlausClass = factory.getOWLClass(highPlausClassIRI);
        IRI hasSeverityIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "hasContraindicationPlausibility");
        OWLObjectProperty hasSeverityProp = factory.getOWLObjectProperty(hasSeverityIRI);

        manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(topPlausClass, topClass));
        manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(lowPlausClass, topPlausClass));
        manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(mediumPlausClass, lowPlausClass));
        manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(highPlausClass, mediumPlausClass));

        manager.addAxiom(ontology, factory.getOWLSubObjectPropertyOfAxiom(hasSeverityProp, topProp));



        for (String drugCode : drug2ContrMap.keySet()) {

            Contraindication contr = drug2ContrMap.get(drugCode);

            // drug class
            IRI drugClassIRI = IRI.create(IRI_NAME + IRI_DELIMITER + drugCode);
            OWLClass drugClass = factory.getOWLClass(drugClassIRI);

            // drug code
            manager.addAxiom(ontology,
                    factory.getOWLAnnotationAssertionAxiom(
                            labelProp, drugClass.getIRI(), factory.getOWLLiteral(contr.drugName)));

            // categories
            if (contr.drugCategory != null) {
                IRI drugCategoryIRI = IRI.create(IRI_NAME + IRI_DELIMITER + contr.drugCategory);
                OWLClass drugCategoryClass = factory.getOWLClass(drugCategoryIRI);
                manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(drugCategoryClass, topDrugClass));
                manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(drugClass, drugCategoryClass));
            }

            // conditions
            Set<String> condIDs = contr.conditions;
            for (String condID : condIDs) {
                OWLClass condClass = findICD9Class(condID);
                if (condClass == null) {
                    continue;
                }

                // patient
                String condName = null;
                for (OWLAnnotationAssertionAxiom ann : ontology.getAnnotationAssertionAxioms(condClass.getIRI())) {
                    condName = ann.getValue().asLiteral().get().getLiteral();
                    break;
                }
                IRI patientIRI = IRI.create(IRI_NAME + IRI_DELIMITER
                        + TOP_PATIENT + ENTITY_DELIMITER + condClass.getIRI().getShortForm());
                OWLClass patientClass = factory.getOWLClass(patientIRI);
                manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(patientClass, topPatientClass));
                manager.addAxiom(ontology, factory.getOWLAnnotationAssertionAxiom(labelProp, patientClass.getIRI(),
                        factory.getOWLLiteral("Patient with " + condName)));

                OWLClassExpression patientExpr = factory.getOWLObjectIntersectionOf(
                        topPatientClass,
                        factory.getOWLObjectSomeValuesFrom(hasConditionProp, condClass));

                manager.addAxiom(ontology, factory.getOWLEquivalentClassesAxiom(patientClass, patientExpr));

                // implication
                OWLClass plausClass = null;
                if (contr.severity.equals("Minimal")) {
                    plausClass = lowPlausClass;
                } else if (contr.severity.equals("Moderate")) {
                    plausClass = mediumPlausClass;
                } else if (contr.severity.equals("High")) {
                    plausClass = highPlausClass;
                } else {
                    System.out.println("Wrong plausibility : " + contr.severity);
                }

                OWLClassExpression plausExpr = factory.getOWLObjectIntersectionOf(drugClass,
                        factory.getOWLObjectSomeValuesFrom(hasSeverityProp, plausClass));
                OWLClassExpression drugExpr = factory.getOWLObjectSomeValuesFrom(hasContrDrugProp, plausExpr);
                manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(patientClass, drugExpr));

            }
        }
    }



    private void createInconsistencyChecks(OWLClass topPatientClass,
                                           OWLObjectProperty hasDrugProp,
                                           OWLObjectProperty hasContrDrugProp) {
        Set<String> drugCodes = new HashSet<>(drug2IndicMap.keySet());
        drugCodes.retainAll(drug2ContrMap.keySet());
        for (String drugCode : drugCodes) {
            IRI drugClassIRI = IRI.create(IRI_NAME + IRI_DELIMITER + drugCode);
            OWLClass drugClass = factory.getOWLClass(drugClassIRI);
            OWLClassExpression indicExpr = factory.getOWLObjectSomeValuesFrom(hasDrugProp, drugClass);
            OWLClassExpression contrExpr = factory.getOWLObjectSomeValuesFrom(hasContrDrugProp, drugClass);
            OWLClassExpression conjExpr = factory.getOWLObjectIntersectionOf(topPatientClass, indicExpr, contrExpr);
            manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(conjExpr, factory.getOWLNothing()));
        }
    }




    private void createMappingOntology(File ontFile) throws IOException, OWLOntologyStorageException {
        // populate the ontology
        // classes
        IRI topDrugClassIRI = IRI.create(IRI_NAME + IRI_DELIMITER + TOP_MEDICINE);
        OWLClass topDrugClass = factory.getOWLClass(topDrugClassIRI);
        IRI topContrClassIRI = IRI.create(IRI_NAME + IRI_DELIMITER + TOP_CONTRAINDICATION);
        OWLClass topContrClass = factory.getOWLClass(topContrClassIRI);
        IRI givenContrClassIRI = IRI.create(IRI_NAME + IRI_DELIMITER + GIVEN_CONTRAINDICATION);
        OWLClass givenContrClass = factory.getOWLClass(givenContrClassIRI);
        IRI inferredContrClassIRI = IRI.create(IRI_NAME + IRI_DELIMITER + INFERRED_CONTRAINDICATION);
        OWLClass inferredContrClass = factory.getOWLClass(inferredContrClassIRI);
        manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(givenContrClass, topContrClass));
        manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(inferredContrClass, topContrClass));

        // properties
        IRI hasDrugIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "drug");
        OWLObjectProperty hasDrugProp = factory.getOWLObjectProperty(hasDrugIRI);
        IRI hasConditionIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "condition");
        OWLObjectProperty hasConditionProp = factory.getOWLObjectProperty(hasConditionIRI);
        IRI hasSeverityIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "plausibility");
        OWLDataProperty hasSeverityProp = factory.getOWLDataProperty(hasSeverityIRI);
        OWLAnnotationProperty labelProp = factory.getRDFSLabel();


        Set<OWLClass> cls1 = new HashSet<>();
        Set<OWLClass> cls2 = new HashSet<>();
        Map<OWLNamedIndividual, OWLClass> contrCondMap = new HashMap<>();
        for (String drugCode : drug2ContrMap.keySet()) {

            Contraindication contr = drug2ContrMap.get(drugCode);

            // drugName
            IRI drugIndIRI = IRI.create(IRI_NAME + IRI_DELIMITER + drugCode);
            OWLNamedIndividual drugInd = factory.getOWLNamedIndividual(drugIndIRI);
            manager.addAxiom(ontology, factory.getOWLClassAssertionAxiom(topDrugClass, drugInd));

            // drugName code
            manager.addAxiom(ontology,
                    factory.getOWLAnnotationAssertionAxiom(
                            labelProp, drugInd.getIRI(), factory.getOWLLiteral(contr.drugName)));

            // categories
            if (contr.drugCategory != null) {
                IRI drugCategoryIRI = IRI.create(IRI_NAME + IRI_DELIMITER + contr.drugCategory);
                OWLClass drugCategoryClass = factory.getOWLClass(drugCategoryIRI);
                manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(drugCategoryClass, topDrugClass));
                manager.addAxiom(ontology, factory.getOWLClassAssertionAxiom(drugCategoryClass, drugInd));
            }

            // conditions
            Set<String> condIDs = contr.conditions;
            for (String condID : condIDs) {
                OWLClass condClass = findICD9Class(condID);
                if (condClass == null) {
                    continue;
                }
                cls1.add(condClass);
                Set<OWLClass> condClasses = new HashSet<>(
                        reasoner.getSubClasses(condClass, false).getFlattened()
                );
                condClasses.remove(factory.getOWLNothing());
                condClasses.add(condClass);

                for (OWLClass subCl : condClasses) {
                    cls2.add(subCl);
                    IRI condIndIRI = IRI.create(IRI_NAME + IRI_DELIMITER +
                            subCl.getIRI().getShortForm());
                    OWLNamedIndividual condInd = factory.getOWLNamedIndividual(condIndIRI);

                    // axioms
                    manager.addAxiom(ontology, factory.getOWLClassAssertionAxiom(subCl, condInd));

                    IRI contrIRI = IRI.create(IRI_NAME + IRI_DELIMITER +
                            subCl.getIRI().getShortForm() + ENTITY_DELIMITER + drugCode);
                    OWLNamedIndividual contrInd = factory.getOWLNamedIndividual(contrIRI);

                    manager.addAxiom(ontology, factory.getOWLObjectPropertyAssertionAxiom(
                            hasDrugProp, contrInd, drugInd));
                    manager.addAxiom(ontology, factory.getOWLObjectPropertyAssertionAxiom(
                            hasConditionProp, contrInd, condInd));

                    manager.addAxiom(ontology, factory.getOWLDataPropertyAssertionAxiom(
                            hasSeverityProp, contrInd, contr.severity));

                    // annotation
                    Set<OWLAnnotationAssertionAxiom> condAnnots =
                            ontology.getAnnotationAssertionAxioms(subCl.getIRI());
                    OWLLiteral condName = null;
                    for (OWLAnnotationAssertionAxiom ann : condAnnots) {
                        condName = ann.getValue().asLiteral().get();
                        break;
                    }
                    if (condName != null) {
                        manager.addAxiom(ontology,
                                factory.getOWLAnnotationAssertionAxiom(
                                        labelProp, condInd.getIRI(), condName));
                    }

                    contrCondMap.put(contrInd, subCl);
                }
            }
        }

        for (OWLNamedIndividual contrInd : contrCondMap.keySet()) {
            OWLClass cl = contrCondMap.get(contrInd);
            if (cls1.contains(cl)) {
                manager.addAxiom(ontology, factory.getOWLClassAssertionAxiom(givenContrClass, contrInd));
            } else {
                manager.addAxiom(ontology, factory.getOWLClassAssertionAxiom(inferredContrClass, contrInd));
            }
        }

        Out.p((cls2.size() - cls1.size()) + " out of " + cls2.size()
                + "  conditions are inferred to have contraindications");

        // save the ontology
        if (!ontFile.exists()) {
            ontFile.createNewFile();
        }

        FileOutputStream outputStream = new FileOutputStream(ontFile);
        BufferedOutputStream buffOutputStream = new BufferedOutputStream(outputStream);
        manager.saveOntology(ontology, buffOutputStream);
    }





    private OWLClass findICD9Class(String code) {
        return icd9Map.get(code);
    }


    public static void main(String args[])
            throws Exception {
        DrugContraindicationMapper converter = new DrugContraindicationMapper(
                new File(args[0]), new File(args[1]), new File(args[2]),
                new File(args[3]), new File(args[4]), 1.0);
        converter.addICD9Ontology(new File(args[5]));
        converter.computeClassHierarchy();
        converter.createMekonOntology(new File(args[6]));
    }




}
